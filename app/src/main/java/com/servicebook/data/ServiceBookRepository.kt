package com.servicebook.data

import android.content.Context
import android.content.SharedPreferences
import com.servicebook.model.Car
import com.servicebook.model.DocumentItem
import com.servicebook.model.MaintenanceReminder
import com.servicebook.model.ServiceRecord
import com.servicebook.model.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

enum class LoginResult {
    SUCCESS,
    DRAFT_RESTORED,
    NEED_PROFILE
}

data class PendingAuthSession(
    val requestId: String,
    val botUrl: String,
    val phone: String,
    val mode: String,
    val expiresIn: Int,
    val deadlineMs: Long,
    val clientToken: String
)

object ServiceBookRepository {

    private const val PREFS_NAME = "servicebook_prefs"
    private const val KEY_IS_LOGGED_IN = "is_logged_in"
    private const val KEY_LAST_PHONE = "last_active_phone"
    private const val KEY_AUTH_SESSION_TOKEN = "auth_session_token"
    private const val KEY_YANDEX_ACCESS_TOKEN = "yandex_access_token"
    private const val KEY_AUTH_PROVIDER = "auth_provider"

    private var sharedPrefs: SharedPreferences? = null

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _car = MutableStateFlow(Car("", "", 0, "", 0))
    val car: StateFlow<Car> = _car.asStateFlow()

    private val _userProfile = MutableStateFlow(UserProfile("", "", "", ""))
    val userProfile: StateFlow<UserProfile> = _userProfile.asStateFlow()

    private val _records = MutableStateFlow<List<ServiceRecord>>(emptyList())
    val records: StateFlow<List<ServiceRecord>> = _records.asStateFlow()

    private val _reminders = MutableStateFlow<List<MaintenanceReminder>>(emptyList())
    val reminders: StateFlow<List<MaintenanceReminder>> = _reminders.asStateFlow()

    private val _documents = MutableStateFlow<List<DocumentItem>>(emptyList())
    val documents: StateFlow<List<DocumentItem>> = _documents.asStateFlow()

    fun getPhoneKey(phone: String): String {
        val digits = phone.filter { it.isDigit() }
        return if (digits.length >= 10) digits.takeLast(10) else digits
    }

    fun isPhoneRegistered(rawPhoneDigits: String): Boolean {
        val key = getPhoneKey(rawPhoneDigits)
        if (key.length != 10) return false
        val prefs = sharedPrefs ?: return false
        return prefs.contains("account_$key")
    }

    fun initForTesting(prefs: SharedPreferences) {
        sharedPrefs = prefs
        _isLoggedIn.value = false
        _userProfile.value = UserProfile("", "", "", "")
        _car.value = Car("", "", 0, "", 0)
        _records.value = emptyList()
        _documents.value = emptyList()
        _reminders.value = emptyList()
    }

    fun saveRegistrationDraft(firstName: String, lastName: String, birthDate: String, phone: String) {
        val prefs = sharedPrefs ?: return
        val key = getPhoneKey(phone)
        if (key.isBlank()) return
        val draftObj = JSONObject().apply {
            put("firstName", firstName.trim())
            put("lastName", lastName.trim())
            put("birthDate", birthDate.trim())
            put("phone", phone.trim())
            put("savedAt", System.currentTimeMillis())
        }
        prefs.edit().putString("draft_registration_$key", draftObj.toString()).apply()
    }

    fun getRegistrationDraft(phone: String): UserProfile? {
        val prefs = sharedPrefs ?: return null
        val key = getPhoneKey(phone)
        if (key.isBlank()) return null
        val jsonStr = prefs.getString("draft_registration_$key", null) ?: return null
        return try {
            val obj = JSONObject(jsonStr)
            val first = obj.optString("firstName", "").trim()
            val last = obj.optString("lastName", "").trim()
            val birth = obj.optString("birthDate", "").trim()
            val ph = obj.optString("phone", phone).trim()
            if (first.isNotBlank() || last.isNotBlank()) {
                UserProfile(first, last, birth, ph)
            } else null
        } catch (_: Exception) {
            null
        }
    }

    fun clearRegistrationDraft(phone: String) {
        val prefs = sharedPrefs ?: return
        val key = getPhoneKey(phone)
        if (key.isBlank()) return
        prefs.edit().remove("draft_registration_$key").apply()
    }

    fun savePendingAuth(
        requestId: String,
        botUrl: String,
        phone: String,
        mode: String,
        expiresIn: Int,
        clientToken: String
    ) {
        val prefs = sharedPrefs ?: return
        val deadlineMs = System.currentTimeMillis() + expiresIn.coerceAtLeast(1) * 1_000L
        prefs.edit().apply {
            putString("pending_auth_request_id", requestId)
            putString("pending_auth_bot_url", botUrl)
            putString("pending_auth_phone", phone)
            putString("pending_auth_mode", mode)
            putInt("pending_auth_expires_in", expiresIn)
            putLong("pending_auth_deadline_ms", deadlineMs)
            putString("pending_auth_client_token", clientToken)
            apply()
        }
    }

    fun getPendingAuth(): PendingAuthSession? {
        val prefs = sharedPrefs ?: return null
        val requestId = prefs.getString("pending_auth_request_id", null) ?: return null
        val deadlineMs = prefs.getLong("pending_auth_deadline_ms", 0L)
        if (deadlineMs <= System.currentTimeMillis()) {
            clearPendingAuth()
            return null
        }
        val botUrl = prefs.getString("pending_auth_bot_url", "") ?: ""
        val phone = prefs.getString("pending_auth_phone", "") ?: ""
        val mode = prefs.getString("pending_auth_mode", "login") ?: "login"
        val expiresIn = prefs.getInt("pending_auth_expires_in", 300)
        val clientToken = prefs.getString("pending_auth_client_token", "") ?: ""
        return PendingAuthSession(requestId, botUrl, phone, mode, expiresIn, deadlineMs, clientToken)
    }

    fun clearPendingAuth() {
        val prefs = sharedPrefs ?: return
        prefs.edit().apply {
            remove("pending_auth_request_id")
            remove("pending_auth_bot_url")
            remove("pending_auth_phone")
            remove("pending_auth_mode")
            remove("pending_auth_expires_in")
            remove("pending_auth_deadline_ms")
            remove("pending_auth_client_token")
            apply()
        }
    }

    fun getOrCreateAuthAttemptToken(phone: String, mode: String): String {
        val phoneKey = getPhoneKey(phone)
        val prefs = sharedPrefs
        if (prefs != null) {
            val savedToken = prefs.getString("attempt_auth_token", null)
            val savedPhone = prefs.getString("attempt_auth_phone_key", null)
            val savedMode = prefs.getString("attempt_auth_mode", null)
            val savedAt = prefs.getLong("attempt_auth_created_at", 0L)
            if (!savedToken.isNullOrBlank() &&
                savedPhone == phoneKey &&
                savedMode == mode &&
                System.currentTimeMillis() - savedAt < 300_000L
            ) {
                return savedToken
            }
        }
        val newToken = java.util.UUID.randomUUID().toString()
        prefs?.edit()?.apply {
            putString("attempt_auth_token", newToken)
            putString("attempt_auth_phone_key", phoneKey)
            putString("attempt_auth_mode", mode)
            putLong("attempt_auth_created_at", System.currentTimeMillis())
            apply()
        }
        return newToken
    }

    fun clearAuthAttemptToken() {
        val prefs = sharedPrefs ?: return
        prefs.edit().apply {
            remove("attempt_auth_token")
            remove("attempt_auth_phone_key")
            remove("attempt_auth_mode")
            remove("attempt_auth_created_at")
            apply()
        }
    }

    fun saveAuthSessionToken(token: String) {
        if (token.isBlank()) return
        sharedPrefs?.edit()?.putString(KEY_AUTH_SESSION_TOKEN, token)?.apply()
    }

    fun getAuthSessionToken(): String? =
        sharedPrefs?.getString(KEY_AUTH_SESSION_TOKEN, null)?.takeIf { it.isNotBlank() }

    fun clearAuthSessionToken() {
        sharedPrefs?.edit()?.remove(KEY_AUTH_SESSION_TOKEN)?.apply()
    }

    fun saveYandexAccessToken(token: String) {
        if (token.isBlank()) return
        sharedPrefs?.edit()?.putString(KEY_YANDEX_ACCESS_TOKEN, token)?.apply()
    }

    fun getYandexAccessToken(): String? =
        sharedPrefs?.getString(KEY_YANDEX_ACCESS_TOKEN, null)?.takeIf { it.isNotBlank() }

    fun clearYandexAccessToken() {
        sharedPrefs?.edit()?.remove(KEY_YANDEX_ACCESS_TOKEN)?.apply()
    }

    fun saveAuthProvider(provider: String) {
        if (provider.isBlank()) return
        sharedPrefs?.edit()?.putString(KEY_AUTH_PROVIDER, provider)?.apply()
    }

    fun getAuthProvider(): String? =
        sharedPrefs?.getString(KEY_AUTH_PROVIDER, null)?.takeIf { it.isNotBlank() }

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPrefs = prefs

        val loggedIn = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
        val lastPhone = prefs.getString(KEY_LAST_PHONE, "") ?: ""

        if (loggedIn && lastPhone.isNotBlank()) {
            val loaded = loadAccountByPhone(lastPhone)
            _isLoggedIn.value = loaded
        } else {
            _isLoggedIn.value = false
            _userProfile.value = UserProfile("", "", "", "")
            _car.value = Car("", "", 0, "", 0)
            _records.value = emptyList()
            _documents.value = emptyList()
            _reminders.value = emptyList()
        }
    }

    fun loadAccountByPhone(phone: String): Boolean {
        val key = getPhoneKey(phone)
        val prefs = sharedPrefs ?: return false
        val jsonStr = prefs.getString("account_$key", null) ?: return false

        return try {
            val userObj = JSONObject(jsonStr)
            val first = userObj.optString("firstName", "")
            val last = userObj.optString("lastName", "")
            val birth = userObj.optString("birthDate", "")
            val fullPhone = userObj.optString("phone", phone)
            _userProfile.value = UserProfile(first, last, birth, fullPhone)

            val carObj = userObj.optJSONObject("car")
            if (carObj != null) {
                val brand = carObj.optString("brand", "")
                val model = carObj.optString("model", "")
                val year = carObj.optInt("year", 2020)
                val engine = carObj.optString("engine", "")
                val mileage = carObj.optInt("mileage", 0)
                _car.value = Car(brand, model, year, engine, mileage)
                if (brand.isNotBlank() && mileage > 0) {
                    generateRemindersForMileage(mileage)
                } else {
                    _reminders.value = emptyList()
                }
            } else {
                _car.value = Car("", "", 0, "", 0)
                _reminders.value = emptyList()
            }

            val recList = mutableListOf<ServiceRecord>()
            val recArray = userObj.optJSONArray("records")
            if (recArray != null) {
                for (i in 0 until recArray.length()) {
                    val r = recArray.getJSONObject(i)
                    recList.add(
                        ServiceRecord(
                            id = r.getString("id"),
                            title = r.getString("title"),
                            category = r.getString("category"),
                            date = r.getString("date"),
                            mileage = r.getInt("mileage"),
                            price = r.getInt("price"),
                            note = r.optString("note", ""),
                            receiptFileName = r.optString("receiptFileName", ""),
                            receiptFileSize = r.optString("receiptFileSize", ""),
                            receiptDrawableName = r.optString("receiptDrawableName", "receipt_oil"),
                            receiptFilePath = if (r.has("receiptFilePath") && !r.isNull("receiptFilePath")) r.getString("receiptFilePath") else null
                        )
                    )
                }
            }
            _records.value = recList

            val docList = mutableListOf<DocumentItem>()
            val docArray = userObj.optJSONArray("documents")
            if (docArray != null) {
                for (i in 0 until docArray.length()) {
                    val d = docArray.getJSONObject(i)
                    docList.add(
                        DocumentItem(
                            id = d.getString("id"),
                            title = d.getString("title"),
                            date = d.getString("date"),
                            fileFormat = d.optString("fileFormat", "JPG"),
                            fileSize = d.optString("fileSize", ""),
                            isReceipt = d.optBoolean("isReceipt", true),
                            drawableResName = d.optString("drawableResName", "receipt_oil"),
                            filePath = if (d.has("filePath") && !d.isNull("filePath")) d.getString("filePath") else null
                        )
                    )
                }
            }
            _documents.value = docList
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun saveCurrentAccount() {
        val prefs = sharedPrefs ?: return
        val currentPhone = _userProfile.value.phone
        val key = getPhoneKey(currentPhone)
        if (key.length != 10) return

        try {
            val userObj = JSONObject().apply {
                put("firstName", _userProfile.value.firstName)
                put("lastName", _userProfile.value.lastName)
                put("birthDate", _userProfile.value.birthDate)
                put("phone", _userProfile.value.phone)

                val carObj = JSONObject().apply {
                    put("brand", _car.value.brand)
                    put("model", _car.value.model)
                    put("year", _car.value.year)
                    put("engine", _car.value.engine)
                    put("mileage", _car.value.mileage)
                }
                put("car", carObj)

                val recArray = JSONArray()
                for (r in _records.value) {
                    val rObj = JSONObject().apply {
                        put("id", r.id)
                        put("title", r.title)
                        put("category", r.category)
                        put("date", r.date)
                        put("mileage", r.mileage)
                        put("price", r.price)
                        put("note", r.note)
                        put("receiptFileName", r.receiptFileName)
                        put("receiptFileSize", r.receiptFileSize)
                        put("receiptDrawableName", r.receiptDrawableName)
                        if (r.receiptFilePath != null) put("receiptFilePath", r.receiptFilePath)
                    }
                    recArray.put(rObj)
                }
                put("records", recArray)

                val docArray = JSONArray()
                for (d in _documents.value) {
                    val dObj = JSONObject().apply {
                        put("id", d.id)
                        put("title", d.title)
                        put("date", d.date)
                        put("fileFormat", d.fileFormat)
                        put("fileSize", d.fileSize)
                        put("isReceipt", d.isReceipt)
                        put("drawableResName", d.drawableResName)
                        if (d.filePath != null) put("filePath", d.filePath)
                    }
                    docArray.put(dObj)
                }
                put("documents", docArray)
            }

            prefs.edit().apply {
                putString("account_$key", userObj.toString())
                putString(KEY_LAST_PHONE, currentPhone)
                putBoolean(KEY_IS_LOGGED_IN, _isLoggedIn.value)
                apply()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun prepareRegistration(firstName: String, lastName: String, birthDate: String, phone: String) {
        val trimmedPhone = phone.trim()
        val trimmedFirst = firstName.trim()
        val trimmedLast = lastName.trim()
        val trimmedBirth = birthDate.trim()
        _userProfile.value = UserProfile(
            firstName = trimmedFirst,
            lastName = trimmedLast,
            birthDate = trimmedBirth,
            phone = trimmedPhone
        )
        _car.value = Car("", "", 0, "", 0)
        _records.value = emptyList()
        _documents.value = emptyList()
        _reminders.value = emptyList()
        saveRegistrationDraft(trimmedFirst, trimmedLast, trimmedBirth, trimmedPhone)
        // NOT logged in until code is verified!
    }

    fun completeRegistrationAndLogin(phone: String? = null): Boolean {
        val targetPhone = phone ?: _userProfile.value.phone
        if (_userProfile.value.firstName.isBlank() && targetPhone.isNotBlank()) {
            val draft = getRegistrationDraft(targetPhone)
            if (draft != null && (draft.firstName.isNotBlank() || draft.lastName.isNotBlank())) {
                _userProfile.value = draft
            }
        }
        if (_userProfile.value.firstName.isBlank()) {
            _isLoggedIn.value = false
            return false
        }
        _isLoggedIn.value = true
        saveCurrentAccount()
        if (targetPhone.isNotBlank()) {
            clearRegistrationDraft(targetPhone)
        }
        return true
    }

    fun completeRegistrationAndLogin(first: String, last: String, birthDate: String, phone: String): Boolean {
        prepareRegistration(first, last, birthDate, phone)
        return completeRegistrationAndLogin(phone)
    }

    fun completeLogin(phone: String): LoginResult {
        val loaded = loadAccountByPhone(phone)
        if (loaded) {
            _isLoggedIn.value = true
            saveCurrentAccount()
            return LoginResult.SUCCESS
        }

        val current = _userProfile.value
        if (current.phone == phone && current.firstName.isNotBlank()) {
            _isLoggedIn.value = true
            saveCurrentAccount()
            clearRegistrationDraft(phone)
            return LoginResult.SUCCESS
        }

        val draft = getRegistrationDraft(phone)
        if (draft != null && (draft.firstName.isNotBlank() || draft.lastName.isNotBlank())) {
            _userProfile.value = draft
            _car.value = Car("", "", 0, "", 0)
            _records.value = emptyList()
            _documents.value = emptyList()
            _reminders.value = emptyList()
            _isLoggedIn.value = true
            saveCurrentAccount()
            clearRegistrationDraft(phone)
            return LoginResult.DRAFT_RESTORED
        }

        _userProfile.value = UserProfile(phone = phone)
        _car.value = Car("", "", 0, "", 0)
        _records.value = emptyList()
        _documents.value = emptyList()
        _reminders.value = emptyList()
        _isLoggedIn.value = false
        return LoginResult.NEED_PROFILE
    }

    fun loadCarAndRecordsOnly(phone: String) {
        val key = getPhoneKey(phone)
        val prefs = sharedPrefs ?: return
        val jsonStr = prefs.getString("account_$key", null) ?: return
        try {
            val userObj = JSONObject(jsonStr)
            val carObj = userObj.optJSONObject("car")
            if (carObj != null) {
                val brand = carObj.optString("brand", "")
                val model = carObj.optString("model", "")
                val year = carObj.optInt("year", 2020)
                val engine = carObj.optString("engine", "")
                val mileage = carObj.optInt("mileage", 0)
                _car.value = Car(brand, model, year, engine, mileage)
                if (brand.isNotBlank() && mileage > 0) {
                    generateRemindersForMileage(mileage)
                } else {
                    _reminders.value = emptyList()
                }
            }

            val recList = mutableListOf<ServiceRecord>()
            val recArr = userObj.optJSONArray("records")
            if (recArr != null) {
                for (i in 0 until recArr.length()) {
                    val obj = recArr.getJSONObject(i)
                    recList.add(
                        ServiceRecord(
                            id = obj.optString("id", "rec-$i"),
                            title = obj.optString("title", ""),
                            category = obj.optString("category", ""),
                            date = obj.optString("date", ""),
                            mileage = obj.optInt("mileage", 0),
                            price = obj.optInt("price", 0),
                            note = obj.optString("note", ""),
                            receiptFileName = obj.optString("receiptFileName", ""),
                            receiptFileSize = obj.optString("receiptFileSize", ""),
                            receiptDrawableName = obj.optString("receiptDrawableName", "receipt_oil"),
                            receiptFilePath = if (obj.has("receiptFilePath")) obj.optString("receiptFilePath") else null
                        )
                    )
                }
            }
            _records.value = recList

            val docList = mutableListOf<DocumentItem>()
            val docArr = userObj.optJSONArray("documents")
            if (docArr != null) {
                for (i in 0 until docArr.length()) {
                    val obj = docArr.getJSONObject(i)
                    docList.add(
                        DocumentItem(
                            id = obj.optString("id", "doc-$i"),
                            title = obj.optString("title", ""),
                            date = obj.optString("date", ""),
                            fileFormat = obj.optString("fileFormat", "PDF"),
                            fileSize = obj.optString("fileSize", "0 КБ"),
                            isReceipt = obj.optBoolean("isReceipt", false),
                            drawableResName = obj.optString("drawableResName", "receipt_oil"),
                            filePath = if (obj.has("filePath")) obj.optString("filePath") else null
                        )
                    )
                }
            }
            _documents.value = docList
        } catch (_: Exception) {}
    }

    suspend fun fetchYandexProfile(token: String) = withContext(Dispatchers.IO) {
        try {
            val url = java.net.URL("https://login.yandex.ru/info?format=json")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.setRequestProperty("Authorization", "OAuth $token")
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            if (conn.responseCode == 200) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseText)
                val realName = json.optString("real_name", "").trim()
                val firstName = json.optString("first_name", "").ifBlank {
                    realName.split(" ").firstOrNull() ?: ""
                }
                val lastName = json.optString("last_name", "").ifBlank {
                    realName.split(" ").drop(1).joinToString(" ")
                }
                val birthday = json.optString("birthday", "")
                var formattedBirth = if (birthday.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                    val p = birthday.split("-")
                    "${p[2]}.${p[1]}.${p[0]}"
                } else {
                    birthday
                }
                val phoneObj = json.optJSONObject("default_phone")
                val rawPhone = phoneObj?.optString("number", "") ?: ""
                val digits = rawPhone.filter { it.isDigit() }.takeLast(10)
                val cleanPhone = if (digits.length == 10) {
                    "+7 (${digits.take(3)}) ${digits.drop(3).take(3)}-${digits.drop(6).take(2)}-${digits.drop(8)}"
                } else if (rawPhone.isNotBlank()) {
                    rawPhone
                } else {
                    ""
                }

                if (formattedBirth.isBlank()) {
                    val existingKey = getPhoneKey(cleanPhone)
                    val existingJson = sharedPrefs?.getString("account_$existingKey", null)
                    if (existingJson != null) {
                        val existingBirth = JSONObject(existingJson).optString("birthDate", "")
                        if (existingBirth.isNotBlank()) {
                            formattedBirth = existingBirth
                        }
                    }
                }
                
                _userProfile.value = UserProfile(
                    firstName = firstName,
                    lastName = lastName,
                    birthDate = formattedBirth,
                    phone = cleanPhone
                )
                if (cleanPhone.isNotBlank() && isPhoneRegistered(cleanPhone)) {
                    loadCarAndRecordsOnly(cleanPhone)
                    _isLoggedIn.value = true
                    saveCurrentAccount()
                } else {
                    _isLoggedIn.value = false
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun saveCar(brand: String, model: String, year: Int, engine: String, mileage: Int) {
        _car.value = Car(
            brand = brand.trim(),
            model = model.trim(),
            year = year,
            engine = engine.trim(),
            mileage = mileage
        )
        if (brand.isNotBlank() && mileage > 0) {
            generateRemindersForMileage(mileage)
        } else {
            _reminders.value = emptyList()
        }
        saveCurrentAccount()
    }

    fun updateCar(brand: String, model: String, year: Int, engine: String, mileage: Int) {
        saveCar(brand, model, year, engine, mileage)
    }

    fun updateCar(car: Car) {
        _car.value = car
        if (car.brand.isNotBlank() && car.mileage > 0) {
            generateRemindersForMileage(car.mileage)
        }
        saveCurrentAccount()
    }

    fun updateMileage(mileage: Int) {
        _car.update { it.copy(mileage = mileage) }
        generateRemindersForMileage(mileage)
        saveCurrentAccount()
    }

    fun addRecord(record: ServiceRecord) {
        _records.update { listOf(record) + it }
        if (record.mileage > _car.value.mileage) {
            _car.update { it.copy(mileage = record.mileage) }
            generateRemindersForMileage(record.mileage)
        }
        saveCurrentAccount()
    }

    fun addRecord(
        title: String,
        category: String,
        date: String,
        mileage: Int,
        price: Int,
        note: String,
        receiptFileName: String = "",
        receiptFileSize: String = "",
        receiptDrawableName: String = "receipt_oil",
        receiptFilePath: String? = null
    ) {
        val newRecord = ServiceRecord(
            id = "rec-${System.currentTimeMillis()}",
            title = title.trim(),
            category = category.trim(),
            date = date.trim(),
            mileage = mileage,
            price = price,
            note = note.trim(),
            receiptFileName = receiptFileName,
            receiptFileSize = receiptFileSize,
            receiptDrawableName = receiptDrawableName,
            receiptFilePath = receiptFilePath
        )
        addRecord(newRecord)
    }

    fun deleteRecord(recordId: String) {
        _records.update { list -> list.filterNot { it.id == recordId } }
        saveCurrentAccount()
    }

    fun addDocument(
        title: String,
        format: String,
        size: String,
        isReceipt: Boolean,
        filePath: String? = null
    ) {
        val newDoc = DocumentItem(
            id = "doc-${System.currentTimeMillis()}",
            title = title.trim(),
            date = "Сегодня",
            fileFormat = format.uppercase(),
            fileSize = size,
            isReceipt = isReceipt,
            drawableResName = "receipt_oil",
            filePath = filePath
        )
        _documents.update { listOf(newDoc) + it }
        saveCurrentAccount()
    }

    fun updateReminder(
        reminderId: String,
        targetMileage: Int,
        targetDate: String,
        intervalKm: Int = 10000,
        intervalMonths: Int = 12,
        push: Boolean = true
    ) {
        _reminders.update { list ->
            list.map { rem ->
                if (rem.id == reminderId) {
                    val remaining = targetMileage - _car.value.mileage
                    rem.copy(
                        targetMileage = targetMileage,
                        targetDate = targetDate,
                        intervalKm = intervalKm,
                        intervalMonths = intervalMonths,
                        pushEnabled = push,
                        remainingKm = remaining,
                        isOverdue = remaining <= 0
                    )
                } else rem
            }
        }
        saveCurrentAccount()
    }

    fun addDocument(
        title: String,
        fileFormat: String,
        fileSize: String,
        isReceipt: Boolean,
        drawableResName: String = "receipt_oil",
        filePath: String? = null
    ) {
        val newDoc = DocumentItem(
            id = "doc-${System.currentTimeMillis()}",
            title = title.trim(),
            date = "Сегодня",
            fileFormat = fileFormat.uppercase(),
            fileSize = fileSize,
            isReceipt = isReceipt,
            drawableResName = drawableResName,
            filePath = filePath
        )
        _documents.update { listOf(newDoc) + it }
        saveCurrentAccount()
    }

    fun deleteDocument(docId: String) {
        _documents.update { list -> list.filterNot { it.id == docId } }
        saveCurrentAccount()
    }

    fun updateUserProfile(first: String, last: String, birthDate: String) {
        _userProfile.update { it.copy(firstName = first, lastName = last, birthDate = birthDate) }
        saveCurrentAccount()
    }

    fun logout() {
        _isLoggedIn.value = false
        sharedPrefs?.edit()?.apply {
            putBoolean(KEY_IS_LOGGED_IN, false)
            apply()
        }
    }

    fun deleteAccountAndAllData(context: Context? = null) {
        val currentPhone = _userProfile.value.phone.ifBlank {
            sharedPrefs?.getString(KEY_LAST_PHONE, "") ?: ""
        }
        val key = getPhoneKey(currentPhone)

        if (context != null) {
            val filesDirCanonical = context.filesDir.canonicalFile
            val otherAccountPaths = collectOtherAccountFilePaths(key, filesDirCanonical)
            val hasOtherAccounts = sharedPrefs?.all?.keys?.any {
                it.startsWith("account_") && it != "account_$key"
            } == true

            val referencedPaths = buildSet {
                _records.value.mapNotNull { it.receiptFilePath }.forEach { add(it) }
                _documents.value.mapNotNull { it.filePath }.forEach { add(it) }
            }
            for (path in referencedPaths) {
                try {
                    val f = java.io.File(path).canonicalFile
                    if (f.parentFile == filesDirCanonical && f !in otherAccountPaths && f.exists()) {
                        f.delete()
                    }
                } catch (_: Exception) {}
            }
            if (!hasOtherAccounts) {
                try {
                    context.cacheDir.listFiles()?.forEach { f ->
                        f.deleteRecursively()
                    }
                } catch (_: Exception) {}
            }
        }

        sharedPrefs?.edit()?.apply {
            if (key.isNotBlank()) {
                remove("account_$key")
                remove("draft_registration_$key")
            }
            remove(KEY_IS_LOGGED_IN)
            remove(KEY_LAST_PHONE)
            remove(KEY_AUTH_SESSION_TOKEN)
            remove(KEY_YANDEX_ACCESS_TOKEN)
            remove(KEY_AUTH_PROVIDER)
            remove("pending_auth_request_id")
            remove("pending_auth_bot_url")
            remove("pending_auth_phone")
            remove("pending_auth_mode")
            remove("pending_auth_expires_in")
            remove("pending_auth_deadline_ms")
            remove("pending_auth_client_token")
            remove("attempt_auth_token")
            remove("attempt_auth_phone_key")
            remove("attempt_auth_mode")
            remove("attempt_auth_created_at")
            apply()
        }

        _isLoggedIn.value = false
        _userProfile.value = UserProfile("", "", "", "")
        _car.value = Car("", "", 0, "", 0)
        _records.value = emptyList()
        _reminders.value = emptyList()
        _documents.value = emptyList()
    }

    private fun collectOtherAccountFilePaths(currentKey: String, filesDirCanonical: java.io.File): Set<java.io.File> {
        val prefs = sharedPrefs ?: return emptySet()
        val paths = mutableSetOf<java.io.File>()
        for ((prefKey, value) in prefs.all) {
            if (!prefKey.startsWith("account_") || prefKey == "account_$currentKey") continue
            val jsonStr = value as? String ?: continue
            try {
                val obj = JSONObject(jsonStr)
                val recArr = obj.optJSONArray("records")
                if (recArr != null) {
                    for (i in 0 until recArr.length()) {
                        val p = recArr.optJSONObject(i)?.optString("receiptFilePath", "") ?: ""
                        if (p.isNotBlank()) {
                            val f = java.io.File(p).canonicalFile
                            if (f.parentFile == filesDirCanonical) paths.add(f)
                        }
                    }
                }
                val docArr = obj.optJSONArray("documents")
                if (docArr != null) {
                    for (i in 0 until docArr.length()) {
                        val p = docArr.optJSONObject(i)?.optString("filePath", "") ?: ""
                        if (p.isNotBlank()) {
                            val f = java.io.File(p).canonicalFile
                            if (f.parentFile == filesDirCanonical) paths.add(f)
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        return paths
    }

    private fun generateRemindersForMileage(currentMileage: Int) {
        val list = mutableListOf<MaintenanceReminder>()

        val oilInterval = 10000
        val nextOil = ((currentMileage / oilInterval) + 1) * oilInterval
        val oilDiff = nextOil - currentMileage
        list.add(
            MaintenanceReminder(
                id = "rem-oil",
                title = "Замена масла и фильтров",
                targetMileage = nextOil,
                remainingKm = oilDiff,
                targetDate = "Через 6 месяцев",
                isOverdue = oilDiff <= 0,
                intervalKm = 10000
            )
        )

        val brakeInterval = 30000
        val nextBrake = ((currentMileage / brakeInterval) + 1) * brakeInterval
        val brakeDiff = nextBrake - currentMileage
        list.add(
            MaintenanceReminder(
                id = "rem-brakes",
                title = "Проверка тормозной системы",
                targetMileage = nextBrake,
                remainingKm = brakeDiff,
                targetDate = "Через 1 год",
                isOverdue = brakeDiff <= 0,
                intervalKm = 30000
            )
        )

        val plugsInterval = 30000
        val nextPlugs = ((currentMileage / plugsInterval) + 1) * plugsInterval
        val plugsDiff = nextPlugs - currentMileage
        list.add(
            MaintenanceReminder(
                id = "rem-plugs",
                title = "Замена свечей зажигания",
                targetMileage = nextPlugs,
                remainingKm = plugsDiff,
                targetDate = "Через 1 год",
                isOverdue = plugsDiff <= 0,
                intervalKm = 30000
            )
        )

        val coolantInterval = 60000
        val nextCoolant = ((currentMileage / coolantInterval) + 1) * coolantInterval
        val coolantDiff = nextCoolant - currentMileage
        list.add(
            MaintenanceReminder(
                id = "rem-coolant",
                title = "Замена охлаждающей жидкости",
                targetMileage = nextCoolant,
                remainingKm = coolantDiff,
                targetDate = "Через 2 года",
                isOverdue = coolantDiff <= 0,
                intervalKm = 60000
            )
        )

        _reminders.value = list
    }
}
