package com.leobigott.cercamessenger.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.leobigott.cercamessenger.MainActivity
import com.leobigott.cercamessenger.R
import kotlin.math.absoluteValue

class CercaNotificationHelper(
    private val context: Context
) {
    fun showIncomingMessageNotification(
        conversationId: String,
        senderName: String?,
        text: String,
        isEmergency: Boolean = false,
        isPublicBroadcast: Boolean = false
    ) {
        createChannels()

        if (!canPostNotifications()) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("conversationId", conversationId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            conversationId.hashCode().absoluteValue,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = when {
            isPublicBroadcast -> "Nuevo anuncio público"
            isEmergency -> "Nuevo mensaje urgente"
            senderName.isNullOrBlank() -> "Nuevo mensaje CERCA"
            else -> "Mensaje de $senderName"
        }

        val body = text
            .ifBlank { "Mensaje recibido sin conexión." }
            .take(120)

        val channelId = if (isEmergency || isPublicBroadcast) {
            CHANNEL_CRISIS
        } else {
            CHANNEL_MESSAGES
        }

        val priority = if (isEmergency || isPublicBroadcast) {
            NotificationCompat.PRIORITY_HIGH
        } else {
            NotificationCompat.PRIORITY_DEFAULT
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_cerca)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(priority)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()

        NotificationManagerCompat.from(context).notify(
            notificationId(conversationId, isEmergency, isPublicBroadcast),
            notification
        )
    }

    private fun canPostNotifications(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(NotificationManager::class.java)

        val messagesChannel = NotificationChannel(
            CHANNEL_MESSAGES,
            "Mensajes CERCA",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notificaciones de mensajes recibidos sin conexión."
        }

        val crisisChannel = NotificationChannel(
            CHANNEL_CRISIS,
            "Mensajes críticos CERCA",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notificaciones de mensajes urgentes, SOS y anuncios públicos."
        }

        manager.createNotificationChannel(messagesChannel)
        manager.createNotificationChannel(crisisChannel)
    }

    private fun notificationId(
        conversationId: String,
        isEmergency: Boolean,
        isPublicBroadcast: Boolean
    ): Int {
        val suffix = when {
            isPublicBroadcast -> "-broadcast"
            isEmergency -> "-emergency"
            else -> "-message"
        }

        return (conversationId + suffix).hashCode().absoluteValue
    }

    companion object {
        private const val CHANNEL_MESSAGES = "cerca_messages"
        private const val CHANNEL_CRISIS = "cerca_crisis_messages"
    }
}