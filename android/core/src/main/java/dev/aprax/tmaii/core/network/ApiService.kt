package dev.aprax.tmaii.core.network

import dev.aprax.tmaii.core.model.AddressDto
import dev.aprax.tmaii.core.model.AddressResponse
import dev.aprax.tmaii.core.model.AddressesResponse
import dev.aprax.tmaii.core.model.AuthResponse
import dev.aprax.tmaii.core.model.DomainDto
import dev.aprax.tmaii.core.model.DomainResponse
import dev.aprax.tmaii.core.model.DomainVerifyResponse
import dev.aprax.tmaii.core.model.DomainsResponse
import dev.aprax.tmaii.core.model.HealthResponse
import dev.aprax.tmaii.core.model.MeResponse
import dev.aprax.tmaii.core.model.MessageDto
import dev.aprax.tmaii.core.model.MessageResponse
import dev.aprax.tmaii.core.model.MessagesResponse
import dev.aprax.tmaii.core.model.OkResponse
import dev.aprax.tmaii.core.model.ReadResponse
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class LoginBody(val email: String, val password: String)

@Serializable
data class RegisterBody(val email: String, val password: String)

@Serializable
data class AddDomainBody(val name: String)

@Serializable
data class CreateAddressBody(val localPart: String)

@Serializable
data class ReadBody(val read: Boolean)

@Singleton
class ApiService @Inject constructor(private val api: ApiClient) {

    suspend fun health(): HealthResponse = api.get("/health", token = null)

    suspend fun register(body: RegisterBody): AuthResponse = api.post("/api/auth/register", body, token = null)

    suspend fun login(body: LoginBody): AuthResponse = api.post("/api/auth/login", body, token = null)

    suspend fun me(): MeResponse = api.get("/api/auth/me")

    suspend fun listDomains(): List<DomainDto> =
        api.get<DomainsResponse>("/api/domains").domains

    suspend fun addDomain(name: String): DomainDto =
        api.post<DomainResponse>("/api/domains", AddDomainBody(name)).domain

    suspend fun getDomain(id: String): DomainDto =
        api.get<DomainResponse>("/api/domains/$id").domain

    suspend fun verifyDomain(id: String): DomainVerifyResponse =
        api.post("/api/domains/$id/verify")

    suspend fun deleteDomain(id: String): OkResponse = api.delete("/api/domains/$id")

    suspend fun listAddresses(domainId: String): List<AddressDto> =
        api.get<AddressesResponse>("/api/domains/$domainId/addresses").addresses

    suspend fun createAddress(domainId: String, localPart: String): AddressDto =
        api.post<AddressResponse>("/api/domains/$domainId/addresses", CreateAddressBody(localPart)).address

    suspend fun deleteAddress(id: String): OkResponse = api.delete("/api/addresses/$id")

    suspend fun listMessages(
        addressId: String? = null,
        domainId: String? = null,
        unread: Boolean = false,
        limit: Int = 50,
        offset: Int = 0,
    ): MessagesResponse {
        val query = buildList {
            addressId?.let { add("addressId=$it") }
            domainId?.let { add("domainId=$it") }
            if (unread) add("unread=true")
            add("limit=$limit")
            add("offset=$offset")
        }.joinToString("&")
        return api.get("/api/messages?$query")
    }

    suspend fun getMessage(id: String): MessageDto =
        api.get<MessageResponse>("/api/messages/$id").message

    suspend fun markRead(id: String, read: Boolean = true): ReadResponse =
        api.post("/api/messages/$id/read", ReadBody(read))

    suspend fun deleteMessage(id: String): OkResponse = api.delete("/api/messages/$id")
}
