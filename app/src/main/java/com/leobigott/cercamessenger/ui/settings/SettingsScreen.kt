package com.leobigott.cercamessenger.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.leobigott.cercamessenger.core.model.AppLanguage
import com.leobigott.cercamessenger.core.model.LocalizationStore
import com.leobigott.cercamessenger.core.model.NodeMode
import com.leobigott.cercamessenger.protocol.ProtocolEngineProvider
import kotlinx.coroutines.launch
import com.leobigott.cercamessenger.settings.CercaSettingsStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModelFactory(LocalContext.current))
) {
    val metadata = remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val engine = ProtocolEngineProvider.engine
    val selectedMode = remember { mutableStateOf(engine.currentNodeMode) }
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val language by LocalizationStore.language.collectAsState()
    val strings = LocalizationStore.strings(language)
    var confirmDeleteMessages by remember { mutableStateOf(false) }
    var confirmDeleteContacts by remember { mutableStateOf(false) }
    val heartbeatSeconds by CercaSettingsStore.heartbeatSeconds.collectAsState()

    LaunchedEffect(state.result) {
        when (val result = state.result) {
            SettingsActionResult.Idle -> Unit
            is SettingsActionResult.Done -> {
                Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                viewModel.clearResult()
            }
            is SettingsActionResult.Error -> {
                Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                viewModel.clearResult()
            }
        }
    }

    if (confirmDeleteMessages) {
        ConfirmDeleteDialog(
            title = strings.deleteMessagesDialog,
            body = strings.deleteMessagesDialogBody,
            yesDelete = strings.yesDelete,
            cancel = strings.cancel,
            onDismiss = { confirmDeleteMessages = false },
            onConfirm = { confirmDeleteMessages = false; viewModel.deleteAllLocalMessages() }
        )
    }
    if (confirmDeleteContacts) {
        ConfirmDeleteDialog(
            title = strings.deleteContactsDialog,
            body = strings.deleteContactsDialogBody,
            yesDelete = strings.yesDelete,
            cancel = strings.cancel,
            onDismiss = { confirmDeleteContacts = false },
            onConfirm = { confirmDeleteContacts = false; viewModel.deleteAllLocalContacts() }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(strings.settings) }, actions = { Icon(Icons.Default.Tune, contentDescription = null) })
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(bottom = contentPadding.calculateBottomPadding())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            //item { StatusCard(title = strings.activeCrisis, subtitle = strings.activeCrisisDescription) }
            item {
                ListItem(
                    headlineContent = { Text(strings.language) },
                    supportingContent = { Text(strings.languageDescription) },
                    leadingContent = { Icon(Icons.Default.Language, contentDescription = null) },
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 0.dp)) {
                    AppLanguage.values().forEach { option ->
                        FilterChip(
                            selected = language == option,
                            onClick = { LocalizationStore.setLanguage(context, option) },
                            label = { Text(option.label) },
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                    }
                }
            }
            item {
                ListItem(
                    headlineContent = { Text("Frecuencia de búsqueda") },
                    supportingContent = {
                        Text("Menor intervalo = comunicación más rápida, pero mayor consumo de batería.")
                    }
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CercaSettingsStore.allowedHeartbeatSeconds.forEach { seconds ->
                        FilterChip(
                            selected = heartbeatSeconds == seconds,
                            onClick = {
                                CercaSettingsStore.setHeartbeatSeconds(context, seconds)
                                scope.launch {
                                    engine.refreshNearby()
                                }
                            },
                            label = { Text("${seconds}s") }
                        )
                    }
                }
            }
            item {
                ListItem(
                    headlineContent = { Text(strings.showRoutingMetadata) },
                    supportingContent = { Text(strings.showRoutingMetadataDescription) },
                    trailingContent = { Switch(checked = metadata.value, onCheckedChange = { metadata.value = it }) }
                )
            }
            item { ListItem(headlineContent = { Text(strings.nodeMode) }, supportingContent = { Text(strings.nodeModeDescription) }) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NodeMode.values().forEach { mode ->
                        FilterChip(
                            selected = selectedMode.value == mode,
                            onClick = {
                                selectedMode.value = mode
                                scope.launch { engine.setNodeMode(mode) }
                            },
                            label = { Text(mode.label) }
                        )
                    }
                }
            }
            item {
                ListItem(
                    headlineContent = { Text(strings.firebase) },
                    supportingContent = { Text(strings.firebaseDescription) },
                    leadingContent = { Icon(Icons.Default.CloudSync, contentDescription = null) },
                    trailingContent = { Button(enabled = !state.isBusy, onClick = viewModel::syncFirebaseNow) { Text(strings.sync) } }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(strings.deleteLocalMessages) },
                    supportingContent = { Text(strings.deleteLocalMessagesDescription) },
                    leadingContent = { Icon(Icons.Default.DeleteForever, contentDescription = null) },
                    trailingContent = { OutlinedButton(onClick = { confirmDeleteMessages = true }) { Text(strings.delete) } }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(strings.deleteLocalContacts) },
                    supportingContent = { Text(strings.deleteLocalContactsDescription) },
                    leadingContent = { Icon(Icons.Default.DeleteForever, contentDescription = null) },
                    trailingContent = { OutlinedButton(onClick = { confirmDeleteContacts = true }) { Text(strings.delete) } }
                )
            }
            item { ListItem(headlineContent = { Text("About") }, supportingContent = { Text("Proyecto elaborado por estudiantes de la UNIMET. Prototipo funcional. Versión 1.0.0") }) }
            item { ListItem(headlineContent = { Text("Contacto") }, supportingContent = { Text("leonardo.bigott@correo.unimet.edu.ve, patricia.perez@correo.unimet.edu.ve") }) }
        }
    }
}

@Composable
private fun ConfirmDeleteDialog(
    title: String,
    body: String,
    yesDelete: String,
    cancel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { Button(onClick = onConfirm) { Text(yesDelete) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(cancel) } }
    )
}
