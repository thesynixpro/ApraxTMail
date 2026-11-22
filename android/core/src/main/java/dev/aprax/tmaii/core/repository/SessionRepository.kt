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
    val emailFlow: Flow<String?> = settings.email
    val baseUrlFlow: Flow<String> = settings.baseUrl

    suspend fun currentToken(): String? = settings.authToken.first()

    suspend fun configureBackend(baseUrl: String) {
        settings.setBaseUrl(baseUrl)
        client.baseUrl = baseUrl.trimEnd('/')
    }

    suspend fun syncClientFromPrefs() {
        val stored = settings.baseUrl.first()
        client.baseUrl = if (stored.isBlank()) {
            dev.aprax.tmaii.core.BuildConfig.DEFAULT_BASE_URL.trimEnd('/')
        } else {
            stored.trimEnd('/')
        }
        client.authToken = settings.authToken.first()
    }

    suspend fun register(email: String, password: String): AuthResponse {
        syncClientFromPrefs()
        val resp = api.register(RegisterBody(email, password))
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
        settings.clearSession()
        client.authToken = null
    }

    suspend fun me(): MeResponse {
        syncClientFromPrefs()
        return api.me()
    }

    suspend fun health() = api.health()

    suspend fun ensureBackendConfigured() {
        syncClientFromPrefs()
        if (settings.baseUrl.first().isBlank()) {
            configureBackend(dev.aprax.tmaii.core.BuildConfig.DEFAULT_BASE_URL)
        }
    }

    private suspend fun persist(resp: AuthResponse) {
        settings.setSession(resp.token, resp.user.id, resp.user.email)
        client.authToken = resp.token
    }
}
