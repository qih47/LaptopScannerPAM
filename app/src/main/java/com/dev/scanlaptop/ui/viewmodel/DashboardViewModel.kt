package com.dev.scanlaptop.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dev.scanlaptop.data.HistoryLog
import com.dev.scanlaptop.data.model.StatsResult
import com.dev.scanlaptop.data.repository.MonitoringRepository
import io.github.jan.supabase.realtime.RealtimeChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay

class DashboardViewModel : ViewModel() {

    private val repository = MonitoringRepository()

    // ─── State Data ───────────────────────────────────────────
    private val _historyList = MutableStateFlow<List<HistoryLog>>(emptyList())
    val historyList: StateFlow<List<HistoryLog>> = _historyList.asStateFlow()

    private val _searchResults = MutableStateFlow<List<HistoryLog>>(emptyList())
    val searchResults: StateFlow<List<HistoryLog>> = _searchResults.asStateFlow()

    private val _stats = MutableStateFlow(StatsResult())
    val stats: StateFlow<StatsResult> = _stats.asStateFlow()

    // Data khusus analytics — 7 hari penuh, tidak terpotong pagination
    private val _analyticsData = MutableStateFlow<List<HistoryLog>>(emptyList())
    val analyticsData: StateFlow<List<HistoryLog>> = _analyticsData.asStateFlow()

    private val _isAnalyticsLoading = MutableStateFlow(false)
    val isAnalyticsLoading: StateFlow<Boolean> = _isAnalyticsLoading.asStateFlow()

    // ─── State Loading ────────────────────────────────────────
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _isAppending = MutableStateFlow(false)
    val isAppending: StateFlow<Boolean> = _isAppending.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _isLastPage = MutableStateFlow(false)
    val isLastPage: StateFlow<Boolean> = _isLastPage.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // ─── State Realtime ───────────────────────────────────────
    private val _isRealtimeConnected = MutableStateFlow(false)
    val isRealtimeConnected: StateFlow<Boolean> = _isRealtimeConnected.asStateFlow()

    private val _newTransactionEvent = MutableStateFlow<HistoryLog?>(null)
    val newTransactionEvent: StateFlow<HistoryLog?> = _newTransactionEvent.asStateFlow()

    // Counter berbasis Int untuk auto-scroll agar tidak race dengan recompose
    private val _newTransactionCount = MutableStateFlow(0)
    val newTransactionCount: StateFlow<Int> = _newTransactionCount.asStateFlow()

    // ─── Filter & Pagination ──────────────────────────────────
    var currentStatusFilter: String = "ALL"
        private set
    var currentTimeFilter: String = "ALL"
        private set
    private var currentPage: Int = 0

    private var realtimeChannel: RealtimeChannel? = null

    // Antrian (Queue) untuk log realtime masuk
    private val pendingLogsQueue = Channel<HistoryLog>(Channel.UNLIMITED)

    init {
        // Coroutine untuk memproses antrian satu per satu dengan jeda
        viewModelScope.launch {
            for (newLog in pendingLogsQueue) {
                _historyList.value = listOf(newLog) + _historyList.value
                _stats.value = _stats.value.copy(
                    total = _stats.value.total + 1,
                    inCount = if (newLog.status_io == "IN") _stats.value.inCount + 1 else _stats.value.inCount,
                    outCount = if (newLog.status_io == "OUT") _stats.value.outCount + 1 else _stats.value.outCount
                )
                _analyticsData.value = listOf(newLog) + _analyticsData.value
                _newTransactionEvent.value = newLog
                _newTransactionCount.value = _newTransactionCount.value + 1
                
                // Jeda 350ms agar list tidak force close saat di-spam data banyak
                delay(350)
            }
        }
    }

    // ─────────────────────────────────────────────────────────
    // PUBLIC ACTIONS
    // ─────────────────────────────────────────────────────────

    /** Load halaman pertama data dengan filter baru. */
    fun loadHistory(
        statusFilter: String = currentStatusFilter,
        timeFilter: String = currentTimeFilter
    ) {
        currentStatusFilter = statusFilter
        currentTimeFilter = timeFilter
        currentPage = 0
        _isLastPage.value = false
        _errorMessage.value = null

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val data = repository.fetchHistory(0, statusFilter, timeFilter)
                _historyList.value = data
                _isLastPage.value = data.size < MonitoringRepository.PAGE_SIZE
                _stats.value = repository.fetchStats(timeFilter)
            } catch (e: Exception) {
                _errorMessage.value = "Gagal memuat: ${e.localizedMessage}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /** Load halaman berikutnya (infinite scroll). */
    fun loadNextPage() {
        if (_isAppending.value || _isLastPage.value) return
        viewModelScope.launch {
            _isAppending.value = true
            try {
                val nextPage = currentPage + 1
                val data = repository.fetchHistory(nextPage, currentStatusFilter, currentTimeFilter)
                if (data.isNotEmpty()) {
                    _historyList.value = _historyList.value + data
                    currentPage = nextPage
                }
                if (data.size < MonitoringRepository.PAGE_SIZE) _isLastPage.value = true
            } catch (e: Exception) {
                // Silent fail pada pagination
            } finally {
                _isAppending.value = false
            }
        }
    }

    /** Swipe-to-refresh. */
    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            currentPage = 0
            _isLastPage.value = false
            _historyList.value = emptyList()
            _stats.value = StatsResult()
            loadHistory()
            loadAnalyticsData()
            _isRefreshing.value = false
        }
    }

    /** Cari data berdasarkan keyword. */
    fun search(query: String) {
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            _isSearching.value = true
            _searchResults.value = repository.searchHistory(query, currentStatusFilter, currentTimeFilter)
            _isSearching.value = false
        }
    }

    /** Tandai log sebagai sudah dibaca dan update state lokal. */
    fun markAsRead(log: HistoryLog) {
        viewModelScope.launch {
            repository.markAsRead(log.laptop_uuid, log.created_at)
            val updatedLog = log.copy(isOpen = 1)
            updateLogInList(updatedLog)
        }
    }

    /** Load data khusus analytics — tidak dibatasi pagination, tergantung periode. */
    fun loadAnalyticsData(daysBack: Int = 7) {
        viewModelScope.launch {
            _isAnalyticsLoading.value = true
            try {
                val data = repository.fetchRecentLogs(daysBack)
                _analyticsData.value = data
            } catch (e: Exception) {
                // silent fail — analytics data tidak kritikal
            } finally {
                _isAnalyticsLoading.value = false
            }
        }
    }

    /** Reload setelah transaksi baru berhasil disimpan. */
    fun reloadAfterTransaction() {
        loadHistory()
    }

    /** Ambil transaksi pasangan (Masuk/Keluar) untuk timeline mini. */
    suspend fun getPairedTransaction(laptopUuid: String, statusIo: String, createdAt: String): HistoryLog? {
        return repository.fetchPairedTransaction(laptopUuid, statusIo, createdAt)
    }

    /** Clear event transaksi baru setelah ditangani UI. */
    fun clearNewTransactionEvent() {
        _newTransactionEvent.value = null
    }

    /**
     * Mulai subscribe ke Supabase Realtime untuk live update dashboard.
     * Dipanggil dari DashboardScreen saat composable aktif.
     */
    fun startRealtimeSubscription() {
        if (realtimeChannel != null) return // sudah subscribe di instance ini
        viewModelScope.launch {
            try {
                // Reset singleton channel lama agar filter baru bisa didaftarkan
                val existing = MonitoringRepository.liveChannel
                if (existing != null) {
                    try { existing.unsubscribe() } catch (_: Exception) {}
                    MonitoringRepository.liveChannel = null
                }

                realtimeChannel = repository.getRealtimeChannel()

                // URUTAN WAJIB (sama persis dengan RealtimeNotificationService):
                // 1. Buat flow dulu — ini mendaftarkan filter ke channel secara sinkron
                val flow = repository.subscribeToNewTransactions()

                // 2. Subscribe channel dan tunggu sampai confirmed connected
                repository.connectRealtime(blockUntilSubscribed = true)
                _isRealtimeConnected.value = true

                // 3. Baru collect — setelah channel dipastikan subscribed
                flow
                    .onEach { newLog ->
                        // Masukkan ke antrian, jangan langsung update state di sini
                        pendingLogsQueue.send(newLog)
                        _isRealtimeConnected.value = true
                    }
                    .catch { e ->
                        Log.e("DashboardVM", "Realtime flow error: ${e.message}")
                        _isRealtimeConnected.value = false
                    }
                    .launchIn(viewModelScope)

            } catch (e: Exception) {
                Log.e("DashboardVM", "startRealtimeSubscription error: ${e.message}")
                _isRealtimeConnected.value = false
            }
        }
    }

    /** Stop subscription realtime. */
    fun stopRealtimeSubscription() {
        viewModelScope.launch {
            try {
                realtimeChannel?.unsubscribe()
                realtimeChannel = null
                MonitoringRepository.liveChannel = null // reset singleton agar startRealtimeSubscription bisa subscribe ulang dengan bersih
                _isRealtimeConnected.value = false
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopRealtimeSubscription()
    }

    // ─────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────

    private fun updateLogInList(updatedLog: HistoryLog) {
        _historyList.value = _historyList.value.map { log ->
            if (log.laptop_uuid == updatedLog.laptop_uuid && log.created_at == updatedLog.created_at)
                updatedLog else log
        }
        _searchResults.value = _searchResults.value.map { log ->
            if (log.laptop_uuid == updatedLog.laptop_uuid && log.created_at == updatedLog.created_at)
                updatedLog else log
        }
    }
}
