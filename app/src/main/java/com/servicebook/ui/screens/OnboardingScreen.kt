package com.servicebook.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.servicebook.ui.components.PrimaryButton
import com.servicebook.ui.components.TopBarHeader
import com.servicebook.ui.theme.BackgroundLight
import com.servicebook.ui.theme.NavyCard
import com.servicebook.ui.theme.OrangeAccent
import com.servicebook.ui.theme.SurfaceWhite
import com.servicebook.ui.theme.TextPrimary
import com.servicebook.ui.theme.TextSecondary

@Composable
fun OnboardingScreen(
    onFinish: () -> Unit
) {
    var currentStep by remember { mutableIntStateOf(1) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
            .navigationBarsPadding()
            .padding(bottom = 20.dp)
    ) {
        TopBarHeader(
            title = "ServiceBook",
            showBack = currentStep == 2,
            onBackClick = { currentStep = 1 },
            actionText = "Пропустить",
            onActionClick = onFinish
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Center preview card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            if (currentStep == 1) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = NavyCard)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DirectionsCar,
                            contentDescription = null,
                            tint = SurfaceWhite,
                            modifier = Modifier.size(60.dp)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "112 400 км",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = SurfaceWhite
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Lada Granta · 2014",
                            fontSize = 15.sp,
                            color = Color(0xFFBAC7D5)
                        )
                    }
                }
            } else {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = NavyCard)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF233758)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = SurfaceWhite,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "Через 800 км",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = SurfaceWhite
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Пора планировать замену масла",
                            fontSize = 15.sp,
                            color = Color(0xFFBAC7D5)
                        )
                    }
                }
            }
        }

        // Texts
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        ) {
            if (currentStep == 1) {
                Text(
                    text = "Вся история машины —\nв одном месте.",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    lineHeight = 32.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Сохраняйте работы, пробег и чеки.\nБез блокнотов и потерянных записей.",
                    fontSize = 15.sp,
                    color = TextSecondary,
                    lineHeight = 22.sp
                )
            } else {
                Text(
                    text = "Забота о машине —\nбез лишних хлопот.",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    lineHeight = 32.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Напомним о ТО по дате или пробегу с учётом марки и модели авто.",
                    fontSize = 15.sp,
                    color = TextSecondary,
                    lineHeight = 22.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Indicators
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .height(4.dp)
                        .width(if (currentStep == 1) 28.dp else 8.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (currentStep == 1) OrangeAccent else Color(0xFFD1D5DB))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .height(4.dp)
                        .width(if (currentStep == 2) 28.dp else 8.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (currentStep == 2) OrangeAccent else Color(0xFFD1D5DB))
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            PrimaryButton(
                text = if (currentStep == 1) "Продолжить" else "Начать",
                onClick = {
                    if (currentStep == 1) {
                        currentStep = 2
                    } else {
                        onFinish()
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Ваша сервисная книжка всегда рядом",
                fontSize = 13.sp,
                color = TextSecondary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }
}
