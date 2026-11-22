package dev.aprax.tmaii.core.repository

import dev.aprax.tmaii.core.model.AddressDto
import dev.aprax.tmaii.core.model.DomainDto
import dev.aprax.tmaii.core.model.DomainVerifyResponse
import dev.aprax.tmaii.core.network.ApiService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DomainRepository @Inject constructor(
    private val api: ApiService,
    private val session: SessionRepository,
) {
    suspend fun listDomains(): List<DomainDto> {
        session.syncClientFromPrefs()
        return api.listDomains()
    }

    suspend fun addDomain(name: String): DomainDto {
        session.syncClientFromPrefs()
        return api.addDomain(name)
    }

    suspend fun verifyDomain(id: String): DomainVerifyResponse {
        session.syncClientFromPrefs()
        return api.verifyDomain(id)
    }

    suspend fun deleteDomain(id: String) {
        session.syncClientFromPrefs()
        api.deleteDomain(id)
    }

    suspend fun listAddresses(domainId: String): List<AddressDto> {
        session.syncClientFromPrefs()
        return api.listAddresses(domainId)
    }

    suspend fun createAddress(domainId: String, localPart: String): AddressDto {
        session.syncClientFromPrefs()
        return api.createAddress(domainId, localPart)
    }

    suspend fun deleteAddress(id: String) {
        session.syncClientFromPrefs()
        api.deleteAddress(id)
    }
}
