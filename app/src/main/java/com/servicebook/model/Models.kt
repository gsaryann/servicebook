package com.servicebook.model

import java.io.Serializable
import java.util.UUID

data class Car(
    val brand: String,
    val model: String,
    val year: Int,
    val engine: String,
    val mileage: Int
) : Serializable

data class ServiceRecord(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val category: String,
    val date: String,
    val mileage: Int,
    val price: Int,
    val note: String = "",
    val receiptFileName: String = "",
    val receiptFileSize: String = "",
    val receiptDrawableName: String = "receipt_oil",
    val receiptFilePath: String? = null
) : Serializable

data class MaintenanceReminder(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val targetMileage: Int,
    val remainingKm: Int,
    val targetDate: String,
    val isOverdue: Boolean = false,
    val overdueDays: Int = 0,
    val intervalKm: Int = 10000,
    val intervalMonths: Int = 12,
    val pushEnabled: Boolean = true
) : Serializable

enum class DocumentFilter(val label: String) {
    ALL("Все"),
    RECEIPTS("Чеки"),
    ORDERS("Заказ-наряды")
}

data class DocumentItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val date: String,
    val fileFormat: String = "JPG",
    val fileSize: String = "",
    val isReceipt: Boolean = true,
    val drawableResName: String = "receipt_oil",
    val filePath: String? = null
) : Serializable

data class UserProfile(
    val firstName: String = "",
    val lastName: String = "",
    val birthDate: String = "",
    val phone: String = ""
) : Serializable
