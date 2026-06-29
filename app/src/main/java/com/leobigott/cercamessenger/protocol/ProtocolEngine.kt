package com.leobigott.cercamessenger.protocol

import com.leobigott.cercamessenger.core.model.CrisisMessageType
import com.leobigott.cercamessenger.core.model.DeviceNode
import com.leobigott.cercamessenger.core.model.NodeMode
import com.leobigott.cercamessenger.core.model.OfflineMessage
import kotlinx.coroutines.flow.Flow

interface ProtocolEngine {
    val currentNodeMode: NodeMode
    fun observeNearbyDevices(): Flow<List<DeviceNode>>
    fun observeMessages(conversationId: String): Flow<List<OfflineMessage>>
    fun observePublicBroadcasts(): Flow<List<OfflineMessage>>
    fun observeCrisisReports(): Flow<List<OfflineMessage>>
    suspend fun sendMessage(conversationId: String, destinationId: String, text: String)
    suspend fun markConversationRead(conversationId: String)
    suspend fun restartNearby()
    suspend fun refreshNearby()
    suspend fun forceNearbyScan()
    suspend fun deleteMessage(messageId: String)
    suspend fun deletePublicBroadcast(messageId: String)
    suspend fun deleteAllPublicBroadcasts()
    suspend fun deleteConversation(conversationId: String)
    suspend fun sendCrisisMessage(
        type: CrisisMessageType,
        text: String,
        approximateLocation: String? = null,
        peopleAffected: Int? = null,
        requiresResponse: Boolean = true
    )
    suspend fun sendPublicBroadcast(
        text: String,
        approximateLocation: String? = null,
        peopleAffected: Int? = null,
        requiresResponse: Boolean = false
    )
    suspend fun setNodeMode(mode: NodeMode)
    suspend fun syncCloudNow()
    suspend fun startDiscovery()
    suspend fun stopDiscovery()
}
