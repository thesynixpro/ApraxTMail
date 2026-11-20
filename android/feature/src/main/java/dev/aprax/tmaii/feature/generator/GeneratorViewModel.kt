package dev.aprax.tmaii.feature.generator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.aprax.tmaii.core.model.ProviderDomain
import dev.aprax.tmaii.core.model.ProviderInfo
import dev.aprax.tmaii.core.model.ProviderQuota
import dev.aprax.tmaii.core.network.ApiException
import dev.aprax.tmaii.core.repository.MailboxRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GeneratorState(
    val isLoading: Boolean = true,
    val providers: List<ProviderInfo> = emptyList(),
    val quotas: Map<String, ProviderQuota> = emptyMap(),
    val domains: List<ProviderDomain> = emptyList(),
    val selectedProvider: String = "",
    val randomMode: Boolean = true,
    val username: String = "",
    val domain: String = "",
    val creating: Boolean = false,
    val createdAddress: String? = null,
    val error: String? = null,
)

// ProviderDomain lives in backend models; mirror a minimal shape for Android
data class SimpleDomain(val domain: String, val usable: Boolean = true)

@HiltViewModel
class GeneratorViewModel @Inject constructor(
    private val repo: MailboxRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(GeneratorState())
    val state: StateFlow<GeneratorState> = _state

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val providers = repo.providers()
                val quotas = repo.quotas().associateBy { it.providerId }
                val default = providers.firstOrNull { it.enabled && it.configured }?.id
                    ?: providers.firstOrNull()?.id
                    ?: ""
                _state.value = _state.value.copy(
                    providers = providers,
                    quotas = quotas,
                    selectedProvider = _state.value.selectedProvider.ifBlank { default },
                    isLoading = false,
                )
                loadDomains()
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    private suspend fun loadDomains() {
        // Domains come through the providers endpoint capabilities; the backend
        // exposes no dedicated domain list on the internal API, so custom mode
        // lets the user type a domain that the provider validates on create.
        _state.value = _state.value.copy(domains = emptyList())
    }

    fun selectProvider(id: String) {
        _state.value = _state.value.copy(selectedProvider = id)
    }

    fun setRandomMode(random: Boolean) {
        _state.value = _state.value.copy(randomMode = random)
    }

    fun setUsername(value: String) {
        _state.value = _state.value.copy(username = value.filter { it.isLetterOrDigit() || it == '.' || it == '_' || it == '-' }.take(30))
    }

    fun setDomain(value: String) {
        _state.value = _state.value.copy(domain = value)
    }

    fun create() {
        val s = _state.value
        viewModelScope.launch {
            _state.value = s.copy(creating = true, error = null, createdAddress = null)
            try {
                val mailbox = repo.createMailbox(
                    provider = s.selectedProvider,
                    username = s.username.takeIf { !s.randomMode && it.isNotBlank() },
                    domain = s.domain.takeIf { !s.randomMode && it.isNotBlank() },
                    random = s.randomMode,
                )
                _state.value = _state.value.copy(
                    creating = false,
                    createdAddress = mailbox.address,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    creating = false,
                    error = (e as? ApiException)?.message ?: e.message ?: "Creation failed",
                )
            }
        }
    }

    fun consumeCreated() {
        _state.value = _state.value.copy(createdAddress = null)
    }

    fun consumeError() {
        _state.value = _state.value.copy(error = null)
    }
}
