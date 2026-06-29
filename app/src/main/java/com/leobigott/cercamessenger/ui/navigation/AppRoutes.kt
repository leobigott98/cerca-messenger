package com.leobigott.cercamessenger.ui.navigation

import android.net.Uri

sealed class AppRoute(val route: String) {
    data object Crisis : AppRoute("crisis")
    data object Conversations : AppRoute("conversations")
    data object Nearby : AppRoute("nearby")
    data object Guide : AppRoute("guide")
    data object Settings : AppRoute("settings")
    data object Contacts : AppRoute("contacts")
    data object Chat : AppRoute("chat/{conversationId}/{peerId}/{peerName}") {
        fun create(conversationId: String, peerId: String, peerName: String): String =
            "chat/$conversationId/$peerId/${Uri.encode(peerName)}"
    }
}
