package dev.aprax.tmaii.core.repository

import dev.aprax.tmaii.core.database.LocalMailboxEntity
import dev.aprax.tmaii.core.database.LocalMessageEntity
import dev.aprax.tmaii.core.database.MailboxDao
import dev.aprax.tmaii.core.database.MessageDao
import dev.aprax.tmaii.core.model.CreateMailboxBody
import dev.aprax.tmaii.core.model.Mailbox
import dev.aprax.tmaii.core.model.Message
import dev.aprax.tmaii.core.model.MessageDetail
import dev.aprax.tmaii.core.model.OtpResponse
import dev.aprax.tmaii.core.model.ProviderInfo
import dev.aprax.tmaii.core.model.ProviderQuota
import dev.aprax.tmaii.core.network.ApiService
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MailboxRepository @Inject constructor(
    private val api: ApiService,
    private val session: SessionRepository,
    private val mailboxDao: MailboxDao,
    private val messageDao: MessageDao,
) {
    fun observeHistory(): Flow<List<LocalMailboxEntity>> = mailboxDao.observeAll()

    fun observeCachedMessages(mailboxId: String): Flow<List<LocalMessageEntity>> =
        messageDao.observeForMailbox(mailboxId)

    suspend fun providers(): List<ProviderInfo> {
        session.syncClientFromPrefs()
        return api.providers()
    }

    suspend fun quotas(): List<ProviderQuota> {
        session.syncClientFromPrefs()
        return runCatching { api.quotas() }.getOrDefault(emptyList())
    }

    suspend fun createMailbox(
        provider: String,
        username: String? = null,
        domain: String? = null,
        random: Boolean = true,
        label: String? = null,
    ): Mailbox {
        session.syncClientFromPrefs()
        val mailbox = api.createMailbox(CreateMailboxBody(provider, username, domain, random, label))
        mailboxDao.upsert(mailbox.toEntity())
        return mailbox
    }

    suspend fun refreshMailboxes(): List<Mailbox> {
        session.syncClientFromPrefs()
        val remote = api.listMailboxes()
        remote.forEach { mailboxDao.upsert(it.toEntity()) }
        return remote
    }

    suspend fun refreshMessages(mailboxId: String): List<Message> {
        session.syncClientFromPrefs()
        val messages = api.listMessages(mailboxId)
        val mailbox = mailboxDao.findById(mailboxId)
        if (mailbox != null) mailboxDao.markSynced(mailboxId, System.currentTimeMillis())
        val existing = messageDao.getForMailbox(mailboxId).associateBy { it.providerMessageId }
        val entities = messages.mapIndexed { index, m ->
            val prev = existing[m.id]
            val otp = m.otp
            LocalMessageEntity(
                id = prev?.id ?: "${mailboxId}_${m.id}",
                mailboxId = mailboxId,
                providerMessageId = m.id,
                sender = m.from,
                senderName = m.fromName,
                subject = m.subject,
                receivedAt = parseIso(m.receivedAt) ?: prev?.receivedAt,
                seen = m.seen || prev?.seen == true,
                hasOtp = otp != null || m.verificationLinks.isNotEmpty() || prev?.hasOtp == true,
                otpCode = otp?.get("code") ?: prev?.otpCode,
                otpSource = otp?.get("source") ?: prev?.otpSource,
                hasVerificationLink = m.verificationLinks.isNotEmpty() || prev?.hasVerificationLink == true,
            )
        }
        messageDao.upsertAll(entities)
        return messages
    }

    suspend fun messageDetail(mailboxId: String, messageId: String): MessageDetail {
        session.syncClientFromPrefs()
        return api.getMessage(mailboxId, messageId)
    }

    suspend fun otp(mailboxId: String): OtpResponse {
        session.syncClientFromPrefs()
        return api.getOtp(mailboxId)
    }

    suspend fun deleteMailbox(mailboxId: String, remote: Boolean = true) {
        session.syncClientFromPrefs()
        runCatching { api.deleteMailbox(mailboxId, remote) }
        messageDao.clearForMailbox(mailboxId)
        mailboxDao.delete(mailboxId)
    }

    suspend fun deleteMessage(mailboxId: String, messageId: String) {
        session.syncClientFromPrefs()
        api.deleteMessage(mailboxId, messageId)
    }

    suspend fun clearHistory() {
        messageDao.clearAll()
        mailboxDao.clear()
    }

    suspend fun localMailbox(id: String): LocalMailboxEntity? = mailboxDao.findById(id)

    private fun Mailbox.toEntity() = LocalMailboxEntity(
        id = id,
        provider = provider,
        address = address,
        createdAt = parseIso(createdAt) ?: System.currentTimeMillis(),
        lastSyncAt = parseIso(lastSyncAt),
        label = label,
    )

    companion object {
        fun parseIso(value: String?): Long? {
            if (value == null) return null
            return runCatching {
                java.time.OffsetDateTime.parse(value).toInstant().toEpochMilli()
            }.recoverCatching {
                java.time.Instant.parse(value).toEpochMilli()
            }.getOrNull()
        }
    }
}
