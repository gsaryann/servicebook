package com.servicebook.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.servicebook.data.ServiceBookRepository
import com.servicebook.model.DocumentFilter
import com.servicebook.ui.components.PrimaryButton
import com.servicebook.ui.components.SecondaryButton
import com.servicebook.ui.components.TopBarHeader
import com.servicebook.ui.theme.BackgroundLight
import com.servicebook.ui.theme.ChipUnselected
import com.servicebook.ui.theme.NavyPrimary
import com.servicebook.ui.theme.OrangeAccent
import com.servicebook.ui.theme.SurfaceWhite
import com.servicebook.ui.theme.TextPrimary
import com.servicebook.ui.theme.TextSecondary
import com.servicebook.util.ImageUtils
import com.servicebook.util.UniversalImageView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentsScreen(
    onOpenDocument: (String) -> Unit
) {
    val context = LocalContext.current
    val car by ServiceBookRepository.car.collectAsState()
    val documents by ServiceBookRepository.documents.collectAsState()
    var selectedFilter by remember { mutableStateOf(DocumentFilter.ALL) }

    var showAddDocSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Camera Launcher for Documents
    val takeDocPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            val (path, size) = ImageUtils.saveBitmapToFile(context, bitmap, "doc_camera")
            val todayStr = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())
            ServiceBookRepository.addDocument(
                title = "Фото чека от $todayStr",
                format = "JPG",
                size = size,
                isReceipt = true,
                filePath = path
            )
            showAddDocSheet = false
        }
    }

    // Gallery Launcher for Documents
    val pickDocImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val res = ImageUtils.saveUriToFile(context, uri, "doc_gallery")
            if (res != null) {
                val todayStr = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())
                ServiceBookRepository.addDocument(
                    title = "Документ от $todayStr",
                    format = "JPG",
                    size = res.second,
                    isReceipt = false,
                    filePath = res.first
                )
                showAddDocSheet = false
            }
        }
    }

    val filteredDocs = when (selectedFilter) {
        DocumentFilter.ALL -> documents
        DocumentFilter.RECEIPTS -> documents.filter { it.isReceipt }
        DocumentFilter.ORDERS -> documents.filter { !it.isReceipt }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Text(
            text = "Документы",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = if (car.brand.isNotBlank()) {
                "${car.brand} ${car.model} · ${documents.size} ${if (documents.size == 1) "файл" else "файлов"}"
            } else {
                "${documents.size} ${if (documents.size == 1) "файл" else "файлов"}"
            },
            fontSize = 14.sp,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Filters
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(DocumentFilter.values()) { filter ->
                val isSelected = filter == selectedFilter
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isSelected) NavyPrimary else ChipUnselected)
                        .clickable { selectedFilter = filter }
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = filter.label,
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (isSelected) SurfaceWhite else TextPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(filteredDocs) { doc ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenDocument(doc.id) },
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
                            tint = if (doc.isReceipt) OrangeAccent else NavyPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = doc.title,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${doc.date} · ${doc.fileFormat} · ${doc.fileSize}",
                                fontSize = 13.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color(0xFFE9F0F8))
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "Чеки связаны с работами",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Добавляйте фото при создании записи или загрузите документ отдельно через камеру или галерею.",
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        PrimaryButton(
            text = "+ Добавить документ",
            onClick = { showAddDocSheet = true }
        )
    }

    if (showAddDocSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddDocSheet = false },
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
                    text = "Добавить документ",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Снимите чек на камеру или выберите скан из галереи",
                    fontSize = 14.sp,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(24.dp))

                PrimaryButton(
                    text = "Сделать снимок на камеру",
                    onClick = {
                        takeDocPhotoLauncher.launch(null)
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                SecondaryButton(
                    text = "Выбрать фото из галереи",
                    onClick = {
                        pickDocImageLauncher.launch("image/*")
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                SecondaryButton(
                    text = "Отмена",
                    onClick = { showAddDocSheet = false }
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun DocumentViewerScreen(
    docId: String,
    onBack: () -> Unit
) {
    val documents by ServiceBookRepository.documents.collectAsState()
    val doc = documents.firstOrNull { it.id == docId } ?: documents.firstOrNull()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(bottom = 16.dp)
    ) {
        TopBarHeader(
            title = "Просмотр файла",
            showBack = true,
            onBackClick = onBack
        )

        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = doc?.title ?: "Документ",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${doc?.date ?: "Сегодня"} · ${doc?.fileSize ?: "2,1 МБ"}",
                fontSize = 13.sp,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(16.dp))

            // REAL PHOTO OR PRESET DISPLAY
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(8.dp, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
            ) {
                UniversalImageView(
                    filePath = doc?.filePath,
                    drawableName = doc?.drawableResName ?: "receipt_oil",
                    contentDescription = "Фото документа",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Fit
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Документ сохранён в электронную сервисную книжку",
                fontSize = 13.sp,
                color = TextSecondary
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Box(modifier = Modifier.padding(horizontal = 20.dp)) {
            SecondaryButton(
                text = "Закрыть",
                onClick = onBack
            )
        }
    }
}
