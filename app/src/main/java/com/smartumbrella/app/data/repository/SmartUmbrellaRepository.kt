package com.smartumbrella.app.data.repository

import com.smartumbrella.app.data.model.DeviceConfig
import com.smartumbrella.app.data.model.RainAlertEvent
import com.smartumbrella.app.data.model.SensorHistoryItem
import com.smartumbrella.app.data.model.UmbrellaStatus
import kotlinx.coroutines.flow.StateFlow

/**
 * Repository interface decoupling UI and ViewModels from the underlying data source
 * (Firebase Realtime Database or Local Mock / Demo).
 */
interface SmartUmbrellaRepository {
    val statusFlow: StateFlow<UmbrellaStatus>
    val isOnlineFlow: StateFlow<Boolean>
    val historyFlow: StateFlow<List<SensorHistoryItem>>
    val alertFlow: StateFlow<List<RainAlertEvent>>
    val configFlow: StateFlow<DeviceConfig>

    fun updateConfig(config: DeviceConfig)
    fun setSimulationMode(enabled: Boolean)
    fun simulateRainEvent(isRaining: Boolean)
    fun clearAlertHistory()
    fun refresh()
}

