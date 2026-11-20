package com.smartumbrella.app.service

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Handles remote Firebase Cloud Messaging push notifications for remote alerts
 * when the application is terminated or running in background.
 */
class SmartUmbrellaFcmService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("SmartUmbrellaFCM", "New FCM registration token: $token")
        // Token can be registered to Firebase or ESP32 server if desired
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val title = remoteMessage.notification?.title
            ?: remoteMessage.data["title"]
            ?: "Rain Detected!"

        val body = remoteMessage.notification?.body
            ?: remoteMessage.data["body"]
            ?: "Your Smart Umbrella has detected rainfall."

        NotificationHelper.showRainNotification(
            context = applicationContext,
            title = title,
            message = body
        )
    }
}

