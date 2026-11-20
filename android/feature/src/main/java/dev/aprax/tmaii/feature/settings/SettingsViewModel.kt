package dev.aprax.tmaii.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.aprax.tmaii.core.model.HealthResponse
import dev.aprax.tmaii.core.model.RefreshInterval
import dev.aprax.tmaii.core.model.ThemeMode
import dev.aprax.tmaii.core.prefs.SettingsDataStore
import dev.aprax.tmaii.core.repository.MailboxRepository
import dev.aprax.tmaii.core.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val checkingBackend: Boolean = false,
    val health: HealthResponse? = null,
    val backendError: String? = null,
    val message: String? = null,
    val role: String? = null,
    val email: String? = null,
    val baseUrl: String = "",
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsDataStore,
    private val session: SessionRepository,
    private val mailboxRepo: MailboxRepository,
) : ViewModel() {

    val themeMode = settings.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)

    val refreshInterval = settings.refreshInterval
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RefreshInterval.SEC_30)

    val defaultProvider = settings.defaultProvider
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "tmailor")

    val notificationsEnabled = settings.notificationsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val autoRefreshEnabled = settings.autoRefreshEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state

    init {
        viewModelScope.launch {
            settings.baseUrl.collect { url ->
                _state.value = _state.value.copy(baseUrl = url)
            }
        }
        viewModelScope.launch {
            settings.email.collect { email ->
                _state.value = _state.value.copy(email = email)
            }
        }
        viewModelScope.launch {
            settings.authRole.collect { role ->
                _state.value = _state.value.copy(role = role)
            }
        }
    }

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { settings.setTheme(mode) }

    fun setRefreshInterval(interval: RefreshInterval) =
        viewModelScope.launch { settings.setRefreshInterval(interval) }

    fun setDefaultProvider(provider: String) =
        viewModelScope.launch { settings.setDefaultProvider(provider) }

    fun setNotifications(enabled: Boolean) =
        viewModelScope.launch { settings.setNotificationsEnabled(enabled) }

    fun setAutoRefresh(enabled: Boolean) =
        viewModelScope.launch { settings.setAutoRefreshEnabled(enabled) }

    fun setBaseUrl(url: String) {
        viewModelScope.launch {
            session.configureBackend(url)
            _state.value = _state.value.copy(message = "Backend URL saved")
        }
    }

    fun checkBackend() {
        viewModelScope.launch {
            _state.value = _state.value.copy(checkingBackend = true, backendError = null)
            try {
                session.syncClientFromPrefs()
                val health = session.health()
                _state.value = _state.value.copy(
                    checkingBackend = false,
                    health = health,
                    backendError = null,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    checkingBackend = false,
                    health = null,
                    backendError = e.message ?: "Backend unreachable",
                )
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            mailboxRepo.clearHistory()
            _state.value = _state.value.copy(message = "Local history cleared")
        }
    }

    fun logout() {
        viewModelScope.launch {
            session.logout()
            _state.value = _state.value.copy(message = "Signed out", role = null, email = null)
        }
    }

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null)
    }
}
