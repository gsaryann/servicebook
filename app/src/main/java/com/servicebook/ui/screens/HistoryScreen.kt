package com.servicebook.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.servicebook.data.ServiceBookRepository
import com.servicebook.ui.components.PrimaryButton
import com.servicebook.ui.components.SecondaryButton
import com.servicebook.ui.components.TopBarHeader
import com.servicebook.ui.theme.BackgroundLight
import com.servicebook.ui.theme.ChipUnselected
import com.servicebook.ui.theme.GreenSuccess
import com.servicebook.ui.theme.NavyCard
import com.servicebook.ui.theme.NavyPrimary
import com.servicebook.ui.theme.RedDanger
import com.servicebook.ui.theme.RedLight
import com.servicebook.ui.theme.SurfaceWhite
import com.servicebook.ui.theme.TextPrimary
import com.servicebook.ui.theme.TextSecondary
import com.servicebook.ui.theme.TextTertiary

import com.servicebook.util.UniversalImageView

val categoriesList = listOf("Все", "Масло", "Тормоза", "Шины")

@Composable
fun HistoryScreen(
    onRecordClick: (String) -> Unit,
    onAddFirstRecord: () -> Unit,
    deletedToastMessage: String? = null
) {
    val car by ServiceBookRepository.car.collectAsState()
    val records by ServiceBookRepository.records.collectAsState()

    var selectedCategory by remember { mutableStateOf("Все") }

    val filteredRecords = if (selectedCategory == "Все") {
        records
    } else {
        records.filter { it.category == selectedCategory }
    }

    val totalCost = filteredRecords.sumOf { it.price }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        // Title
        Text(
            text = "История",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = if (car.brand.isNotBlank()) {
                "${car.brand} ${car.model} · ${records.size} ${if (records.size == 1) "работа" else "работ"}"
            } else {
                "${records.size} ${if (records.size == 1) "работа" else "работ"}"
            },
            fontSize = 14.sp,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Category Filter Chips
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(categoriesList) { cat ->
                val isSelected = cat == selectedCategory
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isSelected) NavyPrimary else ChipUnselected)
                        .clickable { selectedCategory = cat }
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = cat,
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (isSelected) SurfaceWhite else TextPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (records.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(70.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE9EDF2)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = null,
                            tint = NavyPrimary,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "Пока ни одной записи",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Добавьте первое обслуживание.\nДаже прошлые работы пригодятся\nдля полной истории автомобиля.",
                        fontSize = 14.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    PrimaryButton(
                        text = "+ Добавить первую запись",
                        onClick = onAddFirstRecord
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Это займёт всего три шага",
                        fontSize = 13.sp,
                        color = TextTertiary
                    )
                }
            }
        } else {
            // Spend Banner
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = NavyCard)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Расходы за 2026 год",
                        fontSize = 13.sp,
                        color = Color(0xFFBAC7D5)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${formatPrice(totalCost)} ₽",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = SurfaceWhite
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (deletedToastMessage != null) {
                Text(
                    text = deletedToastMessage,
                    color = GreenSuccess,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            // Records List
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredRecords) { record ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onRecordClick(record.id) },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = record.title,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${record.date} · ${formatMileage(record.mileage)} км",
                                    fontSize = 13.sp,
                                    color = TextSecondary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "${formatPrice(record.price)} ₽",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = TextTertiary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RecordDetailsScreen(
    recordId: String,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    onOpenReceipt: (String) -> Unit
) {
    val records by ServiceBookRepository.records.collectAsState()
    val car by ServiceBookRepository.car.collectAsState()
    val record = records.firstOrNull { it.id == recordId }
    val context = LocalContext.current

    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (record == null) {
        onBack()
        return
    }

    val receiptDrawableId = remember(record.receiptDrawableName) {
        val name = if (record.receiptDrawableName.isNotEmpty()) record.receiptDrawableName else "receipt_oil"
        val id = context.resources.getIdentifier(name, "drawable", context.packageName)
        if (id != 0) id else context.resources.getIdentifier("receipt_oil", "drawable", context.packageName)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
            .navigationBarsPadding()
            .padding(bottom = 16.dp)
    ) {
        TopBarHeader(
            title = "Запись",
            showBack = true,
            onBackClick = onBack
        )

        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "${car.brand} ${car.model}",
                fontSize = 14.sp,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = record.title,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = "Детали обслуживания",
                fontSize = 15.sp,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(20.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Дата", color = TextSecondary, fontSize = 14.sp)
                        Text(text = record.date, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 14.sp)
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Пробег", color = TextSecondary, fontSize = 14.sp)
                        Text(text = "${formatMileage(record.mileage)} км", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 14.sp)
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Стоимость", color = TextSecondary, fontSize = 14.sp)
                        Text(text = "${formatPrice(record.price)} ₽", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 14.sp)
                    }
                }
            }

            if (record.note.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Заметка",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = record.note,
                    fontSize = 15.sp,
                    color = TextPrimary
                )
            }

            if (record.receiptFileName.isNotEmpty()) {
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "Прикреплённый чек",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(4.dp, RoundedCornerShape(16.dp))
                        .clickable { onOpenReceipt(record.receiptFileName) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
                ) {
                    Column {
                        UniversalImageView(
                            filePath = record.receiptFilePath,
                            drawableName = record.receiptDrawableName,
                            contentDescription = "Чек",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
                                contentDescription = null,
                                tint = NavyPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = record.receiptFileName,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = TextTertiary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Action Buttons
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            SecondaryButton(
                text = "Удалить запись",
                onClick = { showDeleteConfirm = true },
                containerColor = RedLight,
                contentColor = RedDanger,
                borderColor = Color.Transparent
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Перед удалением запросим подтверждение",
                fontSize = 12.sp,
                color = TextTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = SurfaceWhite,
            shape = RoundedCornerShape(24.dp),
            title = {
                Text(
                    text = "Удалить запись?",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                Text(
                    text = "«${record.title}» · ${record.date}\nЗапись будет удалена безвозвратно.\nЧек останется в документах.",
                    fontSize = 14.sp,
                    color = TextSecondary,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                PrimaryButton(
                    text = "Удалить запись",
                    onClick = {
                        ServiceBookRepository.deleteRecord(record.id)
                        showDeleteConfirm = false
                        onDeleted()
                    },
                    containerColor = RedDanger,
                    contentColor = Color.White
                )
            },
            dismissButton = {
                SecondaryButton(
                    text = "Отмена",
                    onClick = { showDeleteConfirm = false }
                )
            }
        )
    }
}
