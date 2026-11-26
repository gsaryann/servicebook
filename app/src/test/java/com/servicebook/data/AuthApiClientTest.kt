package com.servicebook.data

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AuthApiClientTest {
    private lateinit var server: HttpServer
    private lateinit var baseUrl: String
    private val receivedPath = AtomicReference<String>()
    private val receivedBody = AtomicReference<String>()

    @Before
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            receivedPath.set(exchange.requestURI.path)
            receivedBody.set(exchange.requestBody.bufferedReader().use { it.readText() })
            val response = when (exchange.requestURI.path) {
                "/auth/requests" -> """{"request_id":"request_12345678901234567890","bot_url":"https://t.me/authservicebookbot?start=sb_request_12345678901234567890","expires_in":300}"""
                "/auth/requests/request_12345678901234567890" -> """{"status":"approved"}"""
                "/auth/requests/error_request_12345678901234567890" -> """{"detail":"Номер не найден"}"""
                "/auth/requests/bad_json_request_12345678901234567890" -> "not-json"
                else -> """{"unexpected":true}"""
            }
            val status = if (exchange.requestURI.path == "/auth/requests/error_request_12345678901234567890") 404 else 200
            exchange.sendResponseHeaders(status, response.toByteArray(StandardCharsets.UTF_8).size.toLong())
            exchange.responseBody.use { it.write(response.toByteArray(StandardCharsets.UTF_8)) }
        }
        server.start()
        baseUrl = "http://127.0.0.1:${server.address.port}"
    }

    @After
    fun tearDown() {
        server.stop(0)
    }

    @Test
    fun createRequestPostsPhoneAndModeAndParsesResponse() = runBlocking {
        val result = AuthApiClient.createRequest(baseUrl, "+7 (999) 123-45-67", "register", "token_xyz_123")
        assertEquals("/auth/requests", receivedPath.get())
        assertTrue(receivedBody.get().contains("\"phone\":\"+7 (999) 123-45-67\""))
        assertTrue(receivedBody.get().contains("\"mode\":\"register\""))
        assertTrue(receivedBody.get().contains("\"client_token\":\"token_xyz_123\""))
        assertEquals("request_12345678901234567890", result.requestId)
        assertEquals(300, result.expiresIn)
        assertTrue(result.botUrl.startsWith("https://t.me/"))
    }

    @Test
    fun getStatusParsesApprovedResponse() = runBlocking {
        assertEquals(
            AuthRequestStatus.APPROVED,
            AuthApiClient.getStatus(baseUrl, "request_12345678901234567890")
        )
    }

    @Test
    fun nonSuccessResponseReturnsServerDetail() = runBlocking {
        try {
            AuthApiClient.getStatus(baseUrl, "error_request_12345678901234567890")
            throw AssertionError("Expected AuthApiException")
        } catch (error: AuthApiException) {
            assertEquals("Номер не найден", error.message)
        }
    }

    @Test
    fun malformedJsonDoesNotProduceAnApprovedStatus() = runBlocking {
        try {
            AuthApiClient.getStatus(baseUrl, "bad_json_request_12345678901234567890")
            throw AssertionError("Expected AuthApiException")
        } catch (error: AuthApiException) {
            assertTrue(error.message!!.contains("некорректный JSON"))
        }
    }
}
