package com.servicebook

import com.servicebook.data.AuthApiClient
import com.servicebook.data.AuthApiException
import com.servicebook.data.AuthRequestStatus
import com.servicebook.data.FakeSharedPreferences
import com.servicebook.data.ServiceBookRepository
import com.servicebook.ui.navigation.Screen
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit-тесты логики восстановления состояния MainActivity, BackHandler и повторной попытки
 * после сетевого таймаута.
 * Примечание: проверяется чистая логика координации состояния и HTTP-клиент (без Compose UI / Robolectric).
 */
class MainActivityRecoveryTest {

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
    fun resolveInitialAuthStateRestoresTelegramAuthRegisterModeAndFetchesStatus() = runBlocking {
        val phone = "+7 (999) 444-55-66"
        val requestId = "restored_reg_req_1234567890"
        val polledPaths = CopyOnWriteArrayList<String>()

        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            polledPaths.add(exchange.requestURI.path)
            val response = """{"status":"approved"}"""
            exchange.sendResponseHeaders(200, response.toByteArray(StandardCharsets.UTF_8).size.toLong())
            exchange.responseBody.use { it.write(response.toByteArray(StandardCharsets.UTF_8)) }
        }
        server.start()
        baseUrl = "http://127.0.0.1:${server.address.port}"

        // 1. Пользователь начал регистрацию и перешёл в Telegram
        ServiceBookRepository.prepareRegistration("Мария", "Соколова", "05.05.1994", phone)
        ServiceBookRepository.savePendingAuth(
            requestId = requestId,
            botUrl = "https://t.me/authservicebookbot?start=sb_$requestId",
            phone = phone,
            mode = "register",
            expiresIn = 300,
            clientToken = "attempt_tok_1234567890"
        )

        // 2. Имитация уничтожения и пересоздания процесса
        ServiceBookRepository.initForTesting(fakePrefs)

        // 3. MainActivity вычисляет стартовое состояние
        val restored = resolveInitialAuthState(
            isLoggedIn = ServiceBookRepository.isLoggedIn.value,
            pendingSession = ServiceBookRepository.getPendingAuth()
        )

        assertEquals(Screen.TelegramAuth.route, restored.initialRoute)
        assertTrue(restored.isNewUserRegistration)
        assertEquals(phone, restored.pendingAuthPhone)
        assertNotNull(restored.pendingAuthRequest)
        assertEquals(requestId, restored.pendingAuthRequest?.requestId)
        assertTrue((restored.pendingAuthRequest?.expiresIn ?: 0) in 1..300)

        // 4. Возобновлённый polling запрашивает статус по восстановленному requestId
        val status = AuthApiClient.getStatus(baseUrl, restored.pendingAuthRequest!!.requestId)
        assertEquals(AuthRequestStatus.APPROVED, status)
        assertEquals(listOf("/auth/requests/$requestId"), polledPaths)

        // 5. Завершение регистрации восстанавливает профиль из черновика
        val completed = ServiceBookRepository.completeRegistrationAndLogin(restored.pendingAuthPhone)
        assertTrue(completed)
        assertTrue(ServiceBookRepository.isLoggedIn.value)
        assertEquals("Мария", ServiceBookRepository.userProfile.value.firstName)
    }

    @Test
    fun performTelegramBackNavigationClearsSessionAndNavigatesToLoginWhenBackStackEmpty() {
        val phone = "+7 (999) 555-11-22"
        val token = ServiceBookRepository.getOrCreateAuthAttemptToken(phone, "login")
        assertTrue(token.isNotBlank())
        ServiceBookRepository.savePendingAuth(
            requestId = "req_back_test_123456789012",
            botUrl = "https://t.me/authservicebookbot?start=sb_back",
            phone = phone,
            mode = "login",
            expiresIn = 300,
            clientToken = token
        )

        var navigatedToLogin = false
        var clearedInMemory = false

        // При восстановлении процесса стек навигации пуст: popBackStack() возвращает false
        performTelegramBackNavigation(
            popBackStack = { false },
            navigateToLogin = { navigatedToLogin = true },
            clearPendingRequestState = { clearedInMemory = true }
        )

        assertTrue("Должен произойти переход на Login при пустом back stack", navigatedToLogin)
        assertTrue(clearedInMemory)
        assertNull("pendingAuth должен быть очищен", ServiceBookRepository.getPendingAuth())
        assertTrue("attempt_auth_token должен быть очищен", !fakePrefs.contains("attempt_auth_token"))
    }

    @Test
    fun retryCreateRequestAfterTimeoutReusesSameClientTokenAndReceivesSameRequestId() = runBlocking {
        val phone = "+79996667788"
        val expectedRequestId = "req_timeout_retry_1234567890"
        val requestCount = AtomicInteger(0)
        val receivedTokens = CopyOnWriteArrayList<String>()

        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/auth/requests") { exchange ->
            val body = exchange.requestBody.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            receivedTokens.add(json.optString("client_token", ""))

            val attempt = requestCount.incrementAndGet()
            if (attempt == 1) {
                // Имитируем сетевой таймаут чтения на первом запросе (> 5000 мс)
                try {
                    Thread.sleep(5_500)
                } catch (_: InterruptedException) {
                }
                return@createContext
            }

            val response = """{"request_id":"$expectedRequestId","bot_url":"https://t.me/authservicebookbot?start=sb_$expectedRequestId","expires_in":295}"""
            val bytes = response.toByteArray(StandardCharsets.UTF_8)
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        baseUrl = "http://127.0.0.1:${server.address.port}"

        // Попытка 1: таймаут
        val token1 = ServiceBookRepository.getOrCreateAuthAttemptToken(phone, "login")
        var timedOut = false
        try {
            AuthApiClient.createRequest(baseUrl, phone, "login", token1)
        } catch (_: AuthApiException) {
            timedOut = true
        }
        assertTrue("Первый запрос должен завершиться ошибкой таймаута", timedOut)

        // Попытка 2: повторное нажатие
        val token2 = ServiceBookRepository.getOrCreateAuthAttemptToken(phone, "login")
        val retryResult = AuthApiClient.createRequest(baseUrl, phone, "login", token2)

        assertEquals("Токен попытки должен сохраниться после таймаута", token1, token2)
        assertEquals(2, receivedTokens.size)
        assertEquals(receivedTokens[0], receivedTokens[1])
        assertEquals(expectedRequestId, retryResult.requestId)
    }
}
