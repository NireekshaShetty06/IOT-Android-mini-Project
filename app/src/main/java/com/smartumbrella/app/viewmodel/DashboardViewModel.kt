package com.smartumbrella.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smartumbrella.app.data.model.DeviceConfig
import com.smartumbrella.app.data.model.RainAlertEvent
import com.smartumbrella.app.data.model.SensorHistoryItem
import com.smartumbrella.app.data.model.UmbrellaStatus
import com.smartumbrella.app.data.repository.FirebaseSmartUmbrellaRepository
import com.smartumbrella.app.data.repository.MockSmartUmbrellaRepository
import com.smartumbrella.app.data.repository.SmartUmbrellaRepository
import com.smartumbrella.app.service.NotificationHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DashboardUiState(
    val status: UmbrellaStatus = UmbrellaStatus(),
    val isOnline: Boolean = false,
    val history: List<SensorHistoryItem> = emptyList(),
    val alerts: List<RainAlertEvent> = emptyList(),
    val config: DeviceConfig = DeviceConfig(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    // Live Firebase repository is default; mockRepo is available for offline demo
    private val firebaseRepo = FirebaseSmartUmbrellaRepository(viewModelScope)
    private val mockRepo = MockSmartUmbrellaRepository(viewModelScope)

    // Current active repository (defaults to live Firebase)
    private val activeRepositoryFlow = MutableStateFlow<SmartUmbrellaRepository>(firebaseRepo)

    private var previousRainState: Boolean = false

    @OptIn(ExperimentalCoroutinesApi::class)
    val configState: StateFlow<DeviceConfig> = activeRepositoryFlow.flatMapLatest { it.configFlow }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DeviceConfig()
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<DashboardUiState> = activeRepositoryFlow.flatMapLatest { repo ->
        combine(
            repo.statusFlow,
            repo.isOnlineFlow,
            repo.historyFlow,
            repo.alertFlow,
            repo.configFlow
        ) { status, isOnline, history, alerts, config ->
            // Handle Notification Trigger on rain detected transition
            if (config.notificationsEnabled && status.rainDetected && !previousRainState) {
                NotificationHelper.showRainNotification(
                    context = getApplication(),
                    title = "Rain Detected!",
                    message = "Your Smart Umbrella has detected rainfall. Please deploy canopy!"
                )
            }
            previousRainState = status.rainDetected

            DashboardUiState(
                status = status,
                isOnline = isOnline,
                history = history,
                alerts = alerts,
                config = config,
                isLoading = false
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState(isLoading = true)
    )

    fun toggleSimulationMode(enabled: Boolean) {
        val currentRepo = if (enabled) mockRepo else firebaseRepo
        activeRepositoryFlow.value = currentRepo
        val currentConfig = uiState.value.config.copy(simulationMode = enabled)
        currentRepo.updateConfig(currentConfig)
        currentRepo.setSimulationMode(enabled)
    }

    fun simulateRain(isRaining: Boolean) {
        activeRepositoryFlow.value.simulateRainEvent(isRaining)
    }

    fun updateConfig(config: DeviceConfig) {
        activeRepositoryFlow.value.updateConfig(config)
    }

    fun clearAlerts() {
        activeRepositoryFlow.value.clearAlertHistory()
    }

    fun refresh() {
        activeRepositoryFlow.value.refresh()
    }
}

