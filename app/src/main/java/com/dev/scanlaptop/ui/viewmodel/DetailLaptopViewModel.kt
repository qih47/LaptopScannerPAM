package com.dev.scanlaptop.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dev.scanlaptop.data.HistoryLog
import com.dev.scanlaptop.data.LaptopDetail
import com.dev.scanlaptop.data.UserData
import com.dev.scanlaptop.data.local.AppDatabase
import com.dev.scanlaptop.data.local.entity.ScanQueueEntity
import com.dev.scanlaptop.data.repository.LaptopRepository
import com.dev.scanlaptop.data.repository.MonitoringRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.UnknownHostException
import java.io.IOException

sealed class SaveResult {
    data class Success(val status: String) : SaveResult()
    data class Error(val message: String) : SaveResult()
    object OfflineQueued : SaveResult() // Tanda bahwa data masuk ke antrean offline
}

class DetailLaptopViewModel(application: Application) : AndroidViewModel(application) {

    private val laptopRepository = LaptopRepository()
    private val monitoringRepository = MonitoringRepository()
    private val database = AppDatabase.getDatabase(application)
    
    // ─── State ────────────────────────────────────────────────
    private val _laptop = MutableStateFlow<LaptopDetail?>(null)
    val laptop: StateFlow<LaptopDetail?> = _laptop.asStateFlow()

    private val _logs = MutableStateFlow<List<HistoryLog>>(emptyList())
    val logs: StateFlow<List<HistoryLog>> = _logs.asStateFlow()

    private val _qrHistory = MutableStateFlow<List<LaptopDetail>>(emptyList())
    val qrHistory: StateFlow<List<LaptopDetail>> = _qrHistory.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _saveResult = MutableStateFlow<SaveResult?>(null)
    val saveResult: StateFlow<SaveResult?> = _saveResult.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // ─────────────────────────────────────────────────────────
    // PUBLIC ACTIONS
    // ─────────────────────────────────────────────────────────

    /**
     * Load data laptop dan riwayatnya.
     * Otomatis deteksi apakah laptopUuid adalah UUID atau QR code.
     */
    fun loadData(laptopUuid: String) {
        // Mencegah reload jika data yang sama sudah ada (misal saat back dari LogDetailScreen)
        if (_laptop.value != null && (_laptop.value?.uuid == laptopUuid || _laptop.value?.no_registrasi == laptopUuid)) {
            return
        }
        
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val isUuid = laptopUuid.contains("-") && laptopUuid.length > 20
                val laptopResult = if (isUuid) {
                    _qrHistory.value = emptyList()
                    laptopRepository.getLaptopByUuid(laptopUuid)
                } else {
                    val allLaptops = laptopRepository.getAllLaptopsByQr(laptopUuid)
                    if (allLaptops.isEmpty()) throw Exception("QR Code tidak ditemukan")
                    _qrHistory.value = allLaptops.drop(1)
                    allLaptops.first()
                }
                _laptop.value = laptopResult

                // Load history berdasarkan UUID laptop (selalu UUID, bukan QR)
                val laptopActualUuid = laptopResult.uuid ?: ""
                if (laptopActualUuid.isNotBlank()) {
                    _logs.value = monitoringRepository.fetchHistoryByLaptop(laptopActualUuid)
                }
            } catch (e: Exception) {
                _errorMessage.value = "Gagal memuat data: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Simpan transaksi IN/OUT beserta detail perangkat.
     * Urutan: Insert header -> Insert detail -> Update status setiap perangkat.
     */
    fun confirmTransaction(
        selectedSerialNumbers: List<String>,
        keterangan: String,
        userData: UserData
    ) {
        val currentLaptop = _laptop.value ?: return
        if (selectedSerialNumbers.isEmpty()) return

        viewModelScope.launch {
            _isSaving.value = true
            _saveResult.value = null
            try {
                // Validasi: Semua perangkat yang dipilih harus memiliki status yang sama
                val firstSn = selectedSerialNumbers.first()
                val firstDev = currentLaptop.daftar_perangkat.find { it.no_seri == firstSn }
                val localStatus = firstDev?.status_terakhir ?: "OUT"

                val hasMixedStatus = selectedSerialNumbers.any { sn ->
                    val dev = currentLaptop.daftar_perangkat.find { it.no_seri == sn }
                    (dev?.status_terakhir ?: "OUT") != localStatus
                }

                if (hasMixedStatus) {
                    _saveResult.value = SaveResult.Error(
                        "Perhatian: Anda memilih perangkat dengan status berbeda (ada yang Di Dalam dan Di Luar). Harap pilih perangkat dengan status yang sama."
                    )
                    return@launch
                }

                val expectedNewStatus = if (localStatus == "IN") "OUT" else "IN"

                // [PRE-FLIGHT CHECK] Cek status aktual SEMUA perangkat dari Supabase (SOT mutlak per perangkat)
                val conflictSn = selectedSerialNumbers.firstOrNull { sn ->
                    val freshStatus = laptopRepository.getDeviceLatestStatus(sn) ?: localStatus
                    // Konflik terjadi hanya jika status aktual di Supabase sudah berubah (tidak sama dengan localStatus)
                    freshStatus != localStatus
                }

                if (conflictSn != null) {
                    _saveResult.value = SaveResult.Error(
                        "Gagal: Perangkat (S/N: $conflictSn) sudah diproses petugas lain. Silakan muat ulang data."
                    )
                    return@launch
                }

                // Simpan transaksi (insert header + insert detail)
                monitoringRepository.saveTransaction(
                    laptop = currentLaptop,
                    userData = userData,
                    selectedSerialNumbers = selectedSerialNumbers,
                    keterangan = keterangan,
                    newStatus = expectedNewStatus
                )

                // Update status di-handle langsung oleh Supabase RPC (proses_transaksi)
                // sehingga tidak perlu updateDeviceStatus terpisah dari aplikasi.

                _saveResult.value = SaveResult.Success(expectedNewStatus)
            } catch (e: UnknownHostException) {
                // Tidak ada koneksi internet, masukkan ke antrean lokal
                saveToOfflineQueue(selectedSerialNumbers, keterangan, userData)
            } catch (e: java.net.ConnectException) {
                // Koneksi ditolak / time out
                saveToOfflineQueue(selectedSerialNumbers, keterangan, userData)
            } catch (e: Exception) {
                _saveResult.value = SaveResult.Error("Gagal menyimpan: ${e.message?.take(80)}")
            } finally {
                _isSaving.value = false
            }
        }
    }

    private suspend fun saveToOfflineQueue(
        selectedSerialNumbers: List<String>,
        keterangan: String,
        userData: UserData
    ) {
        val currentLaptop = _laptop.value ?: return
        val firstSn = selectedSerialNumbers.first()
        val firstDev = currentLaptop.daftar_perangkat.find { it.no_seri == firstSn }
        val expectedNewStatus = if (firstDev?.status_terakhir == "IN") "OUT" else "IN"

        val details = selectedSerialNumbers.mapNotNull { sn ->
            val dev = currentLaptop.daftar_perangkat.find { it.no_seri == sn }
            com.dev.scanlaptop.data.model.RpcDeviceDetail(
                no_seri = sn,
                merk = dev?.merk,
                tipe = dev?.tipe
            )
        }

        val queueItem = ScanQueueEntity(
            newStatus = expectedNewStatus,
            laptopUuid = currentLaptop.uuid ?: "",
            nppPetugas = userData.npp,
            keterangan = keterangan,
            lokasi = "Gerbang Pos 2",
            perangkatDetailsJson = Json.encodeToString(details)
        )

        database.scanQueueDao().insertQueue(queueItem)
        
        // Daftarkan WorkManager untuk auto-sync saat koneksi kembali
        val constraints = androidx.work.Constraints.Builder()
            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
            .build()
            
        val syncWork = androidx.work.OneTimeWorkRequestBuilder<com.dev.scanlaptop.worker.SyncWorker>()
            .setConstraints(constraints)
            .build()
            
        androidx.work.WorkManager.getInstance(getApplication())
            .enqueueUniqueWork("OfflineSyncWork", androidx.work.ExistingWorkPolicy.REPLACE, syncWork)
            
        _saveResult.value = SaveResult.OfflineQueued
    }

    /**
     * Reset save result setelah ditangani oleh UI.
     */
    fun clearSaveResult() {
        _saveResult.value = null
    }
}
