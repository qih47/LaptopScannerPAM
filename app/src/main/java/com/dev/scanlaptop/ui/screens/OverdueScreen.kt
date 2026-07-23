package com.dev.scanlaptop.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dev.scanlaptop.data.repository.OverdueItem
import com.dev.scanlaptop.ui.components.shimmerEffect
import com.dev.scanlaptop.ui.viewmodel.OverdueViewModel
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverdueScreen(
    navyGradient: Brush,
    overdueViewModel: OverdueViewModel = viewModel(),
    onItemClicked: (String, Boolean) -> Unit = { _, _ -> }
) {
    val overdueList by overdueViewModel.overdueList.collectAsState()
    val isLoading by overdueViewModel.isLoading.collectAsState()
    val isRefreshing by overdueViewModel.isRefreshing.collectAsState()
    val thresholdHours by overdueViewModel.thresholdHours.collectAsState()
    val filterType by overdueViewModel.filterType.collectAsState()
    val swipeState = rememberSwipeRefreshState(isRefreshing = isRefreshing)

    // Load saat pertama masuk
    LaunchedEffect(Unit) {
        overdueViewModel.loadOverdue()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ─── Filter Threshold State ─────────────────────────────────
        var showFilterSheet by remember { mutableStateOf(false) }
        val sheetState = rememberModalBottomSheetState()

        // ─── Header ──────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(navyGradient)
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Laptop Mengendap", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${overdueList.size}",
                            color = Color.White,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        if (overdueList.isNotEmpty()) {
                            Surface(color = Color(0xFFEF4444), shape = RoundedCornerShape(8.dp)) {
                                Text(
                                    "⚠️ PERLU PERHATIAN",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }

                // Filter Icon Button
                IconButton(
                    onClick = { showFilterSheet = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.FilterList,
                        contentDescription = "Filter",
                        modifier = Modifier.size(24.dp),
                        tint = Color.White
                    )
                }
            }
        }
        
        if (showFilterSheet) {
            com.dev.scanlaptop.ui.components.OverdueFilterSheet(
                filterType = filterType,
                thresholdHours = thresholdHours,
                navyColor = Color(0xFF0D47A1),
                sheetState = sheetState,
                onFilterTypeChange = { overdueViewModel.loadOverdue(thresholdHours, it) },
                onThresholdChange = { overdueViewModel.loadOverdue(it, filterType) },
                onDismissRequest = { showFilterSheet = false }
            )
        }

        HorizontalDivider(color = Color(0xFFE2E8F0))

        // ─── List / Empty / Loading ────────────────────────────
        SwipeRefresh(
            state = swipeState,
            onRefresh = { overdueViewModel.refresh() },
            modifier = Modifier.fillMaxSize().background(Color(0xFFF1F5F9))
        ) {
            if (isLoading || isRefreshing) {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(5) {
                        OverdueCardSkeleton()
                    }
                }
            } else if (overdueList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Surface(
                            color = Color(0xFF10B981).copy(alpha = 0.1f),
                            shape = CircleShape,
                            modifier = Modifier.size(80.dp)
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.padding(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Tidak Ada Perangkat Mengendap",
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            color = Color(0xFF1E293B)
                        )
                        Text(
                            "Semua perangkat yang terdaftar\nsaat ini berstatus IN (di dalam)",
                            color = Color(0xFF64748B),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            color = Color(0xFF0D47A1).copy(alpha = 0.08f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                "Batas waktu: > $thresholdHours jam",
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0D47A1)
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Text(
                            "Laptop Melewati Batas $thresholdHours Jam",
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp,
                            color = Color(0xFF1E293B),
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    items(overdueList, key = { it.laptopUuid }) { item ->
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn() + expandVertically()
                        ) {
                            SwipeToDismissCard(
                                item = item,
                                onItemClick = onItemClicked
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToDismissCard(item: OverdueItem, onItemClick: (String, Boolean) -> Unit) {
    var showDialogProcess by remember { mutableStateOf(false) }
    var showDialogWA by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var touchDownTime by remember { mutableLongStateOf(0L) }

    val dismissState = rememberSwipeToDismissBoxState(
        positionalThreshold = { totalDistance -> totalDistance * 0.7f }, // Ditingkatkan ke 70%
        confirmValueChange = { value ->
            val timeSinceDown = System.currentTimeMillis() - touchDownTime
            if (timeSinceDown < 350) {
                return@rememberSwipeToDismissBoxState false // reject rapid fling
            }
            if (value == SwipeToDismissBoxValue.StartToEnd) {
                showDialogWA = true
            } else if (value == SwipeToDismissBoxValue.EndToStart) {
                showDialogProcess = true
            }
            false // return false so the card snaps back
        }
    )

    if (showDialogProcess) {
        AlertDialog(
            onDismissRequest = { showDialogProcess = false },
            title = { Text("Konfirmasi Proses", fontWeight = FontWeight.Bold, color = Color(0xFF1E293B)) },
            text = { Text("Anda akan dialihkan ke halaman Detail Registrasi untuk memproses status ${if (item.type == "IN") "Keluar" else "Masuk"} perangkat ini. Lanjutkan?", color = Color(0xFF1E293B)) },
            confirmButton = {
                TextButton(onClick = { showDialogProcess = false; onItemClick(item.laptopUuid, true) }) { Text("Lanjutkan", color = Color(0xFF0D47A1), fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showDialogProcess = false }) { Text("Batal", color = Color(0xFF64748B), fontWeight = FontWeight.Bold) }
            },
            containerColor = Color.White
        )
    }

    if (showDialogWA) {
        AlertDialog(
            onDismissRequest = { showDialogWA = false },
            title = { Text("Kirim WhatsApp", fontWeight = FontWeight.Bold, color = Color(0xFF1E293B)) },
            text = { Text("Apakah Anda yakin ingin mengirim pesan peringatan melalui WhatsApp kepada ${item.namaUser}?", color = Color(0xFF1E293B)) },
            confirmButton = {
                TextButton(onClick = { 
                    showDialogWA = false
                    val isAsset = item.kepemilikan.contains("asset", ignoreCase = true) || item.kepemilikan.contains("aset", ignoreCase = true)
                    val message = if (isAsset && item.type == "OUT") {
                        "Pemberitahuan:\nAsset Perusahaan atas nama *${item.namaUser}* (${item.instansiDivisi}) belum dikembali ke perusahaan selama ${item.durasiJam} jam.\nMohon konfirmasi status kembali asset tersebut."
                    } else if (item.type == "IN") {
                        "Pemberitahuan:\nDevice milik atas nama *${item.namaUser}* (${item.instansiDivisi}) belum keluar perusahaan selama ${item.durasiJam} jam.\nMohon konfirmasi jika device akan menetap di dalam perusahaan lebih dari 1 hari."
                    } else {
                        "Pemberitahuan:\nDevice atas nama *${item.namaUser}* (${item.instansiDivisi}) statusnya belum sesuai selama ${item.durasiJam} jam.\nMohon konfirmasi status device tersebut."
                    }
                    
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        setPackage("com.whatsapp")
                        putExtra(Intent.EXTRA_TEXT, message)
                    }
                    try {
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        val fallbackIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, message)
                        }
                        context.startActivity(Intent.createChooser(fallbackIntent, "Kirim Pesan Mengendap via"))
                    }
                }) { Text("Kirim", color = Color(0xFF25D366), fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showDialogWA = false }) { Text("Batal", color = Color(0xFF64748B), fontWeight = FontWeight.Bold) }
            },
            containerColor = Color.White
        )
    }

    SwipeToDismissBox(
        state = dismissState,
        modifier = Modifier.pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                touchDownTime = System.currentTimeMillis()
            }
        },
        backgroundContent = {
            val color = if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) Color(0xFF25D366) else Color(0xFF3B82F6) // WhatsApp Green or Blue
            val alignment = if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 8.dp)
                    .background(color, RoundedCornerShape(16.dp))
                    .padding(horizontal = 24.dp),
                contentAlignment = alignment
            ) {
                if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Message, contentDescription = "Kirim Pesan", tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Kirim WhatsApp", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                } else if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (item.type == "IN") "Proses Keluar" else "Proses Masuk", color = Color.White, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.Default.Check, contentDescription = "Proses", tint = Color.White)
                    }
                }
            }
        },
        content = {
            OverdueCard(item = item, onClick = { onItemClick(item.laptopUuid, false) })
        }
    )
}

@Composable
fun OverdueCard(item: OverdueItem, onClick: () -> Unit = {}) {
    val urgencyColor = when {
        item.durasiJam >= 24 -> Color(0xFFDC2626) // Merah gelap — sangat lama
        item.durasiJam >= 8 -> Color(0xFFEA580C) // Oranye — sudah 1 shift
        else -> Color(0xFFD97706) // Kuning — mulai overdue
    }

    val durasiText = when {
        item.durasiJam >= 24 -> "${item.durasiJam / 24} hari ${item.durasiJam % 24} jam"
        else -> "${item.durasiJam} jam"
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Durasi badge (paling menonjol)
                Surface(
                    color = urgencyColor,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Timer,
                                null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                durasiText,
                                color = Color.White,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        item.namaUser.uppercase(),
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp,
                        color = Color(0xFF1E293B)
                    )
                    Text(
                        item.instansiDivisi,
                        fontSize = 12.sp,
                        color = Color(0xFF0D47A1),
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                // Urgency indicator
                Surface(
                    color = urgencyColor.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        "⏱️ MENGENDAP",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = urgencyColor,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            // Daftar perangkat yang dibawa
            if (item.perangkatList.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = Color(0xFFF1F5F9))
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "${item.perangkatList.size} Perangkat Dibawa:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF64748B)
                )
                Spacer(modifier = Modifier.height(4.dp))
                item.perangkatList.forEach { sn ->
                    Row(
                        modifier = Modifier.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(urgencyColor, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                "S/N: ${sn.uppercase()}",
                                fontSize = 11.sp,
                                color = Color(0xFF1E293B),
                                fontWeight = FontWeight.Bold
                            )
                            val rawKepemilikan = item.kepemilikan.trim()
                            val kepemilikanText = if (rawKepemilikan.isEmpty() || rawKepemilikan == "-") "Belum Terverifikasi" else rawKepemilikan.replace("_", " ").split(" ").joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
                            Text(
                                "Kepemilikan: $kepemilikanText",
                                fontSize = 9.sp,
                                color = if (kepemilikanText == "Belum Terverifikasi") Color(0xFFEF4444) else Color(0xFF3B82F6), // Merah jika belum terverifikasi
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = Color(0xFFF1F5F9))
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = if (item.type == "IN") Color(0xFF2E7D32).copy(alpha = 0.1f) else Color(0xFFC62828).copy(alpha = 0.1f),
                            shape = RoundedCornerShape(4.dp),
                        ) {
                            Text(
                                if (item.type == "IN") "Masuk" else "Keluar",
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (item.type == "IN") Color(0xFF2E7D32) else Color(0xFFC62828)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "${item.lastInTime?.take(16)?.replace("T", " | ") ?: "-"} WIB",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B),
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Person, contentDescription = "Petugas", modifier = Modifier.size(12.dp), tint = Color(0xFF0D47A1))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            item.namaPetugasIn ?: "-",
                            fontSize = 11.sp,
                            color = Color(0xFF0D47A1),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun OverdueCardSkeleton() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        shadowElevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color(0xFFE2E8F0), CircleShape)
                        .shimmerEffect()
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(18.dp)
                            .background(Color(0xFFE2E8F0), RoundedCornerShape(4.dp))
                            .shimmerEffect()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.5f)
                            .height(14.dp)
                            .background(Color(0xFFE2E8F0), RoundedCornerShape(4.dp))
                            .shimmerEffect()
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = Color(0xFFF1F5F9))
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp)
                    .background(Color(0xFFE2E8F0), RoundedCornerShape(4.dp))
                    .shimmerEffect()
            )
        }
    }
}
