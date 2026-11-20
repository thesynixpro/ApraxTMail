package dev.aprax.tmaii.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.aprax.tmaii.core.database.LocalMailboxEntity
import dev.aprax.tmaii.core.model.Mailbox
import dev.aprax.tmaii.core.model.ProviderInfo
import dev.aprax.tmaii.core.model.ProviderQuota
import dev.aprax.tmaii.core.network.ApiException
import dev.aprax.tmaii.core.repository.ActiveMailboxStore
import dev.aprax.tmaii.core.repository.MailboxRepository
import dev.aprax.tmaii.core.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isGenerating: Boolean = false,
    val activeMailbox: Mailbox? = null,
    val providers: List<ProviderInfo> = emptyList(),
    val quotas: Map<String, ProviderQuota> = emptyMap(),
    val messageCount: Int = 0,
    val lastRefreshAt: Long? = null,
    val backendStatus: String = "unknown",
    val error: String? = null,
    val infoMessage: String? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val mailboxRepo: MailboxRepository,
    private val sessionRepo: SessionRepository,
    private val activeStore: ActiveMailboxStore,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state

    val history: StateFlow<List<LocalMailboxEntity>> = mailboxRepo.observeHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var activeMailboxId: String? = null

    init {
        bootstrap()
    }

    fun bootstrap() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                sessionRepo.ensureSession()
                _state.value = _state.value.copy(backendStatus = "connected")
                loadProviders()
                loadMailboxes()
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    error = e.message ?: "Could not connect to backend",
                    backendStatus = "offline",
                    isLoading = false,
                )
            }
        }
    }

    fun retry() = bootstrap()

    private suspend fun loadProviders() {
        try {
            val providers = mailboxRepo.providers()
            val quotas = mailboxRepo.quotas().associateBy { it.providerId }
            _state.value = _state.value.copy(providers = providers, quotas = quotas)
        } catch (_: Exception) {
            // providers list is non-critical for home; keep going
        }
    }

    private suspend fun loadMailboxes() {
        val remote = mailboxRepo.refreshMailboxes()
        val active = remote.firstOrNull { !it.remoteDeleted }
        activeMailboxId = active?.id
        activeStore.set(active?.id)
        _state.value = _state.value.copy(
            activeMailbox = active,
            isLoading = false,
        )
        active?.let { refreshMessages(it.id, silent = true) }
    }

    fun generateRandom(providerId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isGenerating = true, error = null, infoMessage = null)
            try {
                val mailbox = mailboxRepo.createMailbox(provider = providerId, random = true)
                activeMailboxId = mailbox.id
                activeStore.set(mailbox.id)
                _state.value = _state.value.copy(
                    activeMailbox = mailbox,
                    isGenerating = false,
                    infoMessage = "Created ${mailbox.address}",
                    messageCount = 0,
                    lastRefreshAt = null,
                )
                loadMailboxes()
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isGenerating = false,
                    error = e.userMessage(),
                )
            }
        }
    }

    fun generateCustom(providerId: String, username: String, domain: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isGenerating = true, error = null, infoMessage = null)
            try {
                val mailbox = mailboxRepo.createMailbox(
                    provider = providerId,
                    username = username,
                    domain = domain,
                    random = false,
                )
                activeMailboxId = mailbox.id
                activeStore.set(mailbox.id)
                _state.value = _state.value.copy(
                    activeMailbox = mailbox,
                    isGenerating = false,
                    infoMessage = "Created ${mailbox.address}",
                )
                loadMailboxes()
            } catch (e: Exception) {
                _state.value = _state.value.copy(isGenerating = false, error = e.userMessage())
            }
        }
    }

    fun activate(mailbox: Mailbox) {
        activeMailboxId = mailbox.id
        activeStore.set(mailbox.id)
        _state.value = _state.value.copy(activeMailbox = mailbox, messageCount = 0, lastRefreshAt = null)
        refreshMessages(mailbox.id)
    }

    fun refreshMessages(mailboxId: String? = activeMailboxId, silent: Boolean = false) {
        val id = mailboxId ?: return
        viewModelScope.launch {
            if (!silent) _state.value = _state.value.copy(isRefreshing = true, error = null)
            try {
                val messages = mailboxRepo.refreshMessages(id)
                _state.value = _state.value.copy(
                    messageCount = messages.size,
                    lastRefreshAt = System.currentTimeMillis(),
                    isRefreshing = false,
                    error = null,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isRefreshing = false,
                    error = if (silent) null else e.userMessage(),
                )
            }
        }
    }

    fun deleteActiveMailbox() {
        val id = activeMailboxId ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isRefreshing = true, error = null)
            try {
                mailboxRepo.deleteMailbox(id, remote = true)
                activeMailboxId = null
                activeStore.set(null)
                _state.value = _state.value.copy(
                    activeMailbox = null,
                    messageCount = 0,
                    lastRefreshAt = null,
                    isRefreshing = false,
                    infoMessage = "Mailbox deleted",
                )
                loadMailboxes()
            } catch (e: Exception) {
                _state.value = _state.value.copy(isRefreshing = false, error = e.userMessage())
            }
        }
    }

    fun consumeMessage() {
        _state.value = _state.value.copy(infoMessage = null, error = null)
    }

    fun refreshQuotas() {
        viewModelScope.launch { loadProviders() }
    }
}

internal fun Exception.userMessage(): String = when (this) {
    is ApiException -> message
    else -> message ?: "Something went wrong"
}
