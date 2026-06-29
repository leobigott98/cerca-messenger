package com.leobigott.cercamessenger.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.leobigott.cercamessenger.data.local.CercaDatabase
import com.leobigott.cercamessenger.protocol.ProtocolEngine
import com.leobigott.cercamessenger.protocol.ProtocolEngineProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed class SettingsActionResult {
    data object Idle : SettingsActionResult()
    data class Done(val message: String) : SettingsActionResult()
    data class Error(val message: String) : SettingsActionResult()
}

data class SettingsUiState(
    val isBusy: Boolean = false,
    val result: SettingsActionResult = SettingsActionResult.Idle
)

class SettingsViewModel(
    context: Context,
    private val database: CercaDatabase = CercaDatabase.getInstance(context.applicationContext),
    private val protocolEngine: ProtocolEngine = ProtocolEngineProvider.engine
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun deleteAllLocalMessages() = runAction("Mensajes locales borrados.") {
        database.messageDao().deleteAllMessages()
    }

    fun deleteAllLocalContacts() = runAction("Contactos locales borrados.") {
        database.contactDao().deleteAllContacts()
    }

    fun syncFirebaseNow() = runAction("Sincronización Firebase solicitada.") {
        protocolEngine.syncCloudNow()
    }

    fun clearResult() {
        _uiState.update { it.copy(result = SettingsActionResult.Idle) }
    }

    private fun runAction(successMessage: String, block: suspend () -> Unit) {
        if (_uiState.value.isBusy) return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            runCatching { block() }
                .onSuccess { _uiState.update { it.copy(isBusy = false, result = SettingsActionResult.Done(successMessage)) } }
                .onFailure { error -> _uiState.update { it.copy(isBusy = false, result = SettingsActionResult.Error(error.message ?: "Error inesperado.")) } }
        }
    }
}

class SettingsViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = SettingsViewModel(context) as T
}
