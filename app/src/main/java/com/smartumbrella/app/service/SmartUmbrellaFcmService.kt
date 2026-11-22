package com.smartumbrella.app.service

import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Handles remote Firebase Cloud Messaging push notifications for remote alerts
 * when the application is terminated or running in background.
 */
class SmartUmbrellaFcmService : FirebaseMessagingService() {

    private val DATABASE_URL = "https://smart-umbrella-1e4e4-default-rtdb.asia-southeast1.firebasedatabase.app"

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("SmartUmbrellaFCM", "New FCM registration token: $token")
        // Register token in Firebase RTDB so serverless Cloud Functions can broadcast rain alerts
        try {
            val database = FirebaseDatabase.getInstance(DATABASE_URL)
            database.getReference("smartUmbrella").child("fcmTokens").child(token).setValue(true)
        } catch (e: Exception) {
            Log.w("SmartUmbrellaFCM", "Failed to register new FCM token: ${e.message}")
        }
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
