package com.leobigott.cercamessenger.ui.nearby

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leobigott.cercamessenger.core.model.DeviceNode
import com.leobigott.cercamessenger.protocol.ProtocolEngine
import com.leobigott.cercamessenger.protocol.ProtocolEngineProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NearbyUiState(
    val isDiscovering: Boolean = true,
    val isRefreshing: Boolean = false,
    val lastActionMessage: String? = null,
    val devices: List<DeviceNode> = emptyList()
)

class NearbyViewModel(
    private val protocolEngine: ProtocolEngine = ProtocolEngineProvider.engine
) : ViewModel() {
    private val _uiState = MutableStateFlow(NearbyUiState())
    val uiState: StateFlow<NearbyUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            protocolEngine.observeNearbyDevices().collect { devices ->
                _uiState.update { it.copy(devices = devices, isDiscovering = true) }
            }
        }
    }

    fun refreshNearbyNow() {
        if (_uiState.value.isRefreshing) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(isRefreshing = true, lastActionMessage = "Buscando nodos cercanos...")
            }

            runCatching {
                protocolEngine.refreshNearby()
            }.onSuccess {
                _uiState.update {
                    it.copy(isRefreshing = false, lastActionMessage = "Búsqueda de nodos solicitada.")
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isRefreshing = false,
                        lastActionMessage = error.message ?: "No se pudo refrescar Nearby."
                    )
                }
            }
        }
    }
}
