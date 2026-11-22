package dev.aprax.tmaii.core.network

import dev.aprax.tmaii.core.model.ApiErrorBody
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

class ApiException(
    val code: String,
    override val message: String,
    val status: Int = 0,
    val retryAfter: Int? = null,
) : Exception(message)

/**
 * Thin wrapper over Ktor. Auth token is supplied per-call so guest↔account
 * switches never require rebuilding the client.
 */
@Singleton
class ApiClient @Inject constructor() {

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = false
        explicitNulls = false
    }

    @Volatile
    var baseUrl: String = ""

    @Volatile
    var authToken: String? = null

    val httpClient: HttpClient by lazy {
        HttpClient {
            expectSuccess = false
            install(HttpTimeout) {
                requestTimeoutMillis = 30_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 30_000
            }
            install(ContentNegotiation) { json(this@ApiClient.json) }
            defaultRequest {
                // baseUrl set per request; this is only a fallback
            }
        }
    }

    suspend inline fun <reified T> get(path: String, token: String? = authToken): T =
        execute(HttpMethod.Get, path, token) {}

    suspend inline fun <reified T> post(path: String, body: Any? = null, token: String? = authToken): T =
        execute(HttpMethod.Post, path, token) { setBody(body ?: emptyMap<String, String>()) }

    suspend inline fun <reified T> delete(path: String, token: String? = authToken): T =
        execute(HttpMethod.Delete, path, token) {}

    suspend inline fun <reified T> execute(
        method: HttpMethod,
        path: String,
        token: String?,
        crossinline configure: HttpRequestBuilder.() -> Unit,
    ): T {
        if (baseUrl.isBlank()) throw ApiException("not_configured", "Backend URL not configured")
        val url = baseUrl.trimEnd('/') + path
        val response = httpClient.request(url) {
            this.method = method
            token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
            contentType(ContentType.Application.Json)
            configure()
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            val parsed = runCatching { json.decodeFromString<ApiErrorBody>(text) }.getOrNull()
            throw ApiException(
                code = parsed?.error ?: "http_${response.status.value}",
                message = parsed?.message ?: "Request failed (${response.status.value})",
                status = response.status.value,
            )
        }
        if (text.isBlank()) {
            @Suppress("UNCHECKED_CAST")
            return (Unit as T)
        }
        return json.decodeFromString(text)
    }
}
