package com.leobigott.cercamessenger.ui.chat.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.leobigott.cercamessenger.core.design.theme.CercaColors
import com.leobigott.cercamessenger.core.model.MessageStatus
import com.leobigott.cercamessenger.core.model.OfflineMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MessageBubble(message: OfflineMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isFromMe) Arrangement.End else Arrangement.Start
    ) {
        Card(
            modifier = Modifier.widthIn(max = 310.dp),
            shape = RoundedCornerShape(
                topStart = 22.dp,
                topEnd = 22.dp,
                bottomStart = if (message.isFromMe) 22.dp else 6.dp,
                bottomEnd = if (message.isFromMe) 6.dp else 22.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (message.isFromMe) CercaColors.Primary else CercaColors.SurfaceAlt
            )
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = message.text,
                    color = if (message.isFromMe) Color(0xFF031826) else CercaColors.Text
                )
                Text(
                    text = messageMetadata(message),
                    color = if (message.isFromMe) Color(0xAA031826) else statusColor(message.status),
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 6.dp)
                )
                if (message.utilityScore != null || message.bestRelayName != null) {
                    Text(
                        text = "Relay: ${message.bestRelayName ?: "none"} · U=${message.utilityScore ?: 0f}",
                        color = if (message.isFromMe) Color(0x99031826) else CercaColors.Muted,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

private fun messageMetadata(message: OfflineMessage): String {
    val status = when (message.status) {
        MessageStatus.QUEUED -> "Queued"
        MessageStatus.WAITING_FOR_RELAY -> "Waiting for relay"
        MessageStatus.SENDING -> "Sending"
        MessageStatus.SENT_DIRECT -> "Sent direct"
        MessageStatus.RELAYED -> "Relayed"
        MessageStatus.DELIVERED -> "Delivered"
        MessageStatus.EXPIRED -> "Expired"
        MessageStatus.FAILED -> "Failed"
    }

    val time = formatMessageTime(message.timestamp)

    return "$time · $status · hops ${message.hopCount} · copies ${message.copiesLeft}"
}

private fun formatMessageTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val sameDay = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date(now)) ==
            SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date(timestamp))

    val pattern = if (sameDay) {
        "HH:mm"
    } else {
        "dd/MM HH:mm"
    }

    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(timestamp))
}

private fun statusColor(status: MessageStatus): Color = when (status) {
    MessageStatus.DELIVERED, MessageStatus.SENT_DIRECT -> CercaColors.Success
    MessageStatus.RELAYED, MessageStatus.WAITING_FOR_RELAY, MessageStatus.QUEUED -> CercaColors.Warning
    MessageStatus.EXPIRED, MessageStatus.FAILED -> CercaColors.Danger
    MessageStatus.SENDING -> CercaColors.Primary
}
