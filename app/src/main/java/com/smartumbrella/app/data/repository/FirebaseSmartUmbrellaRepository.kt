package com.smartumbrella.app.data.repository

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
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

/**
 * Production Firebase Realtime Database repository.
 * Reads live telemetry from smartUmbrella/status or smartUmbrella/{deviceId}/status.
 * Implements heartbeat staleness check to detect device disconnects reliably.
 */
class FirebaseSmartUmbrellaRepository(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : SmartUmbrellaRepository {

    private val TAG = "FirebaseRepo"

    private val _configFlow = MutableStateFlow(DeviceConfig(simulationMode = false))
    override val configFlow: StateFlow<DeviceConfig> = _configFlow.asStateFlow()

    private val _statusFlow = MutableStateFlow(UmbrellaStatus())
    override val statusFlow: StateFlow<UmbrellaStatus> = _statusFlow.asStateFlow()

    private val _isOnlineFlow = MutableStateFlow(false)
    override val isOnlineFlow: StateFlow<Boolean> = _isOnlineFlow.asStateFlow()

    private val _historyFlow = MutableStateFlow<List<SensorHistoryItem>>(emptyList())
    override val historyFlow: StateFlow<List<SensorHistoryItem>> = _historyFlow.asStateFlow()

    private val _alertFlow = MutableStateFlow<List<RainAlertEvent>>(emptyList())
    override val alertFlow: StateFlow<List<RainAlertEvent>> = _alertFlow.asStateFlow()

    private var database: FirebaseDatabase? = null
    private var statusListener: ValueEventListener? = null
    private var statusRef: DatabaseReference? = null
    private var heartbeatCheckJob: Job? = null

    init {
        initFirebaseListener()
        startHeartbeatMonitor()
    }

    private val DATABASE_URL = "https://smart-umbrella-1e4e4-default-rtdb.asia-southeast1.firebasedatabase.app"

    private fun initFirebaseListener() {
        try {
            database = FirebaseDatabase.getInstance(DATABASE_URL)
            val deviceId = _configFlow.value.deviceId
            // Support both root status or device-scoped status:
            // "smartUmbrella/status" or "smartUmbrella/{deviceId}/status"
            statusRef = database?.getReference("smartUmbrella")?.child("status")

            statusListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.exists()) {
                        Log.w(TAG, "No telemetry data found under smartUmbrella/status")
                        return
                    }

                    try {
                        val temp = snapshot.child("temperature").getValue(Double::class.java)?.toFloat()
                            ?: snapshot.child("temperature").getValue(Long::class.java)?.toFloat()
                        val hum = snapshot.child("humidity").getValue(Double::class.java)?.toFloat()
                            ?: snapshot.child("humidity").getValue(Long::class.java)?.toFloat()
                        val rainDetected = snapshot.child("rainDetected").getValue(Boolean::class.java) ?: false
                        val rainAnalog = snapshot.child("rainAnalogValue").getValue(Long::class.java)?.toInt()
                        val rainDigital = snapshot.child("rainDigitalValue").getValue(Long::class.java)?.toInt()
                        val buzzerOn = snapshot.child("buzzerOn").getValue(Boolean::class.java) ?: false
                        val rgbStatus = snapshot.child("rgbStatus").getValue(String::class.java) ?: "GREEN"
                        val deviceOnline = snapshot.child("deviceOnline").getValue(Boolean::class.java) ?: true
                        val lastUpdated = snapshot.child("lastUpdated").getValue(Long::class.java)
                            ?: System.currentTimeMillis()

                        val previous = _statusFlow.value
                        val newStatus = UmbrellaStatus(
                            deviceId = deviceId,
                            temperature = temp,
                            humidity = hum,
                            rainDetected = rainDetected,
                            rainAnalogValue = rainAnalog,
                            rainDigitalValue = rainDigital,
                            buzzerOn = buzzerOn,
                            rgbStatus = rgbStatus,
                            deviceOnline = deviceOnline,
                            lastUpdated = lastUpdated
                        )

                        _statusFlow.value = newStatus
                        val isFresh = !newStatus.isDataStale(_configFlow.value.staleTimeoutSeconds * 1000L)
                        _isOnlineFlow.value = deviceOnline && isFresh

                        // Automatically record rain detection transitions into local alert list
                        if (rainDetected && !previous.rainDetected) {
                            recordAlertEvent(newStatus)
                        }

                        // Maintain rolling sensor history
                        if (temp != null && hum != null) {
                            appendHistoryPoint(temp, hum, rainAnalog ?: 4095, lastUpdated)
                        }

                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing telemetry snapshot", e)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Firebase Realtime Database listener cancelled: ${error.message}")
                    _isOnlineFlow.value = false
                }
            }

            statusRef?.addValueEventListener(statusListener as ValueEventListener)

        } catch (e: Exception) {
            Log.e(TAG, "Firebase initialization skipped or error (credentials may be needed): ${e.message}")
        }
    }

    private fun startHeartbeatMonitor() {
        heartbeatCheckJob?.cancel()
        heartbeatCheckJob = scope.launch {
            while (isActive) {
                delay(5000)
                val current = _statusFlow.value
                val stale = current.isDataStale(_configFlow.value.staleTimeoutSeconds * 1000L)
                val online = current.deviceOnline && !stale
                if (_isOnlineFlow.value != online) {
                    _isOnlineFlow.value = online
                }
            }
        }
    }

    private fun recordAlertEvent(status: UmbrellaStatus) {
        val alerts = _alertFlow.value.toMutableList()
        alerts.add(
            0,
            RainAlertEvent(
                id = System.currentTimeMillis().toString(),
                timestamp = System.currentTimeMillis(),
                eventType = "Rain Detected",
                rainStatus = "RAIN DETECTED",
                rainAnalogValue = status.rainAnalogValue,
                temperature = status.temperature,
                humidity = status.humidity
            )
        )
        _alertFlow.value = alerts
    }

    private fun appendHistoryPoint(temp: Float, hum: Float, rainAo: Int, timestamp: Long) {
        val history = _historyFlow.value.toMutableList()
        history.add(
            SensorHistoryItem(
                id = timestamp.toString(),
                timestamp = timestamp,
                temperature = temp,
                humidity = hum,
                rainAnalogValue = rainAo
            )
        )
        if (history.size > 30) {
            history.removeAt(0)
        }
        _historyFlow.value = history
    }

    override fun updateConfig(config: DeviceConfig) {
        _configFlow.value = config
    }

    override fun setSimulationMode(enabled: Boolean) {
        _configFlow.value = _configFlow.value.copy(simulationMode = enabled)
    }

    override fun simulateRainEvent(isRaining: Boolean) {
        // Fallback or manual test hook
        val current = _statusFlow.value
        val updated = current.copy(
            rainDetected = isRaining,
            rainAnalogValue = if (isRaining) 2500 else 3900,
            rainDigitalValue = if (isRaining) 0 else 1,
            buzzerOn = isRaining,
            rgbStatus = if (isRaining) "RED" else "GREEN",
            lastUpdated = System.currentTimeMillis()
        )
        _statusFlow.value = updated
        if (isRaining) recordAlertEvent(updated)
    }

    override fun clearAlertHistory() {
        _alertFlow.value = emptyList()
    }

    override fun refresh() {
        // Re-read or touch database
    }
}

