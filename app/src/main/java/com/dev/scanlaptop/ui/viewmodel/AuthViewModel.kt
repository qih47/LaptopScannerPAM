package com.dev.scanlaptop.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dev.scanlaptop.data.UserData
import com.dev.scanlaptop.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class AuthUiState {
    object Idle : AuthUiState()
    object Loading : AuthUiState()
    data class Success(val user: UserData) : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}

class AuthViewModel : ViewModel() {

    private val repository = AuthRepository()

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun login(npp: String, password: String) {
        if (npp.isBlank() || password.isBlank()) {
            _uiState.value = AuthUiState.Error("Isi semua data!")
            return
        }
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            try {
                val user = repository.login(npp, password)
                _uiState.value = if (user != null) {
                    // Beritahu Supabase versi app saat ini agar RLS membuka kunci data
                    repository.updateAppVersionCode(user.npp, com.dev.scanlaptop.BuildConfig.VERSION_CODE)
                    AuthUiState.Success(user)
                } else {
                    AuthUiState.Error("NPP atau Password Salah!")
                }
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Error("Gagal koneksi server")
            }
        }
    }

    fun resetState() {
        _uiState.value = AuthUiState.Idle
    }
}
