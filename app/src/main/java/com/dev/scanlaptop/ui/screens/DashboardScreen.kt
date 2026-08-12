package com.dev.scanlaptop.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import java.time.ZonedDateTime
import java.time.ZoneOffset
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import com.dev.scanlaptop.ui.components.HistoryLogBottomSheetContent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.dev.scanlaptop.R
import com.dev.scanlaptop.data.HistoryLog
import com.dev.scanlaptop.data.UserData
import com.dev.scanlaptop.data.repository.LaptopRepository
import com.dev.scanlaptop.service.RealtimeNotificationService
import com.dev.scanlaptop.ui.components.CameraWithFocusIndicator
import com.dev.scanlaptop.ui.components.FilterBottomSheet
import com.dev.scanlaptop.ui.components.HistoryCardPro
import com.dev.scanlaptop.ui.components.NavBarIcon
import com.dev.scanlaptop.ui.components.StatusChipMini
import com.dev.scanlaptop.ui.components.shimmerEffect
import com.dev.scanlaptop.utils.FeedbackHelper
import com.dev.scanlaptop.ui.viewmodel.DashboardViewModel
import com.dev.scanlaptop.ui.viewmodel.OverdueViewModel
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    navController: NavHostController,
    userData: UserData,
    onLogout: () -> Unit,
    onItemClick: (HistoryLog) -> Unit,
    dashboardViewModel: DashboardViewModel = viewModel(),
    overdueViewModel: OverdueViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sessionManager = remember { com.dev.scanlaptop.data.SessionManager(context) }
    val pushNotifEnabled by sessionManager.pushNotifEnabledFlow.collectAsState(initial = false)
    val sharedPrefs = remember { context.getSharedPreferences("monitoring_prefs", Context.MODE_PRIVATE) }

    // ─── Observe state dari ViewModel ─────────────────────────
    val historyList by dashboardViewModel.historyList.collectAsState()
    val searchResults by dashboardViewModel.searchResults.collectAsState()
    val stats by dashboardViewModel.stats.collectAsState()
    val isLoading by dashboardViewModel.isLoading.collectAsState()
    val isRefreshing by dashboardViewModel.isRefreshing.collectAsState()
    val isAppending by dashboardViewModel.isAppending.collectAsState()
    val isSearching by dashboardViewModel.isSearching.collectAsState()
    val isLastPage by dashboardViewModel.isLastPage.collectAsState()
    val errorMessage by dashboardViewModel.errorMessage.collectAsState()
    val isRealtimeConnected by dashboardViewModel.isRealtimeConnected.collectAsState()
    val overdueCount by overdueViewModel.overdueCount.collectAsState()
    val analyticsData by dashboardViewModel.analyticsData.collectAsState()
    val isAnalyticsLoading by dashboardViewModel.isAnalyticsLoading.collectAsState()
    val newTransactionEvent by dashboardViewModel.newTransactionEvent.collectAsState()
    val newTransactionCount by dashboardViewModel.newTransactionCount.collectAsState()

    // ─── State lokal (UI only) ─────────────────────────────────
    val readItems = remember {
        mutableStateListOf<String>().apply {
            addAll(sharedPrefs.getStringSet("read_uuids", emptySet()) ?: emptySet())
        }
    }
    val triggerMessage by navController.currentBackStackEntry
        ?.savedStateHandle
        ?.getStateFlow<String?>("success_message", null)
        ?.collectAsState() ?: remember { mutableStateOf(null) }

    var visibleBannerText by remember { mutableStateOf<String?>(null) }
    var selectedTab by androidx.compose.runtime.saveable.rememberSaveable { mutableIntStateOf(0) }

    // Observe flag open_scanner dari DetailLaptopScreen (tombol "Scan Lagi")
    val openScannerFlag by navController.currentBackStackEntry
        ?.savedStateHandle
        ?.getStateFlow<Boolean>("open_scanner", false)
        ?.collectAsState() ?: remember { mutableStateOf(false) }

    LaunchedEffect(openScannerFlag) {
        if (openScannerFlag) {
            selectedTab = 1 // tab kamera scanner
            navController.currentBackStackEntry?.savedStateHandle?.remove<Boolean>("open_scanner")
        }
    }

    // State & logic untuk "What's New / Update Hint Popup" pasca update
    val lastSeenVersionCode by sessionManager.lastSeenVersionCodeFlow.collectAsState(initial = -1)
    var showWhatsNewDialog by remember { mutableStateOf(false) }
    var whatsNewNotes by remember { mutableStateOf("") }
    var whatsNewVersionName by remember { mutableStateOf(com.dev.scanlaptop.BuildConfig.VERSION_NAME) }

    LaunchedEffect(lastSeenVersionCode) {
        // -1 = DataStore belum ready (initial value collectAsState), skip dulu
        if (lastSeenVersionCode == -1) return@LaunchedEffect

        // Tampilkan popup jika versi yang tersimpan lebih lama dari versi saat ini
        if (lastSeenVersionCode < com.dev.scanlaptop.BuildConfig.VERSION_CODE) {
            // Coba ambil release notes dari Supabase
            val notes = try {
                val appUpdateRepo = com.dev.scanlaptop.data.repository.AppUpdateRepository(com.dev.scanlaptop.data.SupabaseConfig.client)
                val versionInfo = appUpdateRepo.getAppVersionByCode(com.dev.scanlaptop.BuildConfig.VERSION_CODE)
                if (!versionInfo?.version_name.isNullOrBlank()) {
                    whatsNewVersionName = versionInfo!!.version_name
                }
                versionInfo?.release_notes
            } catch (_: Exception) { null }

            // Jika Supabase tidak ada data → gunakan teks fallback, popup tetap tampil
            whatsNewNotes = if (!notes.isNullOrBlank()) notes else
                "Aplikasi telah diperbarui ke versi ${com.dev.scanlaptop.BuildConfig.VERSION_NAME} dengan peningkatan performa dan perbaikan bug."

            showWhatsNewDialog = true
        }
    }

    if (showWhatsNewDialog) {
        com.dev.scanlaptop.ui.components.WhatsNewDialog(
            versionName = whatsNewVersionName,
            releaseNotes = whatsNewNotes,
            onDismiss = {
                showWhatsNewDialog = false
                scope.launch {
                    sessionManager.setLastSeenVersionCode(com.dev.scanlaptop.BuildConfig.VERSION_CODE)
                }
            }
        )
    }

    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var statusFilter by remember { mutableStateOf("ALL") }
    var timeFilter by remember { mutableStateOf("TODAY") }
    var miniChartPeriod by remember { mutableIntStateOf(1) } // 1 means TODAY

    // Two-way binding for timeFilter and miniChartPeriod
    LaunchedEffect(timeFilter) {
        val newPeriod = when (timeFilter) {
            "TODAY" -> 1
            "WEEK" -> 7
            "MONTH" -> 30
            else -> 0
        }
        if (miniChartPeriod != newPeriod) {
            miniChartPeriod = newPeriod
        }
    }

    LaunchedEffect(miniChartPeriod) {
        val newFilter = when (miniChartPeriod) {
            1 -> "TODAY"
            7 -> "WEEK"
            30 -> "MONTH"
            else -> "ALL"
        }
        if (timeFilter != newFilter) {
            timeFilter = newFilter
        }
        dashboardViewModel.loadAnalyticsData(miniChartPeriod)
    }

    var showFilterSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    // ─── Request POST_NOTIFICATIONS permission (Android 13+) ──
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startNotificationService(context, userData.npp)
        }
    }

    LaunchedEffect(pushNotifEnabled) {
        if (pushNotifEnabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                startNotificationService(context, userData.npp)
            }
        } else {
            context.stopService(Intent(context, com.dev.scanlaptop.service.RealtimeNotificationService::class.java))
        }
    }

    // ─── Init: load data, start realtime, start service ───────
    LaunchedEffect(Unit) {
        dashboardViewModel.loadHistory()
        dashboardViewModel.startRealtimeSubscription()
        overdueViewModel.loadOverdue()
    }

    // Refresh data saat pindah tab
    LaunchedEffect(selectedTab) {
        if (selectedTab == 3) {
            overdueViewModel.loadOverdue()
        } else if (selectedTab == 4) {
            dashboardViewModel.loadAnalyticsData()
        }
    }

    // ─── Handle filter berubah ────────────────────────────────
    LaunchedEffect(statusFilter, timeFilter) {
        dashboardViewModel.loadHistory(statusFilter, timeFilter)
    }

    // ─── Handle search ────────────────────────────────────────
    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotEmpty()) {
            delay(300)
            dashboardViewModel.search(searchQuery)
        }
    }

    // ─── Handle banner sukses dari DetailScreen ───────────────
    LaunchedEffect(triggerMessage) {
        if (triggerMessage != null) {
            visibleBannerText = triggerMessage
            readItems.clear()
            sharedPrefs.edit().remove("read_uuids").apply()
            dashboardViewModel.reloadAfterTransaction()
            overdueViewModel.refresh()
            delay(5000)
            visibleBannerText = null
            navController.currentBackStackEntry?.savedStateHandle?.remove<String>("success_message")
        }
    }

    val colorStart = Color(0xFF0D47A1)
    val colorEnd = Color(0xFF001233)
    val pindadGradient = Brush.verticalGradient(listOf(colorStart, colorEnd))

    val dataToShow = if (searchQuery.isNotEmpty()) searchResults else historyList

    var selectedHistoryLog by remember { mutableStateOf<HistoryLog?>(null) }

    // ─── LazyListState diangkat ke parent agar tidak reset saat pindah tab ─
    val listState = rememberLazyListState()

    // ─── Auto-scroll saat ada transaksi baru dari realtime ───────────────
    // Di parent agar tetap aktif meski HistoryContent sedang tidak dicompose
    LaunchedEffect(newTransactionCount) {
        if (newTransactionCount > 0) {
            // Beri waktu Compose render item baru sebelum scroll
            delay(200)
            listState.animateScrollToItem(0)
        }
    }

    Scaffold(
        containerColor = Color(0xFFF1F5F9),
        topBar = {
            if (selectedTab == 0) {
                TopAppBar(
                    title = {
                        if (isSearchActive) {
                            TextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Cari nama/merk/divisi...", color = Color.White.copy(alpha = 0.7f)) },
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    cursorColor = Color.White,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedIndicatorColor = Color.White,
                                    focusedPlaceholderColor = Color.White.copy(alpha = 0.7f),
                                    unfocusedPlaceholderColor = Color.White.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Monitoring Laptop", fontWeight = FontWeight.Black, color = Color.White)
                            }
                        }
                    },
                    navigationIcon = {
                        if (!isSearchActive) {
                            Box(modifier = Modifier.padding(start = 12.dp, end = 8.dp)) {
                                Image(
                                    painter = painterResource(id = R.drawable.logo_pindad),
                                    contentDescription = "Logo App",
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            isSearchActive = !isSearchActive
                            if (!isSearchActive) {
                                searchQuery = ""
                                dashboardViewModel.search("")
                            }
                        }) {
                            Icon(
                                if (isSearchActive) Icons.Default.Close else Icons.Outlined.Search,
                                null,
                                tint = Color.White
                            )
                        }
                        if (!isSearchActive) {
                            IconButton(onClick = { showFilterSheet = true }) {
                                Icon(
                                    imageVector = Icons.Default.FilterList,
                                    contentDescription = null,
                                    tint = if (statusFilter != "ALL" || timeFilter != "ALL") Color(0xFFFFD600) else Color.White
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = colorStart)
                )
            }
        },
        bottomBar = {
            Box(modifier = Modifier.fillMaxWidth().height(85.dp), contentAlignment = Alignment.BottomCenter) {
                Surface(
                    modifier = Modifier.fillMaxWidth().height(65.dp),
                    color = Color.White,
                    shadowElevation = 25.dp,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NavBarIcon(
                            icon = Icons.AutoMirrored.Filled.List,
                            label = "Riwayat",
                            isSelected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            navyColor = colorStart,
                            modifier = Modifier.weight(1f)
                        )
                        // Tab Mengendap dengan badge counter
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            NavBarIcon(
                                icon = Icons.Default.Warning,
                                label = "Mengendap",
                                isSelected = selectedTab == 3,
                                onClick = { selectedTab = 3 },
                                navyColor = Color(0xFFEF4444),
                                modifier = Modifier
                            )
                            if (overdueCount > 0) {
                                Badge(
                                    containerColor = Color(0xFFEF4444),
                                    modifier = Modifier.align(Alignment.TopEnd).offset(x = (-8).dp, y = 8.dp)
                                ) {
                                    Text("$overdueCount", fontSize = 9.sp, color = Color.White)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.weight(1.2f)) // Dynamic spacing for FAB
                        NavBarIcon(
                            icon = Icons.Default.BarChart,
                            label = "Analitik",
                            isSelected = selectedTab == 4,
                            onClick = { selectedTab = 4 },
                            navyColor = colorStart,
                            modifier = Modifier.weight(1f)
                        )
                        NavBarIcon(
                            icon = Icons.Default.Person,
                            label = "Profil",
                            isSelected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            navyColor = colorStart,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                FloatingActionButton(
                    onClick = { selectedTab = 1 },
                    shape = CircleShape,
                    containerColor = colorStart,
                    modifier = Modifier.offset(y = (-20).dp).size(56.dp),
                    elevation = FloatingActionButtonDefaults.elevation(8.dp)
                ) {
                    Icon(Icons.Rounded.QrCodeScanner, null, tint = Color.White, modifier = Modifier.size(28.dp))
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 60.dp, top = if (selectedTab == 0) padding.calculateTopPadding() else 0.dp)
        ) {
            when (selectedTab) {
                0 -> Column {
                    AnimatedVisibility(
                        visible = visibleBannerText != null,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                            border = BorderStroke(1.dp, Color(0xFF2E7D32))
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF2E7D32))
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Transaksi Berhasil", fontWeight = FontWeight.Black, color = Color(0xFF1B5E20), fontSize = 14.sp)
                                    Text(visibleBannerText ?: "", color = Color(0xFF2E7D32), fontSize = 12.sp)
                                }
                                IconButton(onClick = {
                                    visibleBannerText = null
                                    navController.currentBackStackEntry?.savedStateHandle?.remove<String>("success_message")
                                }) {
                                    Icon(Icons.Default.Close, null, tint = Color(0xFF2E7D32), modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }

                    HistoryContent(
                        dataToShow = dataToShow,
                        stats = stats,
                        isLoading = isLoading,
                        isRefreshing = isRefreshing,
                        isAppending = isAppending,
                        isSearching = isSearching,
                        isLastPage = isLastPage,
                        errorMessage = errorMessage,
                        searchQuery = searchQuery,
                        statusFilter = statusFilter,
                        timeFilter = timeFilter,
                        overdueCount = overdueCount,
                        navyGradient = pindadGradient,
                        analyticsData = analyticsData,
                        listState = listState,
                        onItemClick = { log ->
                            dashboardViewModel.markAsRead(log)
                            if (!readItems.contains(log.laptop_uuid)) {
                                readItems.add(log.laptop_uuid)
                                sharedPrefs.edit().putStringSet("read_uuids", readItems.toSet()).apply()
                            }
                            selectedHistoryLog = log
                        },
                        onLoadNextPage = { dashboardViewModel.loadNextPage() },
                        onRefresh = { dashboardViewModel.refresh() },
                        onRetry = { dashboardViewModel.loadHistory() },
                        newTransactionEvent = newTransactionEvent,
                        newTransactionCount = newTransactionCount,
                        clearNewTransactionEvent = { dashboardViewModel.clearNewTransactionEvent() },
                        fetchPairedTransaction = { uuid, status, date -> dashboardViewModel.getPairedTransaction(uuid, status, date) },
                        isAnalyticsLoading = isAnalyticsLoading,
                        miniChartPeriod = miniChartPeriod,
                        onMiniPeriodChange = { period -> 
                            miniChartPeriod = period
                        }
                    )
                }
                1 -> CameraScannerScreen(onQrDetected = { code ->
                    navController.navigate("detail_laptop/$code")
                    selectedTab = 0
                })
                2 -> ProfileContent(
                    userData = userData,
                    onLogout = onLogout,
                    onHistoryClick = { navController.navigate("officer_history/${userData.npp}") },
                    navyColor = colorStart
                )
                3 -> OverdueScreen(navyGradient = pindadGradient, overdueViewModel = overdueViewModel, onItemClicked = { uuid, showProcess -> navController.navigate("detail_laptop/$uuid?fromHistory=${!showProcess}") })
                4 -> AnalyticsScreen(
                    historyList = analyticsData,
                    navyGradient = pindadGradient,
                    isLoading = isLoading,
                    isRefreshing = isRefreshing,
                    isAnalyticsLoading = isAnalyticsLoading,
                    onRefresh = { dashboardViewModel.refresh() },
                    onChartDaysChange = { days -> dashboardViewModel.loadAnalyticsData(days) },
                    onExportPdf = { startDate, endDate -> 
                        dashboardViewModel.exportPdfByDateRange(context, startDate, endDate)
                    }
                )
            }
        }
    }

    // Filter Bottom Sheet
    if (showFilterSheet) {
        FilterBottomSheet(
            statusFilter = statusFilter,
            timeFilter = timeFilter,
            navyColor = colorStart,
            sheetState = sheetState,
            onStatusChange = { statusFilter = it },
            onTimeChange = { timeFilter = it },
            onDismiss = { showFilterSheet = false }
        )
    }

    if (selectedHistoryLog != null) {
        ModalBottomSheet(
            onDismissRequest = { selectedHistoryLog = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Color.White
        ) {
            HistoryLogBottomSheetContent(
                log = selectedHistoryLog!!,
                onDetailClick = {
                    selectedHistoryLog?.laptop_uuid?.let { uuid ->
                        selectedHistoryLog = null
                        navController.navigate("detail_laptop/$uuid?fromHistory=true")
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryContent(
    dataToShow: List<com.dev.scanlaptop.data.HistoryLog>,
    stats: com.dev.scanlaptop.data.model.StatsResult,
    isLoading: Boolean,
    isRefreshing: Boolean,
    isAppending: Boolean,
    isSearching: Boolean,
    isLastPage: Boolean,
    errorMessage: String?,
    searchQuery: String,
    statusFilter: String,
    timeFilter: String,
    overdueCount: Int,
    navyGradient: Brush,
    analyticsData: List<com.dev.scanlaptop.data.HistoryLog>,
    newTransactionEvent: com.dev.scanlaptop.data.HistoryLog?,
    newTransactionCount: Int,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onItemClick: (com.dev.scanlaptop.data.HistoryLog) -> Unit,
    onLoadNextPage: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    clearNewTransactionEvent: () -> Unit,
    fetchPairedTransaction: suspend (String, String, String) -> com.dev.scanlaptop.data.HistoryLog?,
    isAnalyticsLoading: Boolean = false,
    miniChartPeriod: Int,
    onMiniPeriodChange: (Int) -> Unit = {}
) {
    val navyColor = Color(0xFF0D47A1)
    val swipeRefreshState = com.google.accompanist.swiperefresh.rememberSwipeRefreshState(isRefreshing)

    // trafficCount: hitung transaksi dalam 15 menit terakhir untuk label "Padat" (auto-refresh tiap 1 menit)
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(60_000)
            nowMillis = System.currentTimeMillis()
        }
    }
    var trafficCount = 0
    dataToShow.forEach { log ->
        try {
            val logTime = java.time.ZonedDateTime.parse(log.created_at).toInstant().toEpochMilli()
            if (nowMillis - logTime <= 900_000L) { // 15 menit
                trafficCount++
            }
        } catch (e: Exception) {}
    }

    // Mini Chart Slider Period Filter (1 Hari, 7 Hari, 1 Bulan, Semua Waktu)
    // miniChartPeriod is now passed from parent to sync with timeFilter
    
    // filteredMiniStats removed to use accurate remote stats directly

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
                .background(navyGradient)
                .padding(top = 16.dp, bottom = 24.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                val statusText = when (statusFilter) {
                    "IN" -> "Masuk"
                    "OUT" -> "Keluar"
                    else -> "Semua Status"
                }
                val timeText = when (timeFilter) {
                    "TODAY" -> "Hari Ini"
                    "WEEK" -> "7 Hari"
                    "MONTH" -> "1 Bulan"
                    else -> when (miniChartPeriod) {
                        1 -> "Hari Ini"
                        7 -> "7 Hari"
                        30 -> "1 Bulan"
                        else -> "Semua Waktu"
                    }
                }
                Text(
                    text = "$statusText • $timeText",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Auto-shrink text biar tidak nabrak chip di kanan
                    androidx.compose.material3.Text(
                        text = when {
                            isAnalyticsLoading -> "Menghitung..."
                            searchQuery.isNotEmpty() -> "${dataToShow.size} Hasil Pencarian"
                            else -> "${stats.total} Aktivitas"
                        },
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        style = androidx.compose.material3.MaterialTheme.typography.headlineMedium.copy(
                            fontSize = 22.sp,
                            lineHeight = 26.sp
                        ),
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    )
                    Row {
                        com.dev.scanlaptop.ui.components.StatusChipMini(
                            label = "MASUK",
                            count = if (isAnalyticsLoading) 0 else stats.inCount,
                            color = Color(0xFF00C853)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        com.dev.scanlaptop.ui.components.StatusChipMini(
                            label = "KELUAR",
                            count = if (isAnalyticsLoading) 0 else stats.outCount,
                            color = Color(0xFFEF4444)
                        )
                    }
                }
                
                // --- Mini Sparkline Slider Filter ---
                Spacer(modifier = Modifier.height(16.dp))
                com.dev.scanlaptop.ui.components.MiniSparkline(
                    historyList = analyticsData,
                    modifier = Modifier.fillMaxWidth(),
                    selectedPeriod = miniChartPeriod,
                    isLoading = isAnalyticsLoading,
                    totalRemoteCount = stats.total,
                    onPeriodSelect = { onMiniPeriodChange(it) }
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (searchQuery.isNotEmpty()) "Hasil Pencarian: \"$searchQuery\"" else "Riwayat Terkini",
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        color = Color(0xFF1A237E)
                    )
                    
                    // Insight Chips
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (overdueCount > 0) {
                            Surface(color = Color(0xFFFFEBEE), shape = RoundedCornerShape(16.dp)) {
                                Text("🚨 $overdueCount Mengendap", color = Color(0xFFC62828), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                            }
                        }
                        if (trafficCount >= 5) {
                            Surface(color = Color(0xFFE8F5E9), shape = RoundedCornerShape(16.dp)) {
                                Text("🔥 Padat", color = Color(0xFF2E7D32), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                            }
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        isLoading && dataToShow.isEmpty() -> {
                            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                                repeat(5) {
                                    HistoryCardSkeleton()
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                            }
                        }
                        errorMessage != null && dataToShow.isEmpty() -> {
                            Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Icon(Icons.Default.Error, null, tint = Color.Red, modifier = Modifier.size(48.dp))
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(errorMessage, color = Color.Red, textAlign = TextAlign.Center)
                                Button(onClick = onRetry) { Text("Coba Lagi") }
                            }
                        }
                        else -> {
                            com.google.accompanist.swiperefresh.SwipeRefresh(state = swipeRefreshState, onRefresh = onRefresh) {
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 100.dp)
                                ) {
                                    itemsIndexed(dataToShow, key = { _, item -> item.uuid ?: item.hashCode() }) { index, item ->
                                        if (index >= dataToShow.size - 3 && !isLastPage && !isLoading && !isAppending && !isSearching) {
                                            LaunchedEffect(index) { onLoadNextPage() }
                                        }
                                        com.dev.scanlaptop.ui.components.HistoryCardPro(
                                            log = item,
                                            navyColor = navyColor,
                                            onClick = { onItemClick(item) },
                                            fetchPairedTransaction = {
                                                fetchPairedTransaction(item.laptop_uuid, item.status_io, item.created_at)
                                            }
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))
                                    }

                                    if (isAppending) {
                                        item {
                                            Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = navyColor, strokeWidth = 2.dp)
                                            }
                                        }
                                    }

                                    if (isLastPage && dataToShow.isNotEmpty() && !isSearching) {
                                        item {
                                            Text("Semua riwayat telah dimuat", modifier = Modifier.fillMaxWidth().padding(24.dp), textAlign = TextAlign.Center, color = navyColor, fontSize = 13.sp)
                                        }
                                    }

                                    if (dataToShow.isEmpty() && !isLoading && !isSearching) {
                                        item {
                                            Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Icon(if (searchQuery.isNotEmpty()) Icons.Outlined.Search else Icons.Default.Info, null, tint = navyColor.copy(alpha = 0.5f), modifier = Modifier.size(48.dp))
                                                    Spacer(modifier = Modifier.height(16.dp))
                                                    Text(if (searchQuery.isNotEmpty()) "Tidak ada hasil untuk \"$searchQuery\"" else "Tidak ada data", color = navyColor, fontSize = 16.sp, textAlign = TextAlign.Center)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryCardSkeleton() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Color(0xFFE2E8F0), CircleShape)
                    .run { shimmerEffect() }
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(18.dp)
                        .background(Color(0xFFE2E8F0), RoundedCornerShape(4.dp))
                        .run { shimmerEffect() }
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.4f)
                        .height(14.dp)
                        .background(Color(0xFFE2E8F0), RoundedCornerShape(4.dp))
                        .run { shimmerEffect() }
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Box(
                modifier = Modifier
                    .width(60.dp)
                    .height(24.dp)
                    .background(Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                    .run { shimmerEffect() }
            )
        }
    }
}

@Composable
fun CameraScannerScreen(onQrDetected: (String) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val laptopRepository = remember { com.dev.scanlaptop.data.repository.LaptopRepository() }

    var hasCameraPermission by remember { mutableStateOf(false) }
    var lastScannedCode by remember { mutableStateOf("") }
    var isChecking by remember { mutableStateOf(false) }

    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { hasCameraPermission = it }
    LaunchedEffect(Unit) { launcher.launch(android.Manifest.permission.CAMERA) }

    if (hasCameraPermission) {
        Box(modifier = Modifier.fillMaxSize()) {
            com.dev.scanlaptop.ui.components.CameraWithFocusIndicator(
                onBarcodeDetected = { code ->
                    if (code != lastScannedCode && !isChecking) {
                        lastScannedCode = code
                        isChecking = true
                        scope.launch {
                            try {
                                if (laptopRepository.validateQr(code)) {
                                    com.dev.scanlaptop.utils.FeedbackHelper.playSuccessFeedback(context)
                                    onQrDetected(code)
                                } else {
                                    com.dev.scanlaptop.utils.FeedbackHelper.playErrorFeedback(context)
                                    android.widget.Toast.makeText(context, "Data Tidak Terdaftar!", android.widget.Toast.LENGTH_LONG).show()
                                    lastScannedCode = ""
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("SCAN_VALIDATION", e.message.toString())
                                lastScannedCode = ""
                            } finally {
                                isChecking = false
                            }
                        }
                    }
                }
            )

            androidx.compose.foundation.Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            ) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                val boxSize = canvasWidth * 0.7f
                val boxTop = (canvasHeight - boxSize) / 2
                val boxLeft = (canvasWidth - boxSize) / 2

                drawRect(color = Color.Black.copy(alpha = 0.6f))

                drawRoundRect(
                    color = Color.Transparent,
                    topLeft = androidx.compose.ui.geometry.Offset(boxLeft, boxTop),
                    size = androidx.compose.ui.geometry.Size(boxSize, boxSize),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx(), 16.dp.toPx()),
                    blendMode = androidx.compose.ui.graphics.BlendMode.Clear
                )

                drawRoundRect(
                    color = Color.White,
                    topLeft = androidx.compose.ui.geometry.Offset(boxLeft, boxTop),
                    size = androidx.compose.ui.geometry.Size(boxSize, boxSize),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx(), 16.dp.toPx()),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.dp.toPx())
                )
            }

            val configuration = androidx.compose.ui.platform.LocalConfiguration.current
            val screenWidthDp = configuration.screenWidthDp.dp
            val boxRadiusDp = screenWidthDp * 0.35f

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.offset(y = boxRadiusDp + 42.dp)
                ) {
                    androidx.compose.material3.Text(
                        text = "Posisikan QR di dalam kotak",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    androidx.compose.material3.Text(
                        text = "Tap layar untuk fokus",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp
                    )
                }
            }
        }
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Izin Kamera Diperlukan", fontWeight = FontWeight.Bold)
        }
    }
}






private fun startNotificationService(context: Context, npp: String) {
    val intent = Intent(context, com.dev.scanlaptop.service.RealtimeNotificationService::class.java).apply {
        putExtra(com.dev.scanlaptop.service.RealtimeNotificationService.EXTRA_CURRENT_NPP, npp)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
    } else {
        context.startService(intent)
    }
}
