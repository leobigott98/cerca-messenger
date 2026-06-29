package com.leobigott.cercamessenger.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dtn_messages")
data class DtnMessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val senderId: String,
    val destinationId: String,
    /** Local display text. For relay nodes this may be a placeholder because payloads are encrypted. */
    val text: String,
    val encryptedBodyJson: String? = null,
    val isEncrypted: Boolean = false,
    val timestamp: Long,
    val receivedAt: Long = System.currentTimeMillis(),
    val ttlExpiresAt: Long,
    val isFromMe: Boolean,
    val isEmergency: Boolean,
    val crisisType: String = "PERSONAL",
    val crisisPriority: Int = 0,
    val verificationStatus: String = "UNVERIFIED",
    val approximateLocation: String? = null,
    val peopleAffected: Int? = null,
    val requiresResponse: Boolean = false,
    val destinationScope: String = "DIRECT_CONTACT",
    val status: String,
    val copiesLeft: Int,
    val hopCount: Int,
    val pathCsv: String,
    val bestRelayName: String?,
    val utilityScore: Float?,
    val syncedToCloud: Boolean = false,
    val readAt: Long? = null
)

@Entity(tableName = "peers")
data class PeerEntity(
    @PrimaryKey val nodeId: String,
    val endpointId: String?,
    val displayName: String,
    val isNearby: Boolean,
    val batteryLevel: Double,
    val bufferFreeRatio: Double,
    val linkQuality: Double,
    val hasInternetGateway: Boolean,
    val nodeMode: String = "CITIZEN",
    val lastInternetAt: Long?,
    val credits: Double,
    val smoothedDensity: Double,
    val lastSeenAt: Long
)

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey val nodeId: String,
    val displayName: String,
    val publicKeyPem: String,
    val keyId: String,
    val algorithm: String = "RSA-OAEP-256+A256GCM",
    val verified: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val lastSeenAt: Long? = null
)

@Entity(tableName = "acks")
data class AckEntity(
    @PrimaryKey val messageId: String,
    val deliveredTo: String,
    val deliveredAt: Long,
    val receivedFromNodeId: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "predictions", primaryKeys = ["targetNodeId"])
data class PredictionEntity(
    val targetNodeId: String,
    val value: Double,
    val lastEncounterAt: Long?,
    val lastAgedAt: Long
)

data class ConversationProjection(
    val conversationId: String,
    val peerId: String,
    val peerName: String?,
    val lastMessage: String?,
    val lastMessageTimestamp: Long?,
    val unreadCount: Int,
    val peerIsNearby: Boolean,
    val lastStatus: String?
)
