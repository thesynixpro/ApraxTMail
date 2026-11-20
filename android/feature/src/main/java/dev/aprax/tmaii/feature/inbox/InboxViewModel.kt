package dev.aprax.tmaii.feature.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.aprax.tmaii.core.database.LocalMailboxEntity
import dev.aprax.tmaii.core.database.LocalMessageEntity
import dev.aprax.tmaii.core.model.Message
import dev.aprax.tmaii.core.network.ApiException
import dev.aprax.tmaii.core.repository.MailboxRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InboxUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val mailboxId: String? = null,
    val mailbox: LocalMailboxEntity? = null,
    val remoteMessages: List<Message> = emptyList(),
    val otp: String? = null,
    val otpSource: String? = null,
    val lastRefreshAt: Long? = null,
    val error: String? = null,
    val autoRefreshSeconds: Int = 30,
    val autoRefreshEnabled: Boolean = true,
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class InboxViewModel @Inject constructor(
    private val repo: MailboxRepository,
) : ViewModel() {

    private val mailboxIdFlow = MutableStateFlow<String?>(null)

    val cachedMessages: StateFlow<List<LocalMessageEntity>> = mailboxIdFlow
        .flatMapLatest { id ->
            if (id.isNullOrBlank()) flowOf(emptyList()) else repo.observeCachedMessages(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _state = MutableStateFlow(InboxUiState())
    val state: StateFlow<InboxUiState> = _state

    private var pollJob: Job? = null
    private var attachedId: String? = null

    fun attach(mailboxId: String?) {
        if (mailboxId.isNullOrBlank()) {
            stopPolling()
            _state.value = _state.value.copy(
                isLoading = false,
                mailbox = null,
                mailboxId = null,
                error = "No active mailbox — generate one first",
            )
            mailboxIdFlow.value = null
            return
        }
        if (attachedId == mailboxId && pollJob?.isActive == true) return
        attachedId = mailboxId
        mailboxIdFlow.value = mailboxId
        viewModelScope.launch {
            val local = repo.localMailbox(mailboxId)
            _state.value = _state.value.copy(mailbox = local, mailboxId = mailboxId, isLoading = false, error = null)
            refresh()
            startPolling()
        }
    }

    fun detach() = stopPolling()

    fun refresh() {
        val id = _state.value.mailboxId ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isRefreshing = true, error = null)
            try {
                val messages = repo.refreshMessages(id)
                _state.value = _state.value.copy(
                    remoteMessages = messages,
                    lastRefreshAt = System.currentTimeMillis(),
                    isRefreshing = false,
                )
                try {
                    val otp = repo.otp(id)
                    if (otp.detected && !otp.otp.isNullOrBlank()) {
                        _state.value = _state.value.copy(otp = otp.otp, otpSource = otp.source)
                    }
                } catch (_: Exception) {
                    // OTP endpoint may be plan-gated; cached/local OTP still available
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isRefreshing = false,
                    error = (e as? ApiException)?.message ?: e.message ?: "Refresh failed",
                )
            }
        }
    }

    fun setAutoRefresh(enabled: Boolean, seconds: Int) {
        _state.value = _state.value.copy(autoRefreshEnabled = enabled, autoRefreshSeconds = seconds)
        if (enabled) startPolling() else stopPolling()
    }

    private fun startPolling() {
        stopPolling()
        val seconds = _state.value.autoRefreshSeconds
        if (!_state.value.autoRefreshEnabled || seconds <= 0) return
        pollJob = viewModelScope.launch {
            while (true) {
                delay(seconds.coerceAtLeast(15) * 1000L)
                if (_state.value.autoRefreshEnabled) refresh()
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    fun clearOtp() = _state.value.copy(otp = null, otpSource = null).let { _state.value = it }

    fun consumeError() {
        _state.value = _state.value.copy(error = null)
    }
}
