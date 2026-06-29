package com.leobigott.cercamessenger.ui.contacts

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.leobigott.cercamessenger.data.local.CercaDatabase
import com.leobigott.cercamessenger.data.local.ContactEntity
import com.leobigott.cercamessenger.protocol.DeviceIdentityStore
import com.leobigott.cercamessenger.protocol.crypto.ContactQrPayload
import com.leobigott.cercamessenger.protocol.crypto.KeyManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

sealed class ContactQrResult {
    data object Idle : ContactQrResult()
    data class Success(val name: String) : ContactQrResult()
    data class Error(val message: String) : ContactQrResult()
}

data class PendingScannedContact(
    val nodeId: String,
    val suggestedName: String,
    val publicKeyPem: String,
    val keyId: String,
    val algorithm: String
)

data class ContactsUiState(
    val contacts: List<ContactEntity> = emptyList(),
    val myQrPayload: String = "",
    val scanResult: ContactQrResult = ContactQrResult.Idle,
    val pendingScannedContact: PendingScannedContact? = null,
    val editingContact: ContactEntity? = null
)

class ContactsViewModel(
    context: Context,
    private val database: CercaDatabase = CercaDatabase.getInstance(context.applicationContext),
    private val keyManager: KeyManager = KeyManager()
) : ViewModel() {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val localNodeId = DeviceIdentityStore.getOrCreateNodeId(context.applicationContext)
    private val displayName = android.os.Build.MODEL ?: "CERCA Android"

    private val _uiState = MutableStateFlow(ContactsUiState(myQrPayload = buildMyQrPayload()))
    val uiState: StateFlow<ContactsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            database.contactDao().observeContacts().collect { contacts ->
                _uiState.update { it.copy(contacts = contacts) }
            }
        }
    }

    fun saveScannedQr(raw: String?) {
        if (raw.isNullOrBlank()) {
            _uiState.update { it.copy(scanResult = ContactQrResult.Error("QR cancelado o vacío.")) }
            return
        }
        viewModelScope.launch {
            runCatching {
                val payload = json.decodeFromString<ContactQrPayload>(raw)
                require(payload.type == "CERCA_CONTACT_V1") { "Este QR no es un contacto CERCA." }
                require(payload.nodeId.isNotBlank()) { "Falta el nodeId." }
                require(payload.nodeId != localNodeId) { "Ese QR pertenece a este teléfono." }
                require(payload.publicKeyPem.contains("BEGIN PUBLIC KEY")) { "Falta la clave pública." }
                PendingScannedContact(
                    nodeId = payload.nodeId,
                    suggestedName = payload.displayName,
                    publicKeyPem = payload.publicKeyPem,
                    keyId = payload.keyId,
                    algorithm = payload.algorithm
                )
            }.onSuccess { pending ->
                _uiState.update { it.copy(pendingScannedContact = pending) }
            }.onFailure { error ->
                _uiState.update { it.copy(scanResult = ContactQrResult.Error(error.message ?: "QR inválido.")) }
            }
        }
    }

    fun confirmPendingContact(customName: String) {
        val pending = _uiState.value.pendingScannedContact ?: return
        viewModelScope.launch {
            val cleanName = customName.trim()
            if (cleanName.isBlank()) {
                _uiState.update { it.copy(scanResult = ContactQrResult.Error("El nombre no puede estar vacío.")) }
                return@launch
            }
            val sameName = database.contactDao().getByDisplayName(cleanName)
            if (sameName != null && sameName.nodeId != pending.nodeId) {
                _uiState.update { it.copy(scanResult = ContactQrResult.Error("Ya existe otro contacto llamado '$cleanName'. Usa otro nombre.")) }
                return@launch
            }
            database.contactDao().upsert(
                ContactEntity(
                    nodeId = pending.nodeId,
                    displayName = cleanName,
                    publicKeyPem = pending.publicKeyPem,
                    keyId = pending.keyId,
                    algorithm = pending.algorithm,
                    verified = true,
                    lastSeenAt = null
                )
            )
            _uiState.update {
                it.copy(
                    pendingScannedContact = null,
                    scanResult = ContactQrResult.Success(cleanName)
                )
            }
        }
    }

    fun cancelPendingContact() {
        _uiState.update { it.copy(pendingScannedContact = null) }
    }

    fun startRename(contact: ContactEntity) {
        _uiState.update { it.copy(editingContact = contact) }
    }

    fun confirmRename(newName: String) {
        val contact = _uiState.value.editingContact ?: return
        viewModelScope.launch {
            val cleanName = newName.trim()
            if (cleanName.isBlank()) {
                _uiState.update { it.copy(scanResult = ContactQrResult.Error("El nombre no puede estar vacío.")) }
                return@launch
            }
            val sameName = database.contactDao().getByDisplayName(cleanName)
            if (sameName != null && sameName.nodeId != contact.nodeId) {
                _uiState.update { it.copy(scanResult = ContactQrResult.Error("Ya existe otro contacto llamado '$cleanName'.")) }
                return@launch
            }
            database.contactDao().rename(contact.nodeId, cleanName)
            _uiState.update { it.copy(editingContact = null, scanResult = ContactQrResult.Success(cleanName)) }
        }
    }

    fun cancelRename() {
        _uiState.update { it.copy(editingContact = null) }
    }

    fun deleteContact(nodeId: String) {
        viewModelScope.launch { database.contactDao().delete(nodeId) }
    }

    fun clearScanResult() {
        _uiState.update { it.copy(scanResult = ContactQrResult.Idle) }
    }

    private fun buildMyQrPayload(): String {
        val payload = ContactQrPayload(
            nodeId = localNodeId,
            displayName = displayName,
            keyId = KeyManager.KEY_ID,
            publicKeyPem = keyManager.getPublicKeyPem()
        )
        return json.encodeToString(payload)
    }
}

class ContactsViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = ContactsViewModel(context) as T
}
