package com.leobigott.cercamessenger.ui.crisis

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.leobigott.cercamessenger.core.model.CrisisMessageType
import com.leobigott.cercamessenger.core.model.LocalizationStore
import com.leobigott.cercamessenger.core.model.OfflineMessage
import com.leobigott.cercamessenger.core.model.Strings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrisisScreen(
    contentPadding: PaddingValues,
    viewModel: CrisisViewModel = viewModel(factory = CrisisViewModelFactory())
) {
    val state by viewModel.uiState.collectAsState()
    val language by LocalizationStore.language.collectAsState()
    val strings = LocalizationStore.strings(language)
    val primaryTypes = listOf(
        CrisisMessageType.IM_OK,
        CrisisMessageType.NEED_HELP,
        CrisisMessageType.TRAPPED_PEOPLE,
        CrisisMessageType.INJURED_PEOPLE,
        CrisisMessageType.MEDICINE,
        CrisisMessageType.WATER_FOOD,
        CrisisMessageType.SHELTER,
        CrisisMessageType.FAMILY,
        CrisisMessageType.PUBLIC_BROADCAST,
        CrisisMessageType.OTHERS,
    )

    Scaffold(
        topBar = { TopAppBar(title = { Text(strings.crisisTitle) }) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .padding(bottom = contentPadding.calculateBottomPadding())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(strings.quickReport, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(strings.quickReportDescription)
                        primaryTypes.chunked(2).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { type ->
                                    FilterChip(
                                        selected = state.selectedType == type,
                                        onClick = {
                                            viewModel.selectType(type)
                                            if (type == CrisisMessageType.PUBLIC_BROADCAST) {
                                                viewModel.onPublicBroadcastChange(true)
                                            }
                                        },
                                        label = { Text(type.label) },
                                        leadingIcon = {
                                            when (type) {
                                                CrisisMessageType.IM_OK -> Icon(Icons.Default.CheckCircle, contentDescription = null)
                                                CrisisMessageType.MEDICINE -> Icon(Icons.Default.HealthAndSafety, contentDescription = null)
                                                CrisisMessageType.PUBLIC_BROADCAST -> Icon(Icons.Default.Campaign, contentDescription = null)
                                                else -> Icon(Icons.Default.Warning, contentDescription = null)
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        AssistChip(onClick = {}, label = { Text("${strings.priority}: ${state.selectedType.defaultPriority.label}") })
                        OutlinedTextField(
                            value = state.location,
                            onValueChange = viewModel::onLocationChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(strings.approximateLocation) },
                            placeholder = { Text(strings.locationPlaceholder) }
                        )
                        OutlinedTextField(
                            value = state.peopleAffected,
                            onValueChange = viewModel::onPeopleAffectedChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(strings.peopleAffected) }
                        )
                        OutlinedTextField(
                            value = state.details,
                            onValueChange = viewModel::onDetailsChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(strings.details) },
                            minLines = 3
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(strings.requiresResponse)
                            Switch(checked = state.requiresResponse, onCheckedChange = viewModel::onRequiresResponseChange)
                        }
                        Button(onClick = viewModel::sendReport, enabled = !state.isSending, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                when {
                                    state.isSending -> strings.sending
                                    state.isPublicBroadcast -> strings.createPublicBroadcast
                                    else -> strings.createCrisisReport
                                }
                            )
                        }
                    }
                }
            }
            item {
                    Text(strings.publicAnnouncements, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                 }
            items(state.publicBroadcasts, key = { it.id }) { report -> CrisisReportCard(report, strings) }
            item {
                    Text(strings.transportedReports, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp, bottom = 4.dp))
                }
            items(state.reports, key = { it.id }) { report -> CrisisReportCard(report, strings) }
        }
    }
}

@Composable
private fun CrisisReportCard(report: OfflineMessage, strings: Strings) {
    Card(modifier = Modifier.fillMaxWidth().clickable { }) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(report.crisisType.label, fontWeight = FontWeight.Bold)
            Text(formatReportTime(report.timestamp))
            Text(report.text)
            Text("${strings.hops}: ${report.hopCount} · ${strings.copies}: ${report.copiesLeft} · ${strings.status}: ${report.status}")
        }
    }
}

private fun formatReportTime(timestamp: Long): String {
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
