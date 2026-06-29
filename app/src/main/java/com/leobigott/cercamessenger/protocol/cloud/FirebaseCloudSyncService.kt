package com.leobigott.cercamessenger.protocol.cloud

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.leobigott.cercamessenger.core.model.CrisisConstants
import com.leobigott.cercamessenger.core.model.MessageStatus
import com.leobigott.cercamessenger.core.model.DestinationScope
import com.leobigott.cercamessenger.data.local.CercaDatabase
import com.leobigott.cercamessenger.data.local.DtnMessageEntity
import kotlinx.coroutines.tasks.await

/**
 * Cloud bridge for CERCA.
 *
 * Firestore is NOT the primary transport. The primary transport remains Nearby/DTN.
 * This service only syncs queued local crisis reports when the phone eventually reaches Internet,
 * and downloads active crisis reports from the cloud into Room so CERCA can relay them offline.
 */
class FirebaseCloudSyncService(
    private val database: CercaDatabase,
    private val localNodeId: String,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : CloudSyncService {

    override suspend fun syncNow() {
        ensureAnonymousAuth()
        uploadUnsyncedMessages()
        downloadActiveCrisisReports()
    }

    private suspend fun ensureAnonymousAuth() {
        if (auth.currentUser == null) {
            auth.signInAnonymously().await()
        }
    }

    private suspend fun uploadUnsyncedMessages() {
        val unsynced = database.messageDao().getUnsyncedCloudMessages()
        unsynced.forEach { message ->
            val doc = firestore.collection("crisis_reports").document(message.id)
            doc.set(message.toFirestoreMap(localNodeId), SetOptions.merge()).await()
            database.messageDao().markSyncedToCloud(message.id)
        }
    }

    private suspend fun downloadActiveCrisisReports() {
        val now = System.currentTimeMillis()
        val snapshot = firestore.collection("crisis_reports")
            .whereGreaterThan("ttlExpiresAt", now)
            .limit(250)
            .get()
            .await()

        val localExisting = database.messageDao().getBufferMessageIds().toSet()
        val downloaded = snapshot.documents.mapNotNull { doc ->
            if (doc.id in localExisting) return@mapNotNull null
            val senderId = doc.getString("senderId") ?: return@mapNotNull null
            if (senderId == localNodeId) return@mapNotNull null
            DtnMessageEntity(
                id = doc.id,
                conversationId = doc.getString("conversationId") ?: CrisisConstants.CRISIS_CONVERSATION_ID,
                senderId = senderId,
                destinationId = doc.getString("destinationId") ?: CrisisConstants.CRISIS_DESTINATION_ID,
                text = doc.getString("text") ?: "Reporte de crisis",
                encryptedBodyJson = null,
                isEncrypted = false,
                timestamp = doc.getLong("timestamp") ?: now,
                receivedAt = now,
                ttlExpiresAt = doc.getLong("ttlExpiresAt") ?: (now + 60 * 60 * 1000L),
                isFromMe = false,
                isEmergency = doc.getBoolean("isEmergency") ?: true,
                crisisType = doc.getString("crisisType") ?: "NEED_HELP",
                crisisPriority = (doc.getLong("crisisPriority") ?: 5L).toInt(),
                verificationStatus = doc.getString("verificationStatus") ?: "UNVERIFIED",
                approximateLocation = doc.getString("approximateLocation"),
                peopleAffected = doc.getLong("peopleAffected")?.toInt(),
                requiresResponse = doc.getBoolean("requiresResponse") ?: true,
                destinationScope = doc.getString("destinationScope") ?: DestinationScope.CRISIS_GATEWAY.name,
                status = MessageStatus.RELAYED.name,
                copiesLeft = (doc.getLong("copiesLeft") ?: 8L).toInt(),
                hopCount = (doc.getLong("hopCount") ?: 0L).toInt(),
                pathCsv = doc.getString("pathCsv") ?: senderId,
                bestRelayName = "Firebase gateway",
                utilityScore = null,
                syncedToCloud = true
            )
        }
        if (downloaded.isNotEmpty()) database.messageDao().upsertAll(downloaded)
    }

    private fun DtnMessageEntity.toFirestoreMap(localNodeId: String): Map<String, Any?> = mapOf(
        "id" to id,
        "conversationId" to conversationId,
        "senderId" to senderId,
        "uploadedByNodeId" to localNodeId,
        "destinationId" to destinationId,
        "text" to text,
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
        "createdAt" to Timestamp.now(),
        "source" to "android-cerca"
    )
}
