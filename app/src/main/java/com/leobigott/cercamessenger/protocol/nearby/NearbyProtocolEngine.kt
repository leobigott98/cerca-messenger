package com.leobigott.cercamessenger.protocol.nearby

import android.Manifest
import android.content.Context
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import com.leobigott.cercamessenger.core.model.CrisisConstants
import com.leobigott.cercamessenger.core.model.CrisisMessageType
import com.leobigott.cercamessenger.core.model.DeviceNode
import com.leobigott.cercamessenger.core.model.DestinationScope
import com.leobigott.cercamessenger.core.model.MessageStatus
import com.leobigott.cercamessenger.core.model.NodeMode
import com.leobigott.cercamessenger.core.model.OfflineMessage
import com.leobigott.cercamessenger.data.local.AckEntity
import com.leobigott.cercamessenger.data.local.CercaDatabase
import com.leobigott.cercamessenger.data.local.DtnMessageEntity
import com.leobigott.cercamessenger.data.local.PeerEntity
import com.leobigott.cercamessenger.data.local.PredictionEntity
import com.leobigott.cercamessenger.data.local.toDeviceNode
import com.leobigott.cercamessenger.data.local.toEntity
import com.leobigott.cercamessenger.data.local.toOfflineMessage
import com.leobigott.cercamessenger.data.local.toPayload
import com.leobigott.cercamessenger.protocol.ProtocolEngine
import com.leobigott.cercamessenger.protocol.cerca.AckPayload
import com.leobigott.cercamessenger.protocol.cerca.CercaEnvelope
import com.leobigott.cercamessenger.protocol.cerca.CercaMessagePayload
import com.leobigott.cercamessenger.protocol.cerca.CercaPayloadType
import com.leobigott.cercamessenger.protocol.cerca.CercaProtocolConfig
import com.leobigott.cercamessenger.protocol.cerca.CercaRouterEngine
import com.leobigott.cercamessenger.protocol.cerca.HelloPayload
import com.leobigott.cercamessenger.protocol.cerca.NodeContext
import com.leobigott.cercamessenger.protocol.cerca.SummaryPayload
import com.leobigott.cercamessenger.protocol.cloud.CloudSyncService
import com.leobigott.cercamessenger.protocol.cloud.NoOpCloudSyncService
import com.leobigott.cercamessenger.protocol.crypto.ContactCrypto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.util.UUID
import android.util.Log
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import com.leobigott.cercamessenger.notifications.CercaNotificationHelper
import com.google.android.gms.common.api.ApiException
import com.leobigott.cercamessenger.data.local.DeletedMessageEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

class NearbyProtocolEngine(
    private val context: Context,
    private val database: CercaDatabase,
    private val localNodeId: String,
    private val displayName: String,
    private val config: CercaProtocolConfig = CercaProtocolConfig(),
    private val crypto: ContactCrypto = ContactCrypto(),
    private val cloudSyncService: CloudSyncService = NoOpCloudSyncService()
) : ProtocolEngine {
    private val recentlyForwardedToPeer = mutableMapOf<String, Long>()
    private val connectingStartedAt = mutableMapOf<String, Long>()

    private companion object {
        private const val TAG = "CERCA_Nearby"
        private const val EMPTY_NEARBY_RESET_AFTER_MS = 60_000L
        private const val MIN_NEARBY_RESET_GAP_MS = 30_000L
        private const val PASSIVE_CONNECTION_FALLBACK_MS = 2_500L
        private const val CONNECTING_ENDPOINT_TIMEOUT_MS = 20_000L
    }

    private val nearbyOperationMutex = kotlinx.coroutines.sync.Mutex()

    private val client: ConnectionsClient = Nearby.getConnectionsClient(context)
    private val router = CercaRouterEngine(config)
    private val statusProvider = DeviceStatusProvider(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serviceId = "com.leobigott.cercamessenger.CERCA"

    private var localCredits: Double = 25.0
    private var smoothedDensity: Double = 0.0
    private var lastInternetAt: Long? = null
    private var lastCloudSyncAt: Long = 0L
    private val remotePredictabilities: MutableMap<String, Map<String, Double>> = mutableMapOf()
    private val remoteMessageIds: MutableMap<String, Set<String>> = mutableMapOf()
    private val connectedEndpoints = mutableSetOf<String>()
    private val connectingEndpoints = mutableSetOf<String>()
    private var discoveryRunning = false
    private var advertisingRunning = false
    private val advertisingStarting = AtomicBoolean(false)
    private val discoveryStarting = AtomicBoolean(false)
    private var restartingNearby = false
    private var localNodeMode: NodeMode = loadNodeMode()
    private var emptyHeartbeatCount = 0
    private var lastEmptyNearbyResetAt: Long = 0L

    private val notificationHelper = CercaNotificationHelper(context)

    private val advertisingName: String
        get() = "$localNodeId|$displayName"

    override val currentNodeMode: NodeMode
        get() = localNodeMode

    override suspend fun setNodeMode(mode: NodeMode) {
        localNodeMode = mode
        context.getSharedPreferences("cerca_settings", Context.MODE_PRIVATE)
            .edit()
            .putString("node_mode", mode.name)
            .apply()
    }

    override suspend fun syncCloudNow() {
        cloudSyncService.uploadOnly()
        cloudSyncService.downloadNow()
        tryForwardAll()
    }

    private fun loadNodeMode(): NodeMode {
        val raw = context.getSharedPreferences("cerca_settings", Context.MODE_PRIVATE)
            .getString("node_mode", NodeMode.CITIZEN.name) ?: NodeMode.CITIZEN.name
        return runCatching { NodeMode.valueOf(raw) }.getOrDefault(NodeMode.CITIZEN)
    }

    override fun observeCrisisReports(): Flow<List<OfflineMessage>> {
        return database.messageDao()
            .observeBroadcastConversation(CrisisConstants.CRISIS_CONVERSATION_ID)
            .map { messages ->
                messages.map { message ->
                    message.toOfflineMessage(localNodeId)
                }
            }
    }

    private fun recentlyForwardKey(messageId: String, peerNodeId: String): String {
        return "$messageId::$peerNodeId"
    }

    private fun wasRecentlyForwarded(messageId: String, peerNodeId: String): Boolean {
        val now = System.currentTimeMillis()
        val key = recentlyForwardKey(messageId, peerNodeId)
        val last = recentlyForwardedToPeer[key] ?: return false
        return now - last < 5 * 60_000L
    }

    private fun markRecentlyForwarded(messageId: String, peerNodeId: String) {
        val now = System.currentTimeMillis()
        recentlyForwardedToPeer[recentlyForwardKey(messageId, peerNodeId)] = now

        if (recentlyForwardedToPeer.size > 2_000) {
            val cutoff = now - 15 * 60_000L
            recentlyForwardedToPeer.entries.removeIf { it.value < cutoff }
        }
    }

    override fun observePublicBroadcasts(): Flow<List<OfflineMessage>> {
        return database.messageDao()
            .observeBroadcastConversation(CrisisConstants.PUBLIC_BROADCAST_CONVERSATION_ID)
            .map { messages ->
                messages.map { message ->
                    message.toOfflineMessage(localNodeId)
                }
            }
    }

    override fun observeNearbyDevices(): Flow<List<DeviceNode>> =
        database.peerDao().observePeers().map { peers ->
            val now = System.currentTimeMillis()
            peers
                .filter { peer ->
                    peer.nodeId != localNodeId &&
                            peer.isNearby &&
                            !peer.endpointId.isNullOrBlank() &&
                            now - peer.lastSeenAt <= 120_000L
                }
                .distinctBy { it.nodeId }
                .map { it.toDeviceNode() }
        }

    override fun observeMessages(conversationId: String): Flow<List<OfflineMessage>> {
        return database.messageDao()
            .observeConversation(conversationId, localNodeId)
            .map { messages ->
                messages.map { it.toOfflineMessage(localNodeId) }
            }
    }

    private fun localDirectConversationId(
        senderId: String,
        destinationId: String
    ): String {
        val otherParticipantId = if (senderId == localNodeId) {
            destinationId
        } else {
            senderId
        }

        return "conv-$otherParticipantId"
    }

    private suspend fun trySendDirectNow(message: DtnMessageEntity): Boolean {
        if (database.ackDao().hasAck(message.id)) return false
        if (message.ttlExpiresAt <= System.currentTimeMillis()) return false

        val peer = database.peerDao()
            .all()
            .firstOrNull { candidate ->
                candidate.nodeId == message.destinationId &&
                        candidate.nodeId != localNodeId &&
                        !candidate.endpointId.isNullOrBlank() &&
                        candidate.endpointId in connectedEndpoints &&
                        candidate.isNearby
            }

        val endpointId = peer?.endpointId

        if (!endpointId.isNullOrBlank()) {
            val payload = message.toPayload()
            val newPath = (payload.path + localNodeId).distinct()

            val outboundPayload = payload.copy(
                copiesLeft = 1,
                hopCount = message.hopCount,
                path = newPath
            )

            Log.d(
                TAG,
                "trySendDirectNow sending message=${message.id} to peer=${peer.nodeId} endpoint=$endpointId isNearby=${peer.isNearby} connected=${endpointId in connectedEndpoints}"
            )

            database.messageDao().updateRoutingState(
                messageId = message.id,
                copiesLeft = message.copiesLeft,
                hopCount = message.hopCount,
                pathCsv = newPath.joinToString(","),
                status = MessageStatus.SENDING.name,
                bestRelayName = peer.displayName,
                utilityScore = 1.0f
            )

            sendMessageEnvelope(
                endpointId = endpointId,
                messageId = message.id,
                envelope = baseEnvelope(
                    type = CercaPayloadType.MESSAGE,
                    message = outboundPayload
                )
            )

            return true
        }

        if (connectedEndpoints.isNotEmpty()) {
            Log.d(
                TAG,
                "trySendDirectNow: connected endpoints exist but destination is not mapped yet. Refreshing handshakes."
            )

            connectedEndpoints.toList().forEach { endpointId ->
                sendHello(endpointId)
                sendSummary(endpointId)
            }

            return false
        }

        /*
         * Fallback importante:
         * puede existir conexión física por Nearby, pero todavía no sabemos qué nodeId
         * corresponde a cada endpoint porque el HELLO/SUMMARY no llegó o se perdió.
         *
         * En ese caso enviamos el mensaje cifrado a endpoints conectados.
         * Si el endpoint es el destino, lo recibirá.
         * Si no es el destino, actuará como relay o lo ignorará según CERCA.
         */
//        if (connectedEndpoints.isNotEmpty()) {
//            val payload = message.toPayload()
//            val newPath = (payload.path + localNodeId).distinct()
//
//            val outboundPayload = payload.copy(
//                copiesLeft = message.copiesLeft.coerceAtLeast(1),
//                hopCount = message.hopCount,
//                path = newPath
//            )
//
//            Log.d(
//                TAG,
//                "trySendDirectNow fallback: sending message=${message.id} to connectedEndpoints=${connectedEndpoints.size}"
//            )
//
//            database.messageDao().updateRoutingState(
//                messageId = message.id,
//                copiesLeft = message.copiesLeft,
//                hopCount = message.hopCount,
//                pathCsv = newPath.joinToString(","),
//                status = MessageStatus.SENDING.name,
//                bestRelayName = "Nearby endpoint",
//                utilityScore = 1.0f
//            )
//
//            connectedEndpoints.forEach { connectedEndpointId ->
//                sendMessageEnvelope(
//                    endpointId = connectedEndpointId,
//                    messageId = message.id,
//                    envelope = baseEnvelope(
//                        type = CercaPayloadType.MESSAGE,
//                        message = outboundPayload
//                    )
//                )
//            }
//
//            return true
//        }

        Log.d(
            TAG,
            "trySendDirectNow failed: no endpoint for destination=${message.destinationId}, connectedEndpoints=0"
        )

        restartNearbySoon()
        return false
    }

    private fun canActuallyUseNearby(): Boolean {
        if (!hasNearbyPermissions()) return false

        val bluetoothManager =
            context.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
        val bluetoothEnabled = bluetoothManager.adapter?.isEnabled == true

        val wifiManager =
            context.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
        val wifiEnabled = wifiManager.isWifiEnabled

        val locationManager =
            context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
        val locationEnabled =
            locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) ||
                    locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)

        Log.d(
            TAG,
            "Nearby preflight: bluetooth=$bluetoothEnabled wifi=$wifiEnabled location=$locationEnabled"
        )

        return bluetoothEnabled && wifiEnabled && locationEnabled
    }

    override suspend fun sendMessage(conversationId: String, destinationId: String, text: String) {
        val now = System.currentTimeMillis()
        val contact = database.contactDao().getByNodeId(destinationId)
        if (contact == null) {
            database.messageDao().upsert(
                DtnMessageEntity(
                    id = "local-${UUID.randomUUID()}",
                    conversationId = localDirectConversationId(
                        senderId = localNodeId,
                        destinationId = destinationId
                    ),
                    senderId = localNodeId,
                    destinationId = destinationId,
                    text = "Add this contact by QR before sending encrypted messages.",
                    encryptedBodyJson = null,
                    isEncrypted = false,
                    timestamp = now,
                    receivedAt = now,
                    ttlExpiresAt = now + config.messageTtlMillis,
                    isFromMe = true,
                    isEmergency = false,
                    status = MessageStatus.FAILED.name,
                    copiesLeft = 0,
                    hopCount = 0,
                    pathCsv = localNodeId,
                    bestRelayName = null,
                    utilityScore = null
                )
            )
            return
        }

        val encryptedJson = crypto.encryptTextToJson(text, contact.publicKeyPem)
        val localConversationId = localDirectConversationId(
            senderId = localNodeId,
            destinationId = destinationId
        )

        val message = DtnMessageEntity(
            id = "msg-${UUID.randomUUID()}",
            conversationId = localConversationId,
            senderId = localNodeId,
            destinationId = destinationId,
            text = text,
            encryptedBodyJson = encryptedJson,
            isEncrypted = true,
            timestamp = now,
            receivedAt = now,
            ttlExpiresAt = now + config.messageTtlMillis,
            isFromMe = true,
            isEmergency = false,
            status = MessageStatus.QUEUED.name,
            copiesLeft = config.initialCopies,
            hopCount = 0,
            pathCsv = localNodeId,
            bestRelayName = null,
            utilityScore = null
        )
        database.messageDao().upsert(message)
        val knownPeer = database.peerDao().all().firstOrNull { it.nodeId == destinationId }

        Log.d(
            TAG,
            "sendMessage created=${message.id} destination=$destinationId " +
                    "knownPeer=${knownPeer != null} " +
                    "endpoint=${knownPeer?.endpointId} " +
                    "isNearby=${knownPeer?.isNearby} " +
                    "connectedEndpoints=${connectedEndpoints.size}"
        )

        val sentDirectNow = trySendDirectNow(message)

        if (!sentDirectNow) {
            tryForwardAll()

            val updated = database.messageDao().getById(message.id)
            if (updated?.status == MessageStatus.QUEUED.name) {
                database.messageDao().updateStatus(
                    messageId = message.id,
                    status = MessageStatus.WAITING_FOR_RELAY.name
                )
            }
        }

// Firebase queda en segundo plano.
        if (statusProvider.hasInternet()) {
            scope.launch {
                runCatching { cloudSyncService.uploadOnly() }
                    .onFailure { error ->
                        Log.e(TAG, "cloudSync after sendMessage failed: ${error.message}", error)
                    }
            }
        }
    }

    private fun sendMessageEnvelope(
        endpointId: String,
        messageId: String,
        envelope: CercaEnvelope
    ) {
        val raw = json.encodeToString(envelope)
        val bytes = raw.toByteArray(StandardCharsets.UTF_8)

        client.sendPayload(endpointId, Payload.fromBytes(bytes))
            .addOnSuccessListener {
                Log.d(TAG, "sendPayload SUCCESS endpointId=$endpointId messageId=$messageId")
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "sendPayload FAILED endpointId=$endpointId messageId=$messageId error=${error.message}", error)

                connectedEndpoints.remove(endpointId)
                connectingEndpoints.remove(endpointId)

                scope.launch {
                    database.peerDao().markDisconnected(endpointId)

                    val current = database.messageDao().getById(messageId)
                    if (current != null &&
                        current.status == MessageStatus.SENDING.name &&
                        !database.ackDao().hasAck(messageId)
                    ) {
                        database.messageDao().updateStatus(
                            messageId = messageId,
                            status = MessageStatus.WAITING_FOR_RELAY.name
                        )
                    }

                    updateDensity()
                    startDiscovery()
                    tryForwardAll()
                }
            }
    }

    override suspend fun sendCrisisMessage(
        type: CrisisMessageType,
        text: String,
        approximateLocation: String?,
        peopleAffected: Int?,
        requiresResponse: Boolean
    ) {
        val now = System.currentTimeMillis()
        val priority = type.defaultPriority
        val ttl = when (priority.level) {
            5, 4 -> 6L * config.messageTtlMillis
            3 -> 3L * config.messageTtlMillis
            else -> config.messageTtlMillis
        }
        val copies = when (priority.level) {
            5 -> config.initialCopies * 3
            4 -> config.initialCopies * 2
            3 -> config.initialCopies
            else -> (config.initialCopies / 2).coerceAtLeast(2)
        }
        val title = "[${priority.label}] ${type.label}"
        val body = buildString {
            append(title)
            if (text.isNotBlank()) append("\n").append(text.trim())
            approximateLocation?.takeIf { it.isNotBlank() }?.let { append("\nUbicación: ").append(it) }
            peopleAffected?.let { append("\nPersonas afectadas: ").append(it) }
            if (requiresResponse) append("\nRequiere respuesta")
        }
        val message = DtnMessageEntity(
            id = "crisis-${UUID.randomUUID()}",
            conversationId = CrisisConstants.CRISIS_CONVERSATION_ID,
            senderId = localNodeId,
            destinationId = CrisisConstants.PUBLIC_BROADCAST_DESTINATION_ID,
            text = body,
            encryptedBodyJson = null,
            isEncrypted = false,
            timestamp = now,
            receivedAt = now,
            ttlExpiresAt = now + ttl,
            isFromMe = true,
            isEmergency = priority.level >= 4,
            crisisType = type.name,
            crisisPriority = priority.level,
            verificationStatus = "UNVERIFIED",
            approximateLocation = approximateLocation,
            peopleAffected = peopleAffected,
            requiresResponse = requiresResponse,
            destinationScope = DestinationScope.PUBLIC_BROADCAST.name,
            status = MessageStatus.QUEUED.name,
            copiesLeft = copies,
            hopCount = 0,
            pathCsv = localNodeId,
            bestRelayName = null,
            utilityScore = null
        )
        database.messageDao().upsert(message)

// Los reportes de crisis deben salir por Nearby inmediatamente.
// Firebase no debe bloquear el forwarding local.
        forwardPublicBroadcastNow(message)

        scope.launch {
            runCatching { cloudSyncService.uploadOnly() }
                .onFailure { error ->
                    Log.e(TAG, "cloudSync after sendCrisisMessage failed: ${error.message}", error)
                }
        }
    }

    override suspend fun sendPublicBroadcast(
        text: String,
        approximateLocation: String?,
        peopleAffected: Int?,
        requiresResponse: Boolean
    ) {
        val now = System.currentTimeMillis()
        val ttl = 4L * config.messageTtlMillis
        val copies = (config.initialCopies * 4).coerceAtLeast(24)
        val title = "[Anuncio público]"
        val body = buildString {
            append(title)
            if (text.isNotBlank()) append("\n").append(text.trim())
            approximateLocation?.takeIf { it.isNotBlank() }?.let { append("\nUbicación: ").append(it) }
            peopleAffected?.let { append("\nPersonas afectadas: ").append(it) }
            if (requiresResponse) append("\nRequiere respuesta")
        }
        val message = DtnMessageEntity(
            id = "broadcast-${UUID.randomUUID()}",
            conversationId = CrisisConstants.PUBLIC_BROADCAST_CONVERSATION_ID,
            senderId = localNodeId,
            destinationId = CrisisConstants.PUBLIC_BROADCAST_DESTINATION_ID,
            text = body,
            encryptedBodyJson = null,
            isEncrypted = false,
            timestamp = now,
            receivedAt = now,
            ttlExpiresAt = now + ttl,
            isFromMe = true,
            isEmergency = false,
            crisisType = CrisisMessageType.PUBLIC_BROADCAST.name,
            crisisPriority = CrisisMessageType.PUBLIC_BROADCAST.defaultPriority.level,
            verificationStatus = "UNVERIFIED",
            approximateLocation = approximateLocation,
            peopleAffected = peopleAffected,
            requiresResponse = requiresResponse,
            destinationScope = DestinationScope.PUBLIC_BROADCAST.name,
            status = MessageStatus.QUEUED.name,
            copiesLeft = copies,
            hopCount = 0,
            pathCsv = localNodeId,
            bestRelayName = null,
            utilityScore = null
        )
        database.messageDao().upsert(message)

// Broadcast usa ruta separada. No pasa por CERCA/router normal.
        forwardPublicBroadcastNow(message)

        scope.launch {
            runCatching { cloudSyncService.uploadOnly() }
                .onFailure { error ->
                    Log.e(TAG, "cloudSync after sendPublicBroadcast failed: ${error.message}", error)
                }
        }
    }

    suspend fun sendEmergencyMessage(conversationId: String, destinationId: String, text: String) {
        val now = System.currentTimeMillis()
        val contact = database.contactDao().getByNodeId(destinationId)
        val encryptedJson = contact?.let { crypto.encryptTextToJson(text, it.publicKeyPem) }
        val message = DtnMessageEntity(
            id = "emg-${UUID.randomUUID()}",
            conversationId = conversationId,
            senderId = localNodeId,
            destinationId = destinationId,
            text = if (encryptedJson == null) text else text,
            encryptedBodyJson = encryptedJson,
            isEncrypted = encryptedJson != null,
            timestamp = now,
            receivedAt = now,
            ttlExpiresAt = now + config.messageTtlMillis,
            isFromMe = true,
            isEmergency = true,
            status = MessageStatus.QUEUED.name,
            copiesLeft = config.initialCopies * 2,
            hopCount = 0,
            pathCsv = localNodeId,
            bestRelayName = null,
            utilityScore = null
        )

        database.messageDao().upsert(message)

        val sentDirectNow = trySendDirectNow(message)

        if (!sentDirectNow) {
            tryForwardAll()
        }
    }

    private fun requiredNearbyPermissions(): List<String> = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_ADVERTISE)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
    }

    private fun missingNearbyPermissions(): List<String> {
        return requiredNearbyPermissions().filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
    }

    private fun hasNearbyPermissions(): Boolean {
        val missing = missingNearbyPermissions()
       if (missing.isNotEmpty()) {
            Log.e(TAG, "Missing Nearby permissions: ${missing.joinToString()}")
        }
        return missing.isEmpty()
    }

    @SuppressLint("MissingPermission")
    private suspend fun forceRestartNearbyNow() {
        Log.e(TAG, "Force restarting Nearby NOW")

        advertisingRunning = false
        discoveryRunning = false
        advertisingStarting.set(false)
        discoveryStarting.set(false)
        connectingEndpoints.clear()
        connectedEndpoints.clear()

        runCatching { client.stopDiscovery() }
        runCatching { client.stopAdvertising() }
        runCatching { client.stopAllEndpoints() }

        kotlinx.coroutines.delay(1000)

        startDiscovery()
    }

    @SuppressLint("MissingPermission")
    private suspend fun restartAdvertisingAndDiscoveryOnly() {
        Log.e(TAG, "Restarting advertising/discovery only")

        advertisingRunning = false
        discoveryRunning = false
        advertisingStarting.set(false)
        discoveryStarting.set(false)

        runCatching { client.stopDiscovery() }
        runCatching { client.stopAdvertising() }

        delay(1000)

        startDiscovery()
    }

    @SuppressLint("MissingPermission")
    private suspend fun resetNearbyWhenEmpty(reason: String) {
        Log.e(TAG, "Resetting Nearby because no connections are active. reason=$reason")

        advertisingRunning = false
        discoveryRunning = false
        advertisingStarting.set(false)
        discoveryStarting.set(false)
        connectingEndpoints.clear()
        connectedEndpoints.clear()

        runCatching { client.stopDiscovery() }
        runCatching { client.stopAdvertising() }

        /*
         * Aquí sí usamos stopAllEndpoints porque NO hay conexiones activas.
         * Esto limpia el estado interno de Nearby después de apagar/prender
         * Wi-Fi o Bluetooth.
         */
        runCatching { client.stopAllEndpoints() }

        database.peerDao().markAllDisconnected()

        delay(1500)

        startDiscovery()
    }

    private fun pruneStaleConnectingEndpoints() {
        val now = System.currentTimeMillis()
        val stale = connectingStartedAt
            .filterValues { startedAt -> now - startedAt > CONNECTING_ENDPOINT_TIMEOUT_MS }
            .keys

        stale.forEach { endpointId ->
            Log.e(TAG, "Pruning stale connecting endpoint=$endpointId")
            connectingEndpoints.remove(endpointId)
            connectingStartedAt.remove(endpointId)
            runCatching { client.disconnectFromEndpoint(endpointId) }
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun refreshNearby() = nearbyOperationMutex.withLock {
        pruneStaleConnectingEndpoints()
        if (!hasNearbyPermissions()) {
            Log.e(TAG, "refreshNearby aborted: missing Nearby permissions")
            return
        }

        Log.d(
            TAG,
            "Manual/heartbeat refresh. connected=${connectedEndpoints.size}, connecting=${connectingEndpoints.size}"
        )

        // 1) Mantener vivas las conexiones existentes.
        connectedEndpoints.toList().forEach { endpointId ->
            sendHello(endpointId)
            sendSummary(endpointId)
        }

        if (connectedEndpoints.isEmpty() && connectingEndpoints.isEmpty()) {
            emptyHeartbeatCount++

            val now = System.currentTimeMillis()
            val shouldReset =
                now - lastEmptyNearbyResetAt >= EMPTY_NEARBY_RESET_AFTER_MS

            if (shouldReset) {
                Log.e(
                    TAG,
                    "No Nearby endpoints after heartbeat. Resetting radios. emptyHeartbeatCount=$emptyHeartbeatCount"
                )

                lastEmptyNearbyResetAt = now
                emptyHeartbeatCount = 0
                resetNearbyWhenEmpty("empty heartbeat")
                return
            }
        } else {
            emptyHeartbeatCount = 0
        }

        // 2) Intentar arrancar advertising/discovery si no están activos.
        if (!advertisingRunning || !discoveryRunning) {
            startDiscovery()
        }

        // 3) Intentar reenviar mensajes pendientes.
        recoverStaleSendingMessages()
        tryForwardAll()

        // 4) Si hay internet, sincronizar nube y volver a intentar forwarding.
//        if (statusProvider.hasInternet()) {
//            cloudSyncService.syncNow()
//            recoverStaleSendingMessages()
//            tryForwardAll()
//        }
    }

    private suspend fun recoverStaleSendingMessages() {
        val now = System.currentTimeMillis()
        val staleAfterMillis = 15_000L

        database.messageDao().resetStaleSendingMessages(
            olderThan = now - staleAfterMillis,
            now = now
        )
    }

    @SuppressLint("MissingPermission")
    override suspend fun startDiscovery() {
        if (!canActuallyUseNearby()) {
            Log.e(TAG, "startDiscovery aborted: missing Nearby permissions")
            advertisingRunning = false
            discoveryRunning = false
            advertisingStarting.set(false)
            discoveryStarting.set(false)
            return
        }

        Log.d(
            TAG,
            "startDiscovery called. " +
                    "advertisingRunning=$advertisingRunning " +
                    "discoveryRunning=$discoveryRunning " +
                    "advertisingStarting=${advertisingStarting.get()} " +
                    "discoveryStarting=${discoveryStarting.get()}"
        )

        if (!advertisingRunning && advertisingStarting.compareAndSet(false, true)) {
            client.startAdvertising(
                advertisingName,
                serviceId,
                connectionLifecycleCallback,
                AdvertisingOptions.Builder()
                    .setStrategy(Strategy.P2P_CLUSTER)
                    .build()
            ).addOnSuccessListener {
                advertisingRunning = true
                advertisingStarting.set(false)
                Log.d(TAG, "startAdvertising SUCCESS")
            }.addOnFailureListener { error ->
                val code = (error as? ApiException)?.statusCode
                Log.e(TAG, "startAdvertising FAILED code=$code message=${error.message}", error)

                advertisingRunning = false
                advertisingStarting.set(false)

                scope.launch {
                    restartNearbySoon()
                }
            }
        }

        if (!discoveryRunning && discoveryStarting.compareAndSet(false, true)) {
            client.startDiscovery(
                serviceId,
                endpointDiscoveryCallback,
                DiscoveryOptions.Builder()
                    .setStrategy(Strategy.P2P_CLUSTER)
                    .build()
            ).addOnSuccessListener {
                discoveryRunning = true
                discoveryStarting.set(false)
                Log.d(TAG, "startDiscovery SUCCESS")
            }.addOnFailureListener { error ->
                val code = (error as? ApiException)?.statusCode
                Log.e(TAG, "startDiscovery FAILED code=$code message=${error.message}", error)

                discoveryRunning = false
                discoveryStarting.set(false)

                scope.launch {
                    restartNearbySoon()
                }
            }
        }
    }

    override suspend fun stopDiscovery() {
        Log.d(TAG, "stopDiscovery called")

        advertisingRunning = false
        discoveryRunning = false
        restartingNearby = false
        advertisingStarting.set(false)
        discoveryStarting.set(false)

        connectedEndpoints.clear()
        connectingEndpoints.clear()

        runCatching { client.stopAdvertising() }
        runCatching { client.stopDiscovery() }
        runCatching { client.stopAllEndpoints() }

        database.peerDao().markAllDisconnected()
    }

    private fun restartNearbySoon() {
        if (restartingNearby) return

        restartingNearby = true

        scope.launch {
            kotlinx.coroutines.delay(1200)

            runCatching {
                if (!hasNearbyPermissions()) {
                    Log.e(TAG, "restartNearbySoon aborted: missing Nearby permissions")
                    return@runCatching
                }

                Log.d(TAG, "Restarting Nearby softly")

                advertisingRunning = false
                discoveryRunning = false
                advertisingStarting.set(false)
                discoveryStarting.set(false)

                client.stopDiscovery()
                client.stopAdvertising()

                kotlinx.coroutines.delay(800)

                startDiscovery()
            }.onFailure { error ->
                Log.e(TAG, "restartNearbySoon FAILED: ${error.message}", error)
            }

            restartingNearby = false
        }
    }

    private fun parseNodeIdFromEndpointName(endpointName: String): String? {
        return endpointName.substringBefore("|").takeIf { it.isNotBlank() }
    }

    @SuppressLint("MissingPermission")
    private fun requestConnectionToEndpoint(endpointId: String, reason: String) {
        if (endpointId in connectedEndpoints || endpointId in connectingEndpoints) return

        connectingEndpoints.add(endpointId)
        connectingStartedAt[endpointId] = System.currentTimeMillis()

        Log.d(TAG, "requestConnection reason=$reason endpointId=$endpointId")

        client.requestConnection(advertisingName, endpointId, connectionLifecycleCallback)
            .addOnSuccessListener {
                Log.d(TAG, "requestConnection SUCCESS endpointId=$endpointId")
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "requestConnection FAILED endpointId=$endpointId error=${error.message}", error)
                connectingEndpoints.remove(endpointId)
                scope.launch { startDiscovery() }
            }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            Log.d(TAG, "onEndpointFound endpointId=$endpointId name=${info.endpointName}")

            if (endpointId in connectedEndpoints || endpointId in connectingEndpoints) {
                Log.d(TAG, "Ignoring endpoint already known endpointId=$endpointId")
                return
            }

            val remoteNodeId = parseNodeIdFromEndpointName(info.endpointName)

            if (remoteNodeId == localNodeId) {
                Log.d(TAG, "Ignoring self endpoint endpointId=$endpointId")
                return
            }

            if (remoteNodeId != null && localNodeId > remoteNodeId) {
                Log.d(TAG, "Passive side waiting briefly. local=$localNodeId remote=$remoteNodeId")

                scope.launch {
                    delay(PASSIVE_CONNECTION_FALLBACK_MS)

                    if (endpointId !in connectedEndpoints && endpointId !in connectingEndpoints) {
                        Log.e(TAG, "Passive fallback requesting connection endpointId=$endpointId remote=$remoteNodeId")
                        requestConnectionToEndpoint(endpointId, "passive fallback")
                    }
                }

                return
            }

            requestConnectionToEndpoint(endpointId, "active side")
        }

        override fun onEndpointLost(endpointId: String) {
            Log.d(TAG, "onEndpointLost endpointId=$endpointId")

            /*
             * Importante:
             * onEndpointLost significa que el endpoint dejó de aparecer en discovery,
             * NO que una conexión ya establecida se haya desconectado.
             *
             * Si ya está conectado, no lo removemos aquí.
             * La desconexión real se maneja en onDisconnected().
             */
            if (endpointId in connectedEndpoints) {
                Log.d(TAG, "Endpoint lost from discovery but still connected endpointId=$endpointId")
                return
            }

            connectingEndpoints.remove(endpointId)
            connectingStartedAt.remove(endpointId)

            scope.launch {
                database.peerDao().markDisconnected(endpointId)
                updateDensity()
            }

            // No reiniciamos todo solo porque se perdió un endpoint.
            // Discovery sigue activo y puede volver a encontrarlo.
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            Log.d(TAG, "onConnectionInitiated endpointId=$endpointId name=${info.endpointName}")

            connectingEndpoints.add(endpointId)
            connectingStartedAt[endpointId] = System.currentTimeMillis()

            client.acceptConnection(endpointId, payloadCallback)
                .addOnSuccessListener {
                    Log.d(TAG, "acceptConnection SUCCESS endpointId=$endpointId")
                }
                .addOnFailureListener { error ->
                    Log.e(TAG, "acceptConnection FAILED endpointId=$endpointId error=${error.message}", error)

                    connectingEndpoints.remove(endpointId)
                    connectedEndpoints.remove(endpointId)

                    scope.launch {
                        startDiscovery()
                    }
                }
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            Log.d(TAG, "onConnectionResult endpointId=$endpointId status=${result.status.statusCode}")

            connectingEndpoints.remove(endpointId)
            connectingStartedAt.remove(endpointId)

            if (result.status.isSuccess) {
                Log.d(TAG, "Connection SUCCESS endpointId=$endpointId")

                connectedEndpoints.add(endpointId)

                scope.launch {
                    updateDensity()

                    sendHello(endpointId)
                    sendSummary(endpointId)

                    kotlinx.coroutines.delay(800)

                    sendHello(endpointId)
                    sendSummary(endpointId)

                    tryForwardAll()
                }
            } else {
                Log.e(TAG, "Connection FAILED endpointId=$endpointId status=${result.status.statusCode}")

                connectedEndpoints.remove(endpointId)
                scope.launch {
                    startDiscovery()
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            Log.d(TAG, "onDisconnected endpointId=$endpointId")

            connectedEndpoints.remove(endpointId)
            connectingEndpoints.remove(endpointId)
            connectingStartedAt.remove(endpointId)

            scope.launch {
                database.peerDao().markDisconnected(endpointId)
                updateDensity()
            }

            // No forzamos restart aquí. El heartbeat y discovery activo se encargan.
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type != Payload.Type.BYTES) return

            val raw = String(payload.asBytes() ?: return, StandardCharsets.UTF_8)

            scope.launch {
                runCatching { json.decodeFromString<CercaEnvelope>(raw) }
                    .onSuccess { envelope ->
                        Log.d(TAG, "onPayloadReceived type=${envelope.type} sender=${envelope.senderNodeId} endpointId=$endpointId")
                        handleEnvelope(endpointId, envelope)
                    }
                    .onFailure { error ->
                        Log.e(TAG, "Failed to decode payload endpointId=$endpointId error=${error.message}", error)
                    }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) = Unit
    }

    private fun updateLocalInternetTimestampOnly() {
        if (!statusProvider.hasInternet()) return
        lastInternetAt = System.currentTimeMillis()
    }

    private suspend fun handleEnvelope(endpointId: String, envelope: CercaEnvelope) {
        if (envelope.senderNodeId == localNodeId) {
            Log.d(TAG, "Ignoring self envelope endpointId=$endpointId type=${envelope.type}")
            connectedEndpoints.remove(endpointId)
            connectingEndpoints.remove(endpointId)
            database.peerDao().markDisconnected(endpointId)
            return
        }

        rememberPeerFromEnvelope(endpointId, envelope)
        updatePredictabilityOnEncounter(envelope.senderNodeId)

        when (envelope.type) {
            CercaPayloadType.HELLO -> {
                updateLocalInternetTimestampOnly()
                envelope.hello?.let { handleHello(endpointId, envelope, it) }
                sendSummary(endpointId)
                tryForwardAll()
            }

            CercaPayloadType.SUMMARY -> {
                updateLocalInternetTimestampOnly()
                envelope.summary?.let { handleSummary(endpointId, envelope, it) }
                tryForwardAll()
            }

            CercaPayloadType.MESSAGE -> {
                envelope.message?.let { handleIncomingMessage(endpointId, envelope, it) }
            }

            CercaPayloadType.ACK -> {
                envelope.ack?.let { handleAck(it, receivedFromEndpointId = endpointId) }
            }
        }
    }

    private suspend fun handleHello(endpointId: String, envelope: CercaEnvelope, hello: HelloPayload) {
        upsertPeerFromRemote(endpointId, envelope.senderNodeId, envelope.senderDisplayName, hello)
        updatePredictabilityOnEncounter(envelope.senderNodeId)
    }

    private suspend fun handleSummary(endpointId: String, envelope: CercaEnvelope, summary: SummaryPayload) {
        upsertPeerFromRemote(endpointId, envelope.senderNodeId, envelope.senderDisplayName, summary)
        val now = System.currentTimeMillis()
        val acks = if (summary.recentAcks.isNotEmpty()) {
            summary.recentAcks
        } else {
            summary.ackedMessageIds.map { AckPayload(it, "unknown", now, envelope.senderNodeId) }
        }
        val entities = acks.map {
            AckEntity(
                messageId = it.messageId,
                deliveredTo = it.deliveredTo,
                deliveredAt = it.deliveredAt,
                receivedFromNodeId = envelope.senderNodeId,
                createdAt = now
            )
        }
        database.ackDao().upsertAll(entities)
        val ackIds = entities.map { it.messageId }
        if (ackIds.isNotEmpty()) {
            database.ackDao().markOwnMessagesDelivered(ackIds, localNodeId)
            database.ackDao().deleteRelayedMessagesByAck(ackIds, localNodeId)
            database.ackDao().pruneAcks(config.ackMaxSize)
        }

        cleanupLocalBuffer()

        updatePredictabilityOnEncounter(envelope.senderNodeId)
        remotePredictabilities[envelope.senderNodeId] = summary.predictabilities
        remoteMessageIds[envelope.senderNodeId] = summary.messageIds.toSet()

        val predictionToPeer = database.predictionDao().get(envelope.senderNodeId)?.value ?: 0.0
        summary.predictabilities.forEach { (destinationId, peerPredToDestination) ->
            if (destinationId == localNodeId) return@forEach
            val current = database.predictionDao().get(destinationId)
            val updated = router.updateTransitivePredictability(
                oldPredictionToDestination = current?.value ?: 0.0,
                predictionToPeer = predictionToPeer,
                peerPredictionToDestination = peerPredToDestination
            )
            database.predictionDao().upsert(
                PredictionEntity(destinationId, updated, current?.lastEncounterAt, now)
            )
        }
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private suspend fun handleIncomingMessage(
        endpointId: String,
        envelope: CercaEnvelope,
        incoming: CercaMessagePayload
    ) {
        Log.d(TAG, "handleIncomingMessage id=${incoming.id} from=${incoming.senderId} to=${incoming.destinationId} local=$localNodeId")
        database.messageDao().deleteExpiredPublicBroadcasts(System.currentTimeMillis(), DestinationScope.PUBLIC_BROADCAST.name)

        val isIncomingPublicBroadcast =
            incoming.destinationScope == DestinationScope.PUBLIC_BROADCAST.name

        if (database.deletedMessageDao().isDeleted(incoming.id)) {
            Log.d(TAG, "Ignoring locally deleted message=${incoming.id}")
            return
        }

        /*
         * Los broadcast no usan ACK global. Si ya lo tengo, simplemente no lo guardo
         * ni lo notifico otra vez.
         */
        val existing = database.messageDao().getById(incoming.id)

        if (existing != null) {
            if (!isIncomingPublicBroadcast && incoming.destinationId == localNodeId) {
                val localConversationId = localDirectConversationId(
                    senderId = incoming.senderId,
                    destinationId = incoming.destinationId
                )

                val displayText = if (incoming.isEncrypted && incoming.encryptedBodyJson != null) {
                    runCatching { crypto.decryptJson(incoming.encryptedBodyJson) }
                        .getOrElse { "Unable to decrypt message." }
                } else {
                    incoming.text
                }

                database.messageDao().upsert(
                    existing.copy(
                        conversationId = localConversationId,
                        text = displayText,
                        encryptedBodyJson = incoming.encryptedBodyJson,
                        isEncrypted = incoming.isEncrypted,
                        status = MessageStatus.DELIVERED.name,
                        copiesLeft = 0,
                        receivedAt = System.currentTimeMillis()
                    )
                )

                sendAck(endpointId, incoming.id, localNodeId)

                scope.launch {
                    delay(500)
                    sendAck(endpointId, incoming.id, localNodeId)
                }
            }

            return
        }

        if (!isIncomingPublicBroadcast && database.ackDao().hasAck(incoming.id)) {
            sendAck(endpointId, incoming.id, incoming.destinationId)
            return
        }

        val newPath = (incoming.path + localNodeId).distinct()

        val received = incoming.copy(
            copiesLeft = incoming.copiesLeft,
            hopCount = incoming.hopCount + 1,
            path = newPath
        )

        val isPublicBroadcast =
            received.destinationScope == DestinationScope.PUBLIC_BROADCAST.name

        if (isPublicBroadcast) {
            val targetConversationId =
                if (received.conversationId == CrisisConstants.PUBLIC_BROADCAST_CONVERSATION_ID ||
                    received.crisisType == CrisisMessageType.PUBLIC_BROADCAST.name
                ) {
                    CrisisConstants.PUBLIC_BROADCAST_CONVERSATION_ID
                } else {
                    CrisisConstants.CRISIS_CONVERSATION_ID
                }

            database.messageDao().upsert(
                received.toEntity(localNodeId).copy(
                    conversationId = targetConversationId,
                    destinationId = CrisisConstants.PUBLIC_BROADCAST_DESTINATION_ID,
                    destinationScope = DestinationScope.PUBLIC_BROADCAST.name,
                    text = received.text,
                    isEncrypted = false,
                    encryptedBodyJson = null,
                    isFromMe = received.senderId == localNodeId,
                    status = MessageStatus.RELAYED.name
                )
            )
            val localConversationId = localDirectConversationId(
                senderId = incoming.senderId,
                destinationId = incoming.destinationId
            )

            val displayText = if (incoming.isEncrypted && incoming.encryptedBodyJson != null) {
                runCatching { crypto.decryptJson(incoming.encryptedBodyJson) }
                    .getOrElse { "Unable to decrypt message." }
            } else {
                incoming.text
            }

            notificationHelper.showIncomingMessageNotification(
                conversationId = localConversationId,
                senderName = envelope.senderDisplayName,
                text = displayText,
                isEmergency = received.isEmergency || received.crisisPriority >= 4,
                isPublicBroadcast = false
            )

            val saved = database.messageDao().getById(received.id)
            if (saved != null) {
                scope.launch {
                    forwardPublicBroadcastNow(saved)
                }
            }

            return
        }

        if (received.destinationId == localNodeId) {
            val displayText = if (received.isEncrypted && received.encryptedBodyJson != null) {
                runCatching { crypto.decryptJson(received.encryptedBodyJson) }
                    .getOrElse { "Unable to decrypt message." }
            } else {
                received.text
            }

            val localConversationId = localDirectConversationId(
                senderId = received.senderId,
                destinationId = received.destinationId
            )

            database.messageDao().upsert(
                received.toEntity(localNodeId).copy(
                    conversationId = localConversationId,
                    text = displayText,
                    status = MessageStatus.DELIVERED.name,
                    copiesLeft = 0,
                    receivedAt = System.currentTimeMillis()
                )
            )

            notificationHelper.showIncomingMessageNotification(
                conversationId = received.conversationId,
                senderName = envelope.senderDisplayName,
                text = displayText,
                isEmergency = received.isEmergency || received.crisisPriority >= 4,
                isPublicBroadcast = false
            )

            val ack = AckEntity(
                messageId = received.id,
                deliveredTo = localNodeId,
                deliveredAt = System.currentTimeMillis(),
                receivedFromNodeId = envelope.senderNodeId
            )

            database.ackDao().upsert(ack)
            sendAck(endpointId, received.id, localNodeId)

            scope.launch {
                delay(500)
                sendAck(endpointId, received.id, localNodeId)
            }

            return
        }

        database.messageDao().upsert(
            received.toEntity(localNodeId).copy(
                text = if (received.isEncrypted) "Encrypted relay message" else received.text
            )
        )

        scope.launch {
            tryForwardAll()
        }
    }

    private suspend fun handleAck(
        ack: AckPayload,
        receivedFromEndpointId: String? = null
    ) {
        val alreadyHadAck = database.ackDao().hasAck(ack.messageId)

        val entity = AckEntity(
            messageId = ack.messageId,
            deliveredTo = ack.deliveredTo,
            deliveredAt = ack.deliveredAt,
            receivedFromNodeId = ack.receivedFromNodeId,
            createdAt = System.currentTimeMillis()
        )

        database.ackDao().upsert(entity)
        database.ackDao().markOwnMessagesDelivered(listOf(ack.messageId), localNodeId)
        database.ackDao().deleteRelayedMessagesByAck(listOf(ack.messageId), localNodeId)
        database.ackDao().pruneAcks(config.ackMaxSize)

        if (!alreadyHadAck) {
            floodAck(ack, exceptEndpointId = receivedFromEndpointId)
        }
    }

    private suspend fun floodAck(
        ack: AckPayload,
        exceptEndpointId: String? = null
    ) {
        connectedEndpoints
            .filter { it != exceptEndpointId }
            .forEach { endpointId ->
                sendEnvelope(
                    endpointId,
                    baseEnvelope(
                        type = CercaPayloadType.ACK,
                        ack = ack
                    )
                )
            }
    }

    private suspend fun sendHello(endpointId: String) {
        val envelope = baseEnvelope(
            type = CercaPayloadType.HELLO,
            hello = HelloPayload(
                batteryLevel = statusProvider.batteryRatio(),
                bufferFreeRatio = statusProvider.bufferFreeRatio(),
                hasInternetGateway = statusProvider.hasInternet(),
                lastInternetAt = lastInternetAt,
                credits = localCredits,
                smoothedDensity = smoothedDensity,
                nodeMode = localNodeMode.name
            )
        )
        sendEnvelope(endpointId, envelope)
    }

    private suspend fun sendSummary(endpointId: String) {
        val predictions = database.predictionDao().all().associate { it.targetNodeId to it.value }
        val localMessageIds = database.messageDao().getBufferMessageIds().take(100)
        val recentAcks = database.ackDao().latest(config.ackMaxSize).take(50).map {
            AckPayload(it.messageId, it.deliveredTo, it.deliveredAt, it.receivedFromNodeId)
        }
        val envelope = baseEnvelope(
            type = CercaPayloadType.SUMMARY,
            summary = SummaryPayload(
                batteryLevel = statusProvider.batteryRatio(),
                bufferFreeRatio = statusProvider.bufferFreeRatio(),
                hasInternetGateway = statusProvider.hasInternet(),
                lastInternetAt = lastInternetAt,
                credits = localCredits,
                smoothedDensity = smoothedDensity,
                nodeMode = localNodeMode.name,
                messageIds = localMessageIds,
                recentAcks = recentAcks,
                ackedMessageIds = recentAcks.map { it.messageId },
                predictabilities = predictions
            )
        )
        sendEnvelope(endpointId, envelope)
    }

    private suspend fun sendAck(endpointId: String, messageId: String, deliveredTo: String) {
        Log.d(TAG, "sendAck messageId=$messageId deliveredTo=$deliveredTo endpoint=$endpointId")
        sendEnvelope(endpointId, baseEnvelope(type = CercaPayloadType.ACK, ack = AckPayload(messageId, deliveredTo, System.currentTimeMillis(), localNodeId)))
    }

    private suspend fun cleanupLocalBuffer() {
        val now = System.currentTimeMillis()

        database.messageDao().markExpiredMessages(now)

        val ackIds = database.ackDao().allIds()

        if (ackIds.isNotEmpty()) {
            database.ackDao().markOwnMessagesDelivered(ackIds, localNodeId)
            database.ackDao().deleteRelayedMessagesByAck(ackIds, localNodeId)
            database.ackDao().pruneAcks(config.ackMaxSize)
        }
    }

    private suspend fun forwardPublicBroadcastNow(message: DtnMessageEntity) {
        if (message.destinationScope != DestinationScope.PUBLIC_BROADCAST.name) return
        if (message.ttlExpiresAt <= System.currentTimeMillis()) return
        if (message.copiesLeft <= 0) return

        val payload = message.toPayload()

        val nearbyPeers = database.peerDao()
            .all()
            .filter { peer ->
                !peer.endpointId.isNullOrBlank() &&
                        (peer.isNearby || peer.endpointId in connectedEndpoints)
            }

        if (nearbyPeers.isEmpty() && connectedEndpoints.isEmpty()) {
            Log.d(TAG, "Broadcast ${message.id}: no nearby peers/endpoints")
            return
        }

        var sentCount = 0

        nearbyPeers.forEach { peer ->
            val endpointId = peer.endpointId ?: return@forEach

            val peerAlreadyHasMessage =
                remoteMessageIds[peer.nodeId]?.contains(message.id) == true

            val peerAlreadyInPath =
                payload.path.contains(peer.nodeId)

            if (peerAlreadyHasMessage || peerAlreadyInPath) {
                return@forEach
            }

            if (sentCount >= message.copiesLeft) {
                return@forEach
            }

            val outboundPayload = payload.copy(
                copiesLeft = (message.copiesLeft - sentCount).coerceAtLeast(1),
                hopCount = message.hopCount,
                path = (payload.path + localNodeId).distinct()
            )

            Log.d(
                TAG,
                "Broadcast sending message=${message.id} to peer=${peer.nodeId} endpoint=$endpointId"
            )

            sendEnvelope(
                endpointId,
                baseEnvelope(
                    type = CercaPayloadType.MESSAGE,
                    message = outboundPayload
                )
            )

            sentCount++
        }

        /*
         * Fallback:
         * Si Nearby tiene endpoints conectados pero todavía no están mapeados a PeerEntity,
         * enviamos el broadcast a esos endpoints. Esto no afecta mensajes directos.
         */
        if (sentCount == 0 && connectedEndpoints.isNotEmpty()) {
            connectedEndpoints.forEach { endpointId ->
                if (sentCount >= message.copiesLeft) return@forEach

                val outboundPayload = payload.copy(
                    copiesLeft = (message.copiesLeft - sentCount).coerceAtLeast(1),
                    hopCount = message.hopCount,
                    path = (payload.path + localNodeId).distinct()
                )

                Log.d(
                    TAG,
                    "Broadcast fallback sending message=${message.id} endpoint=$endpointId"
                )

                sendEnvelope(
                    endpointId,
                    baseEnvelope(
                        type = CercaPayloadType.MESSAGE,
                        message = outboundPayload
                    )
                )

                sentCount++
            }
        }

        if (sentCount > 0) {
            val remainingCopies = (message.copiesLeft - sentCount).coerceAtLeast(0)

            database.messageDao().updateRoutingState(
                messageId = message.id,
                copiesLeft = remainingCopies,
                hopCount = message.hopCount,
                pathCsv = payload.path.joinToString(","),
                status = MessageStatus.RELAYED.name,
                bestRelayName = "Public broadcast",
                utilityScore = 1.0f
            )
        }
    }

    private suspend fun tryForwardAll() {
        updateLocalInternetTimestampOnly()
        cleanupLocalBuffer()

        val nearbyPeers = database.peerDao()
            .all()
            .filter { peer ->
                !peer.endpointId.isNullOrBlank() &&
                        (peer.isNearby || peer.endpointId in connectedEndpoints)
            }

        if (nearbyPeers.isEmpty()) return

        val messages = database.messageDao().getForwardableMessages()

        for (message in messages) {
            if (database.ackDao().hasAck(message.id)) continue
            if (message.ttlExpiresAt <= System.currentTimeMillis()) continue

            if (message.copiesLeft <= 0) {
                Log.d(TAG, "tryForwardAll skip message=${message.id}: copiesLeft=${message.copiesLeft}")
                continue
            }

            val payload = message.toPayload()

            val candidate = nearbyPeers
                .mapNotNull { peer -> buildCandidate(message, payload, peer) }
                .filter { it.decision.shouldForward }
                .sortedWith(
                    compareByDescending<CandidateForward> { it.decision.utilityPeer }
                        .thenBy { it.message.ttlExpiresAt }
                        .thenBy { it.message.id }
                )
                .firstOrNull()

            if (candidate != null) {
                forwardMessage(candidate)
            }
        }
    }

    private suspend fun buildCandidate(
        message: DtnMessageEntity,
        payload: CercaMessagePayload,
        peer: PeerEntity
    ): CandidateForward? {
        val endpointId = peer.endpointId ?: return null

        val selfPrediction =
            database.predictionDao().get(payload.destinationId)?.value ?: 0.0

        val peerPrediction =
            remotePredictabilities[peer.nodeId]?.get(payload.destinationId) ?: 0.0

        val self = NodeContext(
            nodeId = localNodeId,
            displayName = displayName,
            batteryLevel = statusProvider.batteryRatio(),
            bufferFreeRatio = statusProvider.bufferFreeRatio(),
            hasInternetGateway = statusProvider.hasInternet(),
            lastInternetAt = lastInternetAt,
            credits = localCredits,
            smoothedDensity = smoothedDensity,
            predictabilityToDestination = selfPrediction,
            nodeMode = localNodeMode.name
        )

        val peerCtx = NodeContext(
            nodeId = peer.nodeId,
            displayName = peer.displayName,
            batteryLevel = peer.batteryLevel,
            bufferFreeRatio = peer.bufferFreeRatio,
            hasInternetGateway = peer.hasInternetGateway,
            lastInternetAt = peer.lastInternetAt,
            credits = peer.credits,
            smoothedDensity = peer.smoothedDensity,
            predictabilityToDestination = peerPrediction,
            nodeMode = peer.nodeMode
        )

        val messageSizeBytes = (payload.encryptedBodyJson ?: payload.text)
            .toByteArray(StandardCharsets.UTF_8)
            .size

        if (wasRecentlyForwarded(payload.id, peer.nodeId)) {
            return null
        }

        val decision = router.forwardingPriority(
            self = self,
            peer = peerCtx,
            message = payload,
            peerAlreadyHasMessage = remoteMessageIds[peer.nodeId]?.contains(payload.id) == true,
            peerAlreadyInPath = payload.path.contains(peer.nodeId),
            messageSizeBytes = messageSizeBytes
        )

        return CandidateForward(
            message = message,
            payload = payload,
            peer = peer,
            endpointId = endpointId,
            decision = decision
        )
    }

    private suspend fun forwardMessage(candidate: CandidateForward) {
        val message = candidate.message
        val payload = candidate.payload
        val newPath = (payload.path + localNodeId).distinct()
        val outboundPayload = payload.copy(
            copiesLeft = if (message.destinationId == candidate.peer.nodeId) 1 else router.receiverCopiesAfterForward(message.copiesLeft),
            hopCount = message.hopCount,
            path = newPath
        )
        sendMessageEnvelope(
            endpointId = candidate.endpointId,
            messageId = message.id,
            envelope = baseEnvelope(
                type = CercaPayloadType.MESSAGE,
                message = outboundPayload
            )
        )
        markRecentlyForwarded(message.id, candidate.peer.nodeId)

        val senderCopies = if (message.destinationId == candidate.peer.nodeId) {
            message.copiesLeft
        } else {
            router.senderCopiesAfterForward(message.copiesLeft)
        }
        val newStatus = if (message.destinationId == candidate.peer.nodeId) {
            MessageStatus.SENDING.name
        } else {
            MessageStatus.RELAYED.name
        }
        database.messageDao().updateRoutingState(
            messageId = message.id,
            copiesLeft = senderCopies,
            hopCount = message.hopCount,
            pathCsv = newPath.joinToString(","),
            status = newStatus,
            bestRelayName = candidate.peer.displayName,
            utilityScore = candidate.decision.utilityPeer.toFloat()
        )
    }

    private fun sendEnvelope(endpointId: String, envelope: CercaEnvelope) {
        val raw = json.encodeToString(envelope)
        val bytes = raw.toByteArray(StandardCharsets.UTF_8)

        runCatching {
            client.sendPayload(endpointId, Payload.fromBytes(bytes))
                .addOnSuccessListener {
                    Log.d(TAG, "sendPayload SUCCESS endpointId=$endpointId type=${envelope.type}")
                }
                .addOnFailureListener { error ->
                    Log.e(
                        TAG,
                        "sendPayload FAILED endpointId=$endpointId type=${envelope.type} error=${error.message}",
                        error
                    )

                    when (envelope.type) {
                        CercaPayloadType.MESSAGE -> {
                            connectedEndpoints.remove(endpointId)
                            connectingEndpoints.remove(endpointId)

                            scope.launch {
                                database.peerDao().markDisconnected(endpointId)
                                updateDensity()
                                startDiscovery()
                            }
                        }

                        CercaPayloadType.HELLO,
                        CercaPayloadType.SUMMARY,
                        CercaPayloadType.ACK -> {

                            scope.launch {
                                startDiscovery()
                            }
                        }
                    }
                }
        }.onFailure { error ->
            Log.e(TAG, "sendPayload THREW endpointId=$endpointId type=${envelope.type} error=${error.message}", error)

            connectedEndpoints.remove(endpointId)
            connectingEndpoints.remove(endpointId)

            scope.launch {
                database.peerDao().markDisconnected(endpointId)
                updateDensity()
            }

            restartNearbySoon()
        }
    }

    private fun baseEnvelope(type: CercaPayloadType, hello: HelloPayload? = null, summary: SummaryPayload? = null, message: CercaMessagePayload? = null, ack: AckPayload? = null): CercaEnvelope =
        CercaEnvelope(type = type, senderNodeId = localNodeId, senderDisplayName = displayName, hello = hello, summary = summary, message = message, ack = ack)

    private fun visibleName(rawName: String): String {
        return rawName.substringAfter("|", rawName).ifBlank { rawName }
    }

    private fun advertisedNodeId(rawName: String): String? {
        if (!rawName.contains("|")) return null

        return rawName.substringBefore("|")
            .takeIf { it.isNotBlank() }
    }
    private suspend fun rememberPeerFromEnvelope(endpointId: String, envelope: CercaEnvelope) {
        val now = System.currentTimeMillis()

        val peer = when {
            envelope.hello != null -> {
                PeerEntity(
                    nodeId = envelope.senderNodeId,
                    endpointId = endpointId,
                    displayName = visibleName(envelope.senderDisplayName),
                    isNearby = true,
                    batteryLevel = envelope.hello.batteryLevel,
                    bufferFreeRatio = envelope.hello.bufferFreeRatio,
                    linkQuality = 1.0,
                    hasInternetGateway = envelope.hello.hasInternetGateway,
                    nodeMode = envelope.hello.nodeMode,
                    lastInternetAt = envelope.hello.lastInternetAt,
                    credits = envelope.hello.credits,
                    smoothedDensity = envelope.hello.smoothedDensity,
                    lastSeenAt = now
                )
            }

            envelope.summary != null -> {
                PeerEntity(
                    nodeId = envelope.senderNodeId,
                    endpointId = endpointId,
                    displayName = visibleName(envelope.senderDisplayName),
                    isNearby = true,
                    batteryLevel = envelope.summary.batteryLevel,
                    bufferFreeRatio = envelope.summary.bufferFreeRatio,
                    linkQuality = 1.0,
                    hasInternetGateway = envelope.summary.hasInternetGateway,
                    nodeMode = envelope.summary.nodeMode,
                    lastInternetAt = envelope.summary.lastInternetAt,
                    credits = envelope.summary.credits,
                    smoothedDensity = envelope.summary.smoothedDensity,
                    lastSeenAt = now
                )
            }

            else -> {
                PeerEntity(
                    nodeId = envelope.senderNodeId,
                    endpointId = endpointId,
                    displayName = visibleName(envelope.senderDisplayName),
                    isNearby = true,
                    batteryLevel = 1.0,
                    bufferFreeRatio = 1.0,
                    linkQuality = 1.0,
                    hasInternetGateway = false,
                    nodeMode = NodeMode.CITIZEN.name,
                    lastInternetAt = null,
                    credits = 25.0,
                    smoothedDensity = 0.0,
                    lastSeenAt = now
                )
            }
        }

        if (peer.nodeId == localNodeId) {
            Log.d(TAG, "Not saving local node as peer endpointId=$endpointId")
            return
        }

        database.peerDao().disconnectOldEndpointsForNode(
            nodeId = peer.nodeId,
            currentEndpointId = endpointId
        )

        database.peerDao().upsert(peer)
    }

    private suspend fun upsertPeerFromRemote(endpointId: String, nodeId: String, name: String, hello: HelloPayload) {
        if (nodeId == localNodeId) {
            Log.d(TAG, "Not saving local node as peer endpointId=$endpointId")
            return
        }

        database.peerDao().disconnectOldEndpointsForNode(
            nodeId = nodeId,
            currentEndpointId = endpointId
        )

        database.peerDao().upsert(PeerEntity(nodeId, endpointId, visibleName(name), true, hello.batteryLevel, hello.bufferFreeRatio, 1.0, hello.hasInternetGateway, hello.nodeMode, hello.lastInternetAt, hello.credits, hello.smoothedDensity, System.currentTimeMillis()))
    }

    private suspend fun upsertPeerFromRemote(endpointId: String, nodeId: String, name: String, summary: SummaryPayload) {

        if (nodeId == localNodeId) {
            Log.d(TAG, "Not saving local node as peer endpointId=$endpointId")
            return
        }

        database.peerDao().disconnectOldEndpointsForNode(
            nodeId = nodeId,
            currentEndpointId = endpointId
        )

        database.peerDao().upsert(PeerEntity(nodeId, endpointId, visibleName(name), true, summary.batteryLevel, summary.bufferFreeRatio, 1.0, summary.hasInternetGateway, summary.nodeMode, summary.lastInternetAt, summary.credits, summary.smoothedDensity, System.currentTimeMillis()))
    }

    private suspend fun updatePredictabilityOnEncounter(peerNodeId: String) {
        val now = System.currentTimeMillis()
        val current = database.predictionDao().get(peerNodeId)
        val aged = current?.let { router.agePrediction(it.value, now - it.lastAgedAt) } ?: 0.0
        val updated = router.updateDirectPredictability(aged, current?.lastEncounterAt, now)
        database.predictionDao().upsert(PredictionEntity(peerNodeId, updated, now, now))
    }

    private suspend fun updateDensity() {
        val nearbyCount = database.peerDao().countNearby()

        val normalizedDensity = (nearbyCount.toDouble() / 10.0).coerceIn(0.0, 1.0)

        smoothedDensity = (0.7 * smoothedDensity) + (0.3 * normalizedDensity)
    }

    private fun refreshLocalInternetState() {
        if (!statusProvider.hasInternet()) return

        val now = System.currentTimeMillis()
        lastInternetAt = now

        val minSyncIntervalMillis = 60_000L

        if (now - lastCloudSyncAt < minSyncIntervalMillis) return

        lastCloudSyncAt = now

        scope.launch {
            runCatching {
                cloudSyncService.syncNow()
                tryForwardAll()
            }.onFailure { error ->
                Log.e(TAG, "cloudSync failed: ${error.message}", error)
            }
        }
    }

    private data class CandidateForward(
        val message: DtnMessageEntity,
        val payload: CercaMessagePayload,
        val peer: PeerEntity,
        val endpointId: String,
        val decision: com.leobigott.cercamessenger.protocol.cerca.ForwardDecision
    )

    override suspend fun markConversationRead(conversationId: String) {
        database.messageDao().markConversationRead(
            conversationId = conversationId,
            localNodeId = localNodeId,
            readAt = System.currentTimeMillis()
        )

        notificationHelper.cancelConversationNotifications(conversationId)
    }

    override suspend fun restartNearby() {
        Log.d(TAG, "Manual restartNearby called")

        stopDiscovery()
        kotlinx.coroutines.delay(1000)

        if (hasNearbyPermissions()) {

            startDiscovery()
        } else {
            Log.e(TAG, "restartNearby aborted: missing permissions")
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun forceNearbyScan() = nearbyOperationMutex.withLock {
        if (!hasNearbyPermissions()) {
            Log.e(TAG, "forceNearbyScan aborted: missing Nearby permissions")
            return
        }

        Log.d(
            TAG,
            "forceNearbyScan called. connected=${connectedEndpoints.size}, connecting=${connectingEndpoints.size}"
        )

        if (connectedEndpoints.isEmpty()) {
            lastEmptyNearbyResetAt = System.currentTimeMillis()
            resetNearbyWhenEmpty("manual force scan")
        } else {
            restartAdvertisingAndDiscoveryOnly()
        }

        connectedEndpoints.toList().forEach { endpointId ->
            sendHello(endpointId)
            sendSummary(endpointId)
        }

        tryForwardAll()
    }

    override suspend fun deleteMessage(messageId: String) {
        database.deletedMessageDao().upsert(
            DeletedMessageEntity(
                messageId = messageId,
                deletedAt = System.currentTimeMillis(),
                reason = "manual_delete"
            )
        )
        database.messageDao().deleteMessageById(messageId)
    }

    override suspend fun deletePublicBroadcast(messageId: String) {
        database.deletedMessageDao().upsert(
            DeletedMessageEntity(
                messageId = messageId,
                deletedAt = System.currentTimeMillis(),
                reason = "public_broadcast_delete"
            )
        )
        database.messageDao().deleteMessageById(messageId)
    }

    override suspend fun deleteAllPublicBroadcasts() {
        val ids = database.messageDao().getPublicBroadcastIds(
            CrisisConstants.PUBLIC_BROADCAST_CONVERSATION_ID
        )

        ids.forEach { id ->
            database.deletedMessageDao().upsert(
                DeletedMessageEntity(
                    messageId = id,
                    deletedAt = System.currentTimeMillis(),
                    reason = "public_broadcast_bulk_delete"
                )
            )
        }

        database.messageDao().deleteAllPublicBroadcasts(
            CrisisConstants.PUBLIC_BROADCAST_CONVERSATION_ID
        )
    }

    override suspend fun deleteConversation(conversationId: String) {
        val ids = database.messageDao().getMessageIdsByConversation(conversationId)

        ids.forEach { id ->
            database.deletedMessageDao().upsert(
                DeletedMessageEntity(
                    messageId = id,
                    deletedAt = System.currentTimeMillis(),
                    reason = "conversation_delete"
                )
            )
        }

        database.messageDao().deleteConversation(conversationId)
    }
}
