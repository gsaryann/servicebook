package com.servicebook.ui.navigation

sealed class Screen(val route: String) {
    object Onboarding1 : Screen("onboarding_1")
    object Onboarding2 : Screen("onboarding_2")
    object Login : Screen("login")
    object Register : Screen("register")
    object TelegramAuth : Screen("telegram_auth")
    object YandexAuth : Screen("yandex_auth")
    object CheckData : Screen("check_data")
    object AddCar : Screen("add_car")

    object MainTab : Screen("main_tab")

    object AddRecordStep1 : Screen("add_record_step1")
    object AddRecordStep2 : Screen("add_record_step2/{category}/{title}") {
        fun createRoute(category: String, title: String) = "add_record_step2/$category/$title"
    }
    object AddRecordStep3 : Screen("add_record_step3")
    object RecordSuccess : Screen("record_success")

    object RecordDetails : Screen("record_details/{recordId}") {
        fun createRoute(recordId: String) = "record_details/$recordId"
    }

    object Reminders : Screen("reminders")
    object EditReminder : Screen("edit_reminder/{reminderId}") {
        fun createRoute(reminderId: String) = "edit_reminder/$reminderId"
    }

    object DocumentViewer : Screen("document_viewer/{docId}") {
        fun createRoute(docId: String) = "document_viewer/$docId"
    }
}

enum class BottomTab(val title: String) {
    DASHBOARD("Главная"),
    HISTORY("История"),
    DOCUMENTS("Документы"),
    PROFILE("Профиль")
}
