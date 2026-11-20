package dev.aprax.tmaii.backend.providers

import dev.aprax.tmaii.backend.utils.ProviderErrorException
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Shared HTTP plumbing for provider adapters: auth headers, JSON handling,
 * and normalization of upstream error bodies into [ProviderErrorException].
 */
class ProviderHttpClient(
    val client: HttpClient,
    val json: Json,
) {
    class ProviderResponse(
        val status: HttpStatusCode,
        val body: String,
        val latencyMs: Long,
        val retryAfterSeconds: Int?,
    )

    suspend fun call(
        providerId: String,
        method: HttpMethod,
        url: String,
        authHeader: String?,
        body: Any? = null,
        extraHeaders: Map<String, String> = emptyMap(),
        errorMapper: (status: HttpStatusCode, body: String) -> Pair<String, String>? = { _, _ -> null },
    ): ProviderResponse {
        val start = System.currentTimeMillis()
        val response: HttpResponse = try {
            client.request(url) {
                this.method = method
                authHeader?.let { header("Authorization", it) }
                extraHeaders.forEach { (k, v) -> header(k, v) }
                if (body != null) {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
            }
        } catch (e: ClientRequestException) {
            throw mapError(providerId, e.response.status, e.response.bodyAsText(), errorMapper, System.currentTimeMillis() - start)
        } catch (e: ServerResponseException) {
            throw ProviderErrorException(
                message = "Provider $providerId returned a server error",
                code = "provider_server_error",
                status = HttpStatusCode.BadGateway,
                providerId = providerId,
            )
        } catch (e: ResponseException) {
            throw mapError(providerId, e.response.status, e.response.bodyAsText(), errorMapper, System.currentTimeMillis() - start)
        } catch (e: Exception) {
            throw ProviderErrorException(
                message = "Could not reach provider $providerId: ${e.message ?: "network error"}",
                code = "provider_unreachable",
                status = HttpStatusCode.BadGateway,
                providerId = providerId,
            )
        }
        val latency = System.currentTimeMillis() - start
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw mapError(providerId, response.status, text, errorMapper, latency)
        }
        val retryAfter = response.headers["Retry-After"]?.toIntOrNull()
        return ProviderResponse(response.status, text, latency, retryAfter)
    }

    private fun mapError(
        providerId: String,
        status: HttpStatusCode,
        body: String,
        errorMapper: (HttpStatusCode, String) -> Pair<String, String>?,
        latencyMs: Long,
    ): ProviderErrorException {
        val mapped = errorMapper(status, body)
        val code = mapped?.first ?: extractGenericErrorCode(body) ?: "provider_error"
        val message = mapped?.second ?: "Provider $providerId request failed (HTTP ${status.value})"
        val retryAfter = extractRetryAfter(body)
        return ProviderErrorException(
            message = message,
            code = code,
            status = if (status == HttpStatusCode.TooManyRequests) status else HttpStatusCode.BadGateway,
            providerId = providerId,
            retryAfterSeconds = retryAfter,
        )
    }

    fun extractGenericErrorCode(body: String): String? = try {
        val element = json.parseToJsonElement(body)
        if (element is JsonObject) {
            val nested = element["error"]
            when {
                nested is JsonObject -> nested["code"]?.jsonPrimitive?.content
                    ?: nested["error"]?.jsonPrimitive?.content
                element["error"] != null -> element["error"]?.jsonPrimitive?.content
                element["code"] != null -> element["code"]?.jsonPrimitive?.content
                else -> element["message"]?.jsonPrimitive?.content?.let { "provider_error" }
            }
        } else null
    } catch (_: Exception) {
        null
    }

    fun extractRetryAfter(body: String): Int? = try {
        val element = json.parseToJsonElement(body)
        if (element is JsonObject) {
            val err = element["error"]
            val source = if (err is JsonObject) err else element
            source["retry_after"]?.jsonPrimitive?.content?.toIntOrNull()
        } else null
    } catch (_: Exception) {
        null
    }

    inline fun <reified T> parse(body: String): T = json.decodeFromString(body)
}
