package com.leobigott.cercamessenger.ui.conversations.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.leobigott.cercamessenger.core.design.components.StatusDot
import com.leobigott.cercamessenger.core.design.theme.CercaColors
import com.leobigott.cercamessenger.core.model.Conversation
import com.leobigott.cercamessenger.core.model.MessageStatus

@Composable
fun ConversationRow(conversation: Conversation, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = CercaColors.Surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatusDot(
                color = if (conversation.peerIsNearby) CercaColors.Success else CercaColors.Muted,
                modifier = Modifier.size(14.dp).clip(CircleShape)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(conversation.peerName, fontWeight = FontWeight.Bold, color = CercaColors.Text)
                Text(conversation.lastMessage, color = CercaColors.Muted)
                Text(statusLabel(conversation.lastStatus), color = statusColor(conversation.lastStatus))
            }
            if (conversation.unreadCount > 0) {
                Badge(containerColor = CercaColors.Primary) { Text(conversation.unreadCount.toString(), color = Color.Black) }
            }
            OutlinedButton(onClick = onDelete) {
                Text("Eliminar")
            }
        }
    }
}

private fun statusLabel(status: MessageStatus): String = when (status) {
    MessageStatus.QUEUED -> "Queued"
    MessageStatus.WAITING_FOR_RELAY -> "Waiting for relay"
    MessageStatus.SENDING -> "Sending"
    MessageStatus.SENT_DIRECT -> "Sent direct"
    MessageStatus.RELAYED -> "Relayed"
    MessageStatus.DELIVERED -> "Delivered"
    MessageStatus.EXPIRED -> "Expired"
    MessageStatus.FAILED -> "Failed"
}

private fun statusColor(status: MessageStatus): Color = when (status) {
    MessageStatus.DELIVERED, MessageStatus.SENT_DIRECT -> CercaColors.Success
    MessageStatus.RELAYED, MessageStatus.WAITING_FOR_RELAY, MessageStatus.QUEUED -> CercaColors.Warning
    MessageStatus.EXPIRED, MessageStatus.FAILED -> CercaColors.Danger
    MessageStatus.SENDING -> CercaColors.Primary
}
