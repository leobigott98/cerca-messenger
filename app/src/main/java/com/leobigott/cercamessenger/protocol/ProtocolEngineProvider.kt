package com.leobigott.cercamessenger.protocol

import android.content.Context
import android.os.Build
import com.leobigott.cercamessenger.data.local.CercaDatabase
import com.leobigott.cercamessenger.protocol.nearby.NearbyProtocolEngine
import com.leobigott.cercamessenger.protocol.cloud.FirebaseCloudSyncService
import com.leobigott.cercamessenger.protocol.crypto.ContactCrypto

object ProtocolEngineProvider {
    @Volatile
    private var engineInstance: ProtocolEngine? = null
    val crypto = ContactCrypto()

    fun init(context: Context) {
        if (engineInstance != null) return
        synchronized(this) {
            if (engineInstance == null) {
                val appContext = context.applicationContext
                val database = CercaDatabase.getInstance(appContext)
                val nodeId = DeviceIdentityStore.getOrCreateNodeId(appContext)
                engineInstance = NearbyProtocolEngine(
                    context = appContext,
                    database = database,
                    localNodeId = nodeId,
                    displayName = "${Build.MODEL ?: "CERCA Android"}|$nodeId",
                    crypto = crypto,
                    cloudSyncService = FirebaseCloudSyncService(
                        context = appContext,
                        database = database,
                        localNodeId = nodeId,
                        crypto = crypto
                    )
                )
            }
        }
    }

    val engine: ProtocolEngine
        get() = requireNotNull(engineInstance) {
            "ProtocolEngineProvider.init(context) must be called before using the engine."
        }
}
