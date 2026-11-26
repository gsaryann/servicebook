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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.servicebook.data.ServiceBookRepository
import com.servicebook.ui.components.CustomInputField
import com.servicebook.ui.components.PrimaryButton
import com.servicebook.ui.components.TopBarHeader
import com.servicebook.ui.theme.BackgroundLight
import com.servicebook.ui.theme.GreenLight
import com.servicebook.ui.theme.GreenSuccess
import com.servicebook.ui.theme.NavyPrimary
import com.servicebook.ui.theme.OrangeAccent
import com.servicebook.ui.theme.OrangeLight
import com.servicebook.ui.theme.RedDanger
import com.servicebook.ui.theme.RedLight
import com.servicebook.ui.theme.SurfaceWhite
import com.servicebook.ui.theme.TextPrimary
import com.servicebook.ui.theme.TextSecondary
import com.servicebook.util.ValidationUtils

@Composable
fun RemindersScreen(
    onConfigureReminder: (String) -> Unit,
    onBack: () -> Unit
) {
    val car by ServiceBookRepository.car.collectAsState()
    val reminders by ServiceBookRepository.reminders.collectAsState()
    var notificationsEnabled by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
            .navigationBarsPadding()
            .padding(bottom = 20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        TopBarHeader(
            title = "Напоминания",
            showBack = true,
            onBackClick = onBack
        )

        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Text(
                text = if (car.brand.isNotBlank()) "${car.brand} ${car.model} · ${car.year}" else "Регламент ТО",
                fontSize = 14.sp,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Banner
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE9F0F8))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Регламент под ваш автомобиль",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Интервалы можно изменить под условия езды.",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Reminders cards
            reminders.forEach { reminder ->
                val (bgColor, accentColor, statusText) = when {
                    reminder.isOverdue -> Triple(RedLight, RedDanger, "Просрочено на ${reminder.overdueDays} дней")
                    reminder.remainingKm < 1000 -> Triple(OrangeLight, OrangeAccent, "Через ${reminder.remainingKm} км · до ${reminder.targetDate}")
                    else -> Triple(GreenLight, GreenSuccess, "Через ${formatMileage(reminder.remainingKm)} км · ${reminder.targetDate}")
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .clickable { onConfigureReminder(reminder.id) },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = bgColor)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = reminder.title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = statusText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = accentColor
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Настроить",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = accentColor
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Своё напоминание · скоро",
                        fontSize = 14.sp,
                        color = TextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Push notifications toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Уведомления включены",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Switch(
                    checked = notificationsEnabled,
                    onCheckedChange = { notificationsEnabled = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = SurfaceWhite,
                        checkedTrackColor = GreenSuccess
                    )
                )
            }
        }
    }
}

@Composable
fun EditReminderScreen(
    reminderId: String,
    onSaved: () -> Unit,
    onBack: () -> Unit
) {
    val reminders by ServiceBookRepository.reminders.collectAsState()
    val reminder = reminders.firstOrNull { it.id == reminderId }

    if (reminder == null) {
        onBack()
        return
    }

    var targetKm by remember { mutableStateOf(reminder.targetMileage.toString()) }
    var targetDate by remember { mutableStateOf(reminder.targetDate) }
    var intervalKm by remember { mutableStateOf(reminder.intervalKm.toString()) }
    var pushEnabled by remember { mutableStateOf(reminder.pushEnabled) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
            .navigationBarsPadding()
            .padding(bottom = 20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        TopBarHeader(
            title = "Настроить ТО",
            showBack = true,
            onBackClick = onBack
        )

        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Text(
                text = reminder.title,
                fontSize = 15.sp,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(24.dp))

            CustomInputField(
                label = "Напомнить на пробеге",
                value = targetKm,
                onlyDigits = true,
                maxLength = 7,
                suffixText = "км",
                onValueChange = { targetKm = it }
            )
            Spacer(modifier = Modifier.height(14.dp))

            CustomInputField(
                label = "Или не позднее даты",
                value = targetDate,
                placeholder = "ДД.ММ.ГГГГ",
                onValueChange = { targetDate = ValidationUtils.formatDateInput(it) }
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Напомним, когда наступит любое условие.",
                fontSize = 13.sp,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(20.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Повторять каждые",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${formatMileage(reminder.intervalKm)} км или ${reminder.intervalMonths} месяцев",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Push-уведомления",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Заранее за 1 000 км / 7 дней\nи в день обслуживания",
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                    }
                    Switch(
                        checked = pushEnabled,
                        onCheckedChange = { pushEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = SurfaceWhite,
                            checkedTrackColor = GreenSuccess
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = "Интервал задан для Lada Granta 2014.\nСверяйтесь с сервисной книжкой авто.",
                fontSize = 13.sp,
                color = TextSecondary,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            PrimaryButton(
                text = "Сохранить напоминание",
                onClick = {
                    val km = targetKm.toIntOrNull() ?: reminder.targetMileage
                    ServiceBookRepository.updateReminder(
                        reminder.id,
                        targetMileage = km,
                        targetDate = targetDate,
                        intervalKm = reminder.intervalKm,
                        intervalMonths = reminder.intervalMonths,
                        push = pushEnabled
                    )
                    onSaved()
                }
            )
        }
    }
}
