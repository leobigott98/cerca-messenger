package com.leobigott.cercamessenger.ui.chat.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.leobigott.cercamessenger.core.design.theme.CercaColors

@Composable
fun MessageInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSendClick: () -> Unit,
    enabled: Boolean
) {
    Surface(
        color = CercaColors.Surface,
        modifier = Modifier
            .navigationBarsPadding(),
        ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                enabled = enabled,
                placeholder = { androidx.compose.material3.Text("Write an offline message...") },
                shape = RoundedCornerShape(22.dp)
            )
            IconButton(onClick = onSendClick, enabled = enabled && value.isNotBlank()) {
                Icon(Icons.Default.Send, contentDescription = "Send", tint = CercaColors.Primary)
            }
        }
    }
}
