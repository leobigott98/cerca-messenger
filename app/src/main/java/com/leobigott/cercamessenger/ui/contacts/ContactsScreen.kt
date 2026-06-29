package com.leobigott.cercamessenger.ui.contacts

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.leobigott.cercamessenger.core.design.components.StatusCard
import com.leobigott.cercamessenger.data.local.ContactEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    contentPadding: PaddingValues,
    onOpenChat: (ContactEntity) -> Unit,
    viewModel: ContactsViewModel = viewModel(factory = ContactsViewModelFactory(LocalContext.current))
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scannerLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        viewModel.saveScannedQr(result.contents)
    }

    LaunchedEffect(state.scanResult) {
        when (val result = state.scanResult) {
            ContactQrResult.Idle -> Unit
            is ContactQrResult.Success -> {
                Toast.makeText(context, "Contacto guardado: ${result.name}", Toast.LENGTH_LONG).show()
                viewModel.clearScanResult()
            }
            is ContactQrResult.Error -> {
                Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                viewModel.clearScanResult()
            }
        }
    }

    state.pendingScannedContact?.let { pending ->
        var name by remember(pending.nodeId) { mutableStateOf(pending.suggestedName) }
        AlertDialog(
            onDismissRequest = viewModel::cancelPendingContact,
            title = { Text("Guardar contacto") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("El ID real del contacto es ${pending.nodeId.take(16)}… Guárdalo con un nombre humano único para evitar confusiones en campo.")
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nombre del contacto") },
                        singleLine = true
                    )
                }
            },
            confirmButton = { Button(onClick = { viewModel.confirmPendingContact(name) }) { Text("Guardar") } },
            dismissButton = { TextButton(onClick = viewModel::cancelPendingContact) { Text("Cancelar") } }
        )
    }

    state.editingContact?.let { contact ->
        var name by remember(contact.nodeId) { mutableStateOf(contact.displayName) }
        AlertDialog(
            onDismissRequest = viewModel::cancelRename,
            title = { Text("Renombrar contacto") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre del contacto") },
                    singleLine = true
                )
            },
            confirmButton = { Button(onClick = { viewModel.confirmRename(name) }) { Text("Guardar") } },
            dismissButton = { TextButton(onClick = viewModel::cancelRename) { Text("Cancelar") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Contactos") },
                actions = { Icon(Icons.Default.QrCode, contentDescription = null) }
            )
        }
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
            item {
                StatusCard(
                    title = "Contactos cifrados",
                    subtitle = "Escanea el QR de otra persona, ponle un nombre único y CERCA usará su nodeId estable para enrutar mensajes. El endpoint de Nearby no es identidad permanente."
                )
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Mi QR CERCA")
                        Spacer(Modifier.height(12.dp))
                        Image(
                            bitmap = generateQrBitmap(state.myQrPayload).asImageBitmap(),
                            contentDescription = "Mi QR de contacto CERCA",
                            modifier = Modifier.size(220.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = {
                            scannerLauncher.launch(
                                ScanOptions()
                                    .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                    .setPrompt("Escanea un QR CERCA")
                                    .setBeepEnabled(false)
                                    .setOrientationLocked(false)
                            )
                        }) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                            Text(" Escanear contacto")
                        }
                    }
                }
            }
            item { Text("Contactos guardados") }
            if (state.contacts.isEmpty()) {
                item {
                    OutlinedButton(
                        onClick = {
                            scannerLauncher.launch(
                                ScanOptions()
                                    .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                    .setPrompt("Escanea un QR CERCA")
                                    .setBeepEnabled(false)
                                    .setOrientationLocked(false)
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Agregar primer contacto por QR") }
                }
            } else {
                items(state.contacts, key = { it.nodeId }) { contact ->
                    ListItem(
                        modifier = Modifier.clickable { onOpenChat(contact) },
                        headlineContent = { Text(contact.displayName) },
                        supportingContent = { Text("${contact.nodeId.take(12)} · ${contact.algorithm}") },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { viewModel.startRename(contact) }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Renombrar")
                                }
                                IconButton(onClick = { viewModel.deleteContact(contact.nodeId) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Borrar")
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

private fun generateQrBitmap(text: String, size: Int = 700): Bitmap {
    val matrix: BitMatrix = MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    for (x in 0 until size) {
        for (y in 0 until size) {
            bmp.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }
    }
    return bmp
}
