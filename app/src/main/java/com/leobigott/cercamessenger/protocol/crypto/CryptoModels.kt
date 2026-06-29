package com.leobigott.cercamessenger.protocol.crypto

import kotlinx.serialization.Serializable

@Serializable
data class ContactQrPayload(
    val type: String = "CERCA_CONTACT_V1",
    val nodeId: String,
    val displayName: String,
    val keyId: String,
    val publicKeyPem: String,
    val algorithm: String = "RSA-OAEP-256+A256GCM",
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class EncryptedMessageBody(
    val algorithm: String = "RSA-OAEP-256+A256GCM",
    val encryptedKey: String,
    val iv: String,
    val ciphertext: String
)
