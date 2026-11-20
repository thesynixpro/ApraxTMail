package dev.aprax.tmaii.backend.repositories

import dev.aprax.tmaii.backend.database.AuditLogsTable
import dev.aprax.tmaii.backend.database.MailboxesTable
import dev.aprax.tmaii.backend.database.MessageCacheTable
import dev.aprax.tmaii.backend.database.SessionsTable
import dev.aprax.tmaii.backend.database.UsageLogsTable
import dev.aprax.tmaii.backend.database.UsersTable
import dev.aprax.tmaii.backend.security.Role
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

data class UserRow(
    val id: String,
    val email: String?,
    val kind: String,
    val status: String,
    val createdAt: OffsetDateTime,
)

data class SessionRow(
    val id: String,
    val userId: String,
    val expiresAt: OffsetDateTime,
    val revokedAt: OffsetDateTime?,
)

data class MailboxRow(
    val id: String,
    val ownerId: String,
    val provider: String,
    val address: String,
    val providerTokenEnc: String?,
    val providerMetaEnc: String?,
    val createdAt: OffsetDateTime,
    val lastSyncAt: OffsetDateTime?,
    val remoteDeleted: Boolean,
    val label: String?,
)

object UserRepo {
    fun create(kind: String, email: String? = null, passwordHash: String? = null): UserRow {
        val id = UUID.randomUUID().toString()
        UsersTable.insert {
            it[UsersTable.id] = id
            it[UsersTable.email] = email
            it[UsersTable.passwordHash] = passwordHash
            it[UsersTable.kind] = kind
        }
        return UserRow(id, email, kind, "active", OffsetDateTime.now())
    }

    fun findById(id: String): UserRow? = UsersTable.selectAll()
        .where { UsersTable.id eq id }
        .map { r ->
            UserRow(
                r[UsersTable.id], r[UsersTable.email], r[UsersTable.kind],
                r[UsersTable.status], r[UsersTable.createdAt],
            )
        }.firstOrNull()

    fun findByEmail(email: String): UserRow? = UsersTable.selectAll()
        .where { UsersTable.email eq email.lowercase() }
        .map { r ->
            UserRow(
                r[UsersTable.id], r[UsersTable.email], r[UsersTable.kind],
                r[UsersTable.status], r[UsersTable.createdAt],
            )
        }.firstOrNull()

    fun passwordHashOf(userId: String): String? = UsersTable.selectAll()
        .where { UsersTable.id eq userId }
        .map { it[UsersTable.passwordHash] }
        .firstOrNull()

    fun setStatus(userId: String, status: String) {
        UsersTable.update({ UsersTable.id eq userId }) {
            it[UsersTable.status] = status
            it[UsersTable.updatedAt] = OffsetDateTime.now()
        }
    }

    fun countByKind(kind: String): Long = UsersTable.selectAll()
        .where { UsersTable.kind eq kind }
        .count()

    fun listUsers(limit: Int, offset: Long): List<UserRow> = UsersTable.selectAll()
        .orderBy(UsersTable.createdAt to org.jetbrains.exposed.sql.SortOrder.DESC)
        .limit(limit, offset)
        .map { r ->
            UserRow(
                r[UsersTable.id], r[UsersTable.email], r[UsersTable.kind],
                r[UsersTable.status], r[UsersTable.createdAt],
            )
        }

    fun transferGuestMailboxes(guestId: String, newOwnerId: String): Int {
        val moved = MailboxesTable.update(
            { (MailboxesTable.ownerId eq guestId) and (MailboxesTable.remoteDeleted eq false) }
        ) { it[MailboxesTable.ownerId] = newOwnerId }
        SessionsTable.deleteWhere { SessionsTable.userId eq guestId }
        return moved
    }
}

object SessionRepo {
    fun create(sessionId: String, userId: String, expiresAt: OffsetDateTime, userAgent: String?, ip: String?) {
        SessionsTable.insert {
            it[id] = sessionId
            it[SessionsTable.userId] = userId
            it[SessionsTable.expiresAt] = expiresAt
            it[SessionsTable.userAgent] = userAgent?.take(256)
            it[SessionsTable.ip] = ip?.take(64)
        }
    }

    fun find(sessionId: String): SessionRow? = SessionsTable.selectAll()
        .where { SessionsTable.id eq sessionId }
        .map {
            SessionRow(
                it[SessionsTable.id],
                it[SessionsTable.userId],
                it[SessionsTable.expiresAt],
                it[SessionsTable.revokedAt],
            )
        }.firstOrNull()

    fun revoke(sessionId: String) {
        SessionsTable.update({ SessionsTable.id eq sessionId }) {
            it[revokedAt] = OffsetDateTime.now()
        }
    }

    fun countActiveGuests(): Long {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val guestIds = UsersTable.selectAll()
            .where { UsersTable.kind eq "GUEST" }
            .map { it[UsersTable.id] }
        if (guestIds.isEmpty()) return 0
        return SessionsTable.selectAll()
            .where {
                (SessionsTable.revokedAt.isNull()) and
                    (SessionsTable.expiresAt greaterEq now) and
                    (SessionsTable.userId inList guestIds)
            }.count()
    }

    fun revokeAllForUser(userId: String) {
        SessionsTable.update({ SessionsTable.userId eq userId }) {
            it[revokedAt] = OffsetDateTime.now()
        }
    }

    fun purgeExpired() {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        SessionsTable.deleteWhere { SessionsTable.expiresAt less now }
    }
}

object MailboxRepo {
    fun insert(
        ownerId: String,
        provider: String,
        address: String,
        providerTokenEnc: String?,
        providerMetaEnc: String?,
        label: String?,
    ): MailboxRow {
        val id = UUID.randomUUID().toString()
        MailboxesTable.insert {
            it[MailboxesTable.id] = id
            it[MailboxesTable.ownerId] = ownerId
            it[MailboxesTable.provider] = provider
            it[MailboxesTable.address] = address
            it[MailboxesTable.providerTokenEnc] = providerTokenEnc
            it[MailboxesTable.providerMetaEnc] = providerMetaEnc
            it[MailboxesTable.label] = label
        }
        return MailboxRow(
            id, ownerId, provider, address, providerTokenEnc,
            providerMetaEnc, OffsetDateTime.now(), null, false, label,
        )
    }

    fun findById(id: String): MailboxRow? = MailboxesTable.selectAll()
        .where { MailboxesTable.id eq id }
        .map { r ->
            MailboxRow(
                r[MailboxesTable.id], r[MailboxesTable.ownerId], r[MailboxesTable.provider],
                r[MailboxesTable.address], r[MailboxesTable.providerTokenEnc],
                r[MailboxesTable.providerMetaEnc], r[MailboxesTable.createdAt],
                r[MailboxesTable.lastSyncAt], r[MailboxesTable.remoteDeleted],
                r[MailboxesTable.label],
            )
        }.firstOrNull()

    fun listForOwner(ownerId: String): List<MailboxRow> = MailboxesTable.selectAll()
        .where { MailboxesTable.ownerId eq ownerId }
        .orderBy(MailboxesTable.createdAt to org.jetbrains.exposed.sql.SortOrder.DESC)
        .map { r ->
            MailboxRow(
                r[MailboxesTable.id], r[MailboxesTable.ownerId], r[MailboxesTable.provider],
                r[MailboxesTable.address], r[MailboxesTable.providerTokenEnc],
                r[MailboxesTable.providerMetaEnc], r[MailboxesTable.createdAt],
                r[MailboxesTable.lastSyncAt], r[MailboxesTable.remoteDeleted],
                r[MailboxesTable.label],
            )
        }

    fun countForOwnerSince(ownerId: String, since: OffsetDateTime): Long = MailboxesTable.selectAll()
        .where { (MailboxesTable.ownerId eq ownerId) and (MailboxesTable.createdAt greaterEq since) }
        .count()

    fun markSynced(id: String) {
        MailboxesTable.update({ MailboxesTable.id eq id }) {
            it[lastSyncAt] = OffsetDateTime.now()
        }
    }

    fun markRemoteDeleted(id: String) {
        MailboxesTable.update({ MailboxesTable.id eq id }) {
            it[remoteDeleted] = true
        }
    }

    fun deleteLocal(id: String) {
        MailboxesTable.deleteWhere { MailboxesTable.id eq id }
    }

    fun countAll(): Long = MailboxesTable.selectAll().count()

    fun countByProvider(): Map<String, Long> = MailboxesTable.selectAll()
        .groupBy { it[MailboxesTable.provider] }
        .mapValues { (_, rows) -> rows.size.toLong() }
}

object MessageCacheRepo {
    data class CachedMessage(
        val providerMessageId: String,
        val sender: String?,
        val senderName: String?,
        val recipient: String?,
        val subject: String?,
        val receivedAt: OffsetDateTime?,
        val seen: Boolean,
        val hasOtp: Boolean,
        val otpCode: String?,
        val otpSource: String?,
        val hasVerificationLink: Boolean,
    )

    fun upsert(mailboxId: String, msg: CachedMessage) {
        val existing = MessageCacheTable.selectAll()
            .where {
                (MessageCacheTable.mailboxId eq mailboxId) and
                    (MessageCacheTable.providerMessageId eq msg.providerMessageId)
            }.firstOrNull()
        if (existing == null) {
            MessageCacheTable.insert {
                it[MessageCacheTable.id] = UUID.randomUUID().toString()
                it[MessageCacheTable.mailboxId] = mailboxId
                it[providerMessageId] = msg.providerMessageId
                it[sender] = msg.sender
                it[senderName] = msg.senderName
                it[recipient] = msg.recipient
                it[subject] = msg.subject
                it[receivedAt] = msg.receivedAt
                it[seen] = msg.seen
                it[hasOtp] = msg.hasOtp
                it[otpCode] = msg.otpCode
                it[otpSource] = msg.otpSource
                it[hasVerificationLink] = msg.hasVerificationLink
            }
        } else {
            MessageCacheTable.update({
                (MessageCacheTable.mailboxId eq mailboxId) and
                    (MessageCacheTable.providerMessageId eq msg.providerMessageId)
            }) {
                it[sender] = msg.sender
                it[senderName] = msg.senderName
                it[subject] = msg.subject
                it[receivedAt] = msg.receivedAt
                it[seen] = msg.seen
                it[hasOtp] = msg.hasOtp
                it[otpCode] = msg.otpCode
                it[otpSource] = msg.otpSource
                it[hasVerificationLink] = msg.hasVerificationLink
                it[cachedAt] = OffsetDateTime.now()
            }
        }
    }

    fun listForMailbox(mailboxId: String): List<CachedMessage> = MessageCacheTable.selectAll()
        .where { MessageCacheTable.mailboxId eq mailboxId }
        .orderBy(MessageCacheTable.receivedAt to org.jetbrains.exposed.sql.SortOrder.DESC)
        .map { r ->
            CachedMessage(
                r[MessageCacheTable.providerMessageId],
                r[MessageCacheTable.sender],
                r[MessageCacheTable.senderName],
                r[MessageCacheTable.recipient],
                r[MessageCacheTable.subject],
                r[MessageCacheTable.receivedAt],
                r[MessageCacheTable.seen],
                r[MessageCacheTable.hasOtp],
                r[MessageCacheTable.otpCode],
                r[MessageCacheTable.otpSource],
                r[MessageCacheTable.hasVerificationLink],
            )
        }

    fun deleteForMailbox(mailboxId: String) {
        MessageCacheTable.deleteWhere { MessageCacheTable.mailboxId eq mailboxId }
    }
}

object UsageLogRepo {
    fun log(
        provider: String,
        action: String,
        ok: Boolean,
        userId: String? = null,
        statusCode: Int? = null,
        latencyMs: Long? = null,
        errorCode: String? = null,
    ) {
        UsageLogsTable.insert {
            it[UsageLogsTable.id] = UUID.randomUUID().toString()
            it[UsageLogsTable.userId] = userId
            it[UsageLogsTable.provider] = provider
            it[UsageLogsTable.action] = action
            it[UsageLogsTable.ok] = ok
            it[UsageLogsTable.statusCode] = statusCode
            it[UsageLogsTable.latencyMs] = latencyMs
            it[UsageLogsTable.errorCode] = errorCode?.take(64)
        }
    }

    fun countSince(since: OffsetDateTime): Long = UsageLogsTable.selectAll()
        .where { UsageLogsTable.createdAt greaterEq since }
        .count()

    fun countByProviderSince(since: OffsetDateTime): Map<String, Long> =
        UsageLogsTable.selectAll()
            .where { UsageLogsTable.createdAt greaterEq since }
            .groupBy { it[UsageLogsTable.provider] }
            .mapValues { (_, rows) -> rows.size.toLong() }

    fun countFailuresSince(since: OffsetDateTime): Long = UsageLogsTable.selectAll()
        .where { (UsageLogsTable.createdAt greaterEq since) and (UsageLogsTable.ok eq false) }
        .count()

    fun countRateLimitsSince(since: OffsetDateTime): Long {
        val codes = listOf("rate_limit_exceeded", "monthly_quota_exceeded", "rate_limited", "DailyCapReached")
        return UsageLogsTable.selectAll()
            .where {
                (UsageLogsTable.createdAt greaterEq since) and
                    (UsageLogsTable.errorCode inList codes)
            }.count()
    }

    fun dailyUsage(days: Int): List<DailyUsage> {
        val since = OffsetDateTime.now(ZoneOffset.UTC).minusDays(days.toLong())
        return UsageLogsTable.selectAll()
            .where { UsageLogsTable.createdAt greaterEq since }
            .groupBy { it[UsageLogsTable.createdAt].toLocalDate() }
            .map { (date, rows) ->
                DailyUsage(
                    date = date.toString(),
                    total = rows.size.toLong(),
                    failed = rows.count { !it[UsageLogsTable.ok] }.toLong(),
                    byProvider = rows.groupingBy { it[UsageLogsTable.provider] }.eachCount()
                        .mapValues { it.value.toLong() },
                )
            }.sortedBy { it.date }
    }

    data class DailyUsage(
        val date: String,
        val total: Long,
        val failed: Long,
        val byProvider: Map<String, Long>,
    )

    fun recentErrors(limit: Int): List<RecentError> = UsageLogsTable.selectAll()
        .where { UsageLogsTable.ok eq false }
        .orderBy(UsageLogsTable.createdAt to org.jetbrains.exposed.sql.SortOrder.DESC)
        .limit(limit)
        .map {
            RecentError(
                provider = it[UsageLogsTable.provider],
                action = it[UsageLogsTable.action],
                errorCode = it[UsageLogsTable.errorCode],
                statusCode = it[UsageLogsTable.statusCode],
                at = it[UsageLogsTable.createdAt].toString(),
            )
        }

    data class RecentError(
        val provider: String,
        val action: String,
        val errorCode: String?,
        val statusCode: Int?,
        val at: String,
    )
}

object AuditLogRepo {
    fun log(adminId: String?, action: String, target: String?, details: String?, ip: String?) {
        AuditLogsTable.insert {
            it[AuditLogsTable.id] = UUID.randomUUID().toString()
            it[AuditLogsTable.adminId] = adminId
            it[AuditLogsTable.action] = action
            it[AuditLogsTable.target] = target
            it[AuditLogsTable.details] = details?.take(4000)
            it[AuditLogsTable.ip] = ip?.take(64)
        }
    }

    fun recent(limit: Int): List<AuditEntry> = AuditLogsTable.selectAll()
        .orderBy(AuditLogsTable.createdAt to org.jetbrains.exposed.sql.SortOrder.DESC)
        .limit(limit)
        .map {
            AuditEntry(
                id = it[AuditLogsTable.id],
                adminId = it[AuditLogsTable.adminId],
                action = it[AuditLogsTable.action],
                target = it[AuditLogsTable.target],
                details = it[AuditLogsTable.details],
                at = it[AuditLogsTable.createdAt].toString(),
            )
        }

    data class AuditEntry(
        val id: String,
        val adminId: String?,
        val action: String,
        val target: String?,
        val details: String?,
        val at: String,
    )
}
