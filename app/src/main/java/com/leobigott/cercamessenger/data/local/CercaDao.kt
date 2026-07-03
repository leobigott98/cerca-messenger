package com.leobigott.cercamessenger.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.leobigott.cercamessenger.core.model.CrisisConstants
import kotlinx.coroutines.flow.Flow

@Dao
interface DtnMessageDao {
    @Query("""
    SELECT * FROM dtn_messages
    ORDER BY receivedAt ASC, timestamp ASC, id ASC
""")
    fun observeAll(): Flow<List<DtnMessageEntity>>

    @Query("""
    SELECT *
    FROM dtn_messages
    WHERE destinationScope = 'PUBLIC_BROADCAST'
    ORDER BY receivedAt ASC, timestamp ASC, id ASC
""")
    fun observePublicBroadcasts(): Flow<List<DtnMessageEntity>>

    @Query("""
    SELECT *
    FROM dtn_messages
    WHERE destinationScope = 'PUBLIC_BROADCAST'
    AND conversationId = :conversationId
    ORDER BY receivedAt DESC, timestamp DESC, id DESC
""")
    fun observeBroadcastConversation(
        conversationId: String
    ): Flow<List<DtnMessageEntity>>

    @Query("""
    SELECT * FROM dtn_messages
    WHERE conversationId = :conversationId
    AND destinationScope = 'DIRECT_CONTACT'
    AND (
        senderId = :localNodeId
        OR destinationId = :localNodeId
    )
    ORDER BY timestamp ASC, receivedAt ASC, id ASC
""")
    fun observeConversation(
        conversationId: String,
        localNodeId: String
    ): Flow<List<DtnMessageEntity>>

    @Query("SELECT * FROM dtn_messages WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): DtnMessageEntity?

    @Query("SELECT * FROM dtn_messages WHERE status NOT IN ('DELIVERED', 'EXPIRED', 'FAILED') ORDER BY crisisPriority DESC, isEmergency DESC, timestamp ASC")
    suspend fun getForwardableMessages(): List<DtnMessageEntity>

    @Query("SELECT id FROM dtn_messages WHERE status NOT IN ('DELIVERED', 'EXPIRED', 'FAILED')")
    suspend fun getBufferMessageIds(): List<String>

    @Query("SELECT id FROM dtn_messages")
    suspend fun getAllMessageIds(): List<String>

    @Query("""
    UPDATE dtn_messages
    SET status = 'DELIVERED',
        copiesLeft = 0,
        syncedToCloud = 1
    WHERE id = :messageId
      AND isFromMe = 1
""")
    suspend fun markDeliveredFromCloudAck(messageId: String)

    @Query("""
    UPDATE dtn_messages
    SET status = :newStatus,
    copiesLeft = CASE WHEN copiesLeft <= 0 THEN 1 ELSE copiesLeft END
    WHERE isFromMe = 1
      AND status = :oldStatus
      AND timestamp < :olderThan
      AND ttlExpiresAt > :now
""")
    suspend fun resetStaleSendingMessages(
        oldStatus: String = "SENDING",
        newStatus: String = "WAITING_FOR_RELAY",
        olderThan: Long,
        now: Long
    )

    @Query("""
    UPDATE dtn_messages
    SET status = 'EXPIRED'
    WHERE ttlExpiresAt <= :now
    AND status NOT IN ('DELIVERED', 'FAILED', 'EXPIRED')
""")
    suspend fun markExpiredMessages(now: Long = System.currentTimeMillis())

    @Query("""
    SELECT
        m.conversationId AS conversationId,
        CASE 
            WHEN m.senderId = :localNodeId THEN m.destinationId 
            ELSE m.senderId 
        END AS peerId,
        COALESCE(c.displayName, p.displayName) AS peerName,
        m.text AS lastMessage,
        m.timestamp AS lastMessageTimestamp,
        (
            SELECT COUNT(*)
            FROM dtn_messages unread
            WHERE unread.conversationId = m.conversationId
            AND unread.destinationScope = 'DIRECT_CONTACT'
            AND unread.destinationId = :localNodeId
            AND unread.senderId != :localNodeId
            AND unread.readAt IS NULL
        ) AS unreadCount,
        COALESCE(p.isNearby, 0) AS peerIsNearby,
        m.status AS lastStatus
    FROM dtn_messages m
    LEFT JOIN contacts c 
        ON c.nodeId = CASE 
            WHEN m.senderId = :localNodeId THEN m.destinationId 
            ELSE m.senderId 
        END
    LEFT JOIN peers p 
        ON p.nodeId = CASE 
            WHEN m.senderId = :localNodeId THEN m.destinationId 
            ELSE m.senderId 
        END
    WHERE m.destinationScope = 'DIRECT_CONTACT'
    AND (
        m.senderId = :localNodeId
        OR m.destinationId = :localNodeId
    )
    AND m.id = (
        SELECT m2.id
        FROM dtn_messages m2
        WHERE m2.conversationId = m.conversationId
        AND m2.destinationScope = 'DIRECT_CONTACT'
        AND (
            m2.senderId = :localNodeId
            OR m2.destinationId = :localNodeId
        )
        ORDER BY m2.receivedAt DESC, m2.timestamp DESC, m2.id DESC
        LIMIT 1
    )
    ORDER BY m.receivedAt DESC, m.timestamp DESC, m.id DESC
""")
    fun observeConversationProjections(localNodeId: String): Flow<List<ConversationProjection>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(message: DtnMessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(messages: List<DtnMessageEntity>)

    @Query("UPDATE dtn_messages SET status = :status WHERE id = :messageId")
    suspend fun updateStatus(messageId: String, status: String)

    @Query("UPDATE dtn_messages SET copiesLeft = :copiesLeft, hopCount = :hopCount, pathCsv = :pathCsv, status = :status, bestRelayName = :bestRelayName, utilityScore = :utilityScore WHERE id = :messageId")
    suspend fun updateRoutingState(
        messageId: String,
        copiesLeft: Int,
        hopCount: Int,
        pathCsv: String,
        status: String,
        bestRelayName: String?,
        utilityScore: Float?
    )

    @Query("""
    UPDATE dtn_messages
    SET readAt = :readAt
    WHERE conversationId = :conversationId
    AND destinationScope = 'DIRECT_CONTACT'
    AND destinationId = :localNodeId
    AND senderId != :localNodeId
    AND readAt IS NULL
""")
    suspend fun markConversationRead(
        conversationId: String,
        localNodeId: String,
        readAt: Long = System.currentTimeMillis()
    )

    @Query("""
        SELECT * FROM dtn_messages
        WHERE syncedToCloud = 0
        AND ttlExpiresAt > :now
        AND status NOT IN ('DELIVERED', 'EXPIRED', 'FAILED')
        ORDER BY 
            isEmergency DESC,
            crisisPriority DESC,
            timestamp ASC
    """)
    suspend fun getUnsyncedCloudMessages(now: Long = System.currentTimeMillis()): List<DtnMessageEntity>

    @Query("""
    DELETE FROM dtn_messages
    WHERE ttlExpiresAt <= :now
      AND (
        conversationId = :publicBroadcastConversationId
        OR destinationScope = 'PUBLIC_BROADCAST'
        OR crisisType = 'PUBLIC_BROADCAST'
      )
""")
    suspend fun deleteExpiredPublicBroadcasts(
        now: Long,
        publicBroadcastConversationId: String
    )

    @Query("UPDATE dtn_messages SET syncedToCloud = 1 WHERE id = :messageId")
    suspend fun markSyncedToCloud(messageId: String)

    @Query("DELETE FROM dtn_messages WHERE id = :messageId")
    suspend fun deleteMessage(messageId: String)

    @Query("DELETE FROM dtn_messages WHERE conversationId = :conversationId")
    suspend fun deleteConversation(conversationId: String)

    @Query("DELETE FROM dtn_messages")
    suspend fun deleteAllMessages()

    @Query("DELETE FROM dtn_messages WHERE id = :messageId")
    suspend fun deleteMessageById(messageId: String)

    @Query("""
    DELETE FROM dtn_messages 
    WHERE conversationId = :publicBroadcastConversationId
       OR destinationScope = 'PUBLIC_BROADCAST'
       OR crisisType = 'PUBLIC_BROADCAST'
""")
    suspend fun deleteAllPublicBroadcasts(
        publicBroadcastConversationId: String = CrisisConstants.PUBLIC_BROADCAST_CONVERSATION_ID
    )

    @Query("""
    SELECT id FROM dtn_messages
    WHERE conversationId = :publicBroadcastConversationId
       OR destinationScope = 'PUBLIC_BROADCAST'
       OR crisisType = 'PUBLIC_BROADCAST'
""")
    suspend fun getPublicBroadcastIds(
        publicBroadcastConversationId: String = CrisisConstants.PUBLIC_BROADCAST_CONVERSATION_ID
    ): List<String>

    @Query("SELECT id FROM dtn_messages WHERE conversationId = :conversationId")
    suspend fun getMessageIdsByConversation(conversationId: String): List<String>
}


@Dao
interface PeerDao {
    @Query("SELECT * FROM peers ORDER BY isNearby DESC, lastSeenAt DESC")
    fun observePeers(): Flow<List<PeerEntity>>

    @Query("SELECT * FROM peers ORDER BY isNearby DESC, lastSeenAt DESC")
    suspend fun all(): List<PeerEntity>

    @Query("SELECT * FROM peers WHERE nodeId = :nodeId LIMIT 1")
    suspend fun getByNodeId(nodeId: String): PeerEntity?

    @Query("SELECT * FROM peers WHERE endpointId = :endpointId LIMIT 1")
    suspend fun getByEndpointId(endpointId: String): PeerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(peer: PeerEntity)

    @Query("UPDATE peers SET isNearby = 0, endpointId = NULL WHERE endpointId = :endpointId")
    suspend fun markDisconnected(endpointId: String)

    @Query("""
    UPDATE peers
    SET isNearby = 0, endpointId = NULL
    WHERE nodeId = :nodeId
      AND endpointId IS NOT NULL
      AND endpointId != :currentEndpointId
""")
    suspend fun disconnectOldEndpointsForNode(
        nodeId: String,
        currentEndpointId: String
    )

    @Query("UPDATE peers SET isNearby = 0, endpointId = NULL")
    suspend fun markAllDisconnected()

    @Query("SELECT COUNT(*) FROM peers WHERE isNearby = 1")
    suspend fun countNearby(): Int
}

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts ORDER BY displayName ASC")
    fun observeContacts(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE nodeId = :nodeId LIMIT 1")
    suspend fun getByNodeId(nodeId: String): ContactEntity?

    @Query("SELECT * FROM contacts WHERE lower(trim(displayName)) = lower(trim(:displayName)) LIMIT 1")
    suspend fun getByDisplayName(displayName: String): ContactEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(contact: ContactEntity)

    @Query("UPDATE contacts SET displayName = :displayName WHERE nodeId = :nodeId")
    suspend fun rename(nodeId: String, displayName: String)

    @Query("DELETE FROM contacts WHERE nodeId = :nodeId")
    suspend fun delete(nodeId: String)

    @Query("DELETE FROM contacts")
    suspend fun deleteAllContacts()
}

@Dao
interface AckDao {
    @Query("SELECT * FROM acks ORDER BY createdAt DESC LIMIT :limit")
    suspend fun latest(limit: Int): List<AckEntity>

    @Query("SELECT messageId FROM acks")
    suspend fun allIds(): List<String>

    @Query("SELECT EXISTS(SELECT 1 FROM acks WHERE messageId = :messageId)")
    suspend fun hasAck(messageId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(ack: AckEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(acks: List<AckEntity>)

    @Query("""
        DELETE FROM dtn_messages
        WHERE id IN (:messageIds)
        AND destinationId != :localNodeId
        AND senderId != :localNodeId
    """)
    suspend fun deleteRelayedMessagesByAck(messageIds: List<String>, localNodeId: String)

    @Query("""
        UPDATE dtn_messages
        SET status = 'DELIVERED'
        WHERE id IN (:messageIds)
        AND senderId = :localNodeId
    """)
    suspend fun markOwnMessagesDelivered(messageIds: List<String>, localNodeId: String)

    @Query("DELETE FROM acks WHERE messageId NOT IN (SELECT messageId FROM acks ORDER BY createdAt DESC LIMIT :keep)")
    suspend fun pruneAcks(keep: Int)
}

@Dao
interface PredictionDao {
    @Query("SELECT * FROM predictions")
    suspend fun all(): List<PredictionEntity>

    @Query("SELECT * FROM predictions WHERE targetNodeId = :targetNodeId LIMIT 1")
    suspend fun get(targetNodeId: String): PredictionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(prediction: PredictionEntity)
}

@Dao
interface DeletedMessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DeletedMessageEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM deleted_messages WHERE messageId = :messageId)")
    suspend fun isDeleted(messageId: String): Boolean

    @Query("DELETE FROM deleted_messages WHERE deletedAt < :cutoff")
    suspend fun pruneOlderThan(cutoff: Long)
}
