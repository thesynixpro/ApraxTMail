package dev.aprax.tmaii.core.repository

import dev.aprax.tmaii.core.model.AuthResponse
import dev.aprax.tmaii.core.model.MeResponse
import dev.aprax.tmaii.core.network.ApiClient
import dev.aprax.tmaii.core.network.ApiService
import dev.aprax.tmaii.core.network.LoginBody
import dev.aprax.tmaii.core.network.RegisterBody
import dev.aprax.tmaii.core.prefs.SettingsDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepository @Inject constructor(
    private val api: ApiService,
    private val client: ApiClient,
    private val settings: SettingsDataStore,
) {
    val tokenFlow: Flow<String?> = settings.authToken
    val roleFlow: Flow<String?> = settings.authRole
    val emailFlow: Flow<String?> = settings.email
    val baseUrlFlow: Flow<String> = settings.baseUrl

    suspend fun currentToken(): String? = settings.authToken.first()

    suspend fun configureBackend(baseUrl: String) {
        settings.setBaseUrl(baseUrl)
        client.baseUrl = baseUrl.trimEnd('/')
    }

    suspend fun syncClientFromPrefs() {
        client.baseUrl = settings.baseUrl.first().trimEnd('/')
        client.authToken = settings.authToken.first()
    }

    suspend fun ensureSession(): String {
        syncClientFromPrefs()
        if (client.baseUrl.isBlank()) {
            client.baseUrl = dev.aprax.tmaii.core.BuildConfig.DEFAULT_BASE_URL.trimEnd('/')
            settings.setBaseUrl(client.baseUrl)
        }
        currentToken()?.let { return it }
        return createGuest()
    }

    suspend fun createGuest(): String {
        syncClientFromPrefs()
        val resp: AuthResponse = api.createGuest()
        persist(resp)
        return resp.token
    }

    suspend fun register(email: String, password: String): AuthResponse {
        syncClientFromPrefs()
        val guest = currentToken()
        val resp = api.register(RegisterBody(email, password, guest))
        persist(resp)
        return resp
    }

    suspend fun login(email: String, password: String): AuthResponse {
        syncClientFromPrefs()
        val resp = api.login(LoginBody(email, password))
        persist(resp)
        return resp
    }

    suspend fun logout() {
        syncClientFromPrefs()
        runCatching { api.logout() }
        settings.clearSession()
        client.authToken = null
    }

    suspend fun me(): MeResponse {
        syncClientFromPrefs()
        return api.me()
    }

    suspend fun health() = api.health()

    private suspend fun persist(resp: AuthResponse) {
        settings.setSession(resp.token, resp.role, resp.userId, resp.email)
        client.authToken = resp.token
    }
}
