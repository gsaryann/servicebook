package com.servicebook.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import com.servicebook.data.LoginResult
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.servicebook.BuildConfig
import com.servicebook.data.AuthApiClient
import com.servicebook.data.AuthRequestStatus
import com.servicebook.data.ServiceBookRepository
import com.servicebook.ui.components.CustomInputField
import com.servicebook.ui.components.PrimaryButton
import com.servicebook.ui.components.SecondaryButton
import com.servicebook.ui.components.TopBarHeader
import com.servicebook.ui.theme.BackgroundLight
import com.servicebook.ui.theme.BorderStroke
import com.servicebook.ui.theme.NavyPrimary
import com.servicebook.ui.theme.RedDanger
import com.servicebook.ui.theme.SurfaceWhite
import com.servicebook.ui.theme.TextPrimary
import com.servicebook.ui.theme.TextSecondary
import com.servicebook.ui.theme.TextTertiary
import com.servicebook.util.DateVisualTransformation
import com.servicebook.util.PhoneVisualTransformation
import com.servicebook.util.ValidationUtils
import kotlinx.coroutines.delay

@Composable
fun LoginScreen(
    onTelegramClick: (String) -> Unit,
    onYandexClick: () -> Unit,
    onRegisterClick: () -> Unit,
    onBack: () -> Unit,
    isTelegramLoading: Boolean = false,
    telegramError: String? = null
) {
    var phoneDigits by remember { mutableStateOf("") }
    var phoneError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(bottom = 20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        TopBarHeader(showBack = true, onBackClick = onBack)

        Spacer(modifier = Modifier.height(10.dp))

        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Text(
                text = "Вход в ServiceBook",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Введите ваш номер телефона для входа через Telegram или войдите через Яндекс ID.",
                fontSize = 15.sp,
                color = TextSecondary,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(28.dp))

            CustomInputField(
                label = "Номер телефона",
                value = phoneDigits,
                placeholder = "999 123-45-67",
                prefixText = "+7 ",
                onlyDigits = true,
                maxLength = 10,
                visualTransformation = PhoneVisualTransformation(),
                isError = phoneError != null,
                errorMessage = phoneError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                onValueChange = {
                    phoneDigits = it
                    phoneError = null
                }
            )

            Spacer(modifier = Modifier.height(20.dp))

            PrimaryButton(
                text = if (isTelegramLoading) "Подключение к серверу..." else "Подтвердить вход в Telegram",
                enabled = !isTelegramLoading,
                onClick = {
                    if (phoneDigits.length != 10) {
                        phoneError = "Введите 10 цифр номера"
                    } else {
                        phoneError = null
                        onTelegramClick("+7$phoneDigits")
                    }
                }
            )
            if (telegramError != null) {
                Text(text = telegramError, color = RedDanger, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = BorderStroke)
                Text(
                    text = "или",
                    modifier = Modifier.padding(horizontal = 12.dp),
                    color = TextTertiary,
                    fontSize = 13.sp
                )
                HorizontalDivider(modifier = Modifier.weight(1f), color = BorderStroke)
            }

            Spacer(modifier = Modifier.height(20.dp))

            SecondaryButton(
                text = "Войти через Яндекс ID",
                onClick = onYandexClick
            )

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "Нет аккаунта? Зарегистрироваться",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = NavyPrimary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onRegisterClick() }
                    .padding(8.dp),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Продолжая, вы принимаете условия сервиса и политику конфиденциальности.",
                fontSize = 12.sp,
                color = TextTertiary,
                lineHeight = 18.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun RegisterScreen(
    onContinueTelegram: () -> Unit,
    onContinueYandex: () -> Unit,
    onRegistered: () -> Unit = {},
    onBack: () -> Unit,
    isTelegramLoading: Boolean = false,
    telegramError: String? = null
) {
    val context = LocalContext.current
    val savedProfile by ServiceBookRepository.userProfile.collectAsState()
    val isLoggedIn by ServiceBookRepository.isLoggedIn.collectAsState()

    var firstName by remember(savedProfile.firstName) { mutableStateOf(savedProfile.firstName) }
    var lastName by remember(savedProfile.lastName) { mutableStateOf(savedProfile.lastName) }
    var birthDateDigits by remember(savedProfile.birthDate) {
        mutableStateOf(savedProfile.birthDate.filter { it.isDigit() })
    }
    var phoneDigits by remember(savedProfile.phone) {
        mutableStateOf(savedProfile.phone.filter { it.isDigit() }.takeLast(10))
    }
    var agreed by remember { mutableStateOf(true) }

    var touchedFirstName by remember { mutableStateOf(false) }
    var touchedLastName by remember { mutableStateOf(false) }
    var touchedBirthDate by remember { mutableStateOf(false) }
    var touchedPhone by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(savedProfile) {
        if (savedProfile.firstName.isNotBlank()) firstName = savedProfile.firstName
        if (savedProfile.lastName.isNotBlank()) lastName = savedProfile.lastName
        val bDigits = savedProfile.birthDate.filter { it.isDigit() }
        if (bDigits.isNotBlank()) birthDateDigits = bDigits
        val pDigits = savedProfile.phone.filter { it.isDigit() }.takeLast(10)
        if (pDigits.isNotBlank()) phoneDigits = pDigits
    }

    val isFirstNameValid = ValidationUtils.isValidName(firstName)
    val isLastNameValid = ValidationUtils.isValidName(lastName)
    val isBirthDateValid = ValidationUtils.isValidDateDigits(birthDateDigits)
    val isPhoneAlreadyRegistered = phoneDigits.length == 10 && !isLoggedIn && ServiceBookRepository.isPhoneRegistered(phoneDigits)
    val isPhoneValid = phoneDigits.length == 10 && !isPhoneAlreadyRegistered

    val isFormValid = isFirstNameValid && isLastNameValid && isBirthDateValid && isPhoneValid && agreed

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        TopBarHeader(showBack = true, onBackClick = onBack)

        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Text(
                text = "Создайте аккаунт",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Заполните данные профиля.\nДальше добавим ваш автомобиль в книжку.",
                fontSize = 15.sp,
                color = TextSecondary,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            CustomInputField(
                label = "Имя *",
                value = firstName,
                placeholder = "Введите ваше имя",
                onlyLetters = true,
                isError = touchedFirstName && !isFirstNameValid,
                errorMessage = "Минимум 2 буквы, только кириллица или латиница",
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    keyboardType = KeyboardType.Text
                ),
                onValueChange = {
                    firstName = it
                    touchedFirstName = true
                }
            )
            Spacer(modifier = Modifier.height(12.dp))

            CustomInputField(
                label = "Фамилия *",
                value = lastName,
                placeholder = "Введите вашу фамилию",
                onlyLetters = true,
                isError = touchedLastName && !isLastNameValid,
                errorMessage = "Минимум 2 буквы, только кириллица или латиница",
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    keyboardType = KeyboardType.Text
                ),
                onValueChange = {
                    lastName = it
                    touchedLastName = true
                }
            )
            Spacer(modifier = Modifier.height(12.dp))

            CustomInputField(
                label = "Дата рождения *",
                value = birthDateDigits,
                placeholder = "ДД.ММ.ГГГГ",
                onlyDigits = true,
                maxLength = 8,
                visualTransformation = DateVisualTransformation(),
                isError = touchedBirthDate && !isBirthDateValid,
                errorMessage = "Укажите реальную дату рождения (например, 15051995)",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                onValueChange = {
                    birthDateDigits = it
                    touchedBirthDate = true
                }
            )
            Spacer(modifier = Modifier.height(12.dp))

            CustomInputField(
                label = "Номер телефона *",
                value = phoneDigits,
                prefixText = "+7",
                placeholder = "(999) 000-00-00",
                onlyDigits = true,
                maxLength = 10,
                visualTransformation = PhoneVisualTransformation(),
                isError = (touchedPhone && !isPhoneValid) || isPhoneAlreadyRegistered,
                errorMessage = if (isPhoneAlreadyRegistered) "Этот номер уже зарегистрирован. Войдите в аккаунт" else "Введите 10 цифр номера (например, 9991234567)",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                onValueChange = {
                    phoneDigits = it
                    touchedPhone = true
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { agreed = !agreed },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (agreed) NavyPrimary else SurfaceWhite)
                        .border(1.dp, if (agreed) NavyPrimary else BorderStroke, RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (agreed) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = SurfaceWhite,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Принимаю условия и даю согласие на обработку персональных данных",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            if (isLoggedIn) {
                PrimaryButton(
                    text = "Завершить регистрацию",
                    enabled = isFormValid,
                    onClick = {
                        val fullPhone = "+7 (${phoneDigits.take(3)}) ${phoneDigits.drop(3).take(3)}-${phoneDigits.drop(6).take(2)}-${phoneDigits.drop(8)}"
                        val fullBirth = ValidationUtils.formatDigitsToDate(birthDateDigits)
                        ServiceBookRepository.completeRegistrationAndLogin(firstName, lastName, fullBirth, fullPhone)
                        onRegistered()
                    }
                )
            } else {
                PrimaryButton(
                    text = if (isTelegramLoading) "Подключение к серверу..." else "Продолжить в Telegram",
                    enabled = isFormValid && !isTelegramLoading,
                    onClick = {
                        val fullPhone = "+7 (${phoneDigits.take(3)}) ${phoneDigits.drop(3).take(3)}-${phoneDigits.drop(6).take(2)}-${phoneDigits.drop(8)}"
                        val fullBirth = ValidationUtils.formatDigitsToDate(birthDateDigits)
                        ServiceBookRepository.prepareRegistration(firstName, lastName, fullBirth, fullPhone)
                        onContinueTelegram()
                    }
                )
                if (telegramError != null) {
                    Text(text = telegramError, color = RedDanger, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                }

                Spacer(modifier = Modifier.height(12.dp))

                SecondaryButton(
                    text = "Заполнить через Яндекс ID",
                    onClick = onContinueYandex
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "После подтверждения вы сразу перейдёте к добавлению авто",
                fontSize = 12.sp,
                color = TextTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun TelegramAuthScreen(
    isRegistration: Boolean = false,
    requestId: String,
    botUrl: String,
    phone: String,
    expiresIn: Int,
    onOpenBot: (String) -> Unit,
    onSuccess: () -> Unit,
    onNeedProfile: () -> Unit = {},
    onBack: () -> Unit
) {
    var status by remember(requestId) { mutableStateOf(AuthRequestStatus.PENDING) }
    var connectionError by remember(requestId) { mutableStateOf<String?>(null) }
    var sessionExchangeFailed by remember(requestId) { mutableStateOf(false) }
    var retrySessionCounter by remember(requestId) { mutableStateOf(0) }

    LaunchedEffect(requestId, retrySessionCounter) {
        sessionExchangeFailed = false
        val deadline = SystemClock.elapsedRealtime() + expiresIn.coerceAtLeast(1) * 1_000L
        while (SystemClock.elapsedRealtime() < deadline) {
            try {
                val nextStatus = AuthApiClient.getStatus(BuildConfig.AUTH_API_BASE_URL, requestId)
                status = nextStatus
                connectionError = null
                when (nextStatus) {
                    AuthRequestStatus.APPROVED -> {
                        val clientToken = ServiceBookRepository.getPendingAuth()?.clientToken
                            ?.takeIf { it.isNotBlank() }
                            ?: ServiceBookRepository.getOrCreateAuthAttemptToken(
                                phone,
                                if (isRegistration) "register" else "login"
                            )
                        val existingSession = ServiceBookRepository.getAuthSessionToken()
                        if (existingSession.isNullOrBlank()) {
                            try {
                                val sessionToken = AuthApiClient.exchangeSession(
                                    BuildConfig.AUTH_API_BASE_URL,
                                    requestId,
                                    clientToken
                                )
                                ServiceBookRepository.saveAuthSessionToken(sessionToken)
                                ServiceBookRepository.saveAuthProvider("telegram")
                            } catch (error: Exception) {
                                sessionExchangeFailed = true
                                connectionError = error.message ?: "Не удалось получить серверную сессию. Повторите попытку."
                                return@LaunchedEffect
                            }
                        } else {
                            ServiceBookRepository.saveAuthProvider("telegram")
                        }
                        if (isRegistration) {
                            val success = ServiceBookRepository.completeRegistrationAndLogin(phone)
                            if (success) onSuccess() else onNeedProfile()
                        } else {
                            when (ServiceBookRepository.completeLogin(phone)) {
                                LoginResult.SUCCESS, LoginResult.DRAFT_RESTORED -> onSuccess()
                                LoginResult.NEED_PROFILE -> onNeedProfile()
                            }
                        }
                        return@LaunchedEffect
                    }
                    AuthRequestStatus.REJECTED, AuthRequestStatus.EXPIRED -> {
                        ServiceBookRepository.clearPendingAuth()
                        ServiceBookRepository.clearAuthAttemptToken()
                        return@LaunchedEffect
                    }
                    AuthRequestStatus.PENDING -> delay(1_500)
                }
            } catch (error: Exception) {
                connectionError = error.message ?: "Нет соединения с локальным сервером. Проверьте Wi-Fi и запущенный сервер."
                delay(2_500)
            }
        }
        if (status == AuthRequestStatus.PENDING) {
            status = AuthRequestStatus.EXPIRED
            ServiceBookRepository.clearPendingAuth()
            ServiceBookRepository.clearAuthAttemptToken()
        }
    }

    val statusText = when (status) {
        AuthRequestStatus.PENDING -> "Ожидаем подтверждение в Telegram..."
        AuthRequestStatus.APPROVED -> "Вход подтверждён"
        AuthRequestStatus.REJECTED -> "Вы отклонили запрос на вход"
        AuthRequestStatus.EXPIRED -> "Время действия запроса истекло"
    }

    Column(
        modifier = Modifier.fillMaxSize().background(BackgroundLight)
            .statusBarsPadding().navigationBarsPadding().padding(bottom = 20.dp)
    ) {
        TopBarHeader(showBack = true, onBackClick = onBack)
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Text("Подтверждение входа", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Откройте Telegram и подтвердите запрос. После подтверждения приложение продолжит автоматически.",
                fontSize = 15.sp, color = TextSecondary, lineHeight = 22.sp
            )
            Spacer(modifier = Modifier.height(24.dp))
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onOpenBot(botUrl) },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE9F0F8))
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF2AABEE)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Открыть бота ServiceBook", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text("Нажмите, чтобы перейти к запросу", fontSize = 12.sp, color = TextSecondary)
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                statusText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                color = when (status) {
                    AuthRequestStatus.APPROVED -> NavyPrimary
                    AuthRequestStatus.REJECTED, AuthRequestStatus.EXPIRED -> RedDanger
                    AuthRequestStatus.PENDING -> TextPrimary
                }
            )
            if (connectionError != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(connectionError!!, color = RedDanger, fontSize = 13.sp)
            }
            Spacer(modifier = Modifier.height(18.dp))
            if (status == AuthRequestStatus.PENDING) {
                Text(
                    "Запрос действует ${expiresIn / 60} мин. Не закрывайте это окно до завершения проверки.",
                    fontSize = 12.sp, color = TextTertiary, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            } else if (sessionExchangeFailed) {
                PrimaryButton(
                    text = "Повторить",
                    onClick = { retrySessionCounter += 1 }
                )
                Spacer(modifier = Modifier.height(10.dp))
                SecondaryButton(text = "Вернуться назад", onClick = onBack)
            } else if (status == AuthRequestStatus.REJECTED || status == AuthRequestStatus.EXPIRED) {
                SecondaryButton(text = "Вернуться назад", onClick = onBack)
            }
        }
    }
}

@Composable
fun YandexAuthScreen(
    onFoundAccount: () -> Unit,
    onFirstEntry: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val openYandexBrowser = {
        try {
            val yandexAuthUri = Uri.parse("https://oauth.yandex.ru/authorize?response_type=token&client_id=f2a2a3573a88413fab17c67ecc095622")
            val intent = Intent(Intent.ACTION_VIEW, yandexAuthUri)
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://passport.yandex.ru/auth")))
            } catch (_: Exception) {}
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(bottom = 20.dp)
    ) {
        TopBarHeader(
            title = "Вернуться в ServiceBook",
            showBack = true,
            onBackClick = onBack
        )

        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Text(
                text = "Вход через Яндекс ID",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Авторизация в вашем Яндекс-аккаунте",
                fontSize = 14.sp,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFC3F1D)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Я",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Страница Яндекса открыта",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Войдите под вашим Яндекс-аккаунтом в браузере. После входа нажмите кнопку «Продолжить» ниже для перехода в ServiceBook.",
                        fontSize = 14.sp,
                        color = TextSecondary,
                        lineHeight = 22.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            PrimaryButton(
                text = "Открыть авторизацию Яндекс ID",
                onClick = openYandexBrowser
            )

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "При входе создаётся защищённая сессия на вашем устройстве.",
                fontSize = 12.sp,
                color = TextTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun CheckDataScreen(
    onConfirm: () -> Unit,
    onBack: () -> Unit
) {
    val savedProfile = ServiceBookRepository.userProfile.value
    var firstName by remember { mutableStateOf(savedProfile.firstName) }
    var lastName by remember { mutableStateOf(savedProfile.lastName) }
    var birthDateDigits by remember { mutableStateOf(savedProfile.birthDate.filter { it.isDigit() }) }
    val yandexPhoneDigits = savedProfile.phone.filter { it.isDigit() }.takeLast(10)
    val hasYandexPhone = yandexPhoneDigits.length == 10
    var phoneDigits by remember { mutableStateOf(if (hasYandexPhone) yandexPhoneDigits else "") }
    var touchedPhone by remember { mutableStateOf(false) }

    val isFirstNameValid = ValidationUtils.isValidName(firstName)
    val isLastNameValid = ValidationUtils.isValidName(lastName)
    val isBirthDateValid = ValidationUtils.isValidDateDigits(birthDateDigits)
    val isPhoneValid = phoneDigits.length == 10
    val isFormValid = isFirstNameValid && isLastNameValid && isBirthDateValid && isPhoneValid

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(bottom = 20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        TopBarHeader(showBack = true, onBackClick = onBack)

        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Text(
                text = "Проверьте данные",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (hasYandexPhone) {
                    if (savedProfile.firstName.isNotBlank()) {
                        "Имя и фамилию можно изменить.\nТелефон получен из Яндекса."
                    } else {
                        "Укажите ваши данные для профиля.\nТелефон подтверждён через Telegram."
                    }
                } else {
                    "Имя и фамилию можно изменить.\nУкажите ваш номер телефона для сервисной книжки."
                },
                fontSize = 15.sp,
                color = TextSecondary,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            CustomInputField(
                label = "Имя * · можно изменить",
                value = firstName,
                placeholder = "Введите имя",
                onlyLetters = true,
                isError = firstName.isNotBlank() && !isFirstNameValid,
                errorMessage = "Только буквы, минимум 2 символа",
                onValueChange = { firstName = it }
            )
            Spacer(modifier = Modifier.height(14.dp))

            CustomInputField(
                label = "Фамилия * · можно изменить",
                value = lastName,
                placeholder = "Введите фамилию",
                onlyLetters = true,
                isError = lastName.isNotBlank() && !isLastNameValid,
                errorMessage = "Только буквы, минимум 2 символа",
                onValueChange = { lastName = it }
            )
            Spacer(modifier = Modifier.height(14.dp))

            CustomInputField(
                label = "Дата рождения *",
                value = birthDateDigits,
                placeholder = "ДД.ММ.ГГГГ",
                onlyDigits = true,
                maxLength = 8,
                visualTransformation = DateVisualTransformation(),
                isError = birthDateDigits.isNotBlank() && !isBirthDateValid,
                errorMessage = "Укажите реальную дату рождения",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                onValueChange = { birthDateDigits = it }
            )
            Spacer(modifier = Modifier.height(14.dp))

            if (hasYandexPhone) {
                CustomInputField(
                    label = if (savedProfile.firstName.isNotBlank()) "Телефон · получен из Яндекса" else "Телефон · подтверждён в Telegram",
                    value = savedProfile.phone,
                    readOnly = true,
                    onValueChange = {}
                )
            } else {
                CustomInputField(
                    label = "Номер телефона *",
                    value = phoneDigits,
                    prefixText = "+7 ",
                    placeholder = "(999) 000-00-00",
                    onlyDigits = true,
                    maxLength = 10,
                    visualTransformation = PhoneVisualTransformation(),
                    isError = touchedPhone && !isPhoneValid,
                    errorMessage = "Введите 10 цифр номера телефона",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    onValueChange = {
                        phoneDigits = it
                        touchedPhone = true
                    }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = if (hasYandexPhone) {
                    "Телефон подтверждён и не может быть изменён.\nПродолжая, принимаю условия сервиса и политику конфиденциальности."
                } else {
                    "Продолжая, принимаю условия сервиса и политику конфиденциальности."
                },
                fontSize = 12.sp,
                color = TextTertiary,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            PrimaryButton(
                text = "Всё верно — создать аккаунт",
                enabled = isFormValid,
                onClick = {
                    val fullBirth = ValidationUtils.formatDigitsToDate(birthDateDigits)
                    val fullPhone = if (hasYandexPhone) {
                        savedProfile.phone
                    } else {
                        "+7 (${phoneDigits.take(3)}) ${phoneDigits.drop(3).take(3)}-${phoneDigits.drop(6).take(2)}-${phoneDigits.drop(8)}"
                    }
                    ServiceBookRepository.completeRegistrationAndLogin(firstName, lastName, fullBirth, fullPhone)
                    onConfirm()
                }
            )
        }
    }
}
