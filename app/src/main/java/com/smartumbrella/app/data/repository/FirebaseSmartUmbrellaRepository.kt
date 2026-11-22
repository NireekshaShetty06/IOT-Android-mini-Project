package com.smartumbrella.app.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.messaging.FirebaseMessaging
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
 * Reads live telemetry from smartUmbrella/status.
 * Synchronizes remote configuration via smartUmbrella/config.
 * Persists and streams sensor history via smartUmbrella/history.
 * Implements strict heartbeat staleness check to detect device disconnects reliably.
 */
class FirebaseSmartUmbrellaRepository(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : SmartUmbrellaRepository {

    companion object {
        private const val TAG = "FirebaseRepo"
        private const val DATABASE_URL =
            "https://smart-umbrella-1e4e4-default-rtdb.asia-southeast1.firebasedatabase.app"
        // ESP32 reports telemetry every 3 seconds. 15 seconds represents 5 missed heartbeats.
        const val HEARTBEAT_TIMEOUT_MS = 15_000L
        private const val HISTORY_PERSIST_THROTTLE_MS = 30_000L
    }

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
    private var configListener: ValueEventListener? = null
    private var historyListener: ValueEventListener? = null

    private var statusRef: DatabaseReference? = null
    private var configRef: DatabaseReference? = null
    private var historyRef: DatabaseReference? = null

    private var heartbeatCheckJob: Job? = null
    private var lastHistoryPersistTime = 0L

    init {
        initFirebase()
        startHeartbeatMonitor()
    }

    private fun initFirebase() {
        try {
            database = FirebaseDatabase.getInstance(DATABASE_URL)
            val auth = FirebaseAuth.getInstance()

            // Verify or establish Firebase Authentication session (anonymous sign-in)
            // to satisfy secure database rules (e.g. auth != null) without hardcoded secrets.
            if (auth.currentUser == null) {
                auth.signInAnonymously()
                    .addOnSuccessListener {
                        Log.d(TAG, "Firebase Auth signed in anonymously: ${it.user?.uid}")
                        attachDatabaseListeners()
                        registerFcmToken()
                    }
                    .addOnFailureListener { e ->
                        Log.w(TAG, "Anonymous sign-in failed, proceeding with listeners: ${e.message}")
                        attachDatabaseListeners()
                        registerFcmToken()
                    }
            } else {
                Log.d(TAG, "Firebase Auth already authenticated: ${auth.currentUser?.uid}")
                attachDatabaseListeners()
                registerFcmToken()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Firebase initialization error: ${e.message}")
        }
    }

    private fun attachDatabaseListeners() {
        val rootRef = database?.getReference("smartUmbrella") ?: return

        // 1. Live Telemetry Status Listener
        statusRef = rootRef.child("status")
        statusListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    Log.w(TAG, "No telemetry data found under smartUmbrella/status")
                    _isOnlineFlow.value = false
                    _statusFlow.value = UmbrellaStatus(
                        deviceId = _configFlow.value.deviceId,
                        deviceOnline = false,
                        lastUpdated = 0L
                    )
                    return
                }

                try {
                    val tempVal = snapshot.child("temperature").value
                    val temp = (tempVal as? Number)?.toFloat()
                        ?: (tempVal as? String)?.toFloatOrNull()

                    val humVal = snapshot.child("humidity").value
                    val hum = (humVal as? Number)?.toFloat()
                        ?: (humVal as? String)?.toFloatOrNull()

                    val rainDetected = snapshot.child("rainDetected").value as? Boolean ?: false

                    val rainAnalogVal = snapshot.child("rainAnalogValue").value
                    val rainAnalog = (rainAnalogVal as? Number)?.toInt()
                        ?: (rainAnalogVal as? String)?.toIntOrNull()

                    val rainDigitalVal = snapshot.child("rainDigitalValue").value
                    val rainDigital = (rainDigitalVal as? Number)?.toInt()
                        ?: (rainDigitalVal as? String)?.toIntOrNull()

                    val buzzerOn = snapshot.child("buzzerOn").value as? Boolean ?: false
                    val rgbStatus = snapshot.child("rgbStatus").value as? String ?: "GREEN"

                    // Strict online/offline parsing:
                    // NEVER default missing deviceOnline to true; default to false.
                    val deviceOnline = snapshot.child("deviceOnline").value as? Boolean ?: false

                    // NEVER default missing lastUpdated to System.currentTimeMillis(); use 0L.
                    val lastUpdatedVal = snapshot.child("lastUpdated").value
                    val lastUpdated = (lastUpdatedVal as? Number)?.toLong()
                        ?: (lastUpdatedVal as? String)?.toLongOrNull()
                        ?: 0L

                    val dhtValid = snapshot.child("dhtValid").value as? Boolean ?: true

                    val previous = _statusFlow.value
                    val newStatus = UmbrellaStatus(
                        deviceId = _configFlow.value.deviceId,
                        temperature = temp,
                        humidity = hum,
                        rainDetected = rainDetected,
                        rainAnalogValue = rainAnalog,
                        rainDigitalValue = rainDigital,
                        buzzerOn = buzzerOn,
                        rgbStatus = rgbStatus,
                        deviceOnline = deviceOnline,
                        lastUpdated = lastUpdated,
                        dhtValid = dhtValid
                    )

                    _statusFlow.value = newStatus

                    // Evaluate online state strictly based on both deviceOnline flag AND timestamp freshness
                    val isFresh = lastUpdated > 0L && (System.currentTimeMillis() - lastUpdated) <= HEARTBEAT_TIMEOUT_MS
                    _isOnlineFlow.value = deviceOnline && isFresh

                    // Record transition alert
                    if (rainDetected && !previous.rainDetected) {
                        recordAlertEvent(newStatus)
                    }

                    // Append and persist history
                    if (temp != null && hum != null && lastUpdated > 0L) {
                        appendHistoryPoint(temp, hum, rainAnalog ?: 4095, lastUpdated)
                        maybePersistHistoryToFirebase(temp, hum, rainAnalog ?: 4095, lastUpdated)
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing telemetry snapshot", e)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Firebase status listener cancelled: ${error.message}")
                _isOnlineFlow.value = false
            }
        }
        statusRef?.addValueEventListener(statusListener as ValueEventListener)

        // 2. Remote Configuration Listener
        configRef = rootRef.child("config")
        configListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    try {
                        val trigger = (snapshot.child("rainTriggerThreshold").value as? Number)?.toInt()
                            ?: _configFlow.value.rainTriggerThreshold
                        val clear = (snapshot.child("rainClearThreshold").value as? Number)?.toInt()
                            ?: _configFlow.value.rainClearThreshold
                        val sound = snapshot.child("soundEnabled").value as? Boolean
                            ?: _configFlow.value.soundEnabled
                        val notify = snapshot.child("notificationsEnabled").value as? Boolean
                            ?: _configFlow.value.notificationsEnabled

                        _configFlow.value = _configFlow.value.copy(
                            rainTriggerThreshold = trigger,
                            rainClearThreshold = clear,
                            soundEnabled = sound,
                            notificationsEnabled = notify
                        )
                        Log.d(TAG, "Remote config synchronized from Firebase: trigger=$trigger, clear=$clear, sound=$sound")
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse remote config: ${e.message}")
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "Config listener cancelled: ${error.message}")
            }
        }
        configRef?.addValueEventListener(configListener as ValueEventListener)

        // 3. Persistent History Listener (limitToLast 30 records)
        historyRef = rootRef.child("history")
        historyListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val list = mutableListOf<SensorHistoryItem>()
                    for (child in snapshot.children) {
                        val t = (child.child("temperature").value as? Number)?.toFloat()
                        val h = (child.child("humidity").value as? Number)?.toFloat()
                        val ao = (child.child("rainAnalogValue").value as? Number)?.toInt() ?: 4095
                        val ts = (child.child("timestamp").value as? Number)?.toLong()
                            ?: child.key?.toLongOrNull() ?: 0L
                        if (t != null && h != null && ts > 0L) {
                            list.add(
                                SensorHistoryItem(
                                    id = child.key ?: ts.toString(),
                                    timestamp = ts,
                                    temperature = t,
                                    humidity = h,
                                    rainAnalogValue = ao
                                )
                            )
                        }
                    }
                    if (list.isNotEmpty()) {
                        list.sortBy { it.timestamp }
                        _historyFlow.value = list
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "History listener cancelled: ${error.message}")
            }
        }
        historyRef?.limitToLast(30)?.addValueEventListener(historyListener as ValueEventListener)
    }

    private fun registerFcmToken() {
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                if (!token.isNullOrBlank()) {
                    Log.d(TAG, "Registering FCM token with Firebase RTDB: $token")
                    val tokensRef = database?.getReference("smartUmbrella")?.child("fcmTokens")
                    tokensRef?.child(token)?.setValue(true)
                }
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Unable to retrieve FCM token: ${e.message}")
            }
    }

    private fun startHeartbeatMonitor() {
        heartbeatCheckJob?.cancel()
        heartbeatCheckJob = scope.launch {
            while (isActive) {
                delay(3000)
                val current = _statusFlow.value
                val isFresh = current.lastUpdated > 0L &&
                    (System.currentTimeMillis() - current.lastUpdated) <= HEARTBEAT_TIMEOUT_MS
                val online = current.deviceOnline && isFresh
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
        // Deduplicate records with exact same timestamp
        if (history.none { it.timestamp == timestamp }) {
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
    }

    private fun maybePersistHistoryToFirebase(temp: Float, hum: Float, rainAo: Int, timestamp: Long) {
        val now = System.currentTimeMillis()
        if (now - lastHistoryPersistTime >= HISTORY_PERSIST_THROTTLE_MS) {
            lastHistoryPersistTime = now
            val pointMap = mapOf(
                "timestamp" to timestamp,
                "temperature" to temp,
                "humidity" to hum,
                "rainAnalogValue" to rainAo
            )
            historyRef?.child(timestamp.toString())?.setValue(pointMap)
        }
    }

    override fun updateConfig(config: DeviceConfig) {
        _configFlow.value = config
        // Persist configuration to Firebase RTDB so ESP32 receives it
        val remoteMap = mapOf(
            "rainTriggerThreshold" to config.rainTriggerThreshold,
            "rainClearThreshold" to config.rainClearThreshold,
            "soundEnabled" to config.soundEnabled,
            "notificationsEnabled" to config.notificationsEnabled,
            "lastConfigUpdated" to System.currentTimeMillis()
        )
        configRef?.setValue(remoteMap)
            ?.addOnSuccessListener {
                Log.d(TAG, "Device config successfully published to /smartUmbrella/config")
            }
            ?.addOnFailureListener { e ->
                Log.e(TAG, "Failed to publish device config: ${e.message}")
            }
    }

    override fun setSimulationMode(enabled: Boolean) {
        _configFlow.value = _configFlow.value.copy(simulationMode = enabled)
    }

    override fun simulateRainEvent(isRaining: Boolean) {
        val current = _statusFlow.value
        val updated = current.copy(
            rainDetected = isRaining,
            rainAnalogValue = if (isRaining) 2500 else 3900,
            rainDigitalValue = if (isRaining) 0 else 1,
            buzzerOn = isRaining,
            rgbStatus = if (isRaining) "RED" else "GREEN",
            lastUpdated = System.currentTimeMillis(),
            deviceOnline = true,
            dhtValid = true
        )
        _statusFlow.value = updated
        _isOnlineFlow.value = true
        if (isRaining) recordAlertEvent(updated)
    }

    override fun clearAlertHistory() {
        _alertFlow.value = emptyList()
    }

    override fun refresh() {
        statusRef?.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                statusListener?.onDataChange(snapshot)
            }
            override fun onCancelled(error: DatabaseError) {
                _isOnlineFlow.value = false
            }
        })
    }
}
