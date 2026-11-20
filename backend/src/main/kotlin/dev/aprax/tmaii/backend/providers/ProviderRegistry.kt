package dev.aprax.tmaii.backend.providers

import dev.aprax.tmaii.backend.config.AppConfig
import dev.aprax.tmaii.backend.security.ProviderThrottler
import dev.aprax.tmaii.backend.utils.NotFoundException
import dev.aprax.tmaii.backend.utils.NotSupportedOperationException
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class ProviderRegistry(
    private val config: AppConfig,
    val json: Json = defaultJson,
) {
    val throttler = ProviderThrottler()

    val httpClient: HttpClient = HttpClient {
        expectSuccess = false
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 30_000
        }
        install(Auth) {
            bearer {
                // Tokens are attached per-request by adapters; this is a no-op placeholder
                // so keep-alive auth handling does not interfere with provider keys.
            }
        }
    }

    private val http = ProviderHttpClient(httpClient, json)

    val freecustom: FreeCustomEmailProvider = FreeCustomEmailProvider(
        apiKey = config.fceApiKey,
        enabled = config.fceEnabled,
        http = http,
    )

    val tmailor: TmailorProvider = TmailorProvider(
        apiKey = config.tmailorApiKey,
        enabled = config.tmailorEnabled,
        http = http,
    )

    private val all: Map<String, EmailProvider> = mapOf(
        freecustom.id to freecustom,
        tmailor.id to tmailor,
    )

    fun get(id: String): EmailProvider =
        all[id] ?: throw NotFoundException("Unknown provider: $id")

    fun list(): List<EmailProvider> = all.values.toList()

    fun minIntervalMs(providerId: String): Long = when (providerId) {
        // FreeCustom free plan: 1 request/second
        freecustom.id -> 1100L
        // Tmailor free plan: 30 requests/minute
        tmailor.id -> 2100L
        else -> 250L
    }

    suspend fun <T> execute(providerId: String, block: suspend (EmailProvider) -> T): T {
        val provider = get(providerId)
        if (!provider.isConfigured()) {
            throw NotSupportedOperationException("${provider.displayName} is not configured")
        }
        return throttler.withPermit(providerId, minIntervalMs(providerId)) {
            block(provider)
        }
    }

    fun info(provider: EmailProvider): ProviderInfo = ProviderInfo(
        id = provider.id,
        displayName = provider.displayName,
        enabled = when (provider.id) {
            freecustom.id -> config.fceEnabled
            tmailor.id -> config.tmailorEnabled
            else -> true
        },
        configured = provider.isConfigured(),
        capabilities = provider.capabilities,
        documentationUrl = provider.documentationUrl,
    )

    companion object {
        val defaultJson = Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = false
            explicitNulls = false
        }
    }
}
