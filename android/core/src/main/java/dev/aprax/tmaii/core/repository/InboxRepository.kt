package dev.aprax.tmaii.core.repository

import dev.aprax.tmaii.core.model.MessageDto
import dev.aprax.tmaii.core.model.MessagesResponse
import dev.aprax.tmaii.core.network.ApiService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InboxRepository @Inject constructor(
    private val api: ApiService,
    private val session: SessionRepository,
) {
    suspend fun list(
        addressId: String? = null,
        domainId: String? = null,
        unread: Boolean = false,
        limit: Int = 50,
        offset: Int = 0,
    ): MessagesResponse {
        session.syncClientFromPrefs()
        return api.listMessages(addressId, domainId, unread, limit, offset)
    }

    suspend fun get(id: String): MessageDto {
        session.syncClientFromPrefs()
        return api.getMessage(id)
    }

    suspend fun markRead(id: String, read: Boolean = true) {
        session.syncClientFromPrefs()
        api.markRead(id, read)
    }

    suspend fun delete(id: String) {
        session.syncClientFromPrefs()
        api.deleteMessage(id)
    }

    suspend fun unreadCount(): Int = list(unread = true, limit = 1).total
}
