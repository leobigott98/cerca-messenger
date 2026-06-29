package com.leobigott.cercamessenger.protocol.cerca

import kotlinx.serialization.Serializable

@Serializable
enum class CercaPayloadType {
    HELLO,
    SUMMARY,
    MESSAGE,
    ACK
}

@Serializable
data class CercaEnvelope(
    val type: CercaPayloadType,
    val senderNodeId: String,
    val senderDisplayName: String,
    val createdAt: Long = System.currentTimeMillis(),
    val hello: HelloPayload? = null,
    val summary: SummaryPayload? = null,
    val message: CercaMessagePayload? = null,
    val ack: AckPayload? = null
)

@Serializable
data class HelloPayload(
    val batteryLevel: Double,
    val bufferFreeRatio: Double,
    val hasInternetGateway: Boolean,
    val lastInternetAt: Long?,
    val credits: Double,
    val smoothedDensity: Double,
    val nodeMode: String = "CITIZEN"
)

@Serializable
data class SummaryPayload(
    val batteryLevel: Double,
    val bufferFreeRatio: Double,
    val hasInternetGateway: Boolean,
    val lastInternetAt: Long?,
    val credits: Double,
    val smoothedDensity: Double,
    val nodeMode: String = "CITIZEN",
    val messageIds: List<String> = emptyList(),
    val recentAcks: List<AckPayload> = emptyList(),
    /** Backwards-compatible summary form used by older prototype builds. */
    val ackedMessageIds: List<String> = emptyList(),
    val predictabilities: Map<String, Double>
)

@Serializable
data class CercaMessagePayload(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val destinationId: String,
    /** Local preview/placeholder. The actual network body should be encrypted when isEncrypted=true. */
    val text: String,
    val encryptedBodyJson: String? = null,
    val isEncrypted: Boolean = false,
    val timestamp: Long,
    val ttlExpiresAt: Long,
    val isEmergency: Boolean,
    val crisisType: String = "PERSONAL",
    val crisisPriority: Int = 0,
    val verificationStatus: String = "UNVERIFIED",
    val approximateLocation: String? = null,
    val peopleAffected: Int? = null,
    val requiresResponse: Boolean = false,
    val destinationScope: String = "DIRECT_CONTACT",
    val copiesLeft: Int,
    val hopCount: Int,
    val path: List<String>
)

@Serializable
data class AckPayload(
    val messageId: String,
    val deliveredTo: String,
    val deliveredAt: Long,
    val receivedFromNodeId: String? = null
)

data class NodeContext(
    val nodeId: String,
    val displayName: String,
    val batteryLevel: Double,
    val bufferFreeRatio: Double,
    val hasInternetGateway: Boolean,
    val lastInternetAt: Long?,
    val credits: Double,
    val smoothedDensity: Double,
    val predictabilityToDestination: Double,
    val nodeMode: String = "CITIZEN"
)

data class ForwardDecision(
    val shouldForward: Boolean,
    val utilitySelf: Double,
    val utilityPeer: Double,
    val reason: String
)
