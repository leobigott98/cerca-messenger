package com.leobigott.cercamessenger.protocol.cloud

import android.content.Context
import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.leobigott.cercamessenger.core.model.CrisisConstants
import com.leobigott.cercamessenger.core.model.CrisisMessageType
import com.leobigott.cercamessenger.core.model.DestinationScope
import com.leobigott.cercamessenger.core.model.MessageStatus
import com.leobigott.cercamessenger.core.model.NodeMode
import com.leobigott.cercamessenger.data.local.AckEntity
import com.leobigott.cercamessenger.data.local.CercaDatabase
import com.leobigott.cercamessenger.data.local.DtnMessageEntity
import com.leobigott.cercamessenger.data.local.PeerEntity
import com.leobigott.cercamessenger.protocol.crypto.ContactCrypto
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import java.util.Date
import kotlin.math.max

class FirebaseCloudSyncService(
    private val context: Context,
    private val database: CercaDatabase,
    private val localNodeId: String,
    private val crypto: ContactCrypto = ContactCrypto(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : CloudSyncService {

    private companion object {
        private const val TAG = "CERCA_Firebase"

        private const val PREFS = "cerca_cloud_sync"

        private const val KEY_LAST_OWN_MESSAGE_DOWNLOAD_AT = "last_own_message_download_at"
        private const val KEY_LAST_ACK_DOWNLOAD_AT = "last_ack_download_at"
        private const val KEY_LAST_CRISIS_DOWNLOAD_AT = "last_crisis_download_at"
        private const val KEY_LAST_PUBLIC_DOWNLOAD_AT = "last_public_download_at"
        private const val KEY_LAST_CONFIG_FETCH_AT = "last_config_fetch_at"

        private const val MIN_OWN_MESSAGE_DOWNLOAD_INTERVAL_MS = 3 * 60 * 1000L
        private const val MIN_ACK_DOWNLOAD_INTERVAL_MS = 60 * 1000L
        private const val MIN_GATEWAY_DOWNLOAD_INTERVAL_MS = 5 * 60 * 1000L
        private const val MIN_CRISIS_DOWNLOAD_INTERVAL_MS = 5 * 60 * 1000L
        private const val MIN_PUBLIC_DOWNLOAD_INTERVAL_MS = 10 * 60 * 1000L
        private const val MIN_CONFIG_FETCH_INTERVAL_MS = 30 * 60 * 1000L

        private const val ACK_TTL_MILLIS = 4 * 60 * 60 * 1000L

        private const val DEFAULT_DIRECT_DOWNLOAD_LIMIT = 30
        private const val DEFAULT_ACK_DOWNLOAD_LIMIT = 50
        private const val DEFAULT_CRISIS_DOWNLOAD_LIMIT = 30
        private const val DEFAULT_PUBLIC_DOWNLOAD_LIMIT = 20
        private const val DEFAULT_GATEWAY_MAX_TOTAL_MESSAGES = 40

        private const val RECENT_NODE_WINDOW_MS = 30 * 60 * 1000L
        private const val RECENT_INTERNET_WINDOW_MS = 15 * 60 * 1000L
        private const val GATEWAY_TARGET_WINDOW_MS = 2 * 60 * 60 * 1000L
    }

    private val syncMutex = Mutex()

    private var cachedConfig: CloudRuntimeConfig = CloudRuntimeConfig()
    private var configLoadedAt: Long = 0L

    private data class CloudRuntimeConfig(
        val cloudEnabled: Boolean = true,
        val cloudUploadEnabled: Boolean = true,
        val cloudDownloadEnabled: Boolean = true,
        val gatewayDownloadEnabled: Boolean = true,
        val crisisDownloadEnabled: Boolean = true,
        val publicBroadcastDownloadEnabled: Boolean = true,
        val directDownloadLimit: Int = DEFAULT_DIRECT_DOWNLOAD_LIMIT,
        val ackDownloadLimit: Int = DEFAULT_ACK_DOWNLOAD_LIMIT,
        val crisisDownloadLimit: Int = DEFAULT_CRISIS_DOWNLOAD_LIMIT,
        val publicDownloadLimit: Int = DEFAULT_PUBLIC_DOWNLOAD_LIMIT,
        val gatewayMaxTotalMessages: Int = DEFAULT_GATEWAY_MAX_TOTAL_MESSAGES,
        val blockedNodeIds: Set<String> = emptySet()
    )

    private data class GatewayPolicy(
        val threshold: Double,
        val maxTargetNodes: Int,
        val maxMessagesPerNode: Int,
        val maxTotalMessages: Int
    )

    private data class ScoredPeer(
        val peer: PeerEntity,
        val score: Double
    )

    private fun prefs() =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun getLastSyncAt(key: String): Long {
        return prefs().getLong(key, 0L)
    }

    private fun setLastSyncAt(key: String, value: Long) {
        prefs().edit().putLong(key, value).apply()
    }

    private fun canRunSync(key: String, minIntervalMs: Long, now: Long): Boolean {
        val last = getLastSyncAt(key)
        return now - last >= minIntervalMs
    }

    private fun gatewayMessageKey(nodeId: String): String {
        return "last_gateway_messages_$nodeId"
    }

    private fun gatewayAckKey(nodeId: String): String {
        return "last_gateway_acks_$nodeId"
    }

    override suspend fun uploadOnly() = syncMutex.withLock {
        ensureAnonymousAuth()

        val config = loadRuntimeConfig()
        if (!config.cloudEnabled || !config.cloudUploadEnabled) {
            Log.d(TAG, "uploadOnly skipped: cloud upload disabled")
            return@withLock
        }

        if (localNodeId in config.blockedNodeIds) {
            Log.e(TAG, "uploadOnly skipped: local node is blocked")
            return@withLock
        }

        uploadUnsyncedMessages(config)
    }

    override suspend fun downloadNow() = syncMutex.withLock {
        ensureAnonymousAuth()

        val config = loadRuntimeConfig()
        if (!config.cloudEnabled || !config.cloudDownloadEnabled) {
            Log.d(TAG, "downloadNow skipped: cloud download disabled")
            return@withLock
        }

        if (localNodeId in config.blockedNodeIds) {
            Log.e(TAG, "downloadNow skipped: local node is blocked")
            return@withLock
        }

        val now = System.currentTimeMillis()

        if (canRunSync(KEY_LAST_ACK_DOWNLOAD_AT, MIN_ACK_DOWNLOAD_INTERVAL_MS, now)) {
            downloadOwnAcks(config)
            setLastSyncAt(KEY_LAST_ACK_DOWNLOAD_AT, now)
        }

        if (canRunSync(KEY_LAST_OWN_MESSAGE_DOWNLOAD_AT, MIN_OWN_MESSAGE_DOWNLOAD_INTERVAL_MS, now)) {
            downloadOwnMessages(config)
            setLastSyncAt(KEY_LAST_OWN_MESSAGE_DOWNLOAD_AT, now)
        }

        if (config.gatewayDownloadEnabled &&
            canRunSync("last_gateway_download_at", MIN_GATEWAY_DOWNLOAD_INTERVAL_MS, now)
        ) {
            downloadGatewayBatches(config)
            setLastSyncAt("last_gateway_download_at", now)
        }

        if (config.crisisDownloadEnabled &&
            canRunSync(KEY_LAST_CRISIS_DOWNLOAD_AT, MIN_CRISIS_DOWNLOAD_INTERVAL_MS, now)
        ) {
            downloadCrisisReports(config)
            setLastSyncAt(KEY_LAST_CRISIS_DOWNLOAD_AT, now)
        }

        if (config.publicBroadcastDownloadEnabled &&
            canRunSync(KEY_LAST_PUBLIC_DOWNLOAD_AT, MIN_PUBLIC_DOWNLOAD_INTERVAL_MS, now)
        ) {
            downloadPublicBroadcasts(config)
            setLastSyncAt(KEY_LAST_PUBLIC_DOWNLOAD_AT, now)
        }
    }

    override suspend fun syncNow() = syncMutex.withLock {
        ensureAnonymousAuth()

        val config = loadRuntimeConfig()
        if (!config.cloudEnabled) {
            Log.d(TAG, "syncNow skipped: cloud disabled")
            return@withLock
        }

        if (localNodeId in config.blockedNodeIds) {
            Log.e(TAG, "syncNow skipped: local node is blocked")
            return@withLock
        }

        if (config.cloudUploadEnabled) {
            uploadUnsyncedMessages(config)
        }

        if (config.cloudDownloadEnabled) {
            val now = System.currentTimeMillis()

            if (canRunSync(KEY_LAST_ACK_DOWNLOAD_AT, MIN_ACK_DOWNLOAD_INTERVAL_MS, now)) {
                downloadOwnAcks(config)
                setLastSyncAt(KEY_LAST_ACK_DOWNLOAD_AT, now)
            }

            if (canRunSync(KEY_LAST_OWN_MESSAGE_DOWNLOAD_AT, MIN_OWN_MESSAGE_DOWNLOAD_INTERVAL_MS, now)) {
                downloadOwnMessages(config)
                setLastSyncAt(KEY_LAST_OWN_MESSAGE_DOWNLOAD_AT, now)
            }

            if (config.gatewayDownloadEnabled &&
                canRunSync("last_gateway_download_at", MIN_GATEWAY_DOWNLOAD_INTERVAL_MS, now)
            ) {
                downloadGatewayBatches(config)
                setLastSyncAt("last_gateway_download_at", now)
            }

            if (config.crisisDownloadEnabled &&
                canRunSync(KEY_LAST_CRISIS_DOWNLOAD_AT, MIN_CRISIS_DOWNLOAD_INTERVAL_MS, now)
            ) {
                downloadCrisisReports(config)
                setLastSyncAt(KEY_LAST_CRISIS_DOWNLOAD_AT, now)
            }

            if (config.publicBroadcastDownloadEnabled &&
                canRunSync(KEY_LAST_PUBLIC_DOWNLOAD_AT, MIN_PUBLIC_DOWNLOAD_INTERVAL_MS, now)
            ) {
                downloadPublicBroadcasts(config)
                setLastSyncAt(KEY_LAST_PUBLIC_DOWNLOAD_AT, now)
            }
        }
    }

    private suspend fun ensureAnonymousAuth() {
        if (auth.currentUser == null) {
            Log.d(TAG, "No Firebase user. Signing in anonymously...")
            auth.signInAnonymously().await()
        }

        Log.d(
            TAG,
            "Firebase auth user=${auth.currentUser?.uid} isAnonymous=${auth.currentUser?.isAnonymous}"
        )
    }

    private suspend fun loadRuntimeConfig(): CloudRuntimeConfig {
        val now = System.currentTimeMillis()

        if (now - configLoadedAt < MIN_CONFIG_FETCH_INTERVAL_MS) {
            return cachedConfig
        }

        val lastFetch = getLastSyncAt(KEY_LAST_CONFIG_FETCH_AT)
        if (now - lastFetch < MIN_CONFIG_FETCH_INTERVAL_MS) {
            return cachedConfig
        }

        return runCatching {
            val doc = firestore.collection("app_config")
                .document("global")
                .get()
                .await()

            if (!doc.exists()) {
                cachedConfig
            } else {
                CloudRuntimeConfig(
                    cloudEnabled = doc.getBoolean("cloudEnabled") ?: true,
                    cloudUploadEnabled = doc.getBoolean("cloudUploadEnabled") ?: true,
                    cloudDownloadEnabled = doc.getBoolean("cloudDownloadEnabled") ?: true,
                    gatewayDownloadEnabled = doc.getBoolean("gatewayDownloadEnabled") ?: true,
                    crisisDownloadEnabled = doc.getBoolean("crisisDownloadEnabled") ?: true,
                    publicBroadcastDownloadEnabled = doc.getBoolean("publicBroadcastDownloadEnabled") ?: true,
                    directDownloadLimit = (doc.getLong("directDownloadLimit") ?: DEFAULT_DIRECT_DOWNLOAD_LIMIT.toLong()).toInt(),
                    ackDownloadLimit = (doc.getLong("ackDownloadLimit") ?: DEFAULT_ACK_DOWNLOAD_LIMIT.toLong()).toInt(),
                    crisisDownloadLimit = (doc.getLong("crisisDownloadLimit") ?: DEFAULT_CRISIS_DOWNLOAD_LIMIT.toLong()).toInt(),
                    publicDownloadLimit = (doc.getLong("publicDownloadLimit") ?: DEFAULT_PUBLIC_DOWNLOAD_LIMIT.toLong()).toInt(),
                    gatewayMaxTotalMessages = (doc.getLong("gatewayMaxTotalMessages") ?: DEFAULT_GATEWAY_MAX_TOTAL_MESSAGES.toLong()).toInt(),
                    blockedNodeIds = (doc.get("blockedNodeIds") as? List<*>)
                        ?.mapNotNull { it as? String }
                        ?.toSet()
                        ?: emptySet()
                )
            }
        }.onSuccess { config ->
            cachedConfig = config
            configLoadedAt = now
            setLastSyncAt(KEY_LAST_CONFIG_FETCH_AT, now)
            Log.d(TAG, "Cloud config loaded: $config")
        }.onFailure { error ->
            Log.e(TAG, "Cloud config load failed, using cached/default config: ${error.message}", error)
            configLoadedAt = now
            setLastSyncAt(KEY_LAST_CONFIG_FETCH_AT, now)
        }.getOrDefault(cachedConfig)
    }

    private suspend fun uploadUnsyncedMessages(config: CloudRuntimeConfig) {
        val now = System.currentTimeMillis()
        val unsynced = database.messageDao()
            .getUnsyncedCloudMessages()
            .take(config.gatewayMaxTotalMessages.coerceAtLeast(10))

        Log.d(TAG, "uploadUnsyncedMessages candidates=${unsynced.size}")

        unsynced.forEach { message ->
            if (message.ttlExpiresAt <= now) return@forEach

            val docRef = cloudDocumentForMessage(message)
            val data = message.toFirestoreMap()

            runCatching {
                docRef.set(data, SetOptions.merge()).await()
                database.messageDao().markSyncedToCloud(message.id)

                Log.d(
                    TAG,
                    "Cloud upload SUCCESS id=${message.id} path=${docRef.path} destination=${message.destinationId}"
                )
            }.onFailure { error ->
                Log.e(
                    TAG,
                    "Cloud upload FAILED id=${message.id} path=${docRef.path} error=${error.message}",
                    error
                )
            }
        }
    }

    private fun cloudDocumentForMessage(message: DtnMessageEntity): DocumentReference {
        val isBroadcast =
            message.destinationScope == DestinationScope.PUBLIC_BROADCAST.name

        if (isBroadcast) {
            val isPublicBroadcast =
                message.crisisType == CrisisMessageType.PUBLIC_BROADCAST.name ||
                        message.conversationId == CrisisConstants.PUBLIC_BROADCAST_CONVERSATION_ID

            val collection = if (isPublicBroadcast) {
                "public_broadcasts"
            } else {
                "crisis_reports"
            }

            return firestore.collection(collection).document(message.id)
        }

        return firestore.collection("node_inboxes")
            .document(message.destinationId)
            .collection("messages")
            .document(message.id)
    }

    private suspend fun downloadOwnMessages(config: CloudRuntimeConfig) {
        val now = System.currentTimeMillis()
        val lastDownloadAt = getLastSyncAt(KEY_LAST_OWN_MESSAGE_DOWNLOAD_AT)

        val snapshot = firestore.collection("node_inboxes")
            .document(localNodeId)
            .collection("messages")
            .whereGreaterThan("timestamp", lastDownloadAt)
            .orderBy("timestamp")
            .limit(config.directDownloadLimit.toLong())
            .get()
            .await()

        Log.d(TAG, "downloadOwnMessages snapshot=${snapshot.size()}")

        var maxTimestamp = lastDownloadAt
        val localExisting = database.messageDao().getAllMessageIds().toSet()
        val downloaded = mutableListOf<DtnMessageEntity>()

        snapshot.documents.forEach { doc ->
            val timestamp = doc.getLong("timestamp") ?: now
            maxTimestamp = max(maxTimestamp, timestamp)

            val ttlExpiresAt = doc.getLong("ttlExpiresAt") ?: return@forEach
            if (ttlExpiresAt <= now) return@forEach

            val senderId = doc.getString("senderId") ?: return@forEach
            if (senderId == localNodeId) return@forEach

            val destinationId = doc.getString("destinationId") ?: return@forEach
            if (destinationId != localNodeId) return@forEach

            uploadDeliveryAck(
                messageId = doc.id,
                senderId = senderId,
                destinationId = destinationId,
                ttlExpiresAt = ttlExpiresAt
            )

            if (doc.id in localExisting) return@forEach

            val entity = doc.toDtnMessageEntity(
                now = now,
                forceDestinationScope = DestinationScope.DIRECT_CONTACT.name
            ) ?: return@forEach

            downloaded += entity.copy(
                conversationId = localDirectConversationId(senderId, destinationId),
                destinationScope = DestinationScope.DIRECT_CONTACT.name,
                status = MessageStatus.DELIVERED.name,
                copiesLeft = 0,
                syncedToCloud = true
            )
        }

        if (downloaded.isNotEmpty()) {
            database.messageDao().upsertAll(downloaded)
        }

        if (maxTimestamp > lastDownloadAt) {
            setLastSyncAt(KEY_LAST_OWN_MESSAGE_DOWNLOAD_AT, maxTimestamp)
        }

        Log.d(TAG, "downloadOwnMessages inserted=${downloaded.size}")
    }

    private suspend fun downloadOwnAcks(config: CloudRuntimeConfig) {
        val now = System.currentTimeMillis()
        val lastAckDownloadAt = getLastSyncAt(KEY_LAST_ACK_DOWNLOAD_AT)

        val snapshot = firestore.collection("node_inboxes")
            .document(localNodeId)
            .collection("acks")
            .whereGreaterThan("timestamp", lastAckDownloadAt)
            .orderBy("timestamp")
            .limit(config.ackDownloadLimit.toLong())
            .get()
            .await()

        Log.d(TAG, "downloadOwnAcks snapshot=${snapshot.size()}")

        var maxTimestamp = lastAckDownloadAt

        snapshot.documents.forEach { doc ->
            val timestamp = doc.getLong("timestamp") ?: now
            maxTimestamp = max(maxTimestamp, timestamp)
            insertAckFromCloud(doc, now)
        }

        if (maxTimestamp > lastAckDownloadAt) {
            setLastSyncAt(KEY_LAST_ACK_DOWNLOAD_AT, maxTimestamp)
        }
    }

    private suspend fun downloadGatewayBatches(config: CloudRuntimeConfig) {
        val targets = selectGatewayTargets(config)

        if (targets.isEmpty()) {
            Log.d(TAG, "downloadGatewayBatches skipped: no CERCA targets")
            return
        }

        var totalMessages = 0

        for (target in targets) {
            if (totalMessages >= config.gatewayMaxTotalMessages) break

            val remaining = config.gatewayMaxTotalMessages - totalMessages
            val insertedMessages = downloadGatewayMessagesForNode(
                nodeId = target.peer.nodeId,
                limit = remaining.coerceAtMost(3)
            )

            downloadGatewayAcksForNode(
                nodeId = target.peer.nodeId,
                limit = 5
            )

            totalMessages += insertedMessages
        }

        Log.d(TAG, "downloadGatewayBatches targets=${targets.size} messages=$totalMessages")
    }

    private suspend fun selectGatewayTargets(config: CloudRuntimeConfig): List<ScoredPeer> {
        val now = System.currentTimeMillis()
        val peers = database.peerDao()
            .all()
            .filter { it.nodeId != localNodeId }

        val recentNodes = peers.filter {
            now - it.lastSeenAt <= RECENT_NODE_WINDOW_MS
        }

        val recentGateways = recentNodes.count { peer ->
            peer.hasInternetGateway ||
                    peer.nodeMode == NodeMode.GATEWAY.name ||
                    (peer.lastInternetAt != null && now - peer.lastInternetAt <= RECENT_INTERNET_WINDOW_MS)
        }

        val policy = computeGatewayPolicy(
            recentNodeCount = recentNodes.size,
            recentGatewayCount = recentGateways,
            config = config
        )

        val predictions = database.predictionDao()
            .all()
            .associate { it.targetNodeId to it.value }

        val scored = peers
            .filter { now - it.lastSeenAt <= GATEWAY_TARGET_WINDOW_MS }
            .map { peer ->
                val prediction = predictions[peer.nodeId] ?: 0.0
                val recencyBoost = when {
                    now - peer.lastSeenAt <= 10 * 60 * 1000L -> 0.35
                    now - peer.lastSeenAt <= 30 * 60 * 1000L -> 0.25
                    else -> 0.10
                }

                val internetPenalty = if (
                    peer.hasInternetGateway ||
                    (peer.lastInternetAt != null && now - peer.lastInternetAt <= RECENT_INTERNET_WINDOW_MS)
                ) {
                    0.10
                } else {
                    0.0
                }

                val score = (max(prediction, recencyBoost) - internetPenalty)
                    .coerceIn(0.0, 1.0)

                ScoredPeer(peer = peer, score = score)
            }
            .sortedByDescending { it.score }

        val selected = scored
            .filter { it.score >= policy.threshold }
            .take(policy.maxTargetNodes)

        return if (selected.isNotEmpty()) {
            selected
        } else {
            scored.take((policy.maxTargetNodes / 2).coerceAtLeast(1))
        }
    }

    private fun computeGatewayPolicy(
        recentNodeCount: Int,
        recentGatewayCount: Int,
        config: CloudRuntimeConfig
    ): GatewayPolicy {
        val ratio = if (recentNodeCount <= 0) {
            0.0
        } else {
            recentGatewayCount.toDouble() / recentNodeCount.toDouble()
        }

        val base = when {
            recentNodeCount < 5 -> GatewayPolicy(
                threshold = 0.20,
                maxTargetNodes = 20,
                maxMessagesPerNode = 2,
                maxTotalMessages = 50
            )

            ratio == 0.0 -> GatewayPolicy(
                threshold = 0.10,
                maxTargetNodes = 40,
                maxMessagesPerNode = 3,
                maxTotalMessages = 100
            )

            ratio < 0.10 -> GatewayPolicy(
                threshold = 0.15,
                maxTargetNodes = 40,
                maxMessagesPerNode = 3,
                maxTotalMessages = 100
            )

            ratio < 0.25 -> GatewayPolicy(
                threshold = 0.30,
                maxTargetNodes = 20,
                maxMessagesPerNode = 3,
                maxTotalMessages = 50
            )

            ratio < 0.50 -> GatewayPolicy(
                threshold = 0.50,
                maxTargetNodes = 10,
                maxMessagesPerNode = 3,
                maxTotalMessages = 25
            )

            else -> GatewayPolicy(
                threshold = 0.65,
                maxTargetNodes = 5,
                maxMessagesPerNode = 2,
                maxTotalMessages = 15
            )
        }

        return base.copy(
            maxTotalMessages = base.maxTotalMessages.coerceAtMost(config.gatewayMaxTotalMessages),
            maxMessagesPerNode = base.maxMessagesPerNode.coerceAtMost(3)
        )
    }

    private suspend fun downloadGatewayMessagesForNode(
        nodeId: String,
        limit: Int
    ): Int {
        val now = System.currentTimeMillis()
        val key = gatewayMessageKey(nodeId)
        val lastDownloadAt = getLastSyncAt(key)

        val snapshot = firestore.collection("node_inboxes")
            .document(nodeId)
            .collection("messages")
            .whereGreaterThan("timestamp", lastDownloadAt)
            .orderBy("timestamp")
            .limit(limit.toLong())
            .get()
            .await()

        var maxTimestamp = lastDownloadAt
        var inserted = 0
        val localExisting = database.messageDao().getAllMessageIds().toSet()

        snapshot.documents.forEach { doc ->
            val timestamp = doc.getLong("timestamp") ?: now
            maxTimestamp = max(maxTimestamp, timestamp)

            val ttlExpiresAt = doc.getLong("ttlExpiresAt") ?: return@forEach
            if (ttlExpiresAt <= now) return@forEach
            if (doc.id in localExisting) return@forEach

            val senderId = doc.getString("senderId") ?: return@forEach
            if (senderId == localNodeId) return@forEach

            val destinationId = doc.getString("destinationId") ?: return@forEach
            if (destinationId == localNodeId) return@forEach

            val entity = doc.toDtnMessageEntity(
                now = now,
                forceDestinationScope = DestinationScope.DIRECT_CONTACT.name
            ) ?: return@forEach

            database.messageDao().upsert(
                entity.copy(
                    text = if (entity.isEncrypted) "Encrypted relay message" else entity.text,
                    status = MessageStatus.RELAYED.name,
                    copiesLeft = entity.copiesLeft.coerceAtLeast(1),
                    syncedToCloud = true
                )
            )

            inserted++
        }

        if (maxTimestamp > lastDownloadAt) {
            setLastSyncAt(key, maxTimestamp)
        }

        return inserted
    }

    private suspend fun downloadGatewayAcksForNode(
        nodeId: String,
        limit: Int
    ) {
        val now = System.currentTimeMillis()
        val key = gatewayAckKey(nodeId)
        val lastDownloadAt = getLastSyncAt(key)

        val snapshot = firestore.collection("node_inboxes")
            .document(nodeId)
            .collection("acks")
            .whereGreaterThan("timestamp", lastDownloadAt)
            .orderBy("timestamp")
            .limit(limit.toLong())
            .get()
            .await()

        var maxTimestamp = lastDownloadAt

        snapshot.documents.forEach { doc ->
            val timestamp = doc.getLong("timestamp") ?: now
            maxTimestamp = max(maxTimestamp, timestamp)
            insertAckFromCloud(doc, now)
        }

        if (maxTimestamp > lastDownloadAt) {
            setLastSyncAt(key, maxTimestamp)
        }
    }

    private suspend fun downloadCrisisReports(config: CloudRuntimeConfig) {
        val now = System.currentTimeMillis()
        val lastDownloadAt = getLastSyncAt(KEY_LAST_CRISIS_DOWNLOAD_AT)

        val snapshot = firestore.collection("crisis_reports")
            .whereGreaterThan("timestamp", lastDownloadAt)
            .orderBy("timestamp")
            .limit(config.crisisDownloadLimit.toLong())
            .get()
            .await()

        var maxTimestamp = lastDownloadAt
        val localExisting = database.messageDao().getAllMessageIds().toSet()
        val downloaded = mutableListOf<DtnMessageEntity>()

        snapshot.documents.forEach { doc ->
            val timestamp = doc.getLong("timestamp") ?: now
            maxTimestamp = max(maxTimestamp, timestamp)

            val ttlExpiresAt = doc.getLong("ttlExpiresAt") ?: return@forEach
            if (ttlExpiresAt <= now) return@forEach
            if (doc.id in localExisting) return@forEach

            val senderId = doc.getString("senderId") ?: return@forEach
            if (senderId == localNodeId) return@forEach

            val entity = doc.toDtnMessageEntity(
                now = now,
                forceDestinationScope = DestinationScope.PUBLIC_BROADCAST.name
            ) ?: return@forEach

            downloaded += entity.copy(
                conversationId = CrisisConstants.CRISIS_CONVERSATION_ID,
                destinationId = CrisisConstants.PUBLIC_BROADCAST_DESTINATION_ID,
                destinationScope = DestinationScope.PUBLIC_BROADCAST.name,
                isEncrypted = false,
                encryptedBodyJson = null,
                status = MessageStatus.RELAYED.name,
                syncedToCloud = true
            )
        }

        if (downloaded.isNotEmpty()) {
            database.messageDao().upsertAll(downloaded)
        }

        if (maxTimestamp > lastDownloadAt) {
            setLastSyncAt(KEY_LAST_CRISIS_DOWNLOAD_AT, maxTimestamp)
        }

        Log.d(TAG, "downloadCrisisReports inserted=${downloaded.size}")
    }

    private suspend fun downloadPublicBroadcasts(config: CloudRuntimeConfig) {
        val now = System.currentTimeMillis()
        val lastDownloadAt = getLastSyncAt(KEY_LAST_PUBLIC_DOWNLOAD_AT)

        val snapshot = firestore.collection("public_broadcasts")
            .whereGreaterThan("timestamp", lastDownloadAt)
            .orderBy("timestamp")
            .limit(config.publicDownloadLimit.toLong())
            .get()
            .await()

        var maxTimestamp = lastDownloadAt
        val localExisting = database.messageDao().getAllMessageIds().toSet()
        val downloaded = mutableListOf<DtnMessageEntity>()

        snapshot.documents.forEach { doc ->
            val timestamp = doc.getLong("timestamp") ?: now
            maxTimestamp = max(maxTimestamp, timestamp)

            val ttlExpiresAt = doc.getLong("ttlExpiresAt") ?: return@forEach
            if (ttlExpiresAt <= now) return@forEach
            if (doc.id in localExisting) return@forEach

            val senderId = doc.getString("senderId") ?: return@forEach
            if (senderId == localNodeId) return@forEach

            val entity = doc.toDtnMessageEntity(
                now = now,
                forceDestinationScope = DestinationScope.PUBLIC_BROADCAST.name
            ) ?: return@forEach

            downloaded += entity.copy(
                conversationId = CrisisConstants.PUBLIC_BROADCAST_CONVERSATION_ID,
                destinationId = CrisisConstants.PUBLIC_BROADCAST_DESTINATION_ID,
                destinationScope = DestinationScope.PUBLIC_BROADCAST.name,
                isEncrypted = false,
                encryptedBodyJson = null,
                crisisType = CrisisMessageType.PUBLIC_BROADCAST.name,
                status = MessageStatus.RELAYED.name,
                syncedToCloud = true
            )
        }

        if (downloaded.isNotEmpty()) {
            database.messageDao().upsertAll(downloaded)
        }

        if (maxTimestamp > lastDownloadAt) {
            setLastSyncAt(KEY_LAST_PUBLIC_DOWNLOAD_AT, maxTimestamp)
        }

        Log.d(TAG, "downloadPublicBroadcasts inserted=${downloaded.size}")
    }

    private suspend fun insertAckFromCloud(doc: DocumentSnapshot, now: Long) {
        val ttlExpiresAt = doc.getLong("ttlExpiresAt") ?: now
        if (ttlExpiresAt <= now) return

        val messageId = doc.getString("messageId") ?: return
        val ackedByNodeId = doc.getString("ackedByNodeId") ?: return
        val deliveredAt = doc.getLong("timestamp") ?: now

        database.ackDao().upsert(
            AckEntity(
                messageId = messageId,
                deliveredTo = ackedByNodeId,
                deliveredAt = deliveredAt,
                receivedFromNodeId = ackedByNodeId,
                createdAt = now
            )
        )

        database.ackDao().markOwnMessagesDelivered(listOf(messageId), localNodeId)
        database.ackDao().deleteRelayedMessagesByAck(listOf(messageId), localNodeId)
    }

    private suspend fun uploadDeliveryAck(
        messageId: String,
        senderId: String,
        destinationId: String,
        ttlExpiresAt: Long
    ) {
        if (destinationId != localNodeId) return

        val now = System.currentTimeMillis()
        val ackId = "${messageId}_$localNodeId"
        val ackExpiresAt = max(ttlExpiresAt, now + ACK_TTL_MILLIS)

        val data = mapOf(
            "id" to ackId,
            "messageId" to messageId,
            "senderId" to senderId,
            "destinationId" to destinationId,
            "ackedByNodeId" to localNodeId,
            "timestamp" to now,
            "ttlExpiresAt" to ackExpiresAt,
            "createdAt" to Timestamp.now(),
            "createdByUid" to auth.currentUser?.uid,
            "source" to "android-cerca"
        )

        val docRef = firestore.collection("node_inboxes")
            .document(senderId)
            .collection("acks")
            .document(ackId)

        runCatching {
            docRef.set(data, SetOptions.merge()).await()

            Log.d(
                TAG,
                "Cloud ACK uploaded ackId=$ackId path=${docRef.path} messageId=$messageId"
            )
        }.onFailure { error ->
            Log.e(
                TAG,
                "Cloud ACK upload FAILED ackId=$ackId path=${docRef.path} error=${error.message}",
                error
            )
        }
    }

    private fun DocumentSnapshot.toDtnMessageEntity(
        now: Long,
        forceDestinationScope: String? = null
    ): DtnMessageEntity? {
        val senderId = getString("senderId") ?: return null
        val destinationId = getString("destinationId") ?: return null
        val encryptedBodyJson = getString("encryptedBodyJson")
        val isEncrypted = getBoolean("isEncrypted") ?: true
        val timestamp = getLong("timestamp") ?: now
        val ttlExpiresAt = getLong("ttlExpiresAt") ?: return null
        val destinationScope = forceDestinationScope
            ?: getString("destinationScope")
            ?: DestinationScope.DIRECT_CONTACT.name

        val isForMe = destinationId == localNodeId

        val displayText = when {
            isForMe && isEncrypted && encryptedBodyJson != null -> {
                runCatching { crypto.decryptJson(encryptedBodyJson) }
                    .getOrElse { "Unable to decrypt message." }
            }

            isForMe -> getString("text") ?: "Message"

            destinationScope == DestinationScope.PUBLIC_BROADCAST.name -> {
                getString("text") ?: "Broadcast"
            }

            else -> "Encrypted relay message"
        }

        val conversationId = if (destinationScope == DestinationScope.DIRECT_CONTACT.name) {
            localDirectConversationId(senderId, destinationId)
        } else {
            getString("conversationId") ?: localDirectConversationId(senderId, destinationId)
        }

        return DtnMessageEntity(
            id = id,
            conversationId = conversationId,
            senderId = senderId,
            destinationId = destinationId,
            text = displayText,
            encryptedBodyJson = encryptedBodyJson,
            isEncrypted = isEncrypted,
            timestamp = timestamp,
            receivedAt = now,
            ttlExpiresAt = ttlExpiresAt,
            isFromMe = senderId == localNodeId,
            isEmergency = getBoolean("isEmergency") ?: false,
            crisisType = getString("crisisType") ?: "DIRECT_MESSAGE",
            crisisPriority = (getLong("crisisPriority") ?: 1L).toInt(),
            verificationStatus = getString("verificationStatus") ?: "UNVERIFIED",
            approximateLocation = getString("approximateLocation"),
            peopleAffected = getLong("peopleAffected")?.toInt(),
            requiresResponse = getBoolean("requiresResponse") ?: false,
            destinationScope = destinationScope,
            status = if (isForMe) MessageStatus.DELIVERED.name else MessageStatus.RELAYED.name,
            copiesLeft = if (isForMe) 0 else (getLong("copiesLeft") ?: 8L).toInt().coerceAtLeast(1),
            hopCount = (getLong("hopCount") ?: 0L).toInt(),
            pathCsv = getString("pathCsv") ?: senderId,
            bestRelayName = "Firebase gateway",
            utilityScore = null,
            syncedToCloud = true
        )
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

    private fun DtnMessageEntity.toFirestoreMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "conversationId" to conversationId,
        "senderId" to senderId,
        "uploadedByNodeId" to localNodeId,
        "destinationId" to destinationId,
        "text" to if (isEncrypted) "Encrypted message" else text,
        "encryptedBodyJson" to encryptedBodyJson,
        "isEncrypted" to isEncrypted,
        "timestamp" to timestamp,
        "receivedAt" to receivedAt,
        "ttlExpiresAt" to ttlExpiresAt,
        "isEmergency" to isEmergency,
        "crisisType" to crisisType,
        "crisisPriority" to crisisPriority,
        "verificationStatus" to verificationStatus,
        "approximateLocation" to approximateLocation,
        "peopleAffected" to peopleAffected,
        "requiresResponse" to requiresResponse,
        "destinationScope" to destinationScope,
        "copiesLeft" to copiesLeft,
        "hopCount" to hopCount,
        "pathCsv" to pathCsv,
        "createdAt" to Timestamp(Date(timestamp)),
        "updatedAt" to Timestamp.now(),
        "createdByUid" to auth.currentUser?.uid,
        "source" to "android-cerca"
    )
}