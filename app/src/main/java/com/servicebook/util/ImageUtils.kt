package com.servicebook.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ImageUtils {

    /**
     * Saves a camera-captured Bitmap to internal storage and returns Pair(absoluteFilePath, fileSizeFormatted).
     */
    fun saveBitmapToFile(context: Context, bitmap: Bitmap, prefix: String = "receipt"): Pair<String, String> {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "${prefix}_$timeStamp.jpg"
        val file = File(context.filesDir, fileName)

        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }

        val sizeFormatted = formatFileSize(file.length())
        return Pair(file.absolutePath, sizeFormatted)
    }

    /**
     * Copies a gallery-selected Uri stream to internal storage and returns Pair(absoluteFilePath, fileSizeFormatted).
     */
    fun saveUriToFile(context: Context, uri: Uri, prefix: String = "doc"): Pair<String, String>? {
        return try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "${prefix}_$timeStamp.jpg"
            val file = File(context.filesDir, fileName)

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }

            val sizeFormatted = formatFileSize(file.length())
            Pair(file.absolutePath, sizeFormatted)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun formatFileSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f МБ", bytes / (1024.0 * 1024.0)).replace(".", ",")
            bytes >= 1024 -> "${bytes / 1024} КБ"
            else -> "$bytes Б"
        }
    }

    fun loadBitmap(filePath: String?): Bitmap? {
        if (filePath.isNullOrEmpty()) return null
        val file = File(filePath)
        if (!file.exists()) return null
        return try {
            BitmapFactory.decodeFile(file.absolutePath)
        } catch (e: Exception) {
            null
        }
    }
}

@Composable
fun UniversalImageView(
    filePath: String? = null,
    drawableName: String? = "receipt_oil",
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val context = LocalContext.current

    // Try loading real file first
    val realBitmap = remember(filePath) {
        ImageUtils.loadBitmap(filePath)
    }

    if (realBitmap != null) {
        Image(
            bitmap = realBitmap.asImageBitmap(),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale
        )
    } else {
        val safeName = if (drawableName.isNullOrEmpty()) "receipt_oil" else drawableName
        val resId = remember(safeName) {
            val id = context.resources.getIdentifier(safeName, "drawable", context.packageName)
            if (id != 0) id else context.resources.getIdentifier("receipt_oil", "drawable", context.packageName)
        }

        if (resId != 0) {
            Image(
                painter = painterResource(id = resId),
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = contentScale
            )
        } else {
            Box(
                modifier = modifier.background(Color(0xFFE9EDF2)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
                    contentDescription = null,
                    tint = Color(0xFF6B7280),
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}
