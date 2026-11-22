package dev.aprax.tmaii.feature.generator

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.aprax.tmaii.core.model.DomainDto
import dev.aprax.tmaii.core.prefs.SettingsDataStore
import dev.aprax.tmaii.core.repository.DomainRepository
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GeneratorUiState(
    val loading: Boolean = true,
    val creating: Boolean = false,
    val error: String? = null,
    val domains: List<DomainDto> = emptyList(),
    val selectedDomainId: String? = null,
    val createdAddress: String? = null,
)

@HiltViewModel
class GeneratorViewModel @Inject constructor(
    private val domains: DomainRepository,
    private val settings: SettingsDataStore,
) : ViewModel() {

    var state by mutableStateOf(GeneratorUiState())
        private set

    var username by mutableStateOf("")
        private set

    init { load() }

    fun load() {
        state = state.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                val list = domains.listDomains()
                val preferred = list.firstOrNull { it.verified }?.id ?: list.firstOrNull()?.id
                state = GeneratorUiState(
                    loading = false,
                    domains = list,
                    selectedDomainId = state.selectedDomainId ?: preferred,
                )
            } catch (e: Exception) {
                state = state.copy(loading = false, error = e.message ?: "Could not load domains")
            }
        }
    }

    fun onUsernameChange(v: String) {
        username = v.lowercase().filter { it.isLetterOrDigit() || it in "._-" }
        state = state.copy(error = null)
    }

    fun randomize() {
        val words = listOf(
            "swift", "nova", "pixel", "echo", "orbit", "drift", "quartz", "lunar",
            "ember", "frost", "terra", "vivid", "cobalt", "onyx", "zephyr", "mint",
        )
        val a = words.random()
        val b = words.random()
        username = "$a.$b${(10..99).random()}"
    }

    fun selectDomain(id: String) {
        state = state.copy(selectedDomainId = id, error = null)
    }

    fun create() {
        val domainId = state.selectedDomainId
        val local = username.trim()
        if (domainId == null) {
            state = state.copy(error = "Add a custom domain first")
            return
        }
        if (local.isEmpty()) {
            state = state.copy(error = "Enter a username or tap Random")
            return
        }
        state = state.copy(creating = true, error = null)
        viewModelScope.launch {
            try {
                val address = domains.createAddress(domainId, local)
                settings.setLastAddress(address.address)
                state = state.copy(creating = false, createdAddress = address.address)
            } catch (e: Exception) {
                state = state.copy(creating = false, error = e.message ?: "Could not create address")
            }
        }
    }

    fun reset() {
        state = state.copy(createdAddress = null)
        username = ""
    }
}