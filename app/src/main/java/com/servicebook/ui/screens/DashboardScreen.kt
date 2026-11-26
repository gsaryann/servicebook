package com.servicebook.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.servicebook.data.ServiceBookRepository
import com.servicebook.util.ValidationUtils
import com.servicebook.ui.components.CustomInputField
import com.servicebook.ui.components.PrimaryButton
import com.servicebook.ui.components.SecondaryButton
import com.servicebook.ui.components.TopBarHeader
import com.servicebook.ui.theme.BackgroundLight
import com.servicebook.ui.theme.NavyCard
import com.servicebook.ui.theme.OrangeAccent
import com.servicebook.ui.theme.OrangeLight
import com.servicebook.ui.theme.SurfaceWhite
import com.servicebook.ui.theme.TextPrimary
import com.servicebook.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun AddCarScreen(
    onCarSaved: () -> Unit,
    onBack: () -> Unit
) {
    val currentCar by ServiceBookRepository.car.collectAsState()
    val isEditMode = remember(currentCar.brand, currentCar.year) {
        currentCar.year > 0 && currentCar.brand.isNotBlank()
    }
    var brand by remember { mutableStateOf(currentCar.brand) }
    var model by remember { mutableStateOf(currentCar.model) }
    var year by remember { mutableStateOf(if (isEditMode) currentCar.year.toString() else "") }
    var engine by remember { mutableStateOf(currentCar.engine) }
    var mileage by remember { mutableStateOf(if (isEditMode && currentCar.mileage > 0) currentCar.mileage.toString() else "") }
    var touchedYear by remember { mutableStateOf(false) }
    var touchedMileage by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
            .navigationBarsPadding()
            .padding(bottom = 20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        TopBarHeader(
            title = if (isEditMode) "Редактировать автомобиль" else "Добавить авто",
            showBack = true,
            onBackClick = onBack
        )

        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Text(
                text = "Заполните один раз — дальше проще",
                fontSize = 15.sp,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(24.dp))

            val cleanYear = ValidationUtils.parseValidCarYear(year) ?: 0
            val cleanMileage = mileage.filter { it.isDigit() }.toIntOrNull() ?: 0
            val isYearValid = ValidationUtils.isValidCarYear(year)
            val isMileageValid = cleanMileage > 0
            val isBrandValid = brand.trim().length >= 2

            CustomInputField(
                label = "Марка *",
                value = brand,
                onlyLetters = true,
                placeholder = "Например: Lada",
                onValueChange = { brand = it }
            )
            Spacer(modifier = Modifier.height(14.dp))

            CustomInputField(
                label = "Модель *",
                value = model,
                placeholder = "Например: Granta",
                onValueChange = { model = it }
            )
            Spacer(modifier = Modifier.height(14.dp))

            CustomInputField(
                label = "Год выпуска *",
                value = year,
                onlyDigits = true,
                maxLength = 4,
                placeholder = "2014",
                isError = touchedYear && !isYearValid,
                errorMessage = "Укажите год от 1950 до 2030",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                onValueChange = {
                    year = it
                    touchedYear = true
                }
            )
            Spacer(modifier = Modifier.height(14.dp))

            CustomInputField(
                label = "Двигатель",
                value = engine,
                placeholder = "Например: 1.6 л · бензин · 87 л.с.",
                onValueChange = { engine = it }
            )
            Spacer(modifier = Modifier.height(14.dp))

            CustomInputField(
                label = "Текущий пробег *",
                value = mileage,
                onlyDigits = true,
                maxLength = 7,
                suffixText = "км",
                isError = touchedMileage && !isMileageValid,
                errorMessage = "Пробег должен быть больше 0",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                onValueChange = {
                    mileage = it
                    touchedMileage = true
                }
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Пробег поможет точно рассчитать следующее обслуживание.",
                fontSize = 13.sp,
                color = TextSecondary,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(36.dp))

            PrimaryButton(
                text = "Сохранить автомобиль",
                enabled = isBrandValid && isYearValid && isMileageValid,
                onClick = {
                    ServiceBookRepository.updateCar(brand, model, cleanYear, engine, cleanMileage)
                    onCarSaved()
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNewRecordClick: () -> Unit,
    onAllRemindersClick: () -> Unit,
    onDocumentsClick: () -> Unit,
    onRecordDetailsClick: (String) -> Unit
) {
    val car by ServiceBookRepository.car.collectAsState()
    val records by ServiceBookRepository.records.collectAsState()
    val reminders by ServiceBookRepository.reminders.collectAsState()

    var showMileageSheet by remember { mutableStateOf(false) }
    var inputMileage by remember { mutableStateOf(formatMileage(car.mileage)) }
    var mileageError by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    val nearestReminder = reminders.firstOrNull { !it.isOverdue } ?: reminders.firstOrNull()
    val lastRecord = records.firstOrNull()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        // Top Car Title & Notification Icon
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = if (car.brand.isNotBlank()) "${car.brand} ${car.model}" else "Мой автомобиль",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = if (car.brand.isNotBlank()) {
                        "${car.year} · ${if (car.engine.isNotBlank()) car.engine else "1.6 л"}"
                    } else {
                        "Нажмите, чтобы настроить авто"
                    },
                    fontSize = 14.sp,
                    color = TextSecondary
                )
            }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(SurfaceWhite)
                    .clickable { onAllRemindersClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.NotificationsNone,
                    contentDescription = "Уведомления",
                    tint = TextPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Blue Mileage Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = NavyCard)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "Текущий пробег",
                    fontSize = 14.sp,
                    color = Color(0xFFBAC7D5)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = formatMileage(car.mileage),
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = SurfaceWhite
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "км",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFBAC7D5),
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            inputMileage = car.mileage.toString()
                            mileageError = false
                            showMileageSheet = true
                        }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Обновить",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = SurfaceWhite
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.NorthEast,
                        contentDescription = null,
                        tint = SurfaceWhite,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Nearest Maintenance Banner
        if (nearestReminder != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onAllRemindersClick() },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = OrangeLight)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "БЛИЖАЙШЕЕ ТО",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = OrangeAccent,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${nearestReminder.title} через ${nearestReminder.remainingKm} км",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = OrangeAccent
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = ValidationUtils.formatMaintenanceDueSubtitle(
                            targetMileage = nearestReminder.targetMileage,
                            targetDate = nearestReminder.targetDate,
                            formattedMileage = formatMileage(nearestReminder.targetMileage)
                        ),
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Все напоминания",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = OrangeAccent
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = OrangeAccent,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // + Новая запись
        PrimaryButton(
            text = "+ Новая запись",
            onClick = onNewRecordClick
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Быстрые действия
        Text(
            text = "Быстрые действия",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Документы Action
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onDocumentsClick() },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
                    contentDescription = null,
                    tint = TextPrimary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = "Документы",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Последняя работа Action
        if (lastRecord != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onRecordDetailsClick(lastRecord.id) },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Последняя работа",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${lastRecord.title} · ${formatPrice(lastRecord.price)} ₽",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(30.dp))
    }

    if (showMileageSheet) {
        ModalBottomSheet(
            onDismissRequest = { showMileageSheet = false },
            sheetState = sheetState,
            containerColor = SurfaceWhite,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(24.dp)
            ) {
                Text(
                    text = "Обновить пробег",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Предыдущий: ${formatMileage(car.mileage)} км",
                    fontSize = 14.sp,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(20.dp))

                CustomInputField(
                    label = "",
                    value = inputMileage,
                    onlyDigits = true,
                    maxLength = 7,
                    suffixText = "км",
                    isError = mileageError,
                    errorMessage = "Новый пробег не может быть меньше текущего (${formatMileage(car.mileage)} км)",
                    onValueChange = {
                        inputMileage = it
                        mileageError = false
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    placeholder = "Введите новый пробег"
                )

                Spacer(modifier = Modifier.height(28.dp))

                PrimaryButton(
                    text = "Сохранить",
                    enabled = inputMileage.isNotEmpty(),
                    onClick = {
                        val newKm = inputMileage.toIntOrNull() ?: 0
                        if (newKm >= car.mileage) {
                            ServiceBookRepository.updateMileage(newKm)
                            scope.launch { sheetState.hide() }.invokeOnCompletion {
                                showMileageSheet = false
                            }
                        } else {
                            mileageError = true
                        }
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                SecondaryButton(
                    text = "Отмена",
                    onClick = {
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            showMileageSheet = false
                        }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

fun formatMileage(km: Int): String {
    return String.format(java.util.Locale.US, "%,d", km).replace(",", " ")
}

fun formatPrice(price: Int): String {
    return String.format(java.util.Locale.US, "%,d", price).replace(",", " ")
}
