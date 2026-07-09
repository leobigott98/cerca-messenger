package com.leobigott.cercamessenger.ui.nearby.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.leobigott.cercamessenger.core.design.theme.CercaColors
import com.leobigott.cercamessenger.core.model.DeviceNode

@Composable
fun DeviceNodeCard(device: DeviceNode) {
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CercaColors.Surface),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
                Column {
                    Text(device.displayName, color = CercaColors.Text, fontWeight = FontWeight.Bold)
                    Text(device.lastSeenText, color = if (device.isNearby) CercaColors.Success else CercaColors.Muted)
                }
            Text("ID: ${device.id.take(12)}...", color = CercaColors.Muted)
            //Text("Modo: ${device.nodeMode.label}", color = CercaColors.Muted)

            MetricLine(label = "Battery E(j)", value = device.batteryLevel)
            Text(
                text = "Internet Gateway I(j): ${if (device.hasInternetGateway) "Yes" else "No"}",
                color = if (device.hasInternetGateway) CercaColors.Success else CercaColors.Muted
            )
        }
    }
}

@Composable
private fun MetricLine(label: String, value: Float) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("$label: ${(value * 100).toInt()}%", color = CercaColors.Muted)
        LinearProgressIndicator(progress = { value.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
    }
}
