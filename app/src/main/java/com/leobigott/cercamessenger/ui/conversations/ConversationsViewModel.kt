package com.leobigott.cercamessenger.ui.conversations

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.leobigott.cercamessenger.core.model.Conversation
import com.leobigott.cercamessenger.data.local.CercaDatabase
import com.leobigott.cercamessenger.data.local.toConversation
import com.leobigott.cercamessenger.protocol.DeviceIdentityStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class ConversationsUiState(
    val meshStatus: String = "Offline mesh active",
    val discoveryStatus: String = "Searching for nearby devices",
    val conversations: List<Conversation> = emptyList()
)

class ConversationsViewModel(
    context: Context,
    database: CercaDatabase = CercaDatabase.getInstance(context.applicationContext)
) : ViewModel() {
    private val localNodeId = DeviceIdentityStore.getOrCreateNodeId(context.applicationContext)

    val uiState: StateFlow<ConversationsUiState> = database.messageDao()
        .observeConversationProjections(localNodeId)
        .map { rows ->
            ConversationsUiState(
                conversations = rows.map { it.toConversation() }
            )
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            ConversationsUiState()
        )
}

class ConversationsViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ConversationsViewModel(context) as T
    }
}
