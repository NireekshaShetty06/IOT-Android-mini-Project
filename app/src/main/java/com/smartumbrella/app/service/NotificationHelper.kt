package com.smartumbrella.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.smartumbrella.app.MainActivity
import com.smartumbrella.app.R

/**
 * Manages rain detection system notifications with:
 * - Notification channel setup
 * - De-duplication and cooldown tracking
 * - Heads-up priority and vibration
 */
object NotificationHelper {

    private const val CHANNEL_ID = "smart_umbrella_rain_alerts"
    private const val CHANNEL_NAME = "Rain Alerts"
    private const val CHANNEL_DESC = "Instant notifications when Smart Umbrella detects rainfall"
    private const val NOTIFICATION_ID = 1001

    private var lastRainAlertTimestamp: Long = 0L
    private const val ALERT_COOLDOWN_MS: Long = 60_000L // 1 minute cooldown between repeat alerts

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESC
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 200, 300)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showRainNotification(
        context: Context,
        title: String = "Rain Detected!",
        message: String = "Your Smart Umbrella has detected rainfall. Please deploy your umbrella canopy!"
    ) {
        val now = System.currentTimeMillis()
        if (now - lastRainAlertTimestamp < ALERT_COOLDOWN_MS) {
            // Suppress rapid duplicate notifications
            return
        }
        lastRainAlertTimestamp = now

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_umbrella)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVibrate(longArrayOf(0, 300, 200, 300))
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}

