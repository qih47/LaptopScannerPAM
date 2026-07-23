package com.dev.scanlaptop.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dev.scanlaptop.data.SessionManager
import com.dev.scanlaptop.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mindrot.jbcrypt.BCrypt

sealed class ProfileUiState {
    object Idle : ProfileUiState()
    object Loading : ProfileUiState()
    data class Success(val message: String) : ProfileUiState()
    data class Error(val message: String) : ProfileUiState()
    object BiometricVerified : ProfileUiState()
}

class ProfileViewModel : ViewModel() {
    private val repository = AuthRepository()
    
    private val _uiState = MutableStateFlow<ProfileUiState>(ProfileUiState.Idle)
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    fun updateFotoProfil(npp: String, fotoBase64: String, sessionManager: SessionManager) {
        viewModelScope.launch {
            _uiState.value = ProfileUiState.Loading
            try {
                repository.updateFotoProfil(npp, fotoBase64)
                sessionManager.saveFotoProfil(fotoBase64)
                _uiState.value = ProfileUiState.Success("Foto profil berhasil diperbarui!")
            } catch (e: Exception) {
                _uiState.value = ProfileUiState.Error("Gagal memperbarui foto profil.")
            }
        }
    }

    fun updatePassword(npp: String, oldPasswordPlain: String, newPasswordPlain: String) {
        viewModelScope.launch {
            _uiState.value = ProfileUiState.Loading
            try {
                // Verify old password first
                val user = repository.login(npp, oldPasswordPlain)
                if (user != null) {
                    val hashed = BCrypt.hashpw(newPasswordPlain, BCrypt.gensalt())
                    repository.updatePassword(npp, hashed)
                    _uiState.value = ProfileUiState.Success("Password berhasil diubah!")
                } else {
                    _uiState.value = ProfileUiState.Error("Password lama salah!")
                }
            } catch (e: Exception) {
                _uiState.value = ProfileUiState.Error("Gagal memperbarui password.")
            }
        }
    }

    fun verifyPasswordAndEnableBiometric(npp: String, passwordPlain: String) {
        viewModelScope.launch {
            _uiState.value = ProfileUiState.Loading
            try {
                val user = repository.login(npp, passwordPlain)
                if (user != null) {
                    _uiState.value = ProfileUiState.BiometricVerified
                } else {
                    _uiState.value = ProfileUiState.Error("Password salah!")
                }
            } catch (e: Exception) {
                _uiState.value = ProfileUiState.Error("Verifikasi gagal.")
            }
        }
    }
    
    fun resetState() {
        _uiState.value = ProfileUiState.Idle
    }
}
