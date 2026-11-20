package dev.aprax.tmaii.core.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Shared active-mailbox selection across Home, Inbox, History, and sync worker. */
@Singleton
class ActiveMailboxStore @Inject constructor() {
    private val _id = MutableStateFlow<String?>(null)
    val id: StateFlow<String?> = _id

    fun set(mailboxId: String?) {
        _id.value = mailboxId
    }

    fun current(): String? = _id.value
}
