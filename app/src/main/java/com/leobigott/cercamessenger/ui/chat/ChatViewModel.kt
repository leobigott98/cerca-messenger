package com.leobigott.cercamessenger.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.leobigott.cercamessenger.core.model.OfflineMessage
import com.leobigott.cercamessenger.protocol.ProtocolEngine
import com.leobigott.cercamessenger.protocol.ProtocolEngineProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatUiState(
    val conversationId: String,
    val peerId: String,
    val peerName: String,
    val peerIsNearby: Boolean = true,
    val messages: List<OfflineMessage> = emptyList(),
    val inputText: String = "",
    val isSending: Boolean = false
)

class ChatViewModel(
    private val conversationId: String,
    private val peerId: String,
    private val peerName: String,
    private val protocolEngine: ProtocolEngine = ProtocolEngineProvider.engine
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        ChatUiState(
            conversationId = conversationId,
            peerId = peerId,
            peerName = peerName
        )
    )
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        // Cuando se abre el chat, marcamos como leídos los mensajes existentes.
        viewModelScope.launch {
            protocolEngine.markConversationRead(conversationId)
        }

        // Mientras el chat está abierto, si llega un mensaje nuevo,
        // también se marca como leído automáticamente.
        viewModelScope.launch {
            protocolEngine.observeMessages(conversationId).collect { messages ->
                val conversationMessages = messages.filter { msg ->
                    msg.conversationId == conversationId
                }

                _uiState.update {
                    it.copy(messages = conversationMessages)
                }

                protocolEngine.markConversationRead(conversationId)
            }
        }
    }

    fun onInputChange(value: String) {
        _uiState.update { it.copy(inputText = value) }
    }

    fun onSendClick() {
        val text = _uiState.value.inputText.trim()
        if (text.isEmpty()) return
        _uiState.update { it.copy(inputText = "", isSending = true) }
        viewModelScope.launch {
            protocolEngine.sendMessage(conversationId, peerId, text)
            _uiState.update { it.copy(isSending = false) }
        }
    }
}

class ChatViewModelFactory(
    private val conversationId: String,
    private val peerId: String,
    private val peerName: String
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ChatViewModel(conversationId, peerId, peerName) as T
    }
}
