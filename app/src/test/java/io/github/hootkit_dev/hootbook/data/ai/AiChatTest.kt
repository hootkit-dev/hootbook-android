package io.github.hootkit_dev.hootbook.data.ai

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import java.time.LocalDateTime
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AiChatTest {
    private val requests = mutableListOf<HttpRequestData>()

    private fun chat(vararg replies: Pair<HttpStatusCode, String>): AiChat {
        var next = 0
        return AiChat(MockEngine { request ->
            requests += request
            val (status, body) = replies[next++]
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        })
    }

    private fun HttpRequestData.json(): JsonObject =
        Json.parseToJsonElement((body as TextContent).text).jsonObject

    private val tool = AiTool("find", "Find things", buildJsonObject { put("type", "object") })

    @Test
    fun `claude request carries key, version, tools and fallbacks on the first-party API`() = runTest {
        val chat = chat(HttpStatusCode.OK to """{"stop_reason":"end_turn","content":[{"type":"text","text":"Done"}]}""")
        val conversation = AiConversation(AiConfig(apiKey = "k"), "system", listOf(tool))
        chat.addUser(conversation, listOf(AiPart.Text("hi"), AiPart.Image("image/jpeg", "AAAA")))

        val reply = chat.send(conversation)

        assertEquals("Done", reply.text)
        val request = requests.single()
        assertEquals("https://api.anthropic.com/v1/messages", request.url.toString())
        assertEquals("k", request.headers["x-api-key"])
        assertEquals("2023-06-01", request.headers["anthropic-version"])
        assertEquals("server-side-fallback-2026-07-01", request.headers["anthropic-beta"])
        val body = request.json()
        assertEquals("claude-opus-5-5", body["model"]!!.jsonPrimitive.content)
        assertEquals("default", body["fallbacks"]!!.jsonPrimitive.content)
        assertEquals("find", body["tools"]!!.jsonArray[0].jsonObject["name"]!!.jsonPrimitive.content)
        // The image goes before the text that refers to it
        val content = body["messages"]!!.jsonArray[0].jsonObject["content"]!!.jsonArray
        assertEquals("image", content[0].jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("text", content[1].jsonObject["type"]!!.jsonPrimitive.content)
    }

    @Test
    fun `no fallbacks through a proxy`() = runTest {
        val chat = chat(HttpStatusCode.OK to """{"stop_reason":"end_turn","content":[]}""")
        val conversation = AiConversation(AiConfig(baseUrl = "https://proxy.example.com", apiKey = "k"), "s", emptyList())
        chat.addUser(conversation, listOf(AiPart.Text("hi")))
        chat.send(conversation)
        assertNull(requests.single().headers["anthropic-beta"])
        assertNull(requests.single().json()["fallbacks"])
        assertNull(requests.single().json()["tools"])
    }

    @Test
    fun `claude tool calls come back and the reply is kept verbatim, thinking included`() = runTest {
        val first = """{"stop_reason":"tool_use","content":[
            {"type":"thinking","thinking":"","signature":"sig"},
            {"type":"tool_use","id":"t1","name":"find","input":{"q":"x"}},
            {"type":"tool_use","id":"t2","name":"find","input":{"q":"y"}}]}"""
        val chat = chat(HttpStatusCode.OK to first, HttpStatusCode.OK to """{"stop_reason":"end_turn","content":[]}""")
        val conversation = AiConversation(AiConfig(apiKey = "k"), "s", listOf(tool))
        chat.addUser(conversation, listOf(AiPart.Text("hi")))

        val reply = chat.send(conversation)
        assertEquals(listOf("t1", "t2"), reply.calls.map { it.id })
        assertEquals("x", reply.calls[0].input["q"]!!.jsonPrimitive.content)

        chat.addToolResults(conversation, listOf(AiToolResult("t1", "a"), AiToolResult("t2", "b", isError = true)))
        chat.send(conversation)

        val messages = requests[1].json()["messages"]!!.jsonArray
        assertEquals(3, messages.size)
        val assistant = messages[1].jsonObject["content"]!!.jsonArray
        assertEquals("sig", assistant[0].jsonObject["signature"]!!.jsonPrimitive.content)
        // Both results in one user message
        val results = messages[2].jsonObject["content"]!!.jsonArray
        assertEquals(2, results.size)
        assertEquals("true", results[1].jsonObject["is_error"]!!.jsonPrimitive.content)
    }

    @Test
    fun `thinking and token usage are read from both protocols`() = runTest {
        val claude = """{"stop_reason":"end_turn","usage":{"input_tokens":10,"cache_read_input_tokens":90,"output_tokens":7},
            "content":[{"type":"thinking","thinking":"look at the dates","signature":"s"},{"type":"text","text":"done"}]}"""
        val openAi = """{"usage":{"prompt_tokens":120,"completion_tokens":30},
            "choices":[{"finish_reason":"stop","message":{"role":"assistant","content":"ok","reasoning_content":"hmm"}}]}"""
        val chat = chat(HttpStatusCode.OK to claude, HttpStatusCode.OK to openAi)

        val first = AiConversation(AiConfig(apiKey = "k"), "s", emptyList())
        chat.addUser(first, listOf(AiPart.Text("hi")))
        val reply = chat.send(first)
        assertEquals("look at the dates", reply.thinking)
        assertEquals("done", reply.text)
        assertEquals(100, reply.inputTokens)
        assertEquals(7, reply.outputTokens)

        val second = AiConversation(AiConfig(AiProtocol.OPENAI_COMPATIBLE, "https://api.deepseek.com", "deepseek-chat", "k"), "s", emptyList())
        chat.addUser(second, listOf(AiPart.Text("hi")))
        val other = chat.send(second)
        assertEquals("hmm", other.thinking)
        assertEquals(120, other.inputTokens)
        assertEquals(30, other.outputTokens)
    }

    @Test
    fun `refusal and truncation are reported`() = runTest {
        val chat = chat(
            HttpStatusCode.OK to """{"stop_reason":"refusal","content":[]}""",
            HttpStatusCode.OK to """{"stop_reason":"max_tokens","content":[]}"""
        )
        val conversation = AiConversation(AiConfig(apiKey = "k"), "s", emptyList())
        chat.addUser(conversation, listOf(AiPart.Text("hi")))
        try {
            chat.send(conversation)
            fail("expected a refusal")
        } catch (_: AiException) {
        }
        assertTrue(chat.send(conversation).truncated)
    }

    @Test
    fun `http errors carry the provider's message`() = runTest {
        val chat = chat(HttpStatusCode.Unauthorized to """{"type":"error","error":{"type":"authentication_error","message":"invalid x-api-key"}}""")
        val conversation = AiConversation(AiConfig(apiKey = "k"), "s", emptyList())
        chat.addUser(conversation, listOf(AiPart.Text("hi")))
        try {
            chat.send(conversation)
            fail("expected an error")
        } catch (e: AiException) {
            assertEquals("HTTP 401: invalid x-api-key", e.message)
        }
    }

    @Test
    fun `openai-compatible request and tool calls`() = runTest {
        val reply = """{"choices":[{"finish_reason":"tool_calls","message":{"role":"assistant","content":null,
            "tool_calls":[{"id":"c1","type":"function","function":{"name":"find","arguments":"{\"q\":\"x\"}"}},
                          {"id":"c2","type":"function","function":{"name":"find","arguments":"not json"}}]}}]}"""
        val chat = chat(HttpStatusCode.OK to reply, HttpStatusCode.OK to """{"choices":[{"finish_reason":"stop","message":{"role":"assistant","content":"ok"}}]}""")
        val config = AiConfig(AiProtocol.OPENAI_COMPATIBLE, "https://openrouter.ai/api/v1", "anthropic/claude-opus-5.5", "k")
        val conversation = AiConversation(config, "system", listOf(tool))
        // A PDF with a text layer is sent as text, so the content stays a plain string
        chat.addUser(conversation, listOf(AiPart.Pdf("a.pdf", "AAAA", "line 1"), AiPart.Text("hi")))

        val first = chat.send(conversation)
        assertEquals("https://openrouter.ai/api/v1/chat/completions", requests[0].url.toString())
        assertEquals("Bearer k", requests[0].headers["Authorization"])
        val body = requests[0].json()
        val messages = body["messages"]!!.jsonArray
        assertEquals("system", messages[0].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("File a.pdf:\nline 1\n\nhi", messages[1].jsonObject["content"]!!.jsonPrimitive.content)
        assertEquals("function", body["tools"]!!.jsonArray[0].jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("x", first.calls[0].input["q"]!!.jsonPrimitive.content)
        assertEquals("not json", first.calls[1].input[AiChat.INVALID_ARGUMENTS]!!.jsonPrimitive.content)

        chat.addToolResults(conversation, listOf(AiToolResult("c1", "a"), AiToolResult("c2", "bad", isError = true)))
        assertEquals("ok", chat.send(conversation).text)
        val second = requests[1].json()["messages"]!!.jsonArray
        // system, user, assistant, tool, tool
        assertEquals(5, second.size)
        assertEquals("c2", second[4].jsonObject["tool_call_id"]!!.jsonPrimitive.content)
        assertEquals("Error: bad", second[4].jsonObject["content"]!!.jsonPrimitive.content)
    }

    @Test
    fun `a scanned pdf goes to openai-compatible providers as a file`() = runTest {
        val chat = chat(HttpStatusCode.OK to """{"choices":[{"finish_reason":"stop","message":{"role":"assistant","content":""}}]}""")
        val config = AiConfig(AiProtocol.OPENAI_COMPATIBLE, "https://api.openai.com/v1", "m", "k")
        val conversation = AiConversation(config, "s", emptyList())
        chat.addUser(conversation, listOf(AiPart.Pdf("scan.pdf", "AAAA", ""), AiPart.Text("hi")))
        chat.send(conversation)
        val content = requests.single().json()["messages"]!!.jsonArray[1].jsonObject["content"]!!.jsonArray
        assertEquals("file", content[0].jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("data:application/pdf;base64,AAAA",
            content[0].jsonObject["file"]!!.jsonObject["file_data"]!!.jsonPrimitive.content)
        assertFalse(requests.single().json().containsKey("tools"))
    }

    @Test
    fun `openrouter models keep only tool callers and mark vision`() = runTest {
        val chat = chat(HttpStatusCode.OK to """{"data":[
            {"id":"anthropic/claude-opus-5.5","name":"Anthropic: Claude Opus 5.5",
             "architecture":{"input_modalities":["text","image","file"]},"supported_parameters":["tools","max_tokens"]},
            {"id":"anthropic/claude-opus-5.5:batch","name":"batch","supported_parameters":["tools"]},
            {"id":"deepseek/deepseek-chat","name":"DeepSeek Chat",
             "architecture":{"input_modalities":["text"]},"supported_parameters":["tools"]},
            {"id":"some/no-tools","name":"No tools","supported_parameters":["max_tokens"]}]}""")
        val models = chat.listModels(AiConfig(AiProtocol.OPENAI_COMPATIBLE, "https://openrouter.ai/api/v1", "", "k"))
        assertEquals("https://openrouter.ai/api/v1/models", requests.single().url.toString())
        assertEquals("Bearer k", requests.single().headers["Authorization"])
        assertEquals(
            listOf(AiModel("anthropic/claude-opus-5.5", "Anthropic: Claude Opus 5.5", true),
                AiModel("deepseek/deepseek-chat", "DeepSeek Chat", false)),
            models
        )
    }

    @Test
    fun `claude models are listed by display name`() = runTest {
        val chat = chat(HttpStatusCode.OK to """{"data":[{"id":"claude-opus-5-5","display_name":"Claude Opus 5.5","type":"model"}]}""")
        val models = chat.listModels(AiConfig(apiKey = "k"))
        assertEquals("https://api.anthropic.com/v1/models?limit=1000", requests.single().url.toString())
        assertEquals("k", requests.single().headers["x-api-key"])
        assertEquals(listOf(AiModel("claude-opus-5-5", "Claude Opus 5.5", true)), models)
    }

    @Test
    fun `statement dates without a time land at noon`() {
        assertEquals(LocalDateTime.of(2026, 9, 1, 12, 0), LedgerTools.dateTime("2026-09-01"))
        assertEquals(LocalDateTime.of(2026, 9, 1, 8, 5), LedgerTools.dateTime("2026-09-01 08:05"))
        assertEquals(LocalDateTime.of(2026, 9, 1, 8, 5), LedgerTools.dateTime("2026-09-01T08:05:00"))
    }

    @Test
    fun `text files decode as utf-8 or gb18030`() {
        assertEquals("交易时间,金额", decodeText("﻿交易时间,金额".toByteArray(Charsets.UTF_8)))
        assertEquals("交易时间,金额", decodeText("交易时间,金额".toByteArray(charset("GB18030"))))
    }
}
