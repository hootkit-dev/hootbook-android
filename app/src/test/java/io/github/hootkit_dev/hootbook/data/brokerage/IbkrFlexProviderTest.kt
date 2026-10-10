package io.github.hootkit_dev.hootbook.data.brokerage

import io.github.hootkit_dev.hootbook.domain.brokerage.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class IbkrFlexProviderTest {
    private val credentials = BrokerCredentials(mapOf("token" to "1234567890", "queryId" to "42"))
    private val reference = "<FlexStatementResponse><Status>Success</Status><ReferenceCode>999</ReferenceCode><url>https://invalid.example</url></FlexStatementResponse>"
    @Test fun executesTwoStepProtocolAndPacesEveryRequest() = runTest {
        var calls = 0; var paced = 0
        val engine = MockEngine { req ->
            calls++
            assertEquals("ndcdyn.interactivebrokers.com", req.url.host)
            assertEquals("https", req.url.protocol.name)
            assertEquals("1234567890", req.url.parameters["t"])
            assertEquals("3", req.url.parameters["v"])
            assertTrue(req.headers["User-Agent"]!!.startsWith("Hootbook-Android"))
            assertEquals(if (calls == 1) "42" else "999", req.url.parameters["q"])
            assertTrue(req.url.encodedPath.endsWith(if (calls == 1) "/SendRequest" else "/GetStatement"))
            respond(if (calls == 1) reference else flexReport())
        }
        val p = IbkrFlexProvider(engine, IbkrFlexParser()) { paced++ }
        try { assertEquals(1, p.fetchHoldings(credentials).size); assertEquals(2, calls); assertEquals(calls, paced) } finally { p.close() }
    }
    @Test fun pollsPendingReportThenSucceeds() = runTest {
        var calls = 0
        val p = IbkrFlexProvider(MockEngine { calls++; respond(when(calls) { 1 -> reference; 2,3 -> flexError("1019"); else -> flexReport() }) }, IbkrFlexParser()) {}
        try { p.fetchHoldings(credentials); assertEquals(4, calls) } finally { p.close() }
    }
    @Test fun limitsPendingPolls() = runTest {
        var calls = 0
        val p = IbkrFlexProvider(MockEngine { calls++; respond(if (calls == 1) reference else flexError("1019")) }, IbkrFlexParser()) {}
        try {
            try { p.fetchHoldings(credentials); fail() } catch (e: BrokerageException) { assertEquals(BrokerageError.REPORT_NOT_READY, e.error) }
            assertEquals(7, calls)
        } finally { p.close() }
    }
    @Test fun authFailureStopsBeforeFetchingAReport() = runTest {
        var calls = 0
        val p = IbkrFlexProvider(MockEngine { calls++; respond(flexError("1012")) }, IbkrFlexParser()) {}
        try {
            try { p.fetchHoldings(credentials); fail() } catch (e: BrokerageException) { assertEquals(BrokerageError.EXPIRED_CREDENTIALS, e.error) }
            assertEquals(1, calls)
        } finally { p.close() }
    }
    @Test fun rejectsInvalidCredentialsBeforeNetwork() = runTest {
        val p = IbkrFlexProvider(MockEngine { error("Unexpected network") }, IbkrFlexParser()) {}
        try {
            try { p.fetchHoldings(BrokerCredentials(mapOf("token" to "bad"))); fail() }
            catch (e: BrokerageException) { assertEquals(BrokerageError.INVALID_CREDENTIALS, e.error) }
        } finally { p.close() }
    }
    @Test fun rejectsRedirectAndDoesNotLeakTokenInErrors() = runTest {
        var calls = 0
        val p = IbkrFlexProvider(MockEngine { calls++; respond("", HttpStatusCode.Found, headersOf("Location", "https://invalid.example/")) }, IbkrFlexParser()) {}
        try {
            try { p.fetchHoldings(credentials); fail() } catch (e: BrokerageException) {
                assertEquals(BrokerageError.NETWORK, e.error); assertNull(e.cause)
                assertFalse(e.toString().contains("1234567890"))
            }
            assertEquals(1, calls)
        } finally { p.close() }
    }
    @Test fun rateLimitIsNotAutomaticallyRetried() = runTest {
        var calls = 0
        val p = IbkrFlexProvider(MockEngine { calls++; respond("", HttpStatusCode.TooManyRequests) }, IbkrFlexParser()) {}
        try {
            try { p.fetchHoldings(credentials); fail() } catch (e: BrokerageException) { assertEquals(BrokerageError.RATE_LIMITED, e.error) }
            assertEquals(1, calls)
        } finally { p.close() }
    }
    @Test fun preservesCancellation() = runTest {
        val p = IbkrFlexProvider(MockEngine { error("not called") }, IbkrFlexParser()) { throw CancellationException("cancelled") }
        try {
            try { p.fetchHoldings(credentials); fail() } catch (_: CancellationException) { }
        } finally { p.close() }
    }
    @Test fun boundsReportDownload() = runTest {
        val p = IbkrFlexProvider(MockEngine { respond("x".repeat(IbkrFlexParser.MAX_REPORT_BYTES + 1)) }, IbkrFlexParser()) {}
        try {
            try { p.fetchHoldings(credentials); fail() } catch (e: BrokerageException) { assertEquals(BrokerageError.INVALID_REPORT, e.error) }
        } finally { p.close() }
    }
}
