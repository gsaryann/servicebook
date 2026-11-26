package com.servicebook.data

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AccountDeletionTest {

    private lateinit var fakePrefs: FakeSharedPreferences
    private lateinit var server: HttpServer
    private lateinit var baseUrl: String

    @Before
    fun setUp() {
        fakePrefs = FakeSharedPreferences()
        ServiceBookRepository.initForTesting(fakePrefs)
    }

    @After
    fun tearDown() {
        if (::server.isInitialized) {
            server.stop(0)
        }
    }

    @Test
    fun deleteAccountAndAllDataClearsCurrentUserAndPreservesOtherAccounts() {
        // Account A (to be deleted)
        ServiceBookRepository.completeRegistrationAndLogin("Иван", "Петров", "10.10.1990", "+7 (999) 111-22-33")
        ServiceBookRepository.saveCar("Lada", "Granta", 2014, "1.6 л", 112400)
        ServiceBookRepository.addRecord("Замена масла", "Масло", "18.11.2026", 112400, 4650, "5W-40")
        ServiceBookRepository.addDocument("Чек", "JPG", "1.2 МБ", true)
        ServiceBookRepository.saveAuthSessionToken("sess_tok_user_a_1234567890")
        ServiceBookRepository.saveYandexAccessToken("yandex_tok_user_a_1234567890")
        ServiceBookRepository.savePendingAuth("req_12345678901234567890", "https://t.me/bot", "+79991112233", "login", 300, "ct_1234567890123456")

        // Another user's local account in SharedPreferences
        fakePrefs.edit().putString("account_9990009988", """{"firstName":"Мария"}""").apply()

        assertTrue(ServiceBookRepository.isLoggedIn.value)
        assertTrue(ServiceBookRepository.records.value.isNotEmpty())
        assertTrue(ServiceBookRepository.documents.value.isNotEmpty())

        ServiceBookRepository.deleteAccountAndAllData(null)

        assertFalse(ServiceBookRepository.isLoggedIn.value)
        assertEquals("", ServiceBookRepository.userProfile.value.firstName)
        assertEquals("", ServiceBookRepository.car.value.brand)
        assertTrue(ServiceBookRepository.records.value.isEmpty())
        assertTrue(ServiceBookRepository.reminders.value.isEmpty())
        assertTrue(ServiceBookRepository.documents.value.isEmpty())
        assertNull(ServiceBookRepository.getAuthSessionToken())
        assertNull(ServiceBookRepository.getYandexAccessToken())
        assertNull(ServiceBookRepository.getPendingAuth())
        assertFalse(fakePrefs.contains("account_9991112233"))
        assertTrue("Другой локальный аккаунт не должен быть удалён", fakePrefs.contains("account_9990009988"))

        // Re-login after deletion does not restore old data
        val result = ServiceBookRepository.completeLogin("+7 (999) 111-22-33")
        assertEquals(LoginResult.NEED_PROFILE, result)
        assertFalse(ServiceBookRepository.isLoggedIn.value)
    }

    @Test
    fun deleteAccountApiSendsBearerTokenAndHandles401WithoutLocalWipe() = runBlocking {
        var authHeaderReceived: String? = null
        var returnCode = 200
        var responseJson = """{"status":"deleted"}"""

        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/v1/account") { exchange ->
            authHeaderReceived = exchange.requestHeaders.getFirst("Authorization")
            val bytes = responseJson.toByteArray(StandardCharsets.UTF_8)
            exchange.sendResponseHeaders(returnCode, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        baseUrl = "http://127.0.0.1:${server.address.port}"

        ServiceBookRepository.completeRegistrationAndLogin("Алексей", "Иванов", "15.05.1995", "+7 (999) 222-33-44")
        ServiceBookRepository.saveAuthSessionToken("bearer_tok_1234567890abcdef")

        // 1. Expired/invalid token -> 401, local data stays intact
        returnCode = 401
        responseJson = """{"detail":"Сессия недействительна или истекла"}"""
        var caught401: AuthApiException? = null
        try {
            AuthApiClient.deleteAccount(baseUrl, ServiceBookRepository.getAuthSessionToken()!!)
        } catch (e: AuthApiException) {
            caught401 = e
        }
        assertEquals(401, caught401?.statusCode)
        assertTrue(ServiceBookRepository.isLoggedIn.value)

        // 2. Valid token -> 200 OK
        returnCode = 200
        responseJson = """{"status":"deleted"}"""
        AuthApiClient.deleteAccount(baseUrl, ServiceBookRepository.getAuthSessionToken()!!)
        assertEquals("Bearer bearer_tok_1234567890abcdef", authHeaderReceived)
    }

    @Test
    fun collectOtherAccountFilePathsProtectsSecondAccountFiles() {
        val tempRoot = java.nio.file.Files.createTempDirectory("sb_files_test").toFile()
        try {
            val fileA = java.io.File(tempRoot, "receipt_a.jpg").apply { writeText("a") }
            val fileB = java.io.File(tempRoot, "receipt_b.jpg").apply { writeText("b") }
            val docB = java.io.File(tempRoot, "doc_b.jpg").apply { writeText("db") }

            // Create Account B first
            ServiceBookRepository.completeRegistrationAndLogin("Мария", "Соколова", "05.05.1994", "+7 (999) 000-99-88")
            ServiceBookRepository.addRecord("Шины", "Шины", "10.10.2026", 50000, 3000, "", "receipt_b.jpg", "1 КБ", "receipt_tires", fileB.absolutePath)
            ServiceBookRepository.addDocument("Док Б", "JPG", "1 КБ", false, filePath = docB.absolutePath)

            // Switch to Account A
            ServiceBookRepository.completeRegistrationAndLogin("Иван", "Петров", "10.10.1990", "+7 (999) 111-22-33")
            ServiceBookRepository.addRecord("Масло", "Масло", "18.11.2026", 112400, 4650, "", "receipt_a.jpg", "1 КБ", "receipt_oil", fileA.absolutePath)

            // Verify Account A's deletion does not delete Account B's files
            val protectedPaths = ServiceBookRepository.run {
                val method = this::class.java.getDeclaredMethod("collectOtherAccountFilePaths", String::class.java, java.io.File::class.java)
                method.isAccessible = true
                @Suppress("UNCHECKED_CAST")
                method.invoke(this, "9991112233", tempRoot.canonicalFile) as Set<java.io.File>
            }
            assertTrue(fileB.canonicalFile in protectedPaths)
            assertTrue(docB.canonicalFile in protectedPaths)
            assertFalse(fileA.canonicalFile in protectedPaths)
        } finally {
            tempRoot.deleteRecursively()
        }
    }
}
