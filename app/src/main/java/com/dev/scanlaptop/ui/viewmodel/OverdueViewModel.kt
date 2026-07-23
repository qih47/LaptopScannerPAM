package com.dev.scanlaptop.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dev.scanlaptop.data.repository.OverdueItem
import com.dev.scanlaptop.data.repository.OverdueRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OverdueViewModel : ViewModel() {

    private val repository = OverdueRepository()

    private val _overdueList = MutableStateFlow<List<OverdueItem>>(emptyList())
    val overdueList: StateFlow<List<OverdueItem>> = _overdueList.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _overdueCount = MutableStateFlow(0)
    val overdueCount: StateFlow<Int> = _overdueCount.asStateFlow()

    // Threshold yang bisa diubah dari UI
    private val _thresholdHours = MutableStateFlow(8)
    val thresholdHours: StateFlow<Int> = _thresholdHours.asStateFlow()

    // Filter tipe (ALL, IN, OUT)
    private val _filterType = MutableStateFlow("ALL")
    val filterType: StateFlow<String> = _filterType.asStateFlow()

    private var hasLoadedInitial = false

    fun loadOverdue(thresholdHours: Int = _thresholdHours.value, type: String = _filterType.value, isRefreshAction: Boolean = false) {
        if (!isRefreshAction && hasLoadedInitial && _thresholdHours.value == thresholdHours && _filterType.value == type) return
        
        hasLoadedInitial = true
        _thresholdHours.value = thresholdHours
        _filterType.value = type
        viewModelScope.launch {
            if (isRefreshAction) {
                _isRefreshing.value = true
                kotlinx.coroutines.delay(800) // Delay buat pull-to-refresh
            } else {
                _overdueList.value = emptyList() // Kosongkan list agar shimmer skeleton bisa muncul
                _isLoading.value = true
                kotlinx.coroutines.delay(800) // Delay buat initial skeleton loading
            }
            
            val items = repository.fetchOverdueItems(thresholdHours)
            _overdueList.value = if (type == "ALL") items else items.filter { it.type == type }
            _overdueCount.value = items.size
            
            _isLoading.value = false
            _isRefreshing.value = false
        }
    }

    fun refresh() = loadOverdue(isRefreshAction = true)
}
