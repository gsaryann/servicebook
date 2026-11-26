package com.servicebook.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.servicebook.BuildConfig
import com.servicebook.data.AuthApiClient
import com.servicebook.data.AuthApiException
import com.servicebook.data.ServiceBookRepository
import kotlinx.coroutines.launch
import com.servicebook.ui.components.CustomInputField
import com.servicebook.ui.components.SecondaryButton
import com.servicebook.ui.theme.BackgroundLight
import com.servicebook.ui.theme.NavyPrimary
import com.servicebook.ui.theme.RedDanger
import com.servicebook.ui.theme.SurfaceWhite
import com.servicebook.ui.theme.TextPrimary
import com.servicebook.ui.theme.TextSecondary
import com.servicebook.ui.theme.TextTertiary
import com.servicebook.util.DateVisualTransformation
import com.servicebook.util.ValidationUtils

@Composable
fun ProfileScreen(
    onMyCarClick: () -> Unit,
    onRemindersClick: () -> Unit,
    onLogout: () -> Unit,
    onAccountDeleted: () -> Unit = onLogout,
    onReauthenticateForDelete: () -> Unit = onLogout
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val userProfile by ServiceBookRepository.userProfile.collectAsState()
    var firstName by remember(userProfile.firstName) { mutableStateOf(userProfile.firstName) }
    var lastName by remember(userProfile.lastName) { mutableStateOf(userProfile.lastName) }
    var birthDateDigits by remember(userProfile.birthDate) {
        mutableStateOf(userProfile.birthDate.filter { it.isDigit() })
    }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }
    var requiresReauth by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Профиль",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "Личные данные и контакты",
            fontSize = 14.sp,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(20.dp))

        val isFirstNameValid = ValidationUtils.isValidName(firstName)
        val isLastNameValid = ValidationUtils.isValidName(lastName)
        val isBirthDateValid = birthDateDigits.isEmpty() || ValidationUtils.isValidDateDigits(birthDateDigits)

        CustomInputField(
            label = "Имя · можно изменить",
            value = firstName,
            onlyLetters = true,
            isError = !isFirstNameValid,
            errorMessage = "Только буквы, минимум 2 символа",
            onValueChange = {
                firstName = it
                if (ValidationUtils.isValidName(it)) {
                    val fullBirth = ValidationUtils.formatDigitsToDate(birthDateDigits)
                    ServiceBookRepository.updateUserProfile(it, lastName, fullBirth)
                }
            }
        )
        Spacer(modifier = Modifier.height(12.dp))

        CustomInputField(
            label = "Фамилия · можно изменить",
            value = lastName,
            onlyLetters = true,
            isError = !isLastNameValid,
            errorMessage = "Только буквы, минимум 2 символа",
            onValueChange = {
                lastName = it
                if (ValidationUtils.isValidName(it)) {
                    val fullBirth = ValidationUtils.formatDigitsToDate(birthDateDigits)
                    ServiceBookRepository.updateUserProfile(firstName, it, fullBirth)
                }
            }
        )
        Spacer(modifier = Modifier.height(12.dp))

        CustomInputField(
            label = "Дата рождения",
            value = birthDateDigits,
            placeholder = "ДД.ММ.ГГГГ",
            onlyDigits = true,
            maxLength = 8,
            visualTransformation = DateVisualTransformation(),
            isError = !isBirthDateValid,
            errorMessage = "Формат ДД.ММ.ГГГГ (например, 15051995)",
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
            onValueChange = {
                birthDateDigits = it
                if (ValidationUtils.isValidDateDigits(it)) {
                    val fullBirth = ValidationUtils.formatDigitsToDate(it)
                    ServiceBookRepository.updateUserProfile(firstName, lastName, fullBirth)
                }
            }
        )
        Spacer(modifier = Modifier.height(12.dp))

        CustomInputField(
            label = "Телефон · только чтение",
            value = userProfile.phone,
            readOnly = true,
            onValueChange = {}
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Quick Navigation to Car
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onMyCarClick() },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Мой автомобиль",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = TextTertiary
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Quick Navigation to Reminders
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onRemindersClick() },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Напоминания о ТО",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = TextTertiary
                )
            }
        }

        Spacer(modifier = Modifier.height(30.dp))

        Text(
            text = "Выйти из аккаунта",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = RedDanger,
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    ServiceBookRepository.logout()
                    onLogout()
                }
                .padding(12.dp),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Удалить аккаунт и все данные",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = RedDanger,
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    deleteError = null
                    requiresReauth = false
                    showDeleteDialog = true
                }
                .padding(12.dp),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isDeleting) {
                    showDeleteDialog = false
                }
            },
            title = {
                Text(
                    text = "Удалить аккаунт и все данные?",
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                Column {
                    Text(
                        text = "Вы собираетесь безвозвратно удалить аккаунт и все связанные с ним данные: автомобили, историю обслуживания, фотографии, документы и настройки. Восстановить данные после удаления будет невозможно.",
                        color = TextSecondary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                    if (isDeleting) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Удаление аккаунта...", fontSize = 13.sp, color = TextPrimary)
                        }
                    }
                    if (deleteError != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = deleteError!!,
                            color = RedDanger,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
            },
            confirmButton = {
                if (requiresReauth) {
                    TextButton(
                        enabled = !isDeleting,
                        onClick = {
                            showDeleteDialog = false
                            ServiceBookRepository.logout()
                            onReauthenticateForDelete()
                        }
                    ) {
                        Text("Подтвердить вход", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    TextButton(
                        enabled = !isDeleting,
                        onClick = {
                            isDeleting = true
                            deleteError = null
                            coroutineScope.launch {
                                val sessionToken = ServiceBookRepository.getAuthSessionToken()
                                val authProvider = ServiceBookRepository.getAuthProvider()
                                if (!sessionToken.isNullOrBlank()) {
                                    try {
                                        AuthApiClient.deleteAccount(BuildConfig.AUTH_API_BASE_URL, sessionToken)
                                    } catch (error: AuthApiException) {
                                        isDeleting = false
                                        if (error.statusCode == 401) {
                                            requiresReauth = true
                                            deleteError = "Сессия истекла. Подтвердите вход заново, чтобы удалить аккаунт."
                                        } else {
                                            deleteError = error.message ?: "Не удалось удалить аккаунт. Проверьте подключение и повторите попытку."
                                        }
                                        return@launch
                                    } catch (error: Exception) {
                                        isDeleting = false
                                        deleteError = error.message ?: "Не удалось удалить аккаунт. Повторите попытку."
                                        return@launch
                                    }
                                } else if (authProvider == "telegram") {
                                    isDeleting = false
                                    requiresReauth = true
                                    deleteError = "Отсутствует активная серверная сессия. Подтвердите вход через Telegram заново для полного удаления аккаунта."
                                    return@launch
                                }

                                val yandexToken = ServiceBookRepository.getYandexAccessToken()
                                if (!yandexToken.isNullOrBlank()) {
                                    AuthApiClient.revokeYandexToken(yandexToken, "f2a2a3573a88413fab17c67ecc095622")
                                }

                                ServiceBookRepository.deleteAccountAndAllData(context)
                                isDeleting = false
                                showDeleteDialog = false
                                onAccountDeleted()
                            }
                        }
                    ) {
                        Text("Удалить аккаунт", color = RedDanger, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isDeleting,
                    onClick = { showDeleteDialog = false }
                ) {
                    Text("Отмена", color = TextPrimary)
                }
            }
        )
    }
}
