package com.dev.scanlaptop.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dev.scanlaptop.data.HistoryLog
import com.dev.scanlaptop.data.repository.MonitoringRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OfficerHistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val monitoringRepository = MonitoringRepository()

    private val _logs = MutableStateFlow<List<HistoryLog>>(emptyList())
    val logs: StateFlow<List<HistoryLog>> = _logs.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun loadHistory(npp: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val data = monitoringRepository.fetchHistoryByPetugas(npp)
                _logs.value = data
            } catch (e: Exception) {
                _errorMessage.value = "Gagal memuat riwayat pindaian: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
}
