package com.dev.scanlaptop.ui.screens

import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dev.scanlaptop.data.HistoryLog
import com.dev.scanlaptop.data.LaptopDetail
import com.dev.scanlaptop.data.UserData
import com.dev.scanlaptop.ui.viewmodel.DetailLaptopViewModel
import com.dev.scanlaptop.ui.viewmodel.SaveResult
import com.dev.scanlaptop.utils.FeedbackHelper
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailLaptopScreen(
    laptopUuid: String,
    isFromHistory: Boolean = false,
    userData: UserData,
    onBack: () -> Unit,
    onLogClick: (HistoryLog) -> Unit,
    onSuccess: () -> Unit,
    onScanAgain: () -> Unit = onSuccess,
    onHistoryLaptopClick: (String) -> Unit = {},
    viewModel: DetailLaptopViewModel = viewModel()
) {
    val context = LocalContext.current

    // ─── Observe state dari ViewModel ─────────────────────────
    val laptop by viewModel.laptop.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val saveResult by viewModel.saveResult.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val qrHistory by viewModel.qrHistory.collectAsState()

    // ─── State lokal (UI only) ─────────────────────────────────
    var showConfirmDialog by remember { mutableStateOf(false) }
    var showQrHistoryDialog by remember { mutableStateOf(false) }
    var feedbackData by remember { mutableStateOf<Pair<String, String>?>(null) }
    var keteranganUser by remember { mutableStateOf("") }
    val selectedDevices = remember { mutableStateListOf<String>() }
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Hari Ini", "History")
    val scrollState = rememberScrollState()

    // ─── Load data saat masuk screen ──────────────────────────
    LaunchedEffect(laptopUuid) {
        viewModel.loadData(laptopUuid)
    }

    // ─── Handle hasil save ─────────────────────────────────────
    LaunchedEffect(saveResult) {
        when (val result = saveResult) {
            is SaveResult.Success -> {
                FeedbackHelper.playSuccessFeedback(context)
                showConfirmDialog = false
                keteranganUser = ""
                selectedDevices.clear()
                val statusText = if (result.status == "IN") "Laptop berhasil dicatat MASUK." else "Laptop berhasil dicatat KELUAR."
                feedbackData = Pair(result.status, statusText)
                viewModel.clearSaveResult()
            }
            is SaveResult.Error -> {
                FeedbackHelper.playErrorFeedback(context)
                showConfirmDialog = false
                feedbackData = Pair("ERROR", result.message)
                viewModel.clearSaveResult()
            }
            is SaveResult.OfflineQueued -> {
                FeedbackHelper.playSuccessFeedback(context)
                showConfirmDialog = false
                keteranganUser = ""
                selectedDevices.clear()
                feedbackData = Pair("OFFLINE", "Tidak ada koneksi internet. Data disimpan di antrean lokal dan akan dikirim otomatis nanti.")
                viewModel.clearSaveResult()
            }
            null -> Unit
        }
    }

    // ─── Computed values ───────────────────────────────────────
    val filteredLogs = remember(logs, selectedTabIndex) {
        if (selectedTabIndex == 0) {
            val todayDate = LocalDate.now().toString()
            logs.filter { it.created_at.startsWith(todayDate) }
        } else logs
    }

    val isExpired = remember(laptop) {
        try {
            laptop?.berlaku_sampai?.let { tgl ->
                val exp = LocalDate.parse(tgl, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                LocalDate.now().isAfter(exp)
            } ?: false
        } catch (e: Exception) { false }
    }

    // isFromHistory dikirim secara explicit dari navigasi — tidak perlu tebak-tebakan format string
    val isFromHistoryList = isFromHistory

    // ─── Dialog Konfirmasi ─────────────────────────────────────
    if (showConfirmDialog) {
        ModalBottomSheet(
            onDismissRequest = { showConfirmDialog = false },
            containerColor = Color.White,
            dragHandle = { BottomSheetDefaults.DragHandle() },
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .padding(bottom = 24.dp)
            ) {
                Text("PROSES MASUK ATAU KELUAR", fontWeight = FontWeight.Black, color = Color(0xFF1A237E), fontSize = 20.sp)
                Spacer(modifier = Modifier.height(16.dp))
                
                Text("Pilih barang yang diproses:", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    laptop?.daftar_perangkat?.forEach { perangkat ->
                        val sn = perangkat.no_seri ?: ""
                        val isSelected = selectedDevices.contains(sn)
                        val currentStatus = perangkat.status_terakhir ?: "OUT"
                        val themeColor = if (currentStatus == "OUT") Color(0xFF1B5E20) else Color(0xFFB71C1C)

                        Surface(
                            onClick = {
                                if (sn.isNotBlank()) {
                                    if (isSelected) selectedDevices.remove(sn)
                                    else selectedDevices.add(sn)
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) themeColor.copy(alpha = 0.08f) else Color(0xFFF8F9FA),
                            border = BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) themeColor else Color.Black.copy(alpha = 0.1f)
                            )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "${perangkat.merk?.uppercase()} ${perangkat.tipe?.uppercase()}",
                                        fontWeight = FontWeight.Black, fontSize = 14.sp, color = Color.Black
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("S/N: ${sn.uppercase()}", fontSize = 11.sp, color = Color.Black.copy(alpha = 0.7f))
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            if (currentStatus == "IN") "(Di Dalam)" else "(Di Luar)", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
                                            color = if (currentStatus == "IN") Color(0xFF2E7D32) else Color(0xFFC62828)
                                        )
                                    }
                                }
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = null,
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = themeColor,
                                        uncheckedColor = Color.Black.copy(alpha = 0.4f)
                                    ),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                OutlinedTextField(
                    value = keteranganUser,
                    onValueChange = { keteranganUser = it.uppercase() },
                    label = { Text("Keterangan Tambahan", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black,
                        focusedBorderColor = Color(0xFF1A237E)
                    )
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    enabled = selectedDevices.isNotEmpty() && !isSaving,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A237E)),
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    onClick = {
                        // Delegasikan ke ViewModel
                        viewModel.confirmTransaction(
                            selectedSerialNumbers = selectedDevices.toList(),
                            keterangan = keteranganUser,
                            userData = userData
                        )
                    }
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                    } else {
                        Text("KONFIRMASI", color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
                    }
                }
            }
        }
    }

    if (showQrHistoryDialog) {
        ModalBottomSheet(
            onDismissRequest = { showQrHistoryDialog = false },
            containerColor = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .padding(bottom = 24.dp)
            ) {
                Text("Riwayat Penggunaan QR", fontWeight = FontWeight.Black, color = Color(0xFF1A237E), fontSize = 18.sp)
                Spacer(Modifier.height(16.dp))
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    qrHistory.forEach { hist ->
                        Card(
                            onClick = {
                                showQrHistoryDialog = false
                                hist.uuid?.let { onHistoryLaptopClick(it) }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(hist.nama_pengguna?.uppercase() ?: "-", fontWeight = FontWeight.Bold, color = Color.Black, fontSize = 14.sp)
                                Text(hist.instansi_divisi?.uppercase() ?: "-", fontSize = 12.sp, color = Color.DarkGray)
                                Spacer(Modifier.height(8.dp))
                                Text("Berlaku s/d: ${hist.berlaku_sampai ?: "-"}", fontSize = 12.sp, color = Color.Black, fontWeight = FontWeight.SemiBold)
                                
                                if (!hist.daftar_perangkat.isNullOrEmpty()) {
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.Black.copy(alpha = 0.1f))
                                    Text("Perangkat:", fontSize = 11.sp, color = Color.DarkGray, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(4.dp))
                                    hist.daftar_perangkat.forEach { perangkat ->
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 2.dp)) {
                                            Icon(Icons.Default.Devices, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color.Gray)
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                "${perangkat.merk?.uppercase()} ${perangkat.tipe?.uppercase()} - S/N: ${perangkat.no_seri?.uppercase()}",
                                                fontSize = 11.sp, color = Color.Black
                                            )
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

    // ─── Scaffold utama ────────────────────────────────────────
    Scaffold(
        bottomBar = {
            if (!isLoading && laptop != null && !isFromHistoryList) {
                Surface(tonalElevation = 12.dp, shadowElevation = 12.dp, color = Color.White) {
                    Column(modifier = Modifier.padding(16.dp).navigationBarsPadding()) {
                        Button(
                            onClick = {
                                selectedDevices.clear()
                                laptop?.daftar_perangkat?.forEach { perangkat ->
                                    val sn = perangkat.no_seri
                                    if (!sn.isNullOrBlank()) selectedDevices.add(sn)
                                }
                                showConfirmDialog = true
                            },
                            modifier = Modifier.fillMaxWidth().height(60.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isExpired == true) Color(0xFF9E9E9E) else Color(0xFF1A237E),
                                disabledContainerColor = Color(0xFFE0E0E0)
                            ),
                            shape = RoundedCornerShape(14.dp),
                            enabled = !isSaving && isExpired != true
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                            } else {
                                if (isExpired == true) {
                                    Icon(Icons.Default.Block, null, tint = Color.White)
                                    Spacer(Modifier.width(12.dp))
                                    Text("TIDAK BERLAKU", fontWeight = FontWeight.Black, fontSize = 16.sp, color = Color.White)
                                } else {
                                    Icon(Icons.Default.QrCodeScanner, null, tint = Color.White)
                                    Spacer(Modifier.width(12.dp))
                                    Text("PROSES MASUK ATAU KELUAR", fontWeight = FontWeight.Black, fontSize = 16.sp, color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color(0xFF1A237E))
                errorMessage != null -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Error, null, tint = Color(0xFFEF4444), modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(errorMessage ?: "", textAlign = TextAlign.Center, color = Color.Black)
                    }
                }
                else -> {
                    Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState).background(Color(0xFFF0F2F5))) {
                        LaptopHeaderSection(laptop, isExpired == true, onBack)

                        if (qrHistory.isNotEmpty()) {
                            Surface(
                                color = Color(0xFFFFF3E0),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .clickable { showQrHistoryDialog = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFE65100), modifier = Modifier.size(24.dp))
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        "QR Code ini punya ${qrHistory.size} riwayat pemakai sebelumnya. Klik untuk melihat detail.",
                                        fontSize = 12.sp, color = Color(0xFFE65100), fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color(0xFFE65100))
                                }
                            }
                        }

                        Column(modifier = Modifier.padding(16.dp)) {
                            InfoSection(laptop, logs)
                            Spacer(modifier = Modifier.height(24.dp))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(color = Color(0xFF1A237E), shape = CircleShape, modifier = Modifier.size(6.dp, 22.dp)) {}
                                Spacer(Modifier.width(12.dp))
                                Text("Riwayat Aktivitas", fontWeight = FontWeight.Black, fontSize = 20.sp, color = Color(0xFF1A237E))
                            }
                            Spacer(modifier = Modifier.height(16.dp))

                            TabRow(
                                selectedTabIndex = selectedTabIndex, 
                                containerColor = Color.Transparent, 
                                contentColor = Color(0xFF1A237E),
                                indicator = { tabPositions ->
                                    if (selectedTabIndex < tabPositions.size) {
                                        TabRowDefaults.SecondaryIndicator(
                                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                                            color = Color(0xFF1A237E)
                                        )
                                    }
                                }
                            ) {
                                tabs.forEachIndexed { index, title ->
                                    Tab(
                                        selected = selectedTabIndex == index,
                                        onClick = { selectedTabIndex = index },
                                        text = {
                                            Text(
                                                title,
                                                fontWeight = if (selectedTabIndex == index) FontWeight.Black else FontWeight.Normal,
                                                color = Color.Black
                                            )
                                        }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))

                            if (filteredLogs.isEmpty()) {
                                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                    Text("Tidak ada aktivitas.", fontWeight = FontWeight.Bold, color = Color.Black)
                                }
                            } else {
                                filteredLogs.forEach { logItem ->
                                    LogItemSmall(log = logItem, onClick = { onLogClick(logItem) })
                                }
                            }
                            Spacer(modifier = Modifier.height(30.dp))
                        }
                    }
                }
            }
        }
    }

    // ─── Feedback Bottom Sheet (Hasil Transaksi) ────────────────
    feedbackData?.let { data ->
        com.dev.scanlaptop.ui.components.FeedbackBottomSheet(
            status = data.first,
            message = data.second,
            onDismiss = {
                feedbackData = null
                onSuccess()
            },
            onScanAgain = {
                feedbackData = null
                onScanAgain()
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────
// Sub-composable yang bersifat pure UI (tidak ada logic data)
// ─────────────────────────────────────────────────────────────

@Composable
fun InfoSection(laptop: LaptopDetail?, logs: List<HistoryLog> = emptyList()) {
    var isDeviceExpanded by remember { mutableStateOf(false) }

    // Hitung status dan waktu terakhir
    val latestLog = logs.firstOrNull()
    val currentStatus = laptop?.daftar_perangkat?.firstOrNull()?.status_terakhir ?: latestLog?.status_io ?: "OUT"
    val isInside = currentStatus == "IN"
    val statusColor = if (isInside) Color(0xFF2E7D32) else Color(0xFFC62828)
    val statusText = if (isInside) "DI DALAM AREA (IN)" else "DI LUAR AREA (OUT)"

    // Hitung durasi dan waktu terformat
    val timeInfo = remember(latestLog) {
        latestLog?.created_at?.let { ts ->
            try {
                val zdt = ZonedDateTime.parse(ts)
                val now = ZonedDateTime.now()
                val dur = Duration.between(zdt, now)
                val days = dur.toDays()
                val hours = dur.toHours() % 24
                val mins = dur.toMinutes() % 60

                val formattedDate = zdt.format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm WIB", Locale("id", "ID")))
                val durStr = when {
                    days > 0 -> "$days hari $hours jam yang lalu"
                    hours > 0 -> "$hours jam $mins menit yang lalu"
                    else -> "$mins menit yang lalu"
                }
                Triple(formattedDate, durStr, days)
            } catch (e: Exception) {
                Triple(ts.take(16).replace("T", " "), "-", 0L)
            }
        } ?: Triple("Belum ada riwayat scan", "-", 0L)
    }
    val (lastScanDate, durationText, daysInside) = timeInfo

    Card(
        modifier = Modifier.offset(y = (-25).dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // ─── KARTU STATUS & DURASI TERKINI ─────────────────────────
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = statusColor.copy(alpha = 0.08f),
                border = BorderStroke(1.5.dp, statusColor.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (isInside) Icons.Default.Login else Icons.Default.Logout,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "POSISI TERKINI: $statusText",
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp,
                            color = statusColor
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Scan Terakhir : $lastScanDate",
                        fontSize = 12.sp,
                        color = Color.Black.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Bold
                    )
                    if (durationText != "-") {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Durasi : $durationText",
                            fontSize = 12.sp,
                            color = Color.DarkGray,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Badge peringatan mengendap jika di dalam area >= 1 hari
            if (isInside && daysInside >= 1) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFFFF8E1),
                    border = BorderStroke(1.dp, Color(0xFFFFA000)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFF57F17),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "PERINGATAN MENGENDAP (${daysInside} HARI)",
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp,
                                color = Color(0xFFE65100)
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "Perangkat berada di dalam area melampaui 24 jam. Harap periksa alasan keterlambatan keluar.",
                                fontSize = 11.sp,
                                color = Color(0xFF795548),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(color = Color.Black.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(16.dp))

            Text("DOKUMEN REGISTRASI", fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color(0xFF1A237E), letterSpacing = 1.5.sp)
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.weight(1f)) { DetailItemCompact("No. Dokumen", laptop?.no_dokumen?.uppercase(), Icons.Default.Description) }
                Box(modifier = Modifier.weight(1f)) { DetailItemCompact("Tgl Dokumen", laptop?.tanggal_dokumen, Icons.Default.CalendarToday) }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 15.dp), color = Color.Black.copy(alpha = 0.1f))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("DAFTAR PERANGKAT (${laptop?.daftar_perangkat?.size ?: 0})", fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color(0xFF1A237E), letterSpacing = 1.5.sp)
                if ((laptop?.daftar_perangkat?.size ?: 0) > 3) {
                    Text(
                        if (isDeviceExpanded) "Sembunyikan" else "Lihat Semua",
                        modifier = Modifier.clickable { isDeviceExpanded = !isDeviceExpanded },
                        fontSize = 12.sp, color = Color(0xFF0D47A1), fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            val deviceList = if (isDeviceExpanded) laptop?.daftar_perangkat else laptop?.daftar_perangkat?.take(3)
            deviceList?.forEach { perangkat ->
                val currentStatus = perangkat.status_terakhir ?: "OUT"
                val statusColor = if (currentStatus == "IN") Color(0xFF2E7D32) else Color(0xFFC62828)
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Devices, null, tint = Color(0xFF1A237E), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${perangkat.merk?.uppercase()} ${perangkat.tipe?.uppercase()}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.Black)
                        Text("S/N: ${perangkat.no_seri?.uppercase()}", fontSize = 11.sp, color = Color.Black)
                    }
                    Surface(color = statusColor.copy(alpha = 0.1f), shape = RoundedCornerShape(6.dp), border = BorderStroke(1.dp, statusColor.copy(alpha = 0.4f))) {
                        Text(if (currentStatus == "IN") "Di Dalam" else "Di Luar", modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), color = statusColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            val countdownDays = remember(laptop?.berlaku_sampai) {
                try {
                    laptop?.berlaku_sampai?.let { tgl ->
                        val exp = LocalDate.parse(tgl, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                        java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), exp)
                    }
                } catch (e: Exception) { null }
            }
            if (countdownDays != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (countdownDays < 0) Color(0xFFFFEBEE) else Color(0xFFE8F5E9),
                    border = BorderStroke(1.dp, if (countdownDays < 0) Color(0xFFE53935) else Color(0xFF4CAF50)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val text = when {
                        countdownDays < 0 -> "Masa berlaku telah habis ${-countdownDays} hari yang lalu"
                        countdownDays == 0L -> "Berlaku sampai hari ini"
                        else -> "Masa berlaku tersisa $countdownDays Hari lagi"
                    }
                    Text(
                        text = text,
                        modifier = Modifier.padding(12.dp),
                        color = if (countdownDays < 0) Color(0xFFC62828) else Color(0xFF2E7D32),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 15.dp), color = Color.Black.copy(alpha = 0.1f))

            Text("DATA PEMILIK & IDENTITAS", fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color(0xFF1A237E), letterSpacing = 1.5.sp)
            Spacer(modifier = Modifier.height(12.dp))
            DetailItemFull("Nama Pengguna", laptop?.nama_pengguna?.uppercase(), Icons.Default.Person)
            DetailItemFull("NPP / No. ID Card", laptop?.nomor_id_card?.uppercase(), Icons.Default.Badge)
            DetailItemFull("Divisi / Instansi", laptop?.instansi_divisi?.uppercase(), Icons.Default.Business)

            val jenisRaw = laptop?.jenis ?: "-"
            val golonganRaw = laptop?.golongan ?: ""
            val statusDisplay = if (jenisRaw.contains("non_pegawai", ignoreCase = true)) {
                val processedGolongan = if (golonganRaw.equals("PKL_magang", ignoreCase = true)) "PKL/MAGANG" else golonganRaw.ifBlank { "Tamu" }.uppercase()
                "NON PEGAWAI / $processedGolongan"
            } else {
                jenisRaw.replace("_", " ").uppercase()
            }
            DetailItemFull("Golongan Status", statusDisplay, Icons.Default.Category)

            val kepemilikanRaw = laptop?.kepemilikan ?: "-"
            val kepemilikanDisplay = if (kepemilikanRaw == "-") "-" else kepemilikanRaw.replace("_", " ").uppercase()
            DetailItemFull("Status Kepemilikan", kepemilikanDisplay, Icons.Default.CheckCircle)
        }
    }
}

@Composable
fun DetailItemFull(label: String, value: String?, icon: ImageVector) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, modifier = Modifier.size(20.dp), tint = Color(0xFF1A237E))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label.uppercase(), color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
            Text(value?.uppercase() ?: "-", fontWeight = FontWeight.Black, fontSize = 14.sp, color = Color.Black)
        }
    }
}

@Composable
fun DetailItemCompact(label: String, value: String?, icon: ImageVector) {
    Row(modifier = Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, null, modifier = Modifier.size(18.dp), tint = Color(0xFF1A237E))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(label.uppercase(), color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
            Text(value?.uppercase() ?: "-", fontWeight = FontWeight.Black, fontSize = 13.sp, color = Color.Black)
        }
    }
}

@Composable
fun LaptopHeaderSection(laptop: LaptopDetail?, isExpired: Boolean, onBack: () -> Unit) {
    val statusText = if (isExpired) "EXPIRED" else "BERLAKU"
    val badgeColor = if (isExpired) Color(0xFFFF5252) else Color(0xFF66BB6A)
    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)).background(Brush.verticalGradient(listOf(Color(0xFF0D47A1), Color(0xFF001233)))).padding(bottom = 32.dp)) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(start = 8.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = Color.White)
        }
        Column(modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 24.dp).padding(top = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(shape = CircleShape, color = Color.White.copy(0.15f), modifier = Modifier.size(90.dp).border(2.dp, Color.White.copy(0.4f), CircleShape)) {
                Icon(Icons.Default.Group, null, tint = Color.White, modifier = Modifier.padding(20.dp))
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(laptop?.nama_pengguna?.uppercase() ?: "-", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
            val berlakuSampaiStr = remember(laptop?.berlaku_sampai) {
                try {
                    laptop?.berlaku_sampai?.let { tgl ->
                        val ld = LocalDate.parse(tgl, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                        ld.format(DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale("id", "ID"))).uppercase()
                    } ?: "-"
                } catch (e: Exception) { "-" }
            }
            Text(berlakuSampaiStr, color = Color.White.copy(0.9f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Surface(color = badgeColor, shape = RoundedCornerShape(8.dp)) {
                    Text(statusText, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Surface(color = Color.White.copy(0.2f), shape = RoundedCornerShape(8.dp)) {
                    Text(laptop?.no_registrasi ?: "-", modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun LogItemSmall(log: HistoryLog, onClick: () -> Unit) {
    val isEntry = log.status_io == "IN"
    val color = if (isEntry) Color(0xFF2E7D32) else Color(0xFFC62828)
    val petugasNama = log.users?.nama_lengkap?.uppercase() ?: log.petugas_npp?.uppercase() ?: "TIDAK DIKETAHUI"
    val keterangan = log.keterangan?.uppercase()?.takeIf { it.isNotBlank() } ?: "-"

    Card(
        modifier = Modifier.padding(vertical = 6.dp).fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = color, shape = CircleShape, modifier = Modifier.size(36.dp)) {
                    Icon(if (isEntry) Icons.AutoMirrored.Filled.Login else Icons.AutoMirrored.Filled.Logout, null, tint = Color.White, modifier = Modifier.padding(8.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(if (isEntry) "MASUK" else "KELUAR", fontWeight = FontWeight.Black, fontSize = 14.sp, color = color)
                    Text(log.created_at.take(16).replace("T", "  |  "), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black.copy(alpha = 0.7f))
                }
                Icon(Icons.Default.ChevronRight, null, tint = Color.Black.copy(alpha = 0.5f))
            }
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = Color.Black.copy(alpha = 0.05f))
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Default.Badge, contentDescription = "Petugas", tint = Color(0xFF1A237E), modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("PETUGAS", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = Color.Gray)
                    Text(petugasNama, fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color.Black)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Default.Note, contentDescription = "Catatan", tint = Color(0xFF1A237E), modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("CATATAN", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = Color.Gray)
                    Text(keterangan, fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color.Black)
                }
            }
        }
    }
}