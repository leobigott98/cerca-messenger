package com.leobigott.cercamessenger.ui.crisis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.leobigott.cercamessenger.core.model.CrisisConstants
import com.leobigott.cercamessenger.core.model.CrisisMessageType
import com.leobigott.cercamessenger.core.model.OfflineMessage
import com.leobigott.cercamessenger.protocol.ProtocolEngine
import com.leobigott.cercamessenger.protocol.ProtocolEngineProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CrisisUiState(
    val selectedType: CrisisMessageType = CrisisMessageType.NEED_HELP,
    val details: String = "",
    val location: String = "",
    val peopleAffected: String = "",
    val requiresResponse: Boolean = true,
    val isPublicBroadcast: Boolean = false,
    val isSending: Boolean = false,
    val reports: List<OfflineMessage> = emptyList(),
    val publicBroadcasts: List<OfflineMessage> = emptyList()
)

class CrisisViewModel(
    private val protocolEngine: ProtocolEngine = ProtocolEngineProvider.engine
) : ViewModel() {
    private val _uiState = MutableStateFlow(CrisisUiState())
    val uiState: StateFlow<CrisisUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            protocolEngine.observeCrisisReports().collect { messages ->
                _uiState.update {
                    it.copy(
                        reports = messages.sortedWith(
                            compareByDescending<OfflineMessage> { message -> message.receivedAt }
                                .thenByDescending { message -> message.timestamp }
                                .thenByDescending { message -> message.id }
                        )
                    )
                }
            }
        }

        viewModelScope.launch {
            protocolEngine.observePublicBroadcasts().collect { messages ->
                _uiState.update {
                    it.copy(
                        publicBroadcasts = messages.sortedWith(
                            compareByDescending<OfflineMessage> { message -> message.receivedAt }
                                .thenByDescending { message -> message.timestamp }
                                .thenByDescending { message -> message.id }
                        )
                    )
                }
            }
        }
    }

    fun selectType(type: CrisisMessageType) = _uiState.update { it.copy(selectedType = type) }
    fun onDetailsChange(value: String) = _uiState.update { it.copy(details = value) }
    fun onLocationChange(value: String) = _uiState.update { it.copy(location = value) }
    fun onPeopleAffectedChange(value: String) = _uiState.update { it.copy(peopleAffected = value.filter { ch -> ch.isDigit() }) }
    fun onRequiresResponseChange(value: Boolean) = _uiState.update { it.copy(requiresResponse = value) }
    fun onPublicBroadcastChange(value: Boolean) = _uiState.update { it.copy(isPublicBroadcast = value) }

    fun sendReport() {
        val state = _uiState.value
        if (state.isSending) return
        _uiState.update { it.copy(isSending = true) }
        viewModelScope.launch {
            if (state.isPublicBroadcast || state.selectedType == CrisisMessageType.PUBLIC_BROADCAST) {
                protocolEngine.sendPublicBroadcast(
                    text = state.details,
                    approximateLocation = state.location.takeIf { it.isNotBlank() },
                    peopleAffected = state.peopleAffected.toIntOrNull(),
                    requiresResponse = state.requiresResponse
                )
            } else {
                protocolEngine.sendCrisisMessage(
                    type = state.selectedType,
                    text = state.details,
                    approximateLocation = state.location.takeIf { it.isNotBlank() },
                    peopleAffected = state.peopleAffected.toIntOrNull(),
                    requiresResponse = state.requiresResponse
                )
            }
            _uiState.update {
                it.copy(
                    details = "",
                    location = "",
                    peopleAffected = "",
                    requiresResponse = true,
                    isPublicBroadcast = false,
                    isSending = false
                )
            }
        }
    }
}

class CrisisViewModelFactory : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = CrisisViewModel() as T
}
