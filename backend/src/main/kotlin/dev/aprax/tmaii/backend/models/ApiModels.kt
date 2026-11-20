package dev.aprax.tmaii.backend.models

import dev.aprax.tmaii.backend.providers.EmailAttachment
import dev.aprax.tmaii.backend.providers.EmailMessage
import dev.aprax.tmaii.backend.providers.EmailOtp
import dev.aprax.tmaii.backend.providers.VerificationLink
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ApiErrorBody(
    val success: Boolean = false,
    val error: String,
    val message: String,
    val retryAfter: Int? = null,
    val details: Map<String, String>? = null,
)

@Serializable
data class ApiOk(val success: Boolean = true, val message: String? = null)

@Serializable
data class ApiMailbox(
    val id: String,
    val provider: String,
    val address: String,
    val label: String? = null,
    val createdAt: String,
    val lastSyncAt: String? = null,
    val remoteDeleted: Boolean = false,
    val unreadCount: Int = 0,
    val reuseUrl: String? = null,
)

@Serializable
data class ApiMessage(
    val id: String,
    val from: String,
    val fromName: String? = null,
    val to: String? = null,
    val subject: String? = null,
    val receivedAt: String? = null,
    val seen: Boolean = false,
    val hasAttachment: Boolean = false,
    val otp: Map<String, String>? = null,
    val verificationLinks: List<String> = emptyList(),
)

@Serializable
data class ApiMessageDetail(
    val message: EmailMessage,
    val html: String? = null,
    val text: String? = null,
    val attachments: List<EmailAttachment> = emptyList(),
    val bodyAvailable: Boolean = true,
)

@Serializable
data class CreateMailboxBody(
    val provider: String,
    val username: String? = null,
    val domain: String? = null,
    val random: Boolean = true,
    val label: String? = null,
)

@Serializable
data class AuthResponse(
    val token: String,
    val role: String,
    val userId: String,
    val email: String? = null,
    val expiresAt: Long,
)

@Serializable
data class RegisterBody(
    val email: String,
    val password: String,
    /** Optional guest JWT whose mailboxes transfer to the new account. */
    val guestToken: String? = null,
)

@Serializable
data class LoginBody(
    val email: String,
    val password: String,
)

@Serializable
data class MeResponse(
    val userId: String,
    val role: String,
    val email: String? = null,
    val createdAt: String? = null,
)

@Serializable
data class OtpResponse(
    val otp: String? = null,
    val source: String? = null,
    val score: Double? = null,
    val messageId: String? = null,
    val subject: String? = null,
    val detected: Boolean = false,
)

@Serializable
data class HealthResponse(
    val status: String,
    val version: String,
    val database: String,
    val environment: String,
    val providers: List<ProviderHealthSummary> = emptyList(),
    val uptimeSeconds: Long,
)

@Serializable
data class ProviderHealthSummary(
    val id: String,
    val configured: Boolean,
    val enabled: Boolean,
    val reachable: Boolean? = null,
    val message: String? = null,
)

// ---------- Admin DTOs ----------

@Serializable
data class AdminLoginBody(val email: String, val password: String)

@Serializable
data class AdminStatsResponse(
    val totalUsers: Long,
    val totalAccounts: Long,
    val activeGuestSessions: Long,
    val totalMailboxes: Long,
    val mailboxesByProvider: Map<String, Long> = emptyMap(),
    val requestsLast24h: Long,
    val failuresLast24h: Long,
    val rateLimitEventsLast24h: Long,
    val databaseOk: Boolean,
    val backendStatus: String,
    val recentErrors: List<ErrorEntry> = emptyList(),
)

@Serializable
data class ErrorEntry(
    val provider: String,
    val action: String,
    val errorCode: String? = null,
    val statusCode: Int? = null,
    val at: String,
)

@Serializable
data class AdminProviderResponse(
    val id: String,
    val displayName: String,
    val enabled: Boolean,
    val configured: Boolean,
    val keyConfigured: Boolean,
    val keyPreview: String? = null,
    val reachable: Boolean? = null,
    val healthMessage: String? = null,
    val capabilities: dev.aprax.tmaii.backend.providers.ProviderCapabilities,
    val quota: dev.aprax.tmaii.backend.providers.ProviderQuota? = null,
    val documentationUrl: String,
)

@Serializable
data class AdminProviderAction(val action: String) // enable | disable | test

@Serializable
data class AdminUsageResponse(
    val days: List<DayUsage> = emptyList(),
    val byProvider: Map<String, Long> = emptyMap(),
    val totalRequests: Long = 0,
    val totalFailures: Long = 0,
    val rateLimitEvents: Long = 0,
)

@Serializable
data class DayUsage(
    val date: String,
    val total: Long,
    val failed: Long,
    val byProvider: Map<String, Long> = emptyMap(),
)

@Serializable
data class AdminUserEntry(
    val id: String,
    val email: String? = null,
    val kind: String,
    val status: String,
    val createdAt: String,
    val mailboxCount: Long = 0,
)

@Serializable
data class AdminAuditEntry(
    val id: String,
    val adminId: String? = null,
    val action: String,
    val target: String? = null,
    val details: String? = null,
    val at: String,
)
