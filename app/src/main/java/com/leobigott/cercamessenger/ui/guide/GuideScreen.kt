package com.leobigott.cercamessenger.ui.guide

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.leobigott.cercamessenger.core.design.components.StatusCard
import com.leobigott.cercamessenger.core.model.LocalizationStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideScreen(contentPadding: PaddingValues) {
    val language by LocalizationStore.language.collectAsState()
    val strings = LocalizationStore.strings(language)
    Scaffold(
        topBar = { LargeTopAppBar(title = { Text(strings.guideTitle) }, actions = { Icon(Icons.Default.MenuBook, contentDescription = null) }) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(bottom = contentPadding.calculateBottomPadding())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            item { StatusCard(title = strings.guideTitle, subtitle = strings.guideBody) }
            item { GuideCard("CERCA Broadcast", listOf(
                "PUBLIC_BROADCAST is visible to all nearby nodes.",
                "It uses TTL + limited copies + message-id deduplication.",
                "It does not wait for a global ACK because there is no single destination.",
                "Use it for evacuation, refuge, road, medical point or official community announcements."
            )) }
            item { GuideCard("Field checklist", listOf(
                "Charge phones and power banks before deployment.",
                "Open the app once and grant permissions.",
                "Set node mode: citizen, volunteer or gateway.",
                "Keep Bluetooth, Wi‑Fi and location enabled.",
                "Use Firebase only when Internet appears; Nearby/DTN remains primary."
            )) }
        }
    }
}

@Composable
private fun GuideCard(title: String, items: List<String>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            items.forEach { Text("• $it") }
        }
    }
}
