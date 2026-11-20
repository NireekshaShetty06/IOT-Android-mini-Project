package com.smartumbrella.app.data.repository

import com.smartumbrella.app.data.model.DeviceConfig
import com.smartumbrella.app.data.model.RainAlertEvent
import com.smartumbrella.app.data.model.SensorHistoryItem
import com.smartumbrella.app.data.model.UmbrellaStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Realistic Mock Repository used for UI previews, development, and offline demo testing.
 * Provides realistic sensor ranges without inventing fake "live" claims.
 */
class MockSmartUmbrellaRepository(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) : SmartUmbrellaRepository {

    private val _configFlow = MutableStateFlow(DeviceConfig(simulationMode = true))
    override val configFlow: StateFlow<DeviceConfig> = _configFlow.asStateFlow()

    private val _statusFlow = MutableStateFlow(
        UmbrellaStatus(
            deviceId = "umbrella_001",
            temperature = 27.8f,
            humidity = 64.0f,
            rainDetected = false,
            rainAnalogValue = 3850, // Dry canopy
            rainDigitalValue = 1,
            buzzerOn = false,
            rgbStatus = "GREEN",
            deviceOnline = true,
            lastUpdated = System.currentTimeMillis()
        )
    )
    override val statusFlow: StateFlow<UmbrellaStatus> = _statusFlow.asStateFlow()

    private val _isOnlineFlow = MutableStateFlow(true)
    override val isOnlineFlow: StateFlow<Boolean> = _isOnlineFlow.asStateFlow()

    private val _historyFlow = MutableStateFlow<List<SensorHistoryItem>>(generateInitialHistory())
    override val historyFlow: StateFlow<List<SensorHistoryItem>> = _historyFlow.asStateFlow()

    private val _alertFlow = MutableStateFlow<List<RainAlertEvent>>(generateInitialAlerts())
    override val alertFlow: StateFlow<List<RainAlertEvent>> = _alertFlow.asStateFlow()

    private var simulationJob: Job? = null

    init {
        startSimulationLoop()
    }

    private fun startSimulationLoop() {
        simulationJob?.cancel()
        simulationJob = scope.launch {
            while (isActive) {
                delay(3000)
                if (_configFlow.value.simulationMode) {
                    val current = _statusFlow.value
                    val jitterTemp = (current.temperature ?: 27.5f) + (Random.nextFloat() * 0.4f - 0.2f)
                    val jitterHumidity = (current.humidity ?: 65f) + (Random.nextFloat() * 1.0f - 0.5f)

                    val updatedStatus = current.copy(
                        temperature = Math.round(jitterTemp * 10f) / 10f,
                        humidity = Math.round(jitterHumidity * 10f) / 10f,
                        lastUpdated = System.currentTimeMillis(),
                        deviceOnline = true
                    )
                    _statusFlow.value = updatedStatus
                    _isOnlineFlow.value = true

                    // Add periodic history point
                    val newHistory = _historyFlow.value.toMutableList()
                    newHistory.add(
                        SensorHistoryItem(
                            id = System.currentTimeMillis().toString(),
                            timestamp = System.currentTimeMillis(),
                            temperature = updatedStatus.temperature ?: 27.5f,
                            humidity = updatedStatus.humidity ?: 65f,
                            rainAnalogValue = updatedStatus.rainAnalogValue ?: 3800
                        )
                    )
                    if (newHistory.size > 20) {
                        newHistory.removeAt(0)
                    }
                    _historyFlow.value = newHistory
                }
            }
        }
    }

    override fun updateConfig(config: DeviceConfig) {
        _configFlow.value = config
    }

    override fun setSimulationMode(enabled: Boolean) {
        _configFlow.value = _configFlow.value.copy(simulationMode = enabled)
        if (!enabled) {
            simulationJob?.cancel()
        } else {
            startSimulationLoop()
        }
    }

    override fun simulateRainEvent(isRaining: Boolean) {
        val current = _statusFlow.value
        val analog = if (isRaining) 2450 else 3950
        val digital = if (isRaining) 0 else 1
        val rgb = if (isRaining) "RED" else "GREEN"

        val updated = current.copy(
            rainDetected = isRaining,
            rainAnalogValue = analog,
            rainDigitalValue = digital,
            buzzerOn = isRaining,
            rgbStatus = rgb,
            lastUpdated = System.currentTimeMillis()
        )
        _statusFlow.value = updated

        if (isRaining) {
            val alerts = _alertFlow.value.toMutableList()
            alerts.add(
                0,
                RainAlertEvent(
                    id = System.currentTimeMillis().toString(),
                    timestamp = System.currentTimeMillis(),
                    eventType = "Rain Detected",
                    rainStatus = "RAIN DETECTED",
                    rainAnalogValue = analog,
                    temperature = updated.temperature,
                    humidity = updated.humidity
                )
            )
            _alertFlow.value = alerts
        }
    }

    override fun clearAlertHistory() {
        _alertFlow.value = emptyList()
    }

    override fun refresh() {
        val current = _statusFlow.value
        _statusFlow.value = current.copy(lastUpdated = System.currentTimeMillis())
    }

    private fun generateInitialHistory(): List<SensorHistoryItem> {
        val list = mutableListOf<SensorHistoryItem>()
        val now = System.currentTimeMillis()
        val interval = 5 * 60 * 1000L // 5 mins interval
        val baseTemp = 26.5f
        val baseHum = 62.0f

        for (i in 10 downTo 0) {
            list.add(
                SensorHistoryItem(
                    id = "hist_$i",
                    timestamp = now - (i * interval),
                    temperature = baseTemp + (i * 0.2f) + (Random.nextFloat() * 0.4f),
                    humidity = baseHum + (i * 0.5f) + (Random.nextFloat() * 1.5f),
                    rainAnalogValue = 3800 - (Random.nextInt(200))
                )
            )
        }
        return list
    }

    private fun generateInitialAlerts(): List<RainAlertEvent> {
        val now = System.currentTimeMillis()
        return listOf(
            RainAlertEvent(
                id = "alert_1",
                timestamp = now - (25 * 60 * 1000L),
                eventType = "Rain Detected",
                rainStatus = "RAIN DETECTED",
                rainAnalogValue = 2850,
                temperature = 26.4f,
                humidity = 78.0f
            ),
            RainAlertEvent(
                id = "alert_2",
                timestamp = now - (2 * 3600 * 1000L),
                eventType = "Rain Detected",
                rainStatus = "RAIN DETECTED",
                rainAnalogValue = 2400,
                temperature = 25.8f,
                humidity = 82.5f
            )
        )
    }
}

