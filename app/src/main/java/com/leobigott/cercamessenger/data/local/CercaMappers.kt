package com.leobigott.cercamessenger.data.local

import com.leobigott.cercamessenger.core.model.Conversation
import com.leobigott.cercamessenger.core.model.CrisisMessageType
import com.leobigott.cercamessenger.core.model.CrisisPriority
import com.leobigott.cercamessenger.core.model.DeviceNode
import com.leobigott.cercamessenger.core.model.DestinationScope
import com.leobigott.cercamessenger.core.model.MessageStatus
import com.leobigott.cercamessenger.core.model.NodeMode
import com.leobigott.cercamessenger.core.model.OfflineMessage
import com.leobigott.cercamessenger.core.model.VerificationStatus
import com.leobigott.cercamessenger.protocol.cerca.CercaMessagePayload

fun DtnMessageEntity.toOfflineMessage(localNodeId: String): OfflineMessage {
    val ttlMinutes = ((ttlExpiresAt - System.currentTimeMillis()).coerceAtLeast(0L) / 60_000L).toInt()
    return OfflineMessage(
        id = id,
        conversationId = conversationId,
        senderId = senderId,
        destinationId = destinationId,
        text = text,
        timestamp = timestamp,
        receivedAt = receivedAt,
        isFromMe = isFromMe || senderId == localNodeId,
        status = runCatching { MessageStatus.valueOf(status) }.getOrDefault(MessageStatus.QUEUED),
        hopCount = hopCount,
        copiesLeft = copiesLeft,
        ttlRemainingMinutes = ttlMinutes,
        bestRelayName = bestRelayName,
        utilityScore = utilityScore,
        crisisType = runCatching { CrisisMessageType.valueOf(crisisType) }.getOrDefault(CrisisMessageType.PERSONAL),
        crisisPriority = CrisisPriority.values().firstOrNull { it.level == crisisPriority } ?: CrisisPriority.P5_PERSONAL,
        verificationStatus = runCatching { VerificationStatus.valueOf(verificationStatus) }.getOrDefault(VerificationStatus.UNVERIFIED),
        approximateLocation = approximateLocation,
        peopleAffected = peopleAffected,
        requiresResponse = requiresResponse,
        destinationScope = runCatching { DestinationScope.valueOf(destinationScope) }.getOrDefault(DestinationScope.DIRECT_CONTACT)
    )
}

fun PeerEntity.toDeviceNode(): DeviceNode {
    val lastSeenText = if (isNearby) "Nearby now" else {
        val minutes = ((System.currentTimeMillis() - lastSeenAt).coerceAtLeast(0L) / 60_000L)
        "Seen $minutes min ago"
    }

    return DeviceNode(
        id = nodeId,
        displayName = displayName,
        isNearby = isNearby,
        batteryLevel = batteryLevel.toFloat(),
        linkQuality = linkQuality.toFloat(),
        hasInternetGateway = hasInternetGateway,
        nodeMode = runCatching { NodeMode.valueOf(nodeMode) }.getOrDefault(NodeMode.CITIZEN),
        utilityScore = null,
        lastSeenText = lastSeenText
    )
}

fun ConversationProjection.toConversation(): Conversation {
    return Conversation(
        id = conversationId,
        peerName = peerName ?: peerId.take(12),
        peerId = peerId,
        lastMessage = lastMessage ?: "",
        lastMessageTimestamp = lastMessageTimestamp ?: 0L,
        unreadCount = unreadCount,
        peerIsNearby = peerIsNearby,
        lastStatus = runCatching { MessageStatus.valueOf(lastStatus ?: MessageStatus.QUEUED.name) }
            .getOrDefault(MessageStatus.QUEUED)
    )
}

fun DtnMessageEntity.toPayload(): CercaMessagePayload {
    return CercaMessagePayload(
        id = id,
        conversationId = conversationId,
        senderId = senderId,
        destinationId = destinationId,
        text = if (isEncrypted) "Encrypted message" else text,
        encryptedBodyJson = encryptedBodyJson,
        isEncrypted = isEncrypted,
        timestamp = timestamp,
        ttlExpiresAt = ttlExpiresAt,
        isEmergency = isEmergency,
        crisisType = crisisType,
        crisisPriority = crisisPriority,
        verificationStatus = verificationStatus,
        approximateLocation = approximateLocation,
        peopleAffected = peopleAffected,
        requiresResponse = requiresResponse,
        destinationScope = destinationScope,
        copiesLeft = copiesLeft,
        hopCount = hopCount,
        path = pathCsv.split(",").filter { it.isNotBlank() }
    )
}

fun CercaMessagePayload.toEntity(localNodeId: String): DtnMessageEntity {
    return DtnMessageEntity(
        id = id,
        conversationId = conversationId,
        senderId = senderId,
        destinationId = destinationId,
        text = if (isEncrypted) "Encrypted message" else text,
        encryptedBodyJson = encryptedBodyJson,
        isEncrypted = isEncrypted,
        timestamp = timestamp,
        receivedAt = System.currentTimeMillis(),
        ttlExpiresAt = ttlExpiresAt,
        isFromMe = senderId == localNodeId,
        isEmergency = isEmergency,
        crisisType = crisisType,
        crisisPriority = crisisPriority,
        verificationStatus = verificationStatus,
        approximateLocation = approximateLocation,
        peopleAffected = peopleAffected,
        requiresResponse = requiresResponse,
        destinationScope = destinationScope,
        status = if (destinationId == localNodeId) MessageStatus.DELIVERED.name else MessageStatus.RELAYED.name,
        copiesLeft = copiesLeft,
        hopCount = hopCount,
        pathCsv = path.joinToString(","),
        bestRelayName = null,
        utilityScore = null
    )
}
