package dev.aprax.tmaii.feature.detail

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.aprax.tmaii.core.model.MessageDto
import dev.aprax.tmaii.core.repository.InboxRepository
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DetailUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val message: MessageDto? = null,
    val deleted: Boolean = false,
)

@HiltViewModel
class MessageDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val inbox: InboxRepository,
) : ViewModel() {

    private val messageId: String = checkNotNull(savedStateHandle["messageId"])

    var state by mutableStateOf(DetailUiState())
        private set

    var showHtml by mutableStateOf(true)
        private set

    init { load(markRead = true) }

    fun load(markRead: Boolean = false, refreshing: Boolean = false) {
        state = state.copy(error = null, refreshing = refreshing)
        viewModelScope.launch {
            try {
                val msg = inbox.get(messageId)
                if (markRead && !msg.read) {
                    runCatching { inbox.markRead(messageId) }
                    state = state.copy(message = msg.copy(read = true), loading = false, refreshing = false)
                } else {
                    state = state.copy(message = msg, loading = false, refreshing = false)
                }
            } catch (e: Exception) {
                state = state.copy(
                    loading = false,
                    refreshing = false,
                    error = e.message ?: "Could not load message",
                )
            }
        }
    }

    fun toggleFormat() { showHtml = !showHtml }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                inbox.delete(messageId)
                state = state.copy(deleted = true)
                onDone()
            } catch (e: Exception) {
                state = state.copy(error = e.message ?: "Delete failed")
            }
        }
    }
}
