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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import java.time.LocalDate
import java.time.format.DateTimeFormatter

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

    // ─── State lokal (UI only) ─────────────────────────────────
    var showConfirmDialog by remember { mutableStateOf(false) }
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

    // ─── Scaffold utama ────────────────────────────────────────
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detail Registrasi", fontWeight = FontWeight.ExtraBold, color = Color(0xFF1A237E)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color(0xFF1A237E))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
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
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A237E)),
                            shape = RoundedCornerShape(14.dp),
                            enabled = !isSaving
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
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
                        LaptopHeaderSection(laptop, isExpired == true)
                        Column(modifier = Modifier.padding(16.dp)) {
                            InfoSection(laptop)
                            Spacer(modifier = Modifier.height(24.dp))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(color = Color(0xFF1A237E), shape = CircleShape, modifier = Modifier.size(6.dp, 22.dp)) {}
                                Spacer(Modifier.width(12.dp))
                                Text("Riwayat Aktivitas", fontWeight = FontWeight.Black, fontSize = 20.sp, color = Color(0xFF1A237E))
                            }
                            Spacer(modifier = Modifier.height(16.dp))

                            TabRow(selectedTabIndex = selectedTabIndex, containerColor = Color.Transparent, contentColor = Color(0xFF1A237E)) {
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
fun InfoSection(laptop: LaptopDetail?) {
    var isDeviceExpanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.offset(y = (-25).dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
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
fun LaptopHeaderSection(laptop: LaptopDetail?, isExpired: Boolean) {
    val statusText = if (isExpired) "EXPIRED" else "BERLAKU"
    val badgeColor = if (isExpired) Color(0xFFFF5252) else Color(0xFF66BB6A)
    Box(modifier = Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xFF0D47A1), Color(0xFF001233)))).padding(bottom = 32.dp, top = 16.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(shape = CircleShape, color = Color.White.copy(0.15f), modifier = Modifier.size(90.dp).border(2.dp, Color.White.copy(0.4f), CircleShape)) {
                Icon(Icons.Default.Group, null, tint = Color.White, modifier = Modifier.padding(20.dp))
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(laptop?.nama_pengguna?.uppercase() ?: "-", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
            Text("TOTAL PERANGKAT: ${laptop?.daftar_perangkat?.size ?: 0}", color = Color.White.copy(0.9f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
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
    Card(
        modifier = Modifier.padding(vertical = 6.dp).fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = color, shape = CircleShape, modifier = Modifier.size(40.dp)) {
                Icon(if (isEntry) Icons.AutoMirrored.Filled.Login else Icons.AutoMirrored.Filled.Logout, null, tint = Color.White, modifier = Modifier.padding(8.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(if (isEntry) "MASUK" else "KELUAR", fontWeight = FontWeight.Black, fontSize = 14.sp, color = color)
                Text(log.created_at.take(16).replace("T", "  |  "), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            }
            Icon(Icons.Default.ChevronRight, null, tint = Color.Black.copy(alpha = 0.5f))
        }
    }
}