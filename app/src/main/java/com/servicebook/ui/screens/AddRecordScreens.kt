package com.servicebook.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Speed
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.servicebook.data.ServiceBookRepository
import com.servicebook.model.ServiceRecord
import com.servicebook.ui.components.CustomInputField
import com.servicebook.ui.components.PrimaryButton
import com.servicebook.ui.components.SecondaryButton
import com.servicebook.ui.components.TopBarHeader
import com.servicebook.ui.theme.BackgroundLight
import com.servicebook.ui.theme.GreenLight
import com.servicebook.ui.theme.GreenSuccess
import com.servicebook.ui.theme.OrangeAccent
import com.servicebook.ui.theme.OrangeLight
import com.servicebook.ui.theme.SurfaceWhite
import com.servicebook.ui.theme.TextPrimary
import com.servicebook.ui.theme.TextSecondary
import com.servicebook.util.ImageUtils
import com.servicebook.util.UniversalImageView
import com.servicebook.util.ValidationUtils
import java.io.File

data class WorkTemplate(
    val title: String,
    val subtitle: String,
    val category: String,
    val icon: ImageVector,
    val defaultDrawable: String
)

val defaultTemplates = listOf(
    WorkTemplate("Замена масла", "Моторное масло и фильтр", "Масло", Icons.Default.FormatColorFill, "receipt_oil"),
    WorkTemplate("Тормозные колодки", "Передние или задние", "Тормоза", Icons.Default.Speed, "receipt_brakes"),
    WorkTemplate("Шины и колёса", "Шиномонтаж, сезонная замена", "Шины", Icons.Default.DirectionsCar, "receipt_tires"),
    WorkTemplate("Другая работа", "Своя запись обслуживания", "Другое", Icons.Default.Add, "receipt_oil")
)

@Composable
fun StepProgressBar(step: Int) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (i in 1..3) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (i <= step) OrangeAccent else Color(0xFFD1D5DB))
                )
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "ШАГ $step ИЗ 3",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = OrangeAccent,
            letterSpacing = 1.sp
        )
    }
}

@Composable
fun AddRecordStep1Screen(
    onNext: (category: String, title: String) -> Unit,
    onCancel: () -> Unit
) {
    val car by ServiceBookRepository.car.collectAsState()
    var selectedIndex by remember { mutableIntStateOf(0) }
    var customTitle by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(bottom = 20.dp)
    ) {
        TopBarHeader(
            title = "Новая запись",
            showBack = false,
            actionText = "Отмена",
            onActionClick = onCancel
        )

        StepProgressBar(step = 1)

        Spacer(modifier = Modifier.height(16.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Что делали?",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${car.brand} ${car.model} · выберите шаблон или введите своё",
                fontSize = 14.sp,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(24.dp))

            defaultTemplates.forEachIndexed { index, template ->
                val isSelected = selectedIndex == index
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) OrangeAccent else Color.Transparent,
                            shape = RoundedCornerShape(18.dp)
                        )
                        .clickable { selectedIndex = index },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) OrangeLight else SurfaceWhite
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) SurfaceWhite else Color(0xFFF3F5F8)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = template.icon,
                                contentDescription = null,
                                tint = if (isSelected) OrangeAccent else TextSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = template.title,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) OrangeAccent else TextPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = template.subtitle,
                                fontSize = 13.sp,
                                color = TextSecondary
                            )
                        }
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = OrangeAccent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            if (selectedIndex == 3) {
                Spacer(modifier = Modifier.height(8.dp))
                CustomInputField(
                    label = "Название работы *",
                    value = customTitle,
                    placeholder = "Например: Замена свечей зажигания",
                    onValueChange = { customTitle = it }
                )
            }
        }

        Box(modifier = Modifier.padding(horizontal = 24.dp)) {
            val canProceed = selectedIndex != 3 || customTitle.trim().isNotEmpty()
            PrimaryButton(
                text = "Далее",
                enabled = canProceed,
                onClick = {
                    val chosen = defaultTemplates[selectedIndex]
                    val finalTitle = if (selectedIndex == 3 && customTitle.isNotBlank()) customTitle.trim() else chosen.title
                    onNext(chosen.category, finalTitle)
                }
            )
        }
    }
}

@Composable
fun AddRecordStep2Screen(
    category: String,
    title: String,
    onNext: (date: String, mileage: Int, price: Int, note: String) -> Unit,
    onBack: () -> Unit,
    onCancel: () -> Unit
) {
    val car by ServiceBookRepository.car.collectAsState()
    var date by remember { mutableStateOf("18.11.2026") }
    var mileageText by remember { mutableStateOf(car.mileage.toString()) }
    var priceText by remember { mutableStateOf(if (title == "Замена масла") "4650" else "6200") }
    var note by remember { mutableStateOf(if (title == "Замена масла") "масло Mobil 5W-40, 3,5 л" else "") }

    val isDateValid = ValidationUtils.isValidDate(date)
    val mileageInt = mileageText.toIntOrNull() ?: 0
    val isMileageValid = mileageInt > 0
    val priceInt = priceText.toIntOrNull() ?: -1
    val isPriceValid = priceInt >= 0

    val isFormValid = isDateValid && isMileageValid && isPriceValid

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(bottom = 20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        TopBarHeader(
            title = "Новая запись",
            showBack = true,
            onBackClick = onBack,
            actionText = "Отмена",
            onActionClick = onCancel
        )

        StepProgressBar(step = 2)

        Spacer(modifier = Modifier.height(16.dp))

        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Text(
                text = "Детали работы",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                fontSize = 15.sp,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(24.dp))

            CustomInputField(
                label = "Дата проведения *",
                value = date,
                placeholder = "ДД.ММ.ГГГГ",
                isError = !isDateValid,
                errorMessage = "Укажите корректную дату ДД.ММ.ГГГГ",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                onValueChange = { date = ValidationUtils.formatDateInput(it) }
            )
            Spacer(modifier = Modifier.height(14.dp))

            CustomInputField(
                label = "Пробег на момент ТО *",
                value = mileageText,
                placeholder = "Например: 112400",
                onlyDigits = true,
                maxLength = 7,
                suffixText = "км",
                isError = !isMileageValid,
                errorMessage = "Пробег должен быть больше 0",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                onValueChange = { mileageText = it }
            )
            Spacer(modifier = Modifier.height(14.dp))

            CustomInputField(
                label = "Стоимость работы и запчастей *",
                value = priceText,
                placeholder = "Например: 4650",
                onlyDigits = true,
                maxLength = 8,
                suffixText = "₽",
                isError = !isPriceValid,
                errorMessage = "Укажите стоимость (цифры)",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                onValueChange = { priceText = it }
            )
            Spacer(modifier = Modifier.height(14.dp))

            CustomInputField(
                label = "Заметка (необязательно)",
                value = note,
                placeholder = "Например: масло Mobil 5W-40, 3,5 л",
                onValueChange = { note = it }
            )

            Spacer(modifier = Modifier.height(36.dp))

            PrimaryButton(
                text = "Далее — добавить чек",
                enabled = isFormValid,
                onClick = {
                    onNext(date, mileageInt, priceInt, note)
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRecordStep3Screen(
    category: String,
    title: String,
    date: String,
    mileage: Int,
    price: Int,
    note: String,
    onSaved: () -> Unit,
    onBack: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    var hasPhoto by remember { mutableStateOf(false) }
    var photoFileName by remember { mutableStateOf("") }
    var photoFileSize by remember { mutableStateOf("") }
    var photoFilePath by remember { mutableStateOf<String?>(null) }
    val chosenDrawableName = remember {
        when (category) {
            "Тормоза" -> "receipt_brakes"
            "Шины" -> "receipt_tires"
            else -> "receipt_oil"
        }
    }

    var showSourceSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Camera Launcher
    val takePhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            val (path, size) = ImageUtils.saveBitmapToFile(context, bitmap, "receipt")
            photoFilePath = path
            photoFileSize = size
            photoFileName = File(path).name
            hasPhoto = true
            showSourceSheet = false
        }
    }

    // Gallery Picker Launcher
    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val res = ImageUtils.saveUriToFile(context, uri, "receipt")
            if (res != null) {
                photoFilePath = res.first
                photoFileSize = res.second
                photoFileName = File(res.first).name
                hasPhoto = true
                showSourceSheet = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(bottom = 20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        TopBarHeader(
            title = "Новая запись",
            showBack = true,
            onBackClick = onBack,
            actionText = "Отмена",
            onActionClick = onCancel
        )

        StepProgressBar(step = 3)

        Spacer(modifier = Modifier.height(16.dp))

        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Text(
                text = "Сохраните чек",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Сделайте снимок на камеру или выберите из галереи.",
                fontSize = 15.sp,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(20.dp))

            // PHOTO PREVIEW
            if (hasPhoto) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(6.dp, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
                ) {
                    UniversalImageView(
                        filePath = photoFilePath,
                        drawableName = chosenDrawableName,
                        contentDescription = "Чек",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                            .clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = photoFileName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Text(
                    text = "Прикреплён · $photoFileSize",
                    fontSize = 13.sp,
                    color = GreenSuccess
                )

                Spacer(modifier = Modifier.height(18.dp))

                SecondaryButton(
                    text = "Переснять или выбрать другое фото",
                    onClick = { showSourceSheet = true }
                )
            } else {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showSourceSheet = true },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, OrangeAccent.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 36.dp, horizontal = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(OrangeLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = null,
                                tint = OrangeAccent,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Прикрепить фото чека или заказ-наряда",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Снять на камеру или загрузить из галереи",
                            fontSize = 13.sp,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            PrimaryButton(
                text = "Сохранить запись",
                onClick = {
                    val record = ServiceRecord(
                        title = title,
                        category = category,
                        date = date,
                        mileage = mileage,
                        price = price,
                        note = note,
                        receiptFileName = if (hasPhoto) photoFileName else "",
                        receiptFileSize = if (hasPhoto) photoFileSize else "",
                        receiptDrawableName = chosenDrawableName,
                        receiptFilePath = photoFilePath
                    )
                    ServiceBookRepository.addRecord(record)
                    onSaved()
                }
            )

            if (!hasPhoto) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Сохранить без фото чека",
                    fontSize = 14.sp,
                    color = TextSecondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val record = ServiceRecord(
                                title = title,
                                category = category,
                                date = date,
                                mileage = mileage,
                                price = price,
                                note = note,
                                receiptFileName = ""
                            )
                            ServiceBookRepository.addRecord(record)
                            onSaved()
                        }
                        .padding(8.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    if (showSourceSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSourceSheet = false },
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
                    text = "Добавить фото",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Сделайте реальный снимок чека или выберите файл",
                    fontSize = 14.sp,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(24.dp))

                PrimaryButton(
                    text = "Сделать снимок на камеру",
                    onClick = {
                        takePhotoLauncher.launch(null)
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                SecondaryButton(
                    text = "Выбрать фото из галереи",
                    onClick = {
                        pickImageLauncher.launch("image/*")
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                SecondaryButton(
                    text = "Отмена",
                    onClick = { showSourceSheet = false }
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}


@Composable
fun RecordSuccessScreen(
    title: String = "Замена масла",
    onOpenHistory: () -> Unit,
    onGoToMain: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(GreenLight),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = GreenSuccess,
                modifier = Modifier.size(44.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Запись добавлена!",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Работа «$title» успешно сохранена в сервисную книжку и документы автомобиля.",
            fontSize = 15.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )

        Spacer(modifier = Modifier.height(36.dp))

        PrimaryButton(
            text = "Перейти к истории",
            onClick = onOpenHistory
        )

        Spacer(modifier = Modifier.height(12.dp))

        SecondaryButton(
            text = "На главную",
            onClick = onGoToMain
        )
    }
}
