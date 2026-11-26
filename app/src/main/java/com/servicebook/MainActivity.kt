package com.servicebook

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.servicebook.data.AuthApiClient
import com.servicebook.data.AuthRequest
import com.servicebook.data.PendingAuthSession
import com.servicebook.data.ServiceBookRepository
import com.servicebook.ui.components.AppBottomNavigation
import com.servicebook.ui.navigation.BottomTab
import com.servicebook.ui.navigation.Screen
import com.servicebook.ui.screens.AddCarScreen
import com.servicebook.ui.screens.AddRecordStep1Screen
import com.servicebook.ui.screens.AddRecordStep2Screen
import com.servicebook.ui.screens.AddRecordStep3Screen
import com.servicebook.ui.screens.CheckDataScreen
import com.servicebook.ui.screens.DashboardScreen
import com.servicebook.ui.screens.DocumentViewerScreen
import com.servicebook.ui.screens.DocumentsScreen
import com.servicebook.ui.screens.EditReminderScreen
import com.servicebook.ui.screens.HistoryScreen
import com.servicebook.ui.screens.LoginScreen
import com.servicebook.ui.screens.OnboardingScreen
import com.servicebook.ui.screens.ProfileScreen
import com.servicebook.ui.screens.RecordDetailsScreen
import com.servicebook.ui.screens.RecordSuccessScreen
import com.servicebook.ui.screens.RegisterScreen
import com.servicebook.ui.screens.RemindersScreen
import com.servicebook.ui.screens.TelegramAuthScreen
import com.servicebook.ui.screens.YandexAuthScreen
import com.servicebook.ui.theme.ServiceBookTheme
import com.yandex.authsdk.YandexAuthLoginOptions
import com.yandex.authsdk.YandexAuthOptions
import com.yandex.authsdk.YandexAuthResult
import com.yandex.authsdk.YandexAuthSdk
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val deepLinkUri = mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ServiceBookRepository.init(applicationContext)
        intent?.data?.let { deepLinkUri.value = it }
        enableEdgeToEdge()
        setContent {
            ServiceBookTheme {
                ServiceBookApp(
                    deepLinkUri = deepLinkUri.value,
                    onDeepLinkConsumed = { deepLinkUri.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.let { deepLinkUri.value = it }
    }
}

@Composable
fun ServiceBookApp(
    deepLinkUri: Uri? = null,
    onDeepLinkConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val yandexSdk = remember { YandexAuthSdk.create(YandexAuthOptions(context)) }

    val navController = rememberNavController()
    val isLoggedIn by ServiceBookRepository.isLoggedIn.collectAsState()
    var currentBottomTab by remember { mutableStateOf(BottomTab.DASHBOARD) }

    // State for temporary add-record wizard
    var tempCategory by remember { mutableStateOf("Масло") }
    var tempTitle by remember { mutableStateOf("Замена масла") }
    var tempDate by remember { mutableStateOf("18 ноября 2026") }
    var tempMileage by remember { mutableIntStateOf(112400) }
    var tempPrice by remember { mutableIntStateOf(4650) }
    var tempNote by remember { mutableStateOf("Масло Mobil 5W-40, 3,5 л") }
    var historyDeletedToast by remember { mutableStateOf<String?>(null) }
    val initialState = remember {
        resolveInitialAuthState(
            isLoggedIn = isLoggedIn,
            pendingSession = ServiceBookRepository.getPendingAuth()
        )
    }
    var isNewUserRegistration by remember { mutableStateOf(initialState.isNewUserRegistration) }
    var yandexAuthSource by remember { mutableStateOf("login") }
    var pendingAuthRequest by remember { mutableStateOf(initialState.pendingAuthRequest) }
    var pendingAuthPhone by remember { mutableStateOf(initialState.pendingAuthPhone) }
    var authStartError by remember { mutableStateOf<String?>(null) }
    var isAuthStarting by remember { mutableStateOf(false) }

    val startTelegramAuth: (String, String) -> Unit = { phone, mode ->
        coroutineScope.launch {
            if (!isAuthStarting) {
                isAuthStarting = true
                authStartError = null
                val clientToken = ServiceBookRepository.getOrCreateAuthAttemptToken(phone, mode)
                try {
                    val request = AuthApiClient.createRequest(BuildConfig.AUTH_API_BASE_URL, phone, mode, clientToken)
                    ServiceBookRepository.savePendingAuth(
                        requestId = request.requestId,
                        botUrl = request.botUrl,
                        phone = phone,
                        mode = mode,
                        expiresIn = request.expiresIn,
                        clientToken = clientToken
                    )
                    pendingAuthRequest = request
                    pendingAuthPhone = phone
                    isNewUserRegistration = (mode == "register")
                    navController.navigate(Screen.TelegramAuth.route)
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(request.botUrl)))
                    } catch (_: Exception) {
                        // The TelegramAuthScreen keeps an explicit button to open the bot again.
                    }
                } catch (error: Exception) {
                    authStartError = error.message ?: "Не удалось подключиться к локальному серверу ServiceBook."
                } finally {
                    isAuthStarting = false
                }
            }
        }
    }

    val yandexLauncher = rememberLauncherForActivityResult(yandexSdk.contract) { result ->
        when (result) {
            is YandexAuthResult.Success -> {
                val token = result.token.value
                ServiceBookRepository.saveYandexAccessToken(token)
                ServiceBookRepository.saveAuthProvider("yandex")
                coroutineScope.launch {
                    ServiceBookRepository.fetchYandexProfile(token)
                    if (yandexAuthSource == "login") {
                        val currentPhone = ServiceBookRepository.userProfile.value.phone
                        val isRegistered = currentPhone.isNotBlank() && ServiceBookRepository.isPhoneRegistered(currentPhone)
                        if (isRegistered) {
                            val hasCar = ServiceBookRepository.car.value.brand.isNotBlank()
                            if (hasCar) {
                                navController.navigate(Screen.MainTab.route) {
                                    popUpTo(Screen.Login.route) { inclusive = true }
                                }
                            } else {
                                isNewUserRegistration = false
                                navController.navigate(Screen.AddCar.route) {
                                    popUpTo(Screen.Login.route) { inclusive = true }
                                }
                            }
                        } else {
                            navController.navigate(Screen.CheckData.route)
                        }
                    } else {
                        // Launched from RegisterScreen ("Заполнить через Яндекс ID"):
                        // Stay on RegisterScreen, the fields are now auto-populated with Yandex data.
                    }
                }
            }
            is YandexAuthResult.Failure -> {
                // Ignore failure
            }
            is YandexAuthResult.Cancelled -> {
                // User cancelled
            }
        }
    }

    LaunchedEffect(deepLinkUri) {
        deepLinkUri?.let { uri ->
            val isYandexScheme = uri.scheme?.startsWith("yx") == true
            val isYandexHttps = (uri.scheme == "https" && uri.host?.contains("yxf2a2a3573a88413fab17c67ecc095622") == true)
            val isServiceBookScheme = (uri.scheme == "servicebook" && uri.host == "auth")

            if (isYandexScheme || isYandexHttps || isServiceBookScheme) {
                val fragment = uri.fragment ?: ""
                val query = uri.query ?: ""
                val combined = "$fragment&$query"
                val token = combined.split("&")
                    .firstOrNull { it.startsWith("access_token=") }
                    ?.substringAfter("access_token=")

                if (!token.isNullOrBlank()) {
                    ServiceBookRepository.saveYandexAccessToken(token)
                    ServiceBookRepository.saveAuthProvider("yandex")
                    ServiceBookRepository.fetchYandexProfile(token)
                    onDeepLinkConsumed()
                    val hasCar = ServiceBookRepository.car.value.brand.isNotBlank()
                    if (hasCar) {
                        navController.navigate(Screen.MainTab.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    } else {
                        navController.navigate(Screen.AddCar.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    }
                }
            }
        }
    }

    val initialRoute = remember { initialState.initialRoute }

    NavHost(
        navController = navController,
        startDestination = initialRoute
    ) {
        // 1. Onboarding (Pages 1 & 2)
        composable(Screen.Onboarding1.route) {
            OnboardingScreen(
                onFinish = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Onboarding1.route) { inclusive = true }
                    }
                }
            )
        }

        // 2. Auth Flow (Pages 3 - 10)
        composable(Screen.Login.route) {
            LoginScreen(
                onTelegramClick = { phone ->
                    isNewUserRegistration = false
                    startTelegramAuth(phone, "login")
                },
                isTelegramLoading = isAuthStarting,
                telegramError = authStartError,
                onYandexClick = {
                    yandexAuthSource = "login"
                    yandexLauncher.launch(YandexAuthLoginOptions())
                },
                onRegisterClick = { navController.navigate(Screen.Register.route) },
                onBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate(Screen.Onboarding1.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    }
                }
            )
        }

        composable(Screen.Register.route) {
            RegisterScreen(
                onContinueTelegram = {
                    isNewUserRegistration = true
                    startTelegramAuth(ServiceBookRepository.userProfile.value.phone, "register")
                },
                isTelegramLoading = isAuthStarting,
                telegramError = authStartError,
                onContinueYandex = {
                    yandexAuthSource = "register"
                    yandexLauncher.launch(YandexAuthLoginOptions())
                },
                onRegistered = {
                    isNewUserRegistration = true
                    navController.navigate(Screen.AddCar.route) {
                        popUpTo(Screen.Register.route) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.TelegramAuth.route) {
            val handleTelegramBack: () -> Unit = {
                performTelegramBackNavigation(
                    popBackStack = { navController.popBackStack() },
                    navigateToLogin = {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    clearPendingRequestState = { pendingAuthRequest = null }
                )
            }
            androidx.activity.compose.BackHandler(onBack = handleTelegramBack)

            val authRequest = pendingAuthRequest
            if (authRequest != null) {
                TelegramAuthScreen(
                    isRegistration = isNewUserRegistration,
                    requestId = authRequest.requestId,
                    botUrl = authRequest.botUrl,
                    phone = pendingAuthPhone,
                    expiresIn = authRequest.expiresIn,
                    onOpenBot = { url ->
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        } catch (_: Exception) {
                            authStartError = "Не удалось открыть Telegram. Проверьте, установлен ли Telegram."
                        }
                    },
                    onSuccess = {
                        ServiceBookRepository.clearPendingAuth()
                        ServiceBookRepository.clearAuthAttemptToken()
                        pendingAuthRequest = null
                        if (isNewUserRegistration) {
                            isNewUserRegistration = false
                            navController.navigate(Screen.AddCar.route) {
                                popUpTo(Screen.TelegramAuth.route) { inclusive = true }
                            }
                        } else {
                            val hasCar = ServiceBookRepository.car.value.brand.isNotBlank()
                            val dest = if (hasCar) Screen.MainTab.route else Screen.AddCar.route
                            navController.navigate(dest) {
                                popUpTo(Screen.TelegramAuth.route) { inclusive = true }
                            }
                        }
                    },
                    onNeedProfile = {
                        ServiceBookRepository.clearPendingAuth()
                        ServiceBookRepository.clearAuthAttemptToken()
                        pendingAuthRequest = null
                        isNewUserRegistration = false
                        navController.navigate(Screen.CheckData.route) {
                            popUpTo(Screen.TelegramAuth.route) { inclusive = true }
                        }
                    },
                    onBack = handleTelegramBack
                )
            } else {
                Text("Запрос авторизации не создан. Вернитесь назад и повторите попытку.")
            }
        }

        composable(Screen.YandexAuth.route) {
            YandexAuthScreen(
                onFoundAccount = {
                    val dest = if (ServiceBookRepository.car.value.brand.isNotBlank()) {
                        Screen.MainTab.route
                    } else {
                        Screen.AddCar.route
                    }
                    navController.navigate(dest) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onFirstEntry = {
                    navController.navigate(Screen.CheckData.route)
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.CheckData.route) {
            CheckDataScreen(
                onConfirm = {
                    navController.navigate(Screen.AddCar.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.AddCar.route) {
            AddCarScreen(
                onCarSaved = {
                    navController.navigate(Screen.MainTab.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        // 3. Main Tab Screen (Pages 12, 20, 28, 29)
        composable(Screen.MainTab.route) {
            Scaffold(
                bottomBar = {
                    AppBottomNavigation(
                        currentTab = currentBottomTab,
                        onTabSelected = { currentBottomTab = it }
                    )
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    when (currentBottomTab) {
                        BottomTab.DASHBOARD -> DashboardScreen(
                            onNewRecordClick = { navController.navigate(Screen.AddRecordStep1.route) },
                            onAllRemindersClick = { navController.navigate(Screen.Reminders.route) },
                            onDocumentsClick = { currentBottomTab = BottomTab.DOCUMENTS },
                            onRecordDetailsClick = { recordId ->
                                navController.navigate(Screen.RecordDetails.createRoute(recordId))
                            }
                        )
                        BottomTab.HISTORY -> HistoryScreen(
                            onRecordClick = { recordId ->
                                navController.navigate(Screen.RecordDetails.createRoute(recordId))
                            },
                            onAddFirstRecord = { navController.navigate(Screen.AddRecordStep1.route) },
                            deletedToastMessage = historyDeletedToast
                        )
                        BottomTab.DOCUMENTS -> DocumentsScreen(
                            onOpenDocument = { docId ->
                                navController.navigate(Screen.DocumentViewer.createRoute(docId))
                            }
                        )
                        BottomTab.PROFILE -> ProfileScreen(
                            onMyCarClick = { navController.navigate(Screen.AddCar.route) },
                            onRemindersClick = { navController.navigate(Screen.Reminders.route) },
                            onLogout = {
                                navController.navigate(Screen.Login.route) {
                                    popUpTo(0) { inclusive = true }
                                }
                            },
                            onAccountDeleted = {
                                tempCategory = "Масло"
                                tempTitle = "Замена масла"
                                tempDate = "18 ноября 2026"
                                tempMileage = 112400
                                tempPrice = 4650
                                tempNote = "Масло Mobil 5W-40, 3,5 л"
                                historyDeletedToast = null
                                pendingAuthRequest = null
                                pendingAuthPhone = ""
                                authStartError = null
                                isNewUserRegistration = false
                                currentBottomTab = BottomTab.DASHBOARD
                                navController.navigate(Screen.Login.route) {
                                    popUpTo(0) { inclusive = true }
                                }
                            },
                            onReauthenticateForDelete = {
                                navController.navigate(Screen.Login.route) {
                                    popUpTo(0) { inclusive = true }
                                }
                            }
                        )
                    }
                }
            }
        }

        // 4. Add Record Wizard (Pages 15 - 19)
        composable(Screen.AddRecordStep1.route) {
            AddRecordStep1Screen(
                onNext = { cat, tit ->
                    tempCategory = cat
                    tempTitle = tit
                    navController.navigate(Screen.AddRecordStep2.createRoute(cat, tit))
                },
                onCancel = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.AddRecordStep2.route,
            arguments = listOf(
                navArgument("category") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val cat = backStackEntry.arguments?.getString("category") ?: tempCategory
            val tit = backStackEntry.arguments?.getString("title") ?: tempTitle
            AddRecordStep2Screen(
                category = cat,
                title = tit,
                onNext = { d, m, p, n ->
                    tempDate = d
                    tempMileage = m
                    tempPrice = p
                    tempNote = n
                    navController.navigate(Screen.AddRecordStep3.route)
                },
                onBack = { navController.popBackStack() },
                onCancel = { navController.navigate(Screen.MainTab.route) }
            )
        }

        composable(Screen.AddRecordStep3.route) {
            AddRecordStep3Screen(
                category = tempCategory,
                title = tempTitle,
                date = tempDate,
                mileage = tempMileage,
                price = tempPrice,
                note = tempNote,
                onSaved = {
                    navController.navigate(Screen.RecordSuccess.route)
                },
                onBack = { navController.popBackStack() },
                onCancel = { navController.navigate(Screen.MainTab.route) }
            )
        }

        composable(Screen.RecordSuccess.route) {
            RecordSuccessScreen(
                onOpenHistory = {
                    currentBottomTab = BottomTab.HISTORY
                    navController.navigate(Screen.MainTab.route) {
                        popUpTo(Screen.MainTab.route) { inclusive = true }
                    }
                },
                onGoToMain = {
                    currentBottomTab = BottomTab.DASHBOARD
                    navController.navigate(Screen.MainTab.route) {
                        popUpTo(Screen.MainTab.route) { inclusive = true }
                    }
                }
            )
        }

        // 5. Record Details (Page 21, 22, 23)
        composable(
            route = Screen.RecordDetails.route,
            arguments = listOf(navArgument("recordId") { type = NavType.StringType })
        ) { backStackEntry ->
            val recordId = backStackEntry.arguments?.getString("recordId") ?: ""
            RecordDetailsScreen(
                recordId = recordId,
                onBack = { navController.popBackStack() },
                onDeleted = {
                    historyDeletedToast = "Запись удалена. Чек сохранён."
                    currentBottomTab = BottomTab.HISTORY
                    navController.popBackStack()
                },
                onOpenReceipt = {
                    navController.navigate(Screen.DocumentViewer.createRoute("doc-1"))
                }
            )
        }

        // 6. Reminders Flow (Page 25, 26)
        composable(Screen.Reminders.route) {
            RemindersScreen(
                onConfigureReminder = { reminderId ->
                    navController.navigate(Screen.EditReminder.createRoute(reminderId))
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.EditReminder.route,
            arguments = listOf(navArgument("reminderId") { type = NavType.StringType })
        ) { backStackEntry ->
            val reminderId = backStackEntry.arguments?.getString("reminderId") ?: ""
            EditReminderScreen(
                reminderId = reminderId,
                onSaved = { navController.popBackStack() },
                onBack = { navController.popBackStack() }
            )
        }

        // 7. Document Viewer (Page 32)
        composable(
            route = Screen.DocumentViewer.route,
            arguments = listOf(navArgument("docId") { type = NavType.StringType })
        ) { backStackEntry ->
            val docId = backStackEntry.arguments?.getString("docId") ?: ""
            DocumentViewerScreen(
                docId = docId,
                onBack = { navController.popBackStack() }
            )
        }
    }
}

internal data class RestoredTelegramAuthState(
    val initialRoute: String,
    val isNewUserRegistration: Boolean,
    val pendingAuthRequest: AuthRequest?,
    val pendingAuthPhone: String
)

internal fun resolveInitialAuthState(
    isLoggedIn: Boolean,
    pendingSession: PendingAuthSession?,
    nowMs: Long = System.currentTimeMillis()
): RestoredTelegramAuthState {
    if (isLoggedIn) {
        return RestoredTelegramAuthState(
            initialRoute = Screen.MainTab.route,
            isNewUserRegistration = false,
            pendingAuthRequest = null,
            pendingAuthPhone = ""
        )
    }
    val request = pendingSession?.let {
        val remainingSeconds = ((it.deadlineMs - nowMs) / 1000).toInt().coerceAtLeast(1)
        AuthRequest(it.requestId, it.botUrl, remainingSeconds)
    }
    val route = if (request != null) Screen.TelegramAuth.route else Screen.Onboarding1.route
    return RestoredTelegramAuthState(
        initialRoute = route,
        isNewUserRegistration = pendingSession?.mode == "register",
        pendingAuthRequest = request,
        pendingAuthPhone = pendingSession?.phone ?: ""
    )
}

internal fun performTelegramBackNavigation(
    popBackStack: () -> Boolean,
    navigateToLogin: () -> Unit,
    clearPendingRequestState: () -> Unit
) {
    ServiceBookRepository.clearPendingAuth()
    ServiceBookRepository.clearAuthAttemptToken()
    clearPendingRequestState()
    if (!popBackStack()) {
        navigateToLogin()
    }
}
