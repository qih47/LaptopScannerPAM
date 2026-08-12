package com.dev.scanlaptop

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.navArgument
import com.dev.scanlaptop.data.SessionManager
import com.dev.scanlaptop.data.HistoryLog
import com.dev.scanlaptop.data.SupabaseConfig
import com.dev.scanlaptop.data.repository.AppUpdateRepository
import com.dev.scanlaptop.data.repository.AppVersion
import com.dev.scanlaptop.data.repository.AuthRepository
import com.dev.scanlaptop.service.ApkDownloader
import com.dev.scanlaptop.ui.components.ForceUpdateDialog
import com.dev.scanlaptop.ui.screens.DashboardScreen
import com.dev.scanlaptop.ui.screens.DetailLaptopScreen
import com.dev.scanlaptop.ui.screens.LogDetailScreen
import com.dev.scanlaptop.ui.screens.LoginScreen
import com.dev.scanlaptop.ui.screens.SplashScreen
import com.dev.scanlaptop.ui.theme.LaptopScannerAppTheme
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import com.dev.scanlaptop.worker.OverdueWorker
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Setup Overdue Worker
        setupOverdueWorker()
        
        setContent {
            LaptopScannerAppTheme {
                AppNavigation()
            }
        }
    }

    private fun setupOverdueWorker() {
        val workRequest = PeriodicWorkRequestBuilder<OverdueWorker>(
            12, TimeUnit.HOURS, // Run every 12 hours
            15, TimeUnit.MINUTES // Flex interval
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            OverdueWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE, // UPDATE works for WorkManager 2.8+
            workRequest
        )
    }
}

@Composable
fun AppNavigation() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()
    val sessionManager = remember { SessionManager(context) }
    val loggedInUser by sessionManager.userDataFlow.collectAsState(initial = null)
    var showSplash by remember { mutableStateOf(true) }

    // Update variables
    var showForceUpdateDialog by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<AppVersion?>(null) }
    var isDownloading by remember { mutableStateOf(false) }
    val appUpdateRepo = remember { AppUpdateRepository(SupabaseConfig.client) }
    val apkDownloader = remember { ApkDownloader(context) }
    val authRepository = remember { AuthRepository() }

    LaunchedEffect(loggedInUser) {
        if (loggedInUser != null) {
            authRepository.updateAppVersionCode(loggedInUser!!.npp, BuildConfig.VERSION_CODE)
        }
    }

    LaunchedEffect(Unit) {
        val latestVersion = appUpdateRepo.getLatestAppVersion()
        if (latestVersion != null && latestVersion.version_code > BuildConfig.VERSION_CODE) {
            updateInfo = latestVersion
            showForceUpdateDialog = true
        }
    }

    if (showForceUpdateDialog && updateInfo != null) {
        ForceUpdateDialog(
            versionName = updateInfo!!.version_name,
            releaseNotes = updateInfo!!.release_notes ?: "",
            isDownloading = isDownloading,
            isForceUpdate = updateInfo!!.is_force_update,
            onUpdateClick = {
                isDownloading = true
                apkDownloader.downloadApk(
                    url = updateInfo!!.download_url,
                    fileName = "LaptopScanner_v${updateInfo!!.version_name}.apk",
                    onDownloadComplete = { uri ->
                        isDownloading = false
                        apkDownloader.installApk(uri)
                    }
                )
            },
            onIgnoreClick = {
                showForceUpdateDialog = false
            }
        )
    } else if (showSplash) {
        SplashScreen(onTimeout = { showSplash = false })
    } else {
        NavHost(
            navController = navController,
            startDestination = if (loggedInUser == null) "login" else "dashboard",
            enterTransition = {
                fadeIn(animationSpec = tween(300)) + scaleIn(initialScale = 0.95f, animationSpec = tween(300))
            },
            exitTransition = {
                fadeOut(animationSpec = tween(300)) + scaleOut(targetScale = 1.05f, animationSpec = tween(300))
            },
            popEnterTransition = {
                fadeIn(animationSpec = tween(300)) + scaleIn(initialScale = 1.05f, animationSpec = tween(300))
            },
            popExitTransition = {
                fadeOut(animationSpec = tween(300)) + scaleOut(targetScale = 0.95f, animationSpec = tween(300))
            }
        ) {
            // 1. Login
            composable("login") {
                LoginScreen(onLoginSuccess = {
                    navController.navigate("dashboard") {
                        popUpTo("login") { inclusive = true }
                    }
                })
            }

            // 2. Dashboard
            composable("dashboard") {
                loggedInUser?.let { user ->
                    DashboardScreen(
                        navController = navController,
                        userData = user,
                        onLogout = {
                            scope.launch {
                                sessionManager.clearSession()
                                navController.navigate("login") {
                                    popUpTo("dashboard") { inclusive = true }
                                }
                            }
                        },
                        onItemClick = { log ->
                            // Dari riwayat: kirim flag fromHistory=true
                            navController.navigate("detail_laptop/${log.laptop_uuid}?fromHistory=true")
                        }
                    )
                }
            }

            // 3. Detail Laptop — dua sumber:
            //    - Dari scan QR: detail_laptop/{qrCode} (fromHistory default false)
            //    - Dari riwayat: detail_laptop/{uuid}?fromHistory=true
            composable(
                route = "detail_laptop/{laptop_uuid}?fromHistory={fromHistory}",
                arguments = listOf(
                    navArgument("laptop_uuid") { type = NavType.StringType },
                    navArgument("fromHistory") {
                        type = NavType.BoolType
                        defaultValue = false
                    }
                )
            ) { backStackEntry ->
                val uuid = backStackEntry.arguments?.getString("laptop_uuid") ?: ""
                val fromHistory = backStackEntry.arguments?.getBoolean("fromHistory") ?: false
                loggedInUser?.let { user ->
                    DetailLaptopScreen(
                        laptopUuid = uuid,
                        isFromHistory = fromHistory,
                        userData = user,
                        onBack = { navController.popBackStack() },
                        onLogClick = { log ->
                            navController.currentBackStackEntry?.savedStateHandle?.set("selected_log", log)
                            navController.navigate("log_detail")
                        },
                        onSuccess = {
                            navController.previousBackStackEntry?.savedStateHandle?.set(
                                "success_message",
                                "Data berhasil ditambahkan!"
                            )
                            navController.popBackStack()
                        },
                        onScanAgain = {
                            // Set flag agar dashboard langsung buka tab scanner
                            navController.previousBackStackEntry?.savedStateHandle?.set(
                                "open_scanner", true
                            )
                            navController.popBackStack()
                        }
                    )
                }
            }

            // 4. Log Detail
            composable("log_detail") {
                val log = remember {
                    navController.previousBackStackEntry?.savedStateHandle?.get<HistoryLog>("selected_log")
                }

                if (log != null) {
                    LogDetailScreen(log = log, onBack = { navController.popBackStack() })
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Detail log tidak ditemukan")
                            Button(onClick = { navController.popBackStack() }) {
                                Text("Kembali")
                            }
                        }
                    }
                }
            }

            // 5. Officer History
            composable(
                route = "officer_history/{npp}",
                arguments = listOf(navArgument("npp") { type = NavType.StringType })
            ) { backStackEntry ->
                val npp = backStackEntry.arguments?.getString("npp") ?: ""
                val officerViewModel: com.dev.scanlaptop.ui.viewmodel.OfficerHistoryViewModel = viewModel()
                com.dev.scanlaptop.ui.screens.OfficerHistoryScreen(
                    viewModel = officerViewModel,
                    npp = npp,
                    onBack = { navController.popBackStack() },
                    onItemClick = { log ->
                        navController.currentBackStackEntry?.savedStateHandle?.set("selected_log", log)
                        navController.navigate("log_detail")
                    }
                )
            }
        }
    }
}