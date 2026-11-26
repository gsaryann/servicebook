package com.servicebook.data

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

data class AuthRequest(
    val requestId: String,
    val botUrl: String,
    val expiresIn: Int
)

enum class AuthRequestStatus {
    PENDING, APPROVED, REJECTED, EXPIRED
}

class AuthApiException(
    message: String,
    val statusCode: Int? = null,
    cause: Throwable? = null
) : IOException(message, cause)

object AuthApiClient {
    private const val CONNECT_TIMEOUT_MS = 5_000
    private const val READ_TIMEOUT_MS = 5_000
    private const val MAX_RESPONSE_BYTES = 64 * 1024

    suspend fun createRequest(
        baseUrl: String,
        phone: String,
        mode: String,
        clientToken: String? = null
    ): AuthRequest =
        withContext(Dispatchers.IO) {
            require(mode == "register" || mode == "login") { "Неизвестный сценарий авторизации" }
            val payload = JSONObject().apply {
                put("phone", phone)
                put("mode", mode)
                if (clientToken != null) {
                    put("client_token", clientToken)
                }
            }
            val json = execute(baseUrl, "/auth/requests", "POST", payload.toString())
            try {
                AuthRequest(
                    requestId = json.getString("request_id"),
                    botUrl = json.getString("bot_url"),
                    expiresIn = json.getInt("expires_in")
                )
            } catch (error: JSONException) {
                throw AuthApiException("Сервер вернул некорректный ответ авторизации", cause = error)
            }
        }

    suspend fun getStatus(baseUrl: String, requestId: String): AuthRequestStatus =
        withContext(Dispatchers.IO) {
            require(requestId.matches(Regex("[A-Za-z0-9_-]{20,64}"))) {
                "Некорректный идентификатор запроса"
            }
            val encodedId = URLEncoder.encode(requestId, Charsets.UTF_8.name())
            val json = execute(baseUrl, "/auth/requests/$encodedId", "GET", null)
            val status = try {
                json.getString("status")
            } catch (error: JSONException) {
                throw AuthApiException("Сервер вернул некорректный статус авторизации", cause = error)
            }
            when (status) {
                "pending" -> AuthRequestStatus.PENDING
                "approved" -> AuthRequestStatus.APPROVED
                "rejected" -> AuthRequestStatus.REJECTED
                "expired" -> AuthRequestStatus.EXPIRED
                else -> throw AuthApiException("Сервер вернул неизвестный статус авторизации")
            }
        }

    suspend fun exchangeSession(
        baseUrl: String,
        requestId: String,
        clientToken: String
    ): String = withContext(Dispatchers.IO) {
        require(requestId.matches(Regex("[A-Za-z0-9_-]{20,64}"))) {
            "Некорректный идентификатор запроса"
        }
        require(clientToken.matches(Regex("[A-Za-z0-9_-]{16,128}"))) {
            "Некорректный токен клиента"
        }
        val payload = JSONObject().apply {
            put("request_id", requestId)
            put("client_token", clientToken)
        }
        val json = execute(baseUrl, "/api/v1/auth/session", "POST", payload.toString())
        try {
            json.getString("session_token")
        } catch (error: JSONException) {
            throw AuthApiException("Сервер вернул некорректный токен сессии", cause = error)
        }
    }

    suspend fun deleteAccount(baseUrl: String, sessionToken: String) = withContext(Dispatchers.IO) {
        require(sessionToken.isNotBlank()) { "Отсутствует токен сессии" }
        val json = execute(
            baseUrl = baseUrl,
            path = "/api/v1/account",
            method = "DELETE",
            body = null,
            headers = mapOf("Authorization" to "Bearer $sessionToken")
        )
        if (json.optString("status") != "deleted") {
            throw AuthApiException("Сервер не подтвердил удаление аккаунта")
        }
    }

    suspend fun revokeYandexToken(accessToken: String, clientId: String): Boolean = withContext(Dispatchers.IO) {
        if (accessToken.isBlank() || clientId.isBlank()) return@withContext false
        val connection = try {
            (URL("https://oauth.yandex.ru/revoke_token").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            }
        } catch (_: Exception) {
            return@withContext false
        }
        try {
            val form = "access_token=${URLEncoder.encode(accessToken, Charsets.UTF_8.name())}" +
                "&client_id=${URLEncoder.encode(clientId, Charsets.UTF_8.name())}"
            connection.outputStream.use { it.write(form.toByteArray(Charsets.UTF_8)) }
            connection.responseCode in 200..299
        } catch (_: Exception) {
            false
        } finally {
            connection.disconnect()
        }
    }

    private fun execute(
        baseUrl: String,
        path: String,
        method: String,
        body: String?,
        headers: Map<String, String> = emptyMap()
    ): JSONObject {
        val normalizedBase = baseUrl.trim().trimEnd('/')
        if (!normalizedBase.startsWith("http://") && !normalizedBase.startsWith("https://")) {
            throw AuthApiException("Адрес локального сервера должен начинаться с http:// или https://")
        }
        val connection = try {
            (URL("$normalizedBase$path").openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = false
                setRequestProperty("Accept", "application/json")
                for ((k, v) in headers) {
                    setRequestProperty(k, v)
                }
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
            }
        } catch (error: Exception) {
            throw AuthApiException("Не удалось подключиться к локальному серверу ServiceBook", cause = error)
        }

        try {
            if (body != null) {
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val statusCode = connection.responseCode
            val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
            val responseText = stream?.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(4_096)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_RESPONSE_BYTES) throw AuthApiException("Ответ локального сервера слишком большой")
                    output.write(buffer, 0, read)
                }
                output.toString(Charsets.UTF_8.name())
            }.orEmpty()

            if (statusCode !in 200..299) {
                val detail = try {
                    JSONObject(responseText).optString("detail").ifBlank { "Ошибка локального сервера (HTTP $statusCode)" }
                } catch (_: JSONException) {
                    "Ошибка локального сервера (HTTP $statusCode)"
                }
                throw AuthApiException(detail, statusCode = statusCode)
            }
            return try {
                JSONObject(responseText)
            } catch (error: JSONException) {
                throw AuthApiException("Сервер вернул некорректный JSON", cause = error)
            }
        } catch (error: AuthApiException) {
            throw error
        } catch (error: Exception) {
            throw AuthApiException("Не удалось связаться с локальным сервером ServiceBook", cause = error)
        } finally {
            connection.disconnect()
        }
    }
}
