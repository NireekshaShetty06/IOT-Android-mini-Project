package com.smartumbrella.app

import android.app.Application
import com.smartumbrella.app.service.NotificationHelper

class SmartUmbrellaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize Notification Channels for instant rain alerts
        NotificationHelper.createNotificationChannel(this)
    }
}

