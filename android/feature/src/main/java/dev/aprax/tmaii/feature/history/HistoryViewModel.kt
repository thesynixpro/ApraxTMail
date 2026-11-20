package dev.aprax.tmaii.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.aprax.tmaii.core.database.LocalMailboxEntity
import dev.aprax.tmaii.core.repository.MailboxRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repo: MailboxRepository,
) : ViewModel() {

    val history = repo.observeHistory()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun delete(mailbox: LocalMailboxEntity, remote: Boolean) {
        viewModelScope.launch {
            try {
                repo.deleteMailbox(mailbox.id, remote = remote)
                _message.value = if (remote) "Deleted ${mailbox.address}" else "Removed from history"
            } catch (e: Exception) {
                _message.value = e.message ?: "Delete failed"
            }
        }
    }

    fun deleteFromHistory(mailbox: LocalMailboxEntity) {
        viewModelScope.launch {
            repo.deleteMailbox(mailbox.id, remote = false)
            _message.value = "Removed from history"
        }
    }

    fun announce(text: String) {
        _message.value = text
    }

    fun consumeMessage() {
        _message.value = null
    }
}
