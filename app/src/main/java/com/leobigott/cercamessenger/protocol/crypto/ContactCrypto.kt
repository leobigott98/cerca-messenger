package com.leobigott.cercamessenger.protocol.crypto

import android.util.Base64
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.KeyFactory
import java.security.SecureRandom
import java.security.interfaces.RSAPublicKey
import java.security.spec.MGF1ParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec

class ContactCrypto(
    private val keyManager: KeyManager = KeyManager(),
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
) {
    fun encryptTextToJson(plaintext: String, recipientPublicKeyPem: String): String {
        val body = encryptForContact(plaintext, recipientPublicKeyPem)
        return json.encodeToString(body)
    }

    fun decryptJson(encryptedJson: String): String {
        val body = json.decodeFromString<EncryptedMessageBody>(encryptedJson)
        return decryptIncoming(body)
    }

    fun encryptForContact(plaintext: String, recipientPublicKeyPem: String): EncryptedMessageBody {
        val aesKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }

        val aesCipher = Cipher.getInstance("AES/GCM/NoPadding")
        aesCipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(aesKey, "AES"), GCMParameterSpec(128, iv))
        val ciphertext = aesCipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        val publicKey = parsePublicKeyPem(recipientPublicKeyPem)
        val rsaCipher = Cipher.getInstance("RSA/ECB/OAEPPadding")
        rsaCipher.init(Cipher.ENCRYPT_MODE, publicKey, oaepSha256Spec())
        val encryptedKey = rsaCipher.doFinal(aesKey)

        return EncryptedMessageBody(
            encryptedKey = Base64.encodeToString(encryptedKey, Base64.NO_WRAP),
            iv = Base64.encodeToString(iv, Base64.NO_WRAP),
            ciphertext = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        )
    }

    fun decryptIncoming(body: EncryptedMessageBody): String {
        val rsaCipher = Cipher.getInstance("RSA/ECB/OAEPPadding")
        rsaCipher.init(Cipher.DECRYPT_MODE, keyManager.getPrivateKey(), oaepSha256Spec())
        val aesKeyBytes = rsaCipher.doFinal(Base64.decode(body.encryptedKey, Base64.NO_WRAP))

        val aesCipher = Cipher.getInstance("AES/GCM/NoPadding")
        aesCipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(aesKeyBytes, "AES"),
            GCMParameterSpec(128, Base64.decode(body.iv, Base64.NO_WRAP))
        )
        val plaintextBytes = aesCipher.doFinal(Base64.decode(body.ciphertext, Base64.NO_WRAP))
        return plaintextBytes.toString(Charsets.UTF_8)
    }

    private fun parsePublicKeyPem(pem: String): RSAPublicKey {
        val clean = pem
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("\\s".toRegex(), "")
        val decoded = Base64.decode(clean, Base64.DEFAULT)
        val spec = X509EncodedKeySpec(decoded)
        return KeyFactory.getInstance("RSA").generatePublic(spec) as RSAPublicKey
    }

    private fun oaepSha256Spec(): OAEPParameterSpec = OAEPParameterSpec(
        "SHA-256",
        "MGF1",
        MGF1ParameterSpec.SHA1,
        PSource.PSpecified.DEFAULT
    )
}
