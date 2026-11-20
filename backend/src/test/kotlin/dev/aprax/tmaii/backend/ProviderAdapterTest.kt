package dev.aprax.tmaii.backend

import dev.aprax.tmaii.backend.providers.CreateMailboxRequest
import dev.aprax.tmaii.backend.providers.FreeCustomEmailProvider
import dev.aprax.tmaii.backend.providers.ProviderHttpClient
import dev.aprax.tmaii.backend.providers.ProviderRegistry
import dev.aprax.tmaii.backend.providers.TmailorProvider
import dev.aprax.tmaii.backend.utils.ProviderErrorException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProviderAdapterTest {

    private fun mockClient(
        handler: suspend io.ktor.client.engine.mock.MockRequestHandleScope.(io.ktor.client.request.HttpRequestData) -> io.ktor.client.request.HttpResponseData,
    ): HttpClient =
        HttpClient(MockEngine(handler)) {
            expectSuccess = false
            install(ContentNegotiation) { json(ProviderRegistry.defaultJson) }
        }

    private fun providerHttp(client: HttpClient) = ProviderHttpClient(client, ProviderRegistry.defaultJson)

    @Test
    fun freecustomCreateMailboxParsesResponse() = runTest {
        val engine = MockEngine { request ->
            assertTrue(request.url.encodedPath.endsWith("/v1/inboxes"))
            assertEquals("Bearer test-key", request.headers[HttpHeaders.Authorization])
            respond(
                """{"success":true,"data":{"inbox":"abc123@ditapi.info","registered_at":"2026-01-01T00:00:00.000Z"}}""",
                HttpStatusCode.Created,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClient(engine) {
            expectSuccess = false
            install(ContentNegotiation) { json(ProviderRegistry.defaultJson) }
        }
        val provider = FreeCustomEmailProvider("test-key", true, providerHttp(client))
        val account = provider.createMailbox(CreateMailboxRequest(username = "abc123", domain = "ditapi.info", random = false))
        assertEquals("abc123@ditapi.info", account.address)
        assertEquals("freecustom", account.providerId)
        assertTrue(provider.isConfigured())
        assertTrue(provider.capabilities.deleteMailbox)
        assertFalse(provider.capabilities.reuseUrl)
    }

    @Test
    fun freecustomListMessagesParsesOtpDetectedPlaceholder() = runTest {
        val engine = MockEngine {
            respond(
                """
                {"success":true,"data":[
                  {"id":"m1","from":"a@b.com","to":"x@ditapi.info","subject":"Hi","date":"2026-01-01T00:00:00Z","hasAttachment":false,"otp":"__DETECTED__","verificationLink":"https://x.com/v"}
                ],"count":1}
                """.trimIndent(),
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClient(engine) {
            expectSuccess = false
            install(ContentNegotiation) { json(ProviderRegistry.defaultJson) }
        }
        val provider = FreeCustomEmailProvider("test-key", true, providerHttp(client))
        val messages = provider.listMessages(
            dev.aprax.tmaii.backend.providers.EmailAccount("freecustom", "x@ditapi.info"),
        )
        assertEquals(1, messages.size)
        // "__DETECTED__" must NOT be shown as a real OTP
        assertEquals(null, messages[0].otp)
        assertEquals(1, messages[0].verificationLinks.size)
    }

    @Test
    fun freecustomRateLimitMapsToProviderError() = runTest {
        val engine = MockEngine {
            respond(
                """{"success":false,"error":"rate_limit_exceeded","message":"Slow down"}""",
                HttpStatusCode.TooManyRequests,
                headersOf(
                    HttpHeaders.ContentType to listOf("application/json"),
                    "Retry-After" to listOf("2"),
                ),
            )
        }
        val client = HttpClient(engine) {
            expectSuccess = false
            install(ContentNegotiation) { json(ProviderRegistry.defaultJson) }
        }
        val provider = FreeCustomEmailProvider("test-key", true, providerHttp(client))
        val e = assertFailsWith<ProviderErrorException> {
            provider.listMessages(dev.aprax.tmaii.backend.providers.EmailAccount("freecustom", "x@ditapi.info"))
        }
        assertEquals("rate_limit_exceeded", e.code)
    }

    @Test
    fun freecustomUnconfiguredReportsUnhealthy() = runTest {
        val provider = FreeCustomEmailProvider(null, true, providerHttp(mockClient { respond("", HttpStatusCode.OK) }))
        assertFalse(provider.isConfigured())
        val health = provider.healthCheck()
        assertFalse(health.reachable)
        assertFailsWith<dev.aprax.tmaii.backend.utils.NotSupportedOperationException> {
            provider.listDomains()
        }
    }

    @Test
    fun tmailorCreateMailboxSendsIdempotencyKeyAndParsesToken() = runTest {
        val engine = MockEngine { request ->
            assertEquals("POST", request.method.value)
            assertTrue(request.url.encodedPath.endsWith("/v1/mailboxes"))
            assertNotNullHeader(request.headers[ "Idempotency-Key" ])
            assertEquals("Bearer tmk_test", request.headers[HttpHeaders.Authorization])
            respond(
                """{"email":"user@shared.dev","domain":"shared.dev","created_at":"2026-01-01T00:00:00Z","message_count":0,"token":"tok_abc","web":{"accesstoken":"tok_abc","reuse_url":"https://web.tmailor.com/r/xyz"}}""",
                HttpStatusCode.Created,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClient(engine) {
            expectSuccess = false
            install(ContentNegotiation) { json(ProviderRegistry.defaultJson) }
        }
        val provider = TmailorProvider("tmk_test", true, providerHttp(client))
        val account = provider.createMailbox(CreateMailboxRequest(random = true))
        assertEquals("user@shared.dev", account.address)
        assertEquals("tok_abc", account.providerToken)
        assertEquals("https://web.tmailor.com/r/xyz", account.reuseUrl)
        assertTrue(provider.capabilities.idempotency)
        assertTrue(provider.capabilities.reuseUrl)
        assertEquals(24, provider.capabilities.messageRetentionHours)
    }

    @Test
    fun tmailorIpAllowlistErrorSurfaces() = runTest {
        val engine = MockEngine {
            respond(
                """{"error":{"code":"IpNotAllowed","message":"IP not allowlisted","description":"Add your server IP","request_id":"r1"}}""",
                HttpStatusCode.Forbidden,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClient(engine) {
            expectSuccess = false
            install(ContentNegotiation) { json(ProviderRegistry.defaultJson) }
        }
        val provider = TmailorProvider("tmk_test", true, providerHttp(client))
        val e = assertFailsWith<ProviderErrorException> {
            provider.createMailbox(CreateMailboxRequest(random = true))
        }
        assertEquals("ip_not_allowed", e.code)
    }

    @Test
    fun tmailorMessageReadParsesOtpAndLinks() = runTest {
        val engine = MockEngine {
            respond(
                """
                {"id":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","from":{"name":"Site","address":"no-reply@site.com"},"to":"a@b.dev","subject":"Your login code","received_at":"2026-01-01T00:00:00Z","expires_at":"2026-01-02T00:00:00Z","seen":false,
                 "text":"Code is 654321","html":null,"body_available":true,"otp":"654321","codes":["654321"],"links":["https://site.com/verify?u=1"]}
                """.trimIndent(),
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClient(engine) {
            expectSuccess = false
            install(ContentNegotiation) { json(ProviderRegistry.defaultJson) }
        }
        val provider = TmailorProvider("tmk_test", true, providerHttp(client))
        val detail = provider.getMessage(
            dev.aprax.tmaii.backend.providers.EmailAccount("tmailor", "a@b.dev", providerToken = "tok"),
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
        )
        assertEquals("654321", detail.message.otp?.code)
        assertEquals(dev.aprax.tmaii.backend.providers.OtpSource.PROVIDER, detail.message.otp?.source)
        assertTrue(detail.message.verificationLinks.isNotEmpty())
        assertEquals(0, detail.attachments.size) // Tmailor: no attachments
    }

    private fun assertNotNullHeader(v: String?) {
        assertTrue(v != null && v.length in 1..128, "Idempotency-Key missing or invalid")
    }
}
