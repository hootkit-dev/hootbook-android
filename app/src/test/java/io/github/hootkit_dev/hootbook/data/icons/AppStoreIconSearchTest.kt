package io.github.hootkit_dev.hootbook.data.icons

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class AppStoreIconSearchTest {
    private val requests = mutableListOf<HttpRequestData>()

    private fun search(status: HttpStatusCode, body: String) = AppStoreIconSearch(HttpClient(MockEngine { request ->
        requests += request
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
    }))

    @Test fun searchesChineseStoreAndKeepsAppsWithIcons() = runTest {
        val body = """{"resultCount":3,"results":[
            {"trackName":"瑞幸咖啡","sellerName":"Beijing Luckin Coffee Co","artworkUrl100":"https://a/100.jpg","artworkUrl512":"https://a/512.jpg"},
            {"trackName":"No icon","sellerName":"Someone"},
            {"trackName":"星巴克中国","sellerName":"Starbucks Coffee Company","artworkUrl512":"https://b/512.jpg"}]}"""
        val results = search(HttpStatusCode.OK, body).search(" 瑞幸咖啡 ")

        val url = requests.single().url
        assertEquals("itunes.apple.com", url.host)
        assertEquals("瑞幸咖啡", url.parameters["term"])
        assertEquals("cn", url.parameters["country"])
        assertEquals("software", url.parameters["entity"])
        assertEquals(listOf("瑞幸咖啡", "星巴克中国"), results.map { it.name })
        assertEquals("https://a/100.jpg", results[0].thumbnailUrl)
        // Without a small icon the large one is shown in the list
        assertEquals("https://b/512.jpg", results[1].thumbnailUrl)
    }

    @Test fun failuresAreReported() = runTest {
        try {
            search(HttpStatusCode.ServiceUnavailable, "").search("x")
            fail("expected an error")
        } catch (e: IconSearchException) {
            assertEquals("HTTP 503", e.message)
        }
        try {
            search(HttpStatusCode.OK, "<html>").search("x")
            fail("expected an error")
        } catch (_: IconSearchException) {
        }
    }
}
