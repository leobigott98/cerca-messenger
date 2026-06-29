package com.leobigott.cercamessenger.protocol.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey

class KeyManager {
    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val RSA_ALIAS = "cerca_rsa_main"
        const val KEY_ID = "rsa-main-2026-01"
    }

    fun ensureRsaPublicKey(): PublicKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(RSA_ALIAS)) {
            return keyStore.getCertificate(RSA_ALIAS).publicKey
        }

        val generator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_RSA,
            ANDROID_KEYSTORE
        )
        val spec = KeyGenParameterSpec.Builder(
            RSA_ALIAS,
            KeyProperties.PURPOSE_DECRYPT
        )
            .setKeySize(2048)
            .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
            .build()
        generator.initialize(spec)
        return generator.generateKeyPair().public
    }

    fun getPrivateKey(): PrivateKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (!keyStore.containsAlias(RSA_ALIAS)) {
            ensureRsaPublicKey()
        }
        return keyStore.getKey(RSA_ALIAS, null) as PrivateKey
    }

    fun getPublicKeyPem(): String = publicKeyToPem(ensureRsaPublicKey())

    private fun publicKeyToPem(publicKey: PublicKey): String {
        val base64 = Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)
        return "-----BEGIN PUBLIC KEY-----\n$base64\n-----END PUBLIC KEY-----"
    }
}
