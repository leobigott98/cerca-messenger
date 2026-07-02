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
import android.util.Log
import java.util.Date
import com.leobigott.cercamessenger.protocol.crypto.ContactCrypto

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
    private val crypto: ContactCrypto = ContactCrypto(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : CloudSyncService {
    private companion object {
        private const val TAG = "CERCA_Firebase"
    }

    override suspend fun syncNow() {
        Log.d(TAG, "syncNow started")
        ensureAnonymousAuth()
        uploadUnsyncedMessages()
        downloadActiveCrisisReports()
        downloadActiveDtnMessages()
        Log.d(TAG, "syncNow finished")
    }

    private suspend fun ensureAnonymousAuth() {
        if (auth.currentUser == null) {
            auth.signInAnonymously().await()
        }
    }

    private suspend fun uploadUnsyncedMessages() {
        val now = System.currentTimeMillis()
        val unsynced = database.messageDao().getUnsyncedCloudMessages()

        Log.d(TAG, "uploadUnsyncedMessages candidates=${unsynced.size}")

        unsynced.forEach { message ->
            if (message.ttlExpiresAt <= now) {
                Log.d(
                    TAG,
                    "Skipping expired cloud message id=${message.id} scope=${message.destinationScope} ttl=${message.ttlExpiresAt} now=$now"
                )
                return@forEach
            }

            val collection = when (message.destinationScope) {
                DestinationScope.PUBLIC_BROADCAST.name,
                DestinationScope.CRISIS_GATEWAY.name -> "crisis_reports"
                else -> "dtn_messages"
            }

            val data = message.toFirestoreMap(localNodeId)

            Log.d(
                TAG,
                "Uploading cloud message id=${message.id} collection=$collection " +
                        "scope=${message.destinationScope} sender=${message.senderId} " +
                        "destination=${message.destinationId} encrypted=${message.isEncrypted} " +
                        "ttl=${message.ttlExpiresAt} auth=${auth.currentUser?.uid != null}"
            )

            runCatching {
                firestore.collection(collection)
                    .document(message.id)
                    .set(data, SetOptions.merge())
                    .await()

                database.messageDao().markSyncedToCloud(message.id)

                Log.d(TAG, "Cloud upload SUCCESS id=${message.id} collection=$collection")
            }.onFailure { error ->
                Log.e(
                    TAG,
                    "Cloud upload FAILED id=${message.id} collection=$collection " +
                            "scope=${message.destinationScope} error=${error.message}",
                    error
                )
            }
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
            if (database.deletedMessageDao().isDeleted(doc.id)) return@mapNotNull null
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
        Log.d(TAG, "downloadActiveCrisisReports count=${downloaded.size}")
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

    private suspend fun downloadActiveDtnMessages() {
        val now = System.currentTimeMillis()

        val snapshot = firestore.collection("dtn_messages")
            .whereGreaterThan("ttlExpiresAt", now)
            .limit(500)
            .get()
            .await()

        Log.d(TAG, "downloadActiveDtnMessages snapshot=${snapshot.size()}")

        val localExisting = database.messageDao().getAllMessageIds().toSet()

        val downloaded = snapshot.documents.mapNotNull { doc ->
            if (doc.id in localExisting) return@mapNotNull null

            val senderId = doc.getString("senderId") ?: return@mapNotNull null
            if (senderId == localNodeId) return@mapNotNull null

            val destinationId = doc.getString("destinationId") ?: return@mapNotNull null
            val encryptedBodyJson = doc.getString("encryptedBodyJson")
            val isEncrypted = doc.getBoolean("isEncrypted") ?: true
            val isForMe = destinationId == localNodeId

            val conversationId = localDirectConversationId(
                senderId = senderId,
                destinationId = destinationId
            )

            val displayText = when {
                isForMe && isEncrypted && encryptedBodyJson != null -> {
                    runCatching { crypto.decryptJson(encryptedBodyJson) }
                        .onFailure { error ->
                            Log.e(
                                TAG,
                                "Failed to decrypt cloud message id=${doc.id}: ${error.message}",
                                error
                            )
                        }
                        .getOrElse { "Unable to decrypt message." }
                }

                isForMe -> {
                    doc.getString("text") ?: "Message"
                }

                else -> {
                    "Encrypted relay message"
                }
            }

            DtnMessageEntity(
                id = doc.id,
                conversationId = conversationId,
                senderId = senderId,
                destinationId = destinationId,
                text = displayText,
                encryptedBodyJson = encryptedBodyJson,
                isEncrypted = isEncrypted,
                timestamp = doc.getLong("timestamp") ?: now,
                receivedAt = now,
                ttlExpiresAt = doc.getLong("ttlExpiresAt") ?: (now + 24 * 60 * 60 * 1000L),
                isFromMe = false,
                isEmergency = doc.getBoolean("isEmergency") ?: false,
                crisisType = doc.getString("crisisType") ?: "DIRECT_MESSAGE",
                crisisPriority = (doc.getLong("crisisPriority") ?: 1L).toInt(),
                verificationStatus = doc.getString("verificationStatus") ?: "UNVERIFIED",
                approximateLocation = doc.getString("approximateLocation"),
                peopleAffected = doc.getLong("peopleAffected")?.toInt(),
                requiresResponse = doc.getBoolean("requiresResponse") ?: false,
                destinationScope = doc.getString("destinationScope") ?: DestinationScope.DIRECT_CONTACT.name,
                status = if (isForMe) {
                    MessageStatus.DELIVERED.name
                } else {
                    MessageStatus.RELAYED.name
                },
                copiesLeft = if (isForMe) {
                    0
                } else {
                    (doc.getLong("copiesLeft") ?: 8L).toInt()
                },
                hopCount = (doc.getLong("hopCount") ?: 0L).toInt(),
                pathCsv = doc.getString("pathCsv") ?: senderId,
                bestRelayName = "Firebase gateway",
                utilityScore = null,
                syncedToCloud = true
            )
        }

        if (downloaded.isNotEmpty()) {
            database.messageDao().upsertAll(downloaded)
        }

        Log.d(TAG, "downloadActiveDtnMessages inserted=${downloaded.size}")
    }

    private fun DtnMessageEntity.toFirestoreMap(localNodeId: String): Map<String, Any?> = mapOf(
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
        "source" to "android-cerca"
    )
}
