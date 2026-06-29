package com.leobigott.cercamessenger.protocol

import android.content.Context
import java.util.UUID

object DeviceIdentityStore {
    private const val PREFS = "cerca_identity"
    private const val KEY_NODE_ID = "node_id"

    fun getOrCreateNodeId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_NODE_ID, null)
        if (!existing.isNullOrBlank()) return existing

        val created = "node-${UUID.randomUUID()}"
        prefs.edit().putString(KEY_NODE_ID, created).apply()
        return created
    }
}
