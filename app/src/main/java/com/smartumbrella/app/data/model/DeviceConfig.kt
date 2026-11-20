package com.smartumbrella.app.data.model

/**
 * User and device settings preferences.
 */
data class DeviceConfig(
    val deviceId: String = "umbrella_001",
    val deviceName: String = "Smart Umbrella #1",
    val staleTimeoutSeconds: Long = 30L,
    val notificationsEnabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val simulationMode: Boolean = false,
    val rainTriggerThreshold: Int = 3300,
    val rainClearThreshold: Int = 3600
)

