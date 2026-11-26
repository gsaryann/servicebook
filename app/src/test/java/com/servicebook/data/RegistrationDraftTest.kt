package com.servicebook.data

import android.content.SharedPreferences
import com.servicebook.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RegistrationDraftTest {

    private lateinit var fakePrefs: FakeSharedPreferences

    @Before
    fun setUp() {
        fakePrefs = FakeSharedPreferences()
        ServiceBookRepository.initForTesting(fakePrefs)
    }

    @Test
    fun prepareRegistrationSavesDraftToSharedPreferences() {
        val phone = "+7 (999) 111-22-33"
        ServiceBookRepository.prepareRegistration(
            firstName = "Алексей",
            lastName = "Смирнов",
            birthDate = "15.05.1990",
            phone = phone
        )

        val draft = ServiceBookRepository.getRegistrationDraft(phone)
        assertNotNull("Черновик должен быть сохранён в SharedPreferences", draft)
        assertEquals("Алексей", draft?.firstName)
        assertEquals("Смирнов", draft?.lastName)
        assertEquals("15.05.1990", draft?.birthDate)
        assertEquals(phone, draft?.phone)
    }

    @Test
    fun processKillRecoveryRestoresProfileFromDraftOnApproved() {
        val phone = "+7 (999) 222-33-44"
        ServiceBookRepository.prepareRegistration(
            firstName = "Елена",
            lastName = "Ковалёва",
            birthDate = "20.10.1995",
            phone = phone
        )

        // Имитируем завершение процесса: заново инициализируем репозиторий с теми же SharedPreferences
        ServiceBookRepository.initForTesting(fakePrefs)

        // В оперативной памяти профиль пуст
        assertEquals("", ServiceBookRepository.userProfile.value.firstName)

        // При подтверждении регистрации профиль должен восстановиться из сохранённого черновика
        ServiceBookRepository.completeRegistrationAndLogin(phone)

        val restored = ServiceBookRepository.userProfile.value
        assertEquals("Елена", restored.firstName)
        assertEquals("Ковалёва", restored.lastName)
        assertEquals("20.10.1995", restored.birthDate)
        assertEquals(phone, restored.phone)
        assertTrue(ServiceBookRepository.isLoggedIn.value)

        // После завершения регистрации черновик должен быть удалён
        assertNull("Черновик должен быть удалён после успешного завершения регистрации",
            ServiceBookRepository.getRegistrationDraft(phone))
    }

    @Test
    fun clearRegistrationDraftRemovesSavedDraft() {
        val phone = "+7 (999) 333-44-55"
        ServiceBookRepository.saveRegistrationDraft(
            firstName = "Петр",
            lastName = "Сидоров",
            birthDate = "01.01.1988",
            phone = phone
        )
        assertNotNull(ServiceBookRepository.getRegistrationDraft(phone))

        ServiceBookRepository.clearRegistrationDraft(phone)
        assertNull(ServiceBookRepository.getRegistrationDraft(phone))
    }

    @Test
    fun completeRegistrationAndLoginWithBlankProfileAndNoDraftFailsAndDoesNotLogIn() {
        val phone = "+7 (999) 000-11-22"
        // Нет черновика и профиль пуст
        assertNull(ServiceBookRepository.getRegistrationDraft(phone))
        assertEquals("", ServiceBookRepository.userProfile.value.firstName)

        val success = ServiceBookRepository.completeRegistrationAndLogin(phone)

        assertTrue("Нельзя завершать регистрацию без заполненного профиля и черновика", !success)
        assertTrue("Пользователь не должен быть авторизован", !ServiceBookRepository.isLoggedIn.value)
        assertTrue("Фиктивный профиль не должен быть сохранён", !ServiceBookRepository.isPhoneRegistered(phone))
    }

    @Test
    fun cleanDeviceLoginWithoutLocalDataReturnsNeedProfileAndDoesNotCreateFictitiousProfile() {
        val phone = "+7 (999) 777-88-99"
        // На чистом устройстве нет аккаунта и нет черновика
        assertNull(ServiceBookRepository.getRegistrationDraft(phone))
        assertTrue(!ServiceBookRepository.isPhoneRegistered(phone))

        val result = ServiceBookRepository.completeLogin(phone)

        assertEquals(LoginResult.NEED_PROFILE, result)
        assertTrue("Пользователь не должен считаться авторизованным без профиля",
            !ServiceBookRepository.isLoggedIn.value)
        assertTrue("Не должен создаваться фиктивный аккаунт в хранилище",
            !ServiceBookRepository.isPhoneRegistered(phone))
        assertEquals(phone, ServiceBookRepository.userProfile.value.phone)
        assertEquals("", ServiceBookRepository.userProfile.value.firstName)
    }

    @Test
    fun loginWithSavedDraftRestoresProfile() {
        val phone = "+7 (999) 555-66-77"
        ServiceBookRepository.saveRegistrationDraft(
            firstName = "Ольга",
            lastName = "Иванова",
            birthDate = "12.12.1992",
            phone = phone
        )

        val result = ServiceBookRepository.completeLogin(phone)

        assertEquals(LoginResult.DRAFT_RESTORED, result)
        assertTrue(ServiceBookRepository.isLoggedIn.value)
        assertEquals("Ольга", ServiceBookRepository.userProfile.value.firstName)
        assertEquals("Иванова", ServiceBookRepository.userProfile.value.lastName)
        assertTrue(ServiceBookRepository.isPhoneRegistered(phone))
        assertNull(ServiceBookRepository.getRegistrationDraft(phone))
    }

    @Test
    fun pendingAuthSavesAndRestoresAcrossProcessDeath() {
        val phone = "+7 (999) 111-00-11"
        ServiceBookRepository.savePendingAuth(
            requestId = "req_test_12345678901234567890",
            botUrl = "https://t.me/testbot?start=sb_123",
            phone = phone,
            mode = "login",
            expiresIn = 300,
            clientToken = "client_tok_1"
        )

        // Имитируем перезапуск процесса: заново инициализируем репозиторий с тем же SharedPreferences
        ServiceBookRepository.initForTesting(fakePrefs)

        val restored = ServiceBookRepository.getPendingAuth()
        assertNotNull("Ожидающий запрос авторизации должен восстановиться", restored)
        assertEquals("req_test_12345678901234567890", restored?.requestId)
        assertEquals(phone, restored?.phone)
        assertEquals("login", restored?.mode)
        assertEquals("client_tok_1", restored?.clientToken)

        // Очищаем сессию
        ServiceBookRepository.clearPendingAuth()
        assertNull(ServiceBookRepository.getPendingAuth())
    }

    @Test
    fun getPendingAuthReturnsNullAndClearsExpiredSession() {
        val phone = "+7 (999) 111-00-22"
        // Сохраняем сессию с expiresIn = 0 (deadlineMs <= now, уже истекла)
        ServiceBookRepository.savePendingAuth(
            requestId = "req_expired_1234567890123456",
            botUrl = "https://t.me/testbot?start=sb_exp",
            phone = phone,
            mode = "register",
            expiresIn = 0,
            clientToken = "client_tok_exp"
        )
        // Принудительно сдвигаем deadline_ms в прошлое
        fakePrefs.edit().putLong("pending_auth_deadline_ms", System.currentTimeMillis() - 1_000L).apply()

        val restored = ServiceBookRepository.getPendingAuth()
        assertNull("Истёкшая сессия не должна восстанавливаться", restored)
        assertTrue(
            "Ключ истёкшей сессии должен быть удалён из SharedPreferences",
            !fakePrefs.contains("pending_auth_request_id")
        )
    }

    @Test
    fun pendingAuthPreservesRegisterModeAcrossProcessRestart() {
        val phone = "+7 (999) 111-00-33"
        ServiceBookRepository.savePendingAuth(
            requestId = "req_reg_12345678901234567890",
            botUrl = "https://t.me/testbot?start=sb_reg",
            phone = phone,
            mode = "register",
            expiresIn = 300,
            clientToken = "client_tok_reg"
        )

        ServiceBookRepository.initForTesting(fakePrefs)

        val restored = ServiceBookRepository.getPendingAuth()
        assertNotNull(restored)
        assertEquals("register", restored?.mode)
    }

    @Test
    fun authAttemptTokenReusedOnRetryAndClearedOnFinish() {
        val phone = "+7 (999) 222-00-11"
        val firstToken = ServiceBookRepository.getOrCreateAuthAttemptToken(phone, "login")
        val retryToken = ServiceBookRepository.getOrCreateAuthAttemptToken(phone, "login")

        assertTrue(firstToken.length >= 16)
        assertEquals("Повторный запрос (например, после таймаута) должен использовать тот же client_token", firstToken, retryToken)

        // Другой номер получает другой токен
        val otherPhoneToken = ServiceBookRepository.getOrCreateAuthAttemptToken("+7 (999) 222-00-99", "login")
        assertTrue(otherPhoneToken != firstToken)

        // После очистки попытки генерируется новый токен
        ServiceBookRepository.clearAuthAttemptToken()
        val afterClearToken = ServiceBookRepository.getOrCreateAuthAttemptToken(phone, "login")
        assertTrue(afterClearToken != otherPhoneToken)
    }
}

class FakeSharedPreferences : SharedPreferences {
    private val map = mutableMapOf<String, Any>()

    override fun getAll(): MutableMap<String, *> = HashMap(map)

    override fun getString(key: String?, defValue: String?): String? =
        (map[key] as? String) ?: defValue

    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        @Suppress("UNCHECKED_CAST") (map[key] as? MutableSet<String>) ?: defValues

    override fun getInt(key: String?, defValue: Int): Int =
        (map[key] as? Int) ?: defValue

    override fun getLong(key: String?, defValue: Long): Long =
        (map[key] as? Long) ?: defValue

    override fun getFloat(key: String?, defValue: Float): Float =
        (map[key] as? Float) ?: defValue

    override fun getBoolean(key: String?, defValue: Boolean): Boolean =
        (map[key] as? Boolean) ?: defValue

    override fun contains(key: String?): Boolean = map.containsKey(key)

    override fun edit(): SharedPreferences.Editor = FakeEditor(map)

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    private class FakeEditor(private val targetMap: MutableMap<String, Any>) : SharedPreferences.Editor {
        private val staging = mutableMapOf<String, Any?>()
        private var clear = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor {
            if (key != null) staging[key] = value
            return this
        }

        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
            if (key != null) staging[key] = values
            return this
        }

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
            if (key != null) staging[key] = value
            return this
        }

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
            if (key != null) staging[key] = value
            return this
        }

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
            if (key != null) staging[key] = value
            return this
        }

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
            if (key != null) staging[key] = value
            return this
        }

        override fun remove(key: String?): SharedPreferences.Editor {
            if (key != null) staging[key] = this
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            clear = true
            return this
        }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            if (clear) targetMap.clear()
            for ((k, v) in staging) {
                if (v === this || v == null) {
                    targetMap.remove(k)
                } else {
                    targetMap[k] = v
                }
            }
        }
    }
}
