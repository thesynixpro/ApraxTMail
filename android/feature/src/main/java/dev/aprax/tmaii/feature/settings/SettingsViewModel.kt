package dev.aprax.tmaii.feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.aprax.tmaii.core.model.Accent
import dev.aprax.tmaii.core.model.ThemeMode
import dev.aprax.tmaii.core.prefs.SettingsDataStore
import dev.aprax.tmaii.core.repository.SessionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsDataStore,
    private val session: SessionRepository,
) : ViewModel() {

    val themeMode = settings.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)
    val accent = settings.accent.stateIn(viewModelScope, SharingStarted.Eagerly, Accent.VIOLET)
    val baseUrl = settings.baseUrl.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val email = settings.email.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    var urlInput by mutableStateOf("")
        private set
    var urlSaved by mutableStateOf(false)
        private set
    var healthMessage by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            val stored = settings.baseUrl.first()
            if (stored.isNotBlank() && urlInput.isBlank()) urlInput = stored
        }
    }

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { settings.setTheme(mode) }
    }

    fun setAccent(value: Accent) {
        viewModelScope.launch { settings.setAccent(value) }
    }

    fun onUrlChange(v: String) {
        urlInput = v
        urlSaved = false
        healthMessage = null
    }

    fun saveUrl() {
        val url = urlInput.trim().trimEnd('/')
        if (url.isBlank() || !url.startsWith("http")) {
            healthMessage = "Enter a valid URL starting with https://"
            return
        }
        viewModelScope.launch {
            session.configureBackend(url)
            urlSaved = true
            healthMessage = null
        }
    }

    fun testConnection() {
        busy = true
        healthMessage = null
        viewModelScope.launch {
            try {
                session.ensureBackendConfigured()
                val resp = session.health()
                healthMessage = if (resp.status == "ok") "Connected ✓ ${resp.version}" else "Unexpected response: ${resp.status}"
            } catch (e: Exception) {
                healthMessage = e.message ?: "Connection failed"
            } finally {
                busy = false
            }
        }
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            session.logout()
            onDone()
        }
    }
}
