package com.leobigott.cercamessenger.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "deleted_messages")
data class DeletedMessageEntity(
    @PrimaryKey val messageId: String,
    val deletedAt: Long,
    val reason: String
)