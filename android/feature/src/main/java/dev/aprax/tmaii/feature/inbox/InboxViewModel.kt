package dev.aprax.tmaii.feature.inbox

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.aprax.tmaii.core.model.MessageDto
import dev.aprax.tmaii.core.repository.InboxRepository
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InboxUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val messages: List<MessageDto> = emptyList(),
    val total: Int = 0,
    val unreadOnly: Boolean = false,
)

@HiltViewModel
class InboxViewModel @Inject constructor(
    private val inbox: InboxRepository,
) : ViewModel() {

    var state by mutableStateOf(InboxUiState())
        private set

    init { refresh(initial = true) }

    fun refresh(initial: Boolean = false) {
        state = state.copy(refreshing = !initial, error = null)
        viewModelScope.launch {
            try {
                val resp = inbox.list(unread = state.unreadOnly, limit = 100)
                state = InboxUiState(
                    loading = false,
                    messages = resp.messages,
                    total = resp.total,
                    unreadOnly = state.unreadOnly,
                )
            } catch (e: Exception) {
                state = state.copy(
                    loading = false,
                    refreshing = false,
                    error = e.message ?: "Could not load messages",
                )
            }
        }
    }

    fun setUnreadOnly(unreadOnly: Boolean) {
        state = state.copy(unreadOnly = unreadOnly)
        refresh(initial = true)
    }

    fun delete(id: String, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                inbox.delete(id)
                state = state.copy(messages = state.messages.filterNot { it.id == id })
                onDone()
            } catch (e: Exception) {
                state = state.copy(error = e.message ?: "Delete failed")
            }
        }
    }
}
