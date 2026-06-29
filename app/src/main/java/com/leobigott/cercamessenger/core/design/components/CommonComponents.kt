package com.leobigott.cercamessenger.core.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.leobigott.cercamessenger.core.design.theme.CercaColors

@Composable
fun StatusCard(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CercaColors.SurfaceAlt),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(title, color = CercaColors.Text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, color = CercaColors.Muted, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .clip(CircleShape)
            .background(color)
    )
}
