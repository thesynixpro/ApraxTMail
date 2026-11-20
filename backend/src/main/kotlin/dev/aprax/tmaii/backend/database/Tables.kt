package dev.aprax.tmaii.backend.database

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone
import java.time.OffsetDateTime

object UsersTable : Table("users") {
    val id = varchar("id", 36)
    val email = varchar("email", 254).nullable()
    val passwordHash = varchar("password_hash", 100).nullable()
    val kind = varchar("kind", 16) // GUEST | ACCOUNT | ADMIN
    val status = varchar("status", 16).default("active") // active | suspended
    val createdAt = timestampWithTimeZone("created_at").clientDefault { OffsetDateTime.now() }
    val updatedAt = timestampWithTimeZone("updated_at").clientDefault { OffsetDateTime.now() }
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex(email)
    }
}

object SessionsTable : Table("sessions") {
    val id = varchar("id", 36) // jti
    val userId = varchar("user_id", 36).references(UsersTable.id, onDelete = ReferenceOption.CASCADE)
    val expiresAt = timestampWithTimeZone("expires_at")
    val revokedAt = timestampWithTimeZone("revoked_at").nullable()
    val createdAt = timestampWithTimeZone("created_at").clientDefault { OffsetDateTime.now() }
    val userAgent = varchar("user_agent", 256).nullable()
    val ip = varchar("ip", 64).nullable()
    override val primaryKey = PrimaryKey(id)

    init {
        index("idx_sessions_user", false, userId)
    }
}

object MailboxesTable : Table("mailboxes") {
    val id = varchar("id", 36)
    val ownerId = varchar("owner_id", 36).references(UsersTable.id, onDelete = ReferenceOption.CASCADE)
    val provider = varchar("provider", 32)
    val address = varchar("address", 254)
    // Encrypted provider mailbox token (Tmailor) — never returned to clients
    val providerTokenEnc = text("provider_token_enc").nullable()
    // Extra provider state (FCE needs no token; Tmailor web.reuse_url etc.), encrypted JSON
    val providerMetaEnc = text("provider_meta_enc").nullable()
    val createdAt = timestampWithTimeZone("created_at").clientDefault { OffsetDateTime.now() }
    val lastSyncAt = timestampWithTimeZone("last_sync_at").nullable()
    val remoteDeleted = bool("remote_deleted").default(false)
    val label = varchar("label", 64).nullable()
    override val primaryKey = PrimaryKey(id)

    init {
        index("idx_mailboxes_owner", false, ownerId)
        index("idx_mailboxes_provider_addr", false, provider, address)
        uniqueIndex("idx_mailboxes_owner_addr", ownerId, provider, address)
    }
}

object MessageCacheTable : Table("message_cache") {
    val id = varchar("id", 36)
    val mailboxId = varchar("mailbox_id", 36).references(MailboxesTable.id, onDelete = ReferenceOption.CASCADE)
    val providerMessageId = varchar("provider_message_id", 128)
    val sender = varchar("sender", 254).nullable()
    val senderName = varchar("sender_name", 254).nullable()
    val recipient = varchar("recipient", 254).nullable()
    val subject = varchar("subject", 512).nullable()
    val receivedAt = timestampWithTimeZone("received_at").nullable()
    val seen = bool("seen").default(false)
    val hasOtp = bool("has_otp").default(false)
    val otpCode = varchar("otp_code", 32).nullable()
    val otpSource = varchar("otp_source", 16).nullable() // PROVIDER | LOCAL | LINK
    val hasVerificationLink = bool("has_verification_link").default(false)
    val cachedAt = timestampWithTimeZone("cached_at").clientDefault { OffsetDateTime.now() }
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("idx_msg_unique", mailboxId, providerMessageId)
        index("idx_msg_mailbox", false, mailboxId)
    }
}

object UsageLogsTable : Table("usage_logs") {
    val id = varchar("id", 36)
    val userId = varchar("user_id", 36).nullable()
    val provider = varchar("provider", 32)
    val action = varchar("action", 64)
    val statusCode = integer("status_code").nullable()
    val ok = bool("ok")
    val latencyMs = long("latency_ms").nullable()
    val errorCode = varchar("error_code", 64).nullable()
    val createdAt = timestampWithTimeZone("created_at").clientDefault { OffsetDateTime.now() }
    override val primaryKey = PrimaryKey(id)

    init {
        index("idx_usage_created", false, createdAt)
        index("idx_usage_provider", false, provider)
    }
}

object AuditLogsTable : Table("audit_logs") {
    val id = varchar("id", 36)
    val adminId = varchar("admin_id", 36).nullable()
    val action = varchar("action", 128)
    val target = varchar("target", 128).nullable()
    val details = text("details").nullable()
    val ip = varchar("ip", 64).nullable()
    val createdAt = timestampWithTimeZone("created_at").clientDefault { OffsetDateTime.now() }
    override val primaryKey = PrimaryKey(id)

    init {
        index("idx_audit_created", false, createdAt)
    }
}

object RateLimitBucketsTable : Table("rate_limit_buckets") {
    val key = varchar("key", 128)
    val tokens = double("tokens")
    val updatedAt = timestampWithTimeZone("updated_at").clientDefault { OffsetDateTime.now() }
    override val primaryKey = PrimaryKey(key)
}
