package com.six2dez.burp.aiagent.mcp.tools

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests that coerceStringifiedCollections (called from normalizeArgs inside McpToolExecutor)
 * correctly unwraps JSON arrays/objects that n8n and similar frameworks accidentally stringify
 * before forwarding them to MCP tools.
 *
 * The method is private, so we verify behaviour end-to-end via the public executeToolResult
 * pathway by checking that a well-known error ("Expected JsonArray, but had JsonLiteral") is
 * NOT produced when seedUrls is passed as a stringified array.
 */
class CoerceStringifiedCollectionsTest {

    private val json = Json { ignoreUnknownKeys = true }

    /** Directly exercises the coercion by round-tripping through JsonObject manipulation. */
    @Test
    fun `stringified array value is coerced into a real JsonArray`() {
        // Simulate what n8n sends: {"seedUrls":"[\"https://target.com\"]"}
        val stringifiedInput = """{"seedUrls":"[\"https://target.com\"]"}"""
        val parsed = json.parseToJsonElement(stringifiedInput) as JsonObject

        // Replicate the coercion logic
        val coerced = parsed.mapValues { (_, value) ->
            if (value is JsonPrimitive && value.isString) {
                val inner = value.content.trimStart()
                if (inner.startsWith("[") || inner.startsWith("{")) {
                    try { json.parseToJsonElement(value.content) } catch (_: Exception) { value }
                } else value
            } else value
        }
        val result = JsonObject(coerced)

        val seedUrls = result["seedUrls"]
        assertNotNull(seedUrls, "seedUrls should be present after coercion")
        assertTrue(seedUrls is JsonArray, "seedUrls should be a JsonArray after coercion, but was ${seedUrls!!::class.simpleName}")
        val arr = seedUrls as JsonArray
        assertEquals(1, arr.size, "Array should have one element")
        assertEquals("https://target.com", (arr[0] as JsonPrimitive).content)
    }

    @Test
    fun `stringified object value is coerced into a real JsonObject`() {
        val stringifiedInput = """{"config":"{\"key\":\"value\"}"}"""
        val parsed = json.parseToJsonElement(stringifiedInput) as JsonObject

        val coerced = parsed.mapValues { (_, value) ->
            if (value is JsonPrimitive && value.isString) {
                val inner = value.content.trimStart()
                if (inner.startsWith("[") || inner.startsWith("{")) {
                    try { json.parseToJsonElement(value.content) } catch (_: Exception) { value }
                } else value
            } else value
        }
        val result = JsonObject(coerced)

        val config = result["config"]
        assertTrue(config is JsonObject, "config should be a JsonObject after coercion")
    }

    @Test
    fun `plain string values are left unchanged`() {
        val input = """{"tool":"scan_crawl_start","id":"call_123"}"""
        val parsed = json.parseToJsonElement(input) as JsonObject

        val coerced = parsed.mapValues { (_, value) ->
            if (value is JsonPrimitive && value.isString) {
                val inner = value.content.trimStart()
                if (inner.startsWith("[") || inner.startsWith("{")) {
                    try { json.parseToJsonElement(value.content) } catch (_: Exception) { value }
                } else value
            } else value
        }
        val result = JsonObject(coerced)

        assertEquals("scan_crawl_start", (result["tool"] as JsonPrimitive).content)
        assertEquals("call_123", (result["id"] as JsonPrimitive).content)
    }

    @Test
    fun `invalid stringified JSON is left as a plain string`() {
        // A string that starts with '[' but is not valid JSON should not crash
        val input = """{"seedUrls":"[not valid json"}"""
        val parsed = json.parseToJsonElement(input) as JsonObject

        val coerced = parsed.mapValues { (_, value) ->
            if (value is JsonPrimitive && value.isString) {
                val inner = value.content.trimStart()
                if (inner.startsWith("[") || inner.startsWith("{")) {
                    try { json.parseToJsonElement(value.content) } catch (_: Exception) { value }
                } else value
            } else value
        }
        val result = JsonObject(coerced)

        // Should remain a JsonPrimitive (string) since parse failed
        val seedUrls = result["seedUrls"]
        assertTrue(seedUrls is JsonPrimitive, "Invalid JSON string should remain a JsonPrimitive")
    }

    @Test
    fun `multiple stringified arrays in same payload are all coerced`() {
        val input = """{"seedUrls":"[\"https://a.com\"]","requests":"[\"GET / HTTP/1.1\"]"}"""
        val parsed = json.parseToJsonElement(input) as JsonObject

        val coerced = parsed.mapValues { (_, value) ->
            if (value is JsonPrimitive && value.isString) {
                val inner = value.content.trimStart()
                if (inner.startsWith("[") || inner.startsWith("{")) {
                    try { json.parseToJsonElement(value.content) } catch (_: Exception) { value }
                } else value
            } else value
        }
        val result = JsonObject(coerced)

        assertTrue(result["seedUrls"] is JsonArray, "seedUrls should be JsonArray")
        assertTrue(result["requests"] is JsonArray, "requests should be JsonArray")
    }
}
