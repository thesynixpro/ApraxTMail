package dev.aprax.tmaii.feature.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.aprax.tmaii.core.model.MessageDetail
import dev.aprax.tmaii.core.network.ApiException
import dev.aprax.tmaii.core.repository.MailboxRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DetailState(
    val loading: Boolean = true,
    val detail: MessageDetail? = null,
    val error: String? = null,
)

@HiltViewModel
class MessageDetailViewModel @Inject constructor(
    private val repo: MailboxRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DetailState())
    val state: StateFlow<DetailState> = _state

    fun load(mailboxId: String, messageId: String) {
        viewModelScope.launch {
            _state.value = DetailState(loading = true)
            try {
                val detail = repo.messageDetail(mailboxId, messageId)
                _state.value = DetailState(loading = false, detail = detail)
            } catch (e: Exception) {
                _state.value = DetailState(
                    loading = false,
                    error = (e as? ApiException)?.message ?: e.message ?: "Failed to load message",
                )
            }
        }
    }

    fun consumeError() {
        _state.value = _state.value.copy(error = null)
    }
}
