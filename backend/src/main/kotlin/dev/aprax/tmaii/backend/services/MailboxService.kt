package dev.aprax.tmaii.backend.services

import dev.aprax.tmaii.backend.config.AppConfig
import dev.aprax.tmaii.backend.models.ApiMailbox
import dev.aprax.tmaii.backend.models.ApiMessage
import dev.aprax.tmaii.backend.models.ApiMessageDetail
import dev.aprax.tmaii.backend.providers.CreateMailboxRequest
import dev.aprax.tmaii.backend.providers.EmailAccount
import dev.aprax.tmaii.backend.providers.EmailMessage
import dev.aprax.tmaii.backend.providers.EmailMessageDetail
import dev.aprax.tmaii.backend.providers.EmailOtp
import dev.aprax.tmaii.backend.providers.OtpSource
import dev.aprax.tmaii.backend.providers.ProviderRegistry
import dev.aprax.tmaii.backend.repositories.MailboxRepo
import dev.aprax.tmaii.backend.repositories.MailboxRow
import dev.aprax.tmaii.backend.repositories.MessageCacheRepo
import dev.aprax.tmaii.backend.repositories.UsageLogRepo
import dev.aprax.tmaii.backend.security.TokenCipher
import dev.aprax.tmaii.backend.utils.ApiException
import dev.aprax.tmaii.backend.utils.ForbiddenException
import dev.aprax.tmaii.backend.utils.NotFoundException
import dev.aprax.tmaii.backend.utils.TooManyRequestsException
import dev.aprax.tmaii.backend.utils.UnauthorizedException
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

class MailboxService(
    private val config: AppConfig,
    private val registry: ProviderRegistry,
    private val cipher: TokenCipher,
    private val json: Json,
) {
    data class AuthContext(
        val userId: String,
        val role: String,
    )

    suspend fun listProviders() = registry.list().map { registry.info(it) }

    suspend fun providerQuotas(): List<dev.aprax.tmaii.backend.providers.ProviderQuota> {
        return registry.list().filter { it.isConfigured() }.mapNotNull { provider ->
            try {
                registry.throttler.withPermit(provider.id, registry.minIntervalMs(provider.id)) {
                    provider.getQuota()
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    suspend fun createMailbox(
        ctx: AuthContext,
        providerId: String,
        request: CreateMailboxRequest,
        label: String? = null,
    ): ApiMailbox {
        enforceMailboxQuota(ctx)
        val account = registry.execute(providerId) { it.createMailbox(request) }
        val tokenEnc = account.providerToken?.let { cipher.encrypt(it) }
        val metaEnc = cipher.encrypt(
            json.encodeToString(
                MapSerializer(String.serializer(), String.serializer()),
                mapOf(
                    "reuseUrl" to (account.reuseUrl ?: ""),
                    "createdAt" to (account.createdAt ?: ""),
                ),
            ),
        )
        val row = MailboxRepo.insert(
            ownerId = ctx.userId,
            provider = account.providerId,
            address = account.address,
            providerTokenEnc = tokenEnc,
            providerMetaEnc = metaEnc,
            label = label,
        )
        UsageLogRepo.log(account.providerId, "create_mailbox", true, ctx.userId, 201)
        return toApi(row, unread = 0)
    }

    private fun enforceMailboxQuota(ctx: AuthContext) {
        val limit = if (ctx.role == "GUEST") config.guestMailboxLimit else config.accountMailboxLimit
        val windowStart = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1)
        val created = MailboxRepo.countForOwnerSince(ctx.userId, windowStart)
        if (created >= limit) {
            throw TooManyRequestsException(
                message = "Mailbox creation limit reached: $limit per hour. Try again later.",
                code = "mailbox_hourly_limit",
                retryAfterSeconds = 3600,
            )
        }
    }

    fun listMailboxes(ctx: AuthContext): List<ApiMailbox> =
        MailboxRepo.listForOwner(ctx.userId).map { row ->
            val unread = MessageCacheRepo.listForMailbox(row.id).count { !it.seen }
            toApi(row, unread)
        }

    fun getOwnedMailbox(ctx: AuthContext, mailboxId: String): MailboxRow {
        val row = MailboxRepo.findById(mailboxId) ?: throw NotFoundException("Mailbox not found")
        if (row.ownerId != ctx.userId) throw ForbiddenException("Not your mailbox")
        return row
    }

    private fun toApi(row: MailboxRow, unread: Int): ApiMailbox {
        val meta = runCatching {
            row.providerMetaEnc?.let { json.decodeFromString<Map<String, String>>(cipher.decrypt(it)) }
        }.getOrNull() ?: emptyMap()
        return ApiMailbox(
            id = row.id,
            provider = row.provider,
            address = row.address,
            label = row.label,
            createdAt = row.createdAt.toString(),
            lastSyncAt = row.lastSyncAt?.toString(),
            remoteDeleted = row.remoteDeleted,
            unreadCount = unread,
            reuseUrl = meta["reuseUrl"]?.takeIf { it.isNotBlank() },
        )
    }

    private fun accountOf(row: MailboxRow): EmailAccount {
        val token = row.providerTokenEnc?.let { cipher.decrypt(it) }
        val meta = runCatching {
            row.providerMetaEnc?.let { json.decodeFromString<Map<String, String>>(cipher.decrypt(it)) }
        }.getOrNull() ?: emptyMap()
        return EmailAccount(
            providerId = row.provider,
            address = row.address,
            providerToken = token,
            createdAt = meta["createdAt"]?.takeIf { it.isNotBlank() },
            reuseUrl = meta["reuseUrl"]?.takeIf { it.isNotBlank() },
        )
    }

    suspend fun refreshMessages(ctx: AuthContext, mailboxId: String): List<ApiMessage> {
        val row = getOwnedMailbox(ctx, mailboxId)
        if (row.remoteDeleted) return emptyList()
        val account = accountOf(row)
        val messages = try {
            registry.execute(row.provider) { it.listMessages(account) }
        } catch (e: ApiException) {
            UsageLogRepo.log(row.provider, "list_messages", false, ctx.userId, errorCode = e.code)
            throw e
        }
        UsageLogRepo.log(row.provider, "list_messages", true, ctx.userId, 200)
        val cached = mergeCache(row.id, messages)
        MailboxRepo.markSynced(row.id)
        return messages.map { m -> toApiMessage(m, cached[m.id]) }
    }

    private fun mergeCache(mailboxId: String, messages: List<EmailMessage>): Map<String, MessageCacheRepo.CachedMessage> {
        val result = LinkedHashMap<String, MessageCacheRepo.CachedMessage>()
        for (m in messages) {
            val parsedOtp = m.otp
            val cached = MessageCacheRepo.CachedMessage(
                providerMessageId = m.id,
                sender = m.from,
                senderName = m.fromName,
                recipient = m.to,
                subject = m.subject,
                receivedAt = parseTime(m.receivedAt),
                seen = m.seen,
                hasOtp = parsedOtp != null,
                otpCode = parsedOtp?.code,
                otpSource = parsedOtp?.source?.name,
                hasVerificationLink = m.verificationLinks.isNotEmpty(),
            )
            MessageCacheRepo.upsert(mailboxId, cached)
            result[m.id] = cached
        }
        return result
    }

    private fun parseTime(value: String?): OffsetDateTime? {
        if (value.isNullOrBlank()) return null
        return runCatching { OffsetDateTime.parse(value) }
            .recoverCatching { java.time.OffsetDateTime.ofInstant(java.time.Instant.parse(value), java.time.ZoneOffset.UTC) }
            .getOrNull()
    }

    private fun toApiMessage(m: EmailMessage, cached: MessageCacheRepo.CachedMessage?): ApiMessage =
        ApiMessage(
            id = m.id,
            from = m.from,
            fromName = m.fromName,
            to = m.to,
            subject = m.subject,
            receivedAt = m.receivedAt,
            seen = m.seen,
            hasAttachment = m.hasAttachment,
            otp = m.otp?.let {
                mapOf("code" to it.code, "source" to it.source.name, "score" to (it.score?.toString() ?: ""))
            },
            verificationLinks = m.verificationLinks.map { it.url },
        )

    suspend fun getMessage(ctx: AuthContext, mailboxId: String, messageId: String): ApiMessageDetail {
        val row = getOwnedMailbox(ctx, mailboxId)
        val account = accountOf(row)
        val detail: EmailMessageDetail = try {
            registry.execute(row.provider) { it.getMessage(account, messageId) }
        } catch (e: ApiException) {
            UsageLogRepo.log(row.provider, "get_message", false, ctx.userId, errorCode = e.code)
            throw e
        }
        UsageLogRepo.log(row.provider, "get_message", true, ctx.userId, 200)

        var otp = detail.message.otp
        if (otp == null) {
            val source = buildString {
                detail.text?.let { append(it) }
                append('\n')
                detail.html?.let { append(it) }
            }
            OtpParser.extract(source)?.let {
                otp = EmailOtp(code = it, source = OtpSource.LOCAL, messageId = messageId)
            }
        }
        if (otp == null && detail.providerCodes.isNotEmpty()) {
            otp = EmailOtp(code = detail.providerCodes.first(), source = OtpSource.PROVIDER, messageId = messageId)
        }

        val links = buildSet {
            addAll(detail.message.verificationLinks.map { it.url })
            addAll(detail.providerLinks)
            OtpParser.findVerificationLinks((detail.text ?: "") + " " + (detail.html ?: ""))
        }.toList()

        MessageCacheRepo.upsert(
            row.id,
            MessageCacheRepo.CachedMessage(
                providerMessageId = messageId,
                sender = detail.message.from,
                senderName = detail.message.fromName,
                recipient = detail.message.to,
                subject = detail.message.subject,
                receivedAt = parseTime(detail.message.receivedAt),
                seen = true,
                hasOtp = otp != null,
                otpCode = otp?.code,
                otpSource = otp?.source?.name,
                hasVerificationLink = links.isNotEmpty(),
            ),
        )

        val sanitizedHtml = detail.html?.let { OtpParser.sanitizeHtml(it) }
        return ApiMessageDetail(
            message = detail.message.copy(otp = otp, verificationLinks = links.map {
                dev.aprax.tmaii.backend.providers.VerificationLink(it, messageId)
            }),
            html = sanitizedHtml,
            text = detail.text,
            attachments = detail.attachments,
            bodyAvailable = detail.bodyAvailable,
        )
    }

    suspend fun getOtp(ctx: AuthContext, mailboxId: String): EmailOtp? {
        val row = getOwnedMailbox(ctx, mailboxId)
        val account = accountOf(row)

        // 1) Provider-native OTP (may be plan-gated → NotSupportedOperationException)
        val providerOtp = try {
            registry.execute(row.provider) { it.getOtp(account) }
        } catch (e: dev.aprax.tmaii.backend.utils.NotSupportedOperationException) {
            null
        } catch (e: ApiException) {
            UsageLogRepo.log(row.provider, "get_otp", false, ctx.userId, errorCode = e.code)
            null
        }
        if (providerOtp != null) return providerOtp

        // 2) Local parse from latest messages
        val messages = try {
            registry.execute(row.provider) { it.listMessages(account) }
        } catch (e: ApiException) {
            // 3) Fall back to cache
            return cachedOtp(mailboxId)
        }
        for (m in messages) {
            m.otp?.let { return it }
        }
        for (m in messages.take(3)) {
            try {
                val detail = registry.execute(row.provider) { it.getMessage(account, m.id) }
                detail.message.otp?.let { return it }
                val source = (detail.text ?: "") + " " + (detail.html ?: "")
                OtpParser.extract(source)?.let {
                    return EmailOtp(it, OtpSource.LOCAL, messageId = m.id, subject = m.subject)
                }
                detail.providerCodes.firstOrNull()?.let {
                    return EmailOtp(it, OtpSource.PROVIDER, messageId = m.id, subject = m.subject)
                }
            } catch (_: ApiException) {
                continue
            }
        }
        // Verification link fallback: expose newest link, never fabricate a code
        messages.firstOrNull()?.verificationLinks?.firstOrNull()?.let { link ->
            // No code — return null rather than invent one
        }
        return cachedOtp(mailboxId)
    }

    private fun cachedOtp(mailboxId: String): EmailOtp? =
        MessageCacheRepo.listForMailbox(mailboxId)
            .firstOrNull { it.hasOtp && it.otpCode != null }
            ?.let {
                EmailOtp(
                    code = it.otpCode!!,
                    source = runCatching { OtpSource.valueOf(it.otpSource ?: "LOCAL") }.getOrDefault(OtpSource.LOCAL),
                    messageId = it.providerMessageId,
                    subject = it.subject,
                )
            }

    suspend fun deleteMailbox(ctx: AuthContext, mailboxId: String, deleteRemote: Boolean = true) {
        val row = getOwnedMailbox(ctx, mailboxId)
        if (deleteRemote && !row.remoteDeleted) {
            try {
                val account = accountOf(row)
                registry.execute(row.provider) { it.deleteMailbox(account) }
                UsageLogRepo.log(row.provider, "delete_mailbox", true, ctx.userId, 204)
            } catch (e: dev.aprax.tmaii.backend.utils.NotSupportedOperationException) {
                // local-only delete
            } catch (e: ApiException) {
                UsageLogRepo.log(row.provider, "delete_mailbox", false, ctx.userId, errorCode = e.code)
                // Still remove locally; mark remote deleted only on success/not-found
                if (e.code != "not_found") {
                    MailboxRepo.deleteLocal(row.id)
                    MessageCacheRepo.deleteForMailbox(row.id)
                    throw e
                }
            }
        }
        MailboxRepo.deleteLocal(row.id)
        MessageCacheRepo.deleteForMailbox(row.id)
    }

    suspend fun deleteMessage(ctx: AuthContext, mailboxId: String, messageId: String) {
        val row = getOwnedMailbox(ctx, mailboxId)
        val account = accountOf(row)
        try {
            registry.execute(row.provider) { it.deleteMessage(account, messageId) }
            UsageLogRepo.log(row.provider, "delete_message", true, ctx.userId, 204)
        } catch (e: dev.aprax.tmaii.backend.utils.NotSupportedOperationException) {
            throw e
        } catch (e: ApiException) {
            UsageLogRepo.log(row.provider, "delete_message", false, ctx.userId, errorCode = e.code)
            throw e
        }
    }
}
