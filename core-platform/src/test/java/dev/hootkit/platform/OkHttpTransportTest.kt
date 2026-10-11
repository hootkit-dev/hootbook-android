package dev.hootkit.platform

import dev.hootkit.core.*
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class OkHttpTransportTest {
    private lateinit var server: MockWebServer
    private lateinit var transport: OkHttpTransport

    @Before fun setup() {
        val certificate = HeldCertificate.Builder().commonName("localhost").addSubjectAlternativeName("localhost").build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientTls = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        server = MockWebServer().apply { useHttps(serverTls.sslSocketFactory(), false); start() }
        transport = OkHttpTransport(OkHttpClient.Builder()
            .sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager).build())
    }

    @After fun teardown() { server.shutdown() }

    @Test fun errorStatusAndRepeatedHeadersAreNormalResponses() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503).addHeader("X-Repeat", "a").addHeader("X-Repeat", "b").setBody("error"))
        val response = transport.send(request().copy(headers = listOf(HttpHeader("X-Repeat", "one"), HttpHeader("X-Repeat", "two"))))
        assertEquals(503.toUShort(), response.status)
        assertEquals(listOf("a", "b"), response.headers.filter { it.name.equals("X-Repeat", true) }.map { it.value })
        assertEquals(listOf("one", "two"), server.takeRequest().headers.values("X-Repeat"))
        assertEquals("error", response.body.toString(Charsets.UTF_8))
    }

    @Test fun redirectCanBeDisabled() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", server.url("/next")))
        val response = transport.send(request().copy(followRedirects = false))
        assertEquals(302.toUShort(), response.status)
        assertEquals(1, server.requestCount)
    }

    @Test fun redirectCanBeFollowed() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", server.url("/next")))
        server.enqueue(MockResponse().setBody("done"))
        assertEquals("done", transport.send(request()).body.toString(Charsets.UTF_8))
        assertEquals(2, server.requestCount)
    }

    @Test fun chunkedResponseStopsAtLimit() = runBlocking {
        server.enqueue(MockResponse().setChunkedBody("0123456789", 2))
        try { transport.send(request().copy(maxResponseBytes = 5uL)); fail("oversized response") }
        catch (_: PlatformException.TooLarge) {}
    }

    @Test fun declaredOversizeStopsBeforeReading() = runBlocking {
        server.enqueue(MockResponse().setBody("0123456789"))
        try { transport.send(request().copy(maxResponseBytes = 5uL)); fail("oversized response") }
        catch (_: PlatformException.TooLarge) {}
    }

    @Test fun timeoutIncludesBodyAndDiagnosticsExcludeRequest() = runBlocking {
        server.enqueue(MockResponse().setBody("delayed").setBodyDelay(1, TimeUnit.SECONDS))
        try {
            transport.send(request().copy(timeoutMs = 100u, sensitive = true))
            fail("body timeout")
        } catch (failure: PlatformException.Network) {
            assertFalse(failure.message.orEmpty().contains("localhost"))
        }
    }

    @Test fun plaintextRequestIsRejected() = runBlocking {
        try { transport.send(request().copy(url = "http://localhost/")); fail("plaintext request") }
        catch (_: PlatformException.Other) {}
        assertEquals(0, server.requestCount)
    }

    private fun request() = HttpRequest(HttpMethod.GET, server.url("/").toString(), emptyList(), byteArrayOf(), 5000u)
}
