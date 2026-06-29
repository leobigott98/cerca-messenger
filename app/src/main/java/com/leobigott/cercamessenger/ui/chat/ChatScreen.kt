package com.leobigott.cercamessenger.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.leobigott.cercamessenger.core.design.theme.CercaColors
import com.leobigott.cercamessenger.ui.chat.components.MessageBubble
import com.leobigott.cercamessenger.ui.chat.components.MessageInputBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Text(
                        text = state.peerName,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    Text(
                        text = if (state.peerIsNearby) "Nearby" else "Offline",
                        color = if (state.peerIsNearby) CercaColors.Success else CercaColors.Muted,
                        modifier = Modifier.padding(end = 16.dp)
                    )
                }
            )
        },
        bottomBar = {
            MessageInputBar(
                value = state.inputText,
                onValueChange = viewModel::onInputChange,
                onSendClick = viewModel::onSendClick,
                enabled = !state.isSending
            )
        }
    ) { padding ->
        val listState = rememberLazyListState()
        LaunchedEffect(state.messages.size) {
            if (state.messages.isNotEmpty()) {
                listState.scrollToItem(state.messages.lastIndex)
            }
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            state = listState,
        ) {
            items(state.messages, key = { it.id }) { message ->
                MessageBubble(message = message)
            }
        }
    }
}
