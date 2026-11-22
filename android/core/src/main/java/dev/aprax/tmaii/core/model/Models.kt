package dev.aprax.tmaii.core.model

import kotlinx.serialization.Serializable

@Serializable
data class ApiErrorBody(
    val error: String? = null,
    val message: String? = null,
)

@Serializable
data class UserDto(val id: String, val email: String)

@Serializable
data class AuthResponse(val token: String, val user: UserDto)

@Serializable
data class MeResponse(val user: UserDto)

@Serializable
data class HealthResponse(
    val status: String = "unknown",
    val service: String = "",
    val version: String = "",
)

@Serializable
data class DnsRecordDto(
    val type: String,
    val host: String,
    val value: String,
    val priority: Int? = null,
    val required: Boolean = true,
)

@Serializable
data class DomainDto(
    val id: String,
    val name: String,
    val status: String = "pending",
    val mxOk: Boolean = false,
    val txtOk: Boolean = false,
    val records: List<DnsRecordDto> = emptyList(),
    val lastCheckedAt: Long? = null,
    val createdAt: Long = 0,
) {
    val verified: Boolean get() = status == "verified"
}

@Serializable
data class DomainsResponse(val domains: List<DomainDto> = emptyList())

@Serializable
data class DomainResponse(val domain: DomainDto)

@Serializable
data class RecordCheckDto(
    val type: String,
    val name: String,
    val value: String,
    val ok: Boolean,
    val found: List<String> = emptyList(),
)

@Serializable
data class DnsCheckDto(
    val allOk: Boolean = false,
    val mxOk: Boolean = false,
    val txtOk: Boolean = false,
    val missing: List<String> = emptyList(),
    val checks: List<RecordCheckDto> = emptyList(),
)

@Serializable
data class DomainVerifyResponse(val domain: DomainDto, val check: DnsCheckDto)

@Serializable
data class AddressDto(
    val id: String,
    val domainId: String,
    val domain: String,
    val localPart: String,
    val address: String,
    val disabled: Boolean = false,
    val createdAt: Long = 0,
)

@Serializable
data class AddressesResponse(val addresses: List<AddressDto> = emptyList())

@Serializable
data class AddressResponse(val address: AddressDto)

@Serializable
data class AttachmentDto(
    val id: String,
    val filename: String,
    val contentType: String? = null,
    val size: Long = 0,
)

@Serializable
data class MessageDto(
    val id: String,
    val addressId: String? = null,
    val from: String? = null,
    val fromName: String? = null,
    val to: String? = null,
    val subject: String = "(no subject)",
    val otpCode: String? = null,
    val otpSource: String? = null,
    val read: Boolean = false,
    val hasAttachments: Boolean = false,
    val attachmentCount: Int = 0,
    val size: Long = 0,
    val receivedAt: Long = 0,
    val expiresAt: Long = 0,
    val text: String = "",
    val html: String = "",
    val attachments: List<AttachmentDto> = emptyList(),
)

@Serializable
data class MessagesResponse(
    val messages: List<MessageDto> = emptyList(),
    val total: Int = 0,
)

@Serializable
data class MessageResponse(val message: MessageDto)

@Serializable
data class ReadResponse(val ok: Boolean = true, val read: Boolean? = null)

@Serializable
data class OkResponse(val ok: Boolean = true)

enum class ThemeMode { LIGHT, DARK, SYSTEM, LIQUID_GLASS }

enum class Accent(val label: String) {
    VIOLET("Violet"),
    BLUE("Blue"),
    TEAL("Teal"),
    GREEN("Green"),
    AMBER("Amber"),
    ROSE("Rose"),
    ;

    companion object {
        fun fromName(name: String?): Accent = entries.firstOrNull { it.name == name } ?: VIOLET
    }
}
