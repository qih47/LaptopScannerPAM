package com.dev.scanlaptop.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.nativeCanvas
import android.graphics.Paint
import com.dev.scanlaptop.data.HistoryLog
import com.dev.scanlaptop.utils.PdfGenerator
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import android.content.Intent
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import com.dev.scanlaptop.ui.components.shimmerEffect

data class DayStats(
    val label: String,      // "Sen", "Sel", dst
    val date: LocalDate,
    val inCount: Int,
    val outCount: Int
)

data class DivisiStats(
    val nama: String,
    val total: Int
)

/**
 * Analytics screen — bar chart 7 hari + ringkasan + top divisi.
 * Pakai Android Canvas (zero dependency).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    historyList: List<HistoryLog>,
    navyGradient: Brush,
    isLoading: Boolean = false,
    isRefreshing: Boolean = false,
    isAnalyticsLoading: Boolean = false,
    onRefresh: () -> Unit = {},
    onChartDaysChange: (Int) -> Unit = {},
    onExportPdf: (LocalDate, LocalDate) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    var chartDays by remember { mutableIntStateOf(7) }

    // Re-fetch data dari server saat filter periode berubah (di background tanpa memblokir UI)
    LaunchedEffect(chartDays) {
        onChartDaysChange(chartDays)
    }

    // Compute data analytics secara instan dari historyList
    val dayStats = remember(historyList, chartDays) { computeDayStats(historyList, chartDays) }
    val topDivisi = remember(historyList) { computeTopDivisi(historyList) }
    val totalMingguIni = remember(dayStats) { dayStats.sumOf { it.inCount + it.outCount } }
    
    // Perbaikan Rata-rata/Hari: dihitung berdasarkan hari sejak transaksi pertama muncul, dan dibulatkan
    val avgPerHari = remember(dayStats, totalMingguIni) {
        val firstActiveIndex = dayStats.indexOfFirst { (it.inCount + it.outCount) > 0 }
        val elapsedDays = if (firstActiveIndex != -1) (dayStats.size - firstActiveIndex) else 1
        Math.round(totalMingguIni.toFloat() / elapsedDays)
    }
    
    val peakHourDisplay = "07:00 & 16:30"
    
    var showExportSheet by remember { mutableStateOf(false) }

    val swipeRefreshState = com.google.accompanist.swiperefresh.rememberSwipeRefreshState(isRefreshing)

    Box(modifier = Modifier.fillMaxSize()) {
        com.google.accompanist.swiperefresh.SwipeRefresh(
            state = swipeRefreshState,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFF1F5F9))
                    .verticalScroll(rememberScrollState())
            ) {
        // ─── Header ───────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
                .background(navyGradient)
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Column {
                Text("Analitik Monitoring", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                Spacer(modifier = Modifier.height(4.dp))
                val title = when (chartDays) {
                    1 -> "Hari Ini"
                    7 -> "7 Hari Terakhir"
                    30 -> "30 Hari Terakhir"
                    365 -> "12 Bulan Terakhir"
                    else -> "$chartDays Hari Terakhir"
                }
                Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
            }
        }

        Column(modifier = Modifier.padding(16.dp)) {
            if (isLoading || isRefreshing || isAnalyticsLoading) {
                AnalyticsSkeleton()
            } else {            // ─── Summary Cards ─────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SummaryCard(
                    label = "Total Transaksi",
                    value = "$totalMingguIni",
                    icon = Icons.Default.SwapHoriz,
                    color = Color(0xFF0D47A1),
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
                SummaryCard(
                    label = "Rata-rata/Hari",
                    value = "$avgPerHari",
                    icon = Icons.Default.TrendingUp,
                    color = Color(0xFF10B981),
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
                SummaryCard(
                    label = "Jam Tersibuk (WIB)",
                    value = peakHourDisplay,
                    icon = Icons.Default.Schedule,
                    color = Color(0xFFF59E0B),
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ─── Bar Chart IN/OUT 7 Hari ───────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            when (chartDays) {
                                1 -> "Aktivitas Hari Ini"
                                7 -> "Aktivitas 7 Hari Terakhir"
                                30 -> "Aktivitas 30 Hari Terakhir"
                                365 -> "Aktivitas 12 Bulan Terakhir"
                                else -> "Aktivitas"
                            },
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp,
                            color = Color(0xFF1E293B)
                        )
                        Row {
                            listOf(1 to "1H", 7 to "7H", 30 to "30H", 365 to "12B").forEach { (days, label) ->
                                Surface(
                                    color = if (chartDays == days) Color(0xFF0D47A1) else Color(0xFFF1F5F9),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .padding(start = 4.dp)
                                        .clickable {
                                            chartDays = days
                                        }
                                ) {
                                    Text(
                                        label,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (chartDays == days) Color.White else Color(0xFF64748B)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    // Legend
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(10.dp).background(Color(0xFF10B981), RoundedCornerShape(2.dp)))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Masuk", fontSize = 11.sp, color = Color(0xFF64748B))
                        Spacer(modifier = Modifier.width(12.dp))
                        Box(modifier = Modifier.size(10.dp).background(Color(0xFFEF4444), RoundedCornerShape(2.dp)))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Keluar", fontSize = 11.sp, color = Color(0xFF64748B))
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (dayStats.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                            Text("Belum ada data", color = Color(0xFF94A3B8))
                        }
                    } else {
                        BarChart(
                            dayStats = dayStats,
                            modifier = Modifier.fillMaxWidth().height(220.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        // X axis labels
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            dayStats.forEach { day ->
                                Text(
                                    day.label,
                                    fontSize = 10.sp,
                                    color = Color(0xFF64748B),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ─── Top 5 Divisi ──────────────────────────────────
            if (topDivisi.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Top Aktivitas", fontWeight = FontWeight.Black, fontSize = 15.sp, color = Color(0xFF1E293B))
                        Spacer(modifier = Modifier.height(12.dp))

                        val maxCount = topDivisi.maxOf { it.total }.coerceAtLeast(1)
                        topDivisi.forEachIndexed { index, divisi ->
                            DivisiBar(
                                rank = index + 1,
                                divisi = divisi,
                                maxCount = maxCount
                            )
                            if (index < topDivisi.lastIndex) {
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }

            } // closes else block
            Spacer(modifier = Modifier.height(80.dp)) // padding bawah untuk FAB
        } // closes inner Column
    } // closes outer Column
} // closes SwipeRefresh
    // FAB untuk Export PDF
    FloatingActionButton(
        onClick = { showExportSheet = true },
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(24.dp),
        containerColor = Color(0xFF1A237E),
        contentColor = Color.White
    ) {
        Icon(Icons.Default.PictureAsPdf, contentDescription = "Export PDF")
    }

    var showCustomDatePicker by remember { mutableStateOf(false) }

    if (showExportSheet) {
        ModalBottomSheet(
            onDismissRequest = { showExportSheet = false },
            containerColor = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .padding(bottom = 24.dp)
            ) {
                Text(
                    "Pilih Rentang Waktu Laporan",
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp,
                    color = Color(0xFF1A237E)
                )
                Spacer(modifier = Modifier.height(16.dp))

                val options = listOf(
                    "Harian (Hari Ini)" to 0L,
                    "Mingguan (7 Hari Terakhir)" to 7L,
                    "Bulanan (30 Hari Terakhir)" to 30L,
                    "Tahunan (365 Hari Terakhir)" to 365L
                )

                options.forEach { (label, daysAgo) ->
                    TextButton(
                        onClick = {
                            showExportSheet = false
                            val today = LocalDate.now(ZoneId.of("Asia/Jakarta"))
                            val startDate = today.minusDays(daysAgo)
                            onExportPdf(startDate, today)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Text(label, fontSize = 16.sp, color = Color.Black)
                    }
                }

                TextButton(
                    onClick = {
                        showExportSheet = false
                        showCustomDatePicker = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .background(Color(0xFFE8EAF6), RoundedCornerShape(8.dp))
                ) {
                    Text("Pilih Rentang Kustom...", fontSize = 16.sp, color = Color(0xFF1A237E), fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showCustomDatePicker) {
        val dateRangePickerState = androidx.compose.material3.rememberDateRangePickerState()

        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { showCustomDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val startMillis = dateRangePickerState.selectedStartDateMillis
                        val endMillis = dateRangePickerState.selectedEndDateMillis
                        if (startMillis != null && endMillis != null) {
                            val startDate = java.time.Instant.ofEpochMilli(startMillis).atZone(java.time.ZoneId.of("UTC")).toLocalDate()
                            val endDate = java.time.Instant.ofEpochMilli(endMillis).atZone(java.time.ZoneId.of("UTC")).toLocalDate()
                            showCustomDatePicker = false
                            onExportPdf(startDate, endDate)
                        } else {
                            android.widget.Toast.makeText(context, "Pilih tanggal mulai dan akhir", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("GENERATE PDF", fontWeight = FontWeight.Bold, color = Color(0xFF1A237E))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomDatePicker = false }) {
                    Text("BATAL", color = Color.Gray)
                }
            },
            colors = androidx.compose.material3.DatePickerDefaults.colors(containerColor = Color.White)
        ) {
            androidx.compose.material3.DateRangePicker(
                state = dateRangePickerState,
                title = { Text(text = "Pilih Rentang Tanggal", modifier = Modifier.padding(start = 24.dp, top = 24.dp, bottom = 8.dp), fontWeight = FontWeight.Black, color = Color(0xFF1A237E)) },
                headline = {
                    androidx.compose.material3.DateRangePickerDefaults.DateRangePickerHeadline(
                        selectedStartDateMillis = dateRangePickerState.selectedStartDateMillis,
                        selectedEndDateMillis = dateRangePickerState.selectedEndDateMillis,
                        displayMode = dateRangePickerState.displayMode,
                        dateFormatter = androidx.compose.material3.DatePickerDefaults.dateFormatter(),
                        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, bottom = 12.dp)
                    )
                },
                modifier = Modifier.weight(1f),
                colors = androidx.compose.material3.DatePickerDefaults.colors(
                    containerColor = Color.White,
                    titleContentColor = Color(0xFF1A237E),
                    headlineContentColor = Color(0xFF1A237E),
                    weekdayContentColor = Color.Black,
                    subheadContentColor = Color.Black,
                    yearContentColor = Color.Black,
                    currentYearContentColor = Color(0xFF1A237E),
                    selectedYearContentColor = Color.White,
                    selectedYearContainerColor = Color(0xFF1A237E),
                    dayContentColor = Color.Black,
                    selectedDayContentColor = Color.White,
                    selectedDayContainerColor = Color(0xFF1A237E),
                    todayContentColor = Color(0xFF1A237E),
                    todayDateBorderColor = Color(0xFF1A237E),
                    dayInSelectionRangeContentColor = Color.Black,
                    dayInSelectionRangeContainerColor = Color(0xFFE8EAF6)
                )
            )
        }
    }
} // closes Box
} // closes AnalyticsScreen

// ─────────────────────────────────────────────────────────────
// Canvas Bar Chart
// ─────────────────────────────────────────────────────────────

@Composable
fun BarChart(dayStats: List<DayStats>, modifier: Modifier = Modifier) {
    val greenColor = Color(0xFF10B981)
    val redColor = Color(0xFFEF4444)
    val gridColor = Color(0xFFE2E8F0)
    val highlightColor = Color(0x1A1E293B)

    var selectedIndex by remember(dayStats) { mutableStateOf<Int?>(null) }
    val maxVal = dayStats.maxOf { it.inCount + it.outCount }.coerceAtLeast(1)

    Column(modifier = modifier) {
        // Kotak Tooltip Interaktif jika batang diklik
        val selectedStat = selectedIndex?.let { dayStats.getOrNull(it) }
        if (selectedStat != null) {
            Surface(
                color = Color(0xFF1E293B),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "📅 ${selectedStat.label} (${selectedStat.date.format(DateTimeFormatter.ofPattern("dd MMM"))})",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("🟢 MASUK: ${selectedStat.inCount}", color = Color(0xFF10B981), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("🔴 KELUAR: ${selectedStat.outCount}", color = Color(0xFFEF4444), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("TOTAL: ${selectedStat.inCount + selectedStat.outCount}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        } else {
            Text(
                "💡 Ketuk batang grafik di bawah untuk melihat rincian angka",
                fontSize = 11.sp,
                color = Color(0xFF64748B),
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(dayStats) {
                    detectTapGestures { tapOffset ->
                        val barGroupWidth = size.width / dayStats.size
                        val idx = (tapOffset.x / barGroupWidth).toInt().coerceIn(0, dayStats.size - 1)
                        selectedIndex = if (selectedIndex == idx) null else idx
                    }
                }
        ) {
            val barGroupWidth = size.width / dayStats.size
            val barWidth = barGroupWidth * 0.3f
            val maxBarHeight = size.height * 0.9f
            val baseY = size.height

            // Grid lines
            for (i in 1..4) {
                val y = size.height - (size.height * i / 4f) * 0.9f
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            }

            dayStats.forEachIndexed { index, day ->
                val centerX = barGroupWidth * index + barGroupWidth / 2f

                // Highlight jika dipilih
                if (selectedIndex == index) {
                    drawRect(
                        color = highlightColor,
                        topLeft = Offset(barGroupWidth * index, 0f),
                        size = Size(barGroupWidth, size.height)
                    )
                }

                // Bar IN (kiri, hijau)
                val inHeight = (day.inCount.toFloat() / maxVal) * maxBarHeight
                if (inHeight > 0) {
                    drawRoundRect(
                        color = greenColor,
                        topLeft = Offset(centerX - barWidth - 2.dp.toPx(), baseY - inHeight),
                        size = Size(barWidth, inHeight),
                        cornerRadius = CornerRadius(3.dp.toPx())
                    )
                }

                // Bar OUT (kanan, merah)
                val outHeight = (day.outCount.toFloat() / maxVal) * maxBarHeight
                if (outHeight > 0) {
                    drawRoundRect(
                        color = redColor,
                        topLeft = Offset(centerX + 2.dp.toPx(), baseY - outHeight),
                        size = Size(barWidth, outHeight),
                        cornerRadius = CornerRadius(3.dp.toPx())
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Sub-composables
// ─────────────────────────────────────────────────────────────

@Composable
fun SummaryCard(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = value,
                    fontWeight = FontWeight.Black,
                    fontSize = if (value.length > 8) 12.sp else 20.sp,
                    color = Color(0xFF1E293B),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = label,
                    fontSize = 9.sp,
                    color = Color(0xFF64748B),
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun DivisiBar(rank: Int, divisi: DivisiStats, maxCount: Int) {
    val fraction = divisi.total.toFloat() / maxCount
    val barColor = when (rank) {
        1 -> Color(0xFF0D47A1)
        2 -> Color(0xFF1565C0)
        3 -> Color(0xFF1976D2)
        else -> Color(0xFF42A5F5)
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        // Rank number
        Surface(color = barColor.copy(alpha = 0.1f), shape = RoundedCornerShape(6.dp)) {
            Text(
                "#$rank",
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                color = barColor
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                divisi.nama.ifBlank { "-" },
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E293B)
            )
            Spacer(modifier = Modifier.height(3.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(Color(0xFFF1F5F9), RoundedCornerShape(3.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .fillMaxHeight()
                        .background(barColor, RoundedCornerShape(3.dp))
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            "${divisi.total}x",
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            color = barColor
        )
    }
}

// ─────────────────────────────────────────────────────────────
// Data computation functions
// ─────────────────────────────────────────────────────────────

private val WIB = ZoneId.of("Asia/Jakarta")

/**
 * Parse timestamp dari Supabase dan ambil waktu lokal as-is.
 * Ternyata database sudah menyimpan angka waktu dalam WIB, meskipun
 * formatnya UTC (Z atau +00:00). Jadi kita tidak boleh mengkonversinya
 * dengan `withZoneSameInstant` karena akan menambah 7 jam lagi.
 */
private fun parseTimestampLocal(createdAt: String): java.time.LocalDateTime? {
    if (createdAt.isBlank()) return null
    return try {
        // Ambil literal waktu as-is, abaikan offset-nya
        ZonedDateTime.parse(createdAt).toLocalDateTime()
    } catch (e1: Exception) {
        try {
            // Fallback: format ISO lokal
            java.time.LocalDateTime.parse(createdAt.take(19))
        } catch (e2: Exception) {
            null
        }
    }
}

private fun computeDayStats(logs: List<HistoryLog>, days: Int = 7, zone: ZoneId = WIB): List<DayStats> {
    val today = LocalDate.now(zone)
    if (days == 365) {
        val monthFormatter = DateTimeFormatter.ofPattern("MMM", Locale("id", "ID"))
        return (11 downTo 0).map { monthsAgo ->
            val monthDate = today.minusMonths(monthsAgo.toLong())
            val monthLogs = logs.filter { log ->
                val logDate = parseTimestampLocal(log.created_at)?.toLocalDate()
                logDate != null && logDate.month == monthDate.month && logDate.year == monthDate.year
            }
            DayStats(
                label = monthDate.format(monthFormatter),
                date = monthDate,
                inCount = monthLogs.count { it.status_io == "IN" },
                outCount = monthLogs.count { it.status_io == "OUT" }
            )
        }
    } else {
        val dayFormatter = if (days <= 7) DateTimeFormatter.ofPattern("EEE", Locale("id", "ID")) else DateTimeFormatter.ofPattern("dd/MM")
        return ((days - 1) downTo 0).map { daysAgo ->
            val date = today.minusDays(daysAgo.toLong())
            val dayLogs = logs.filter { log ->
                val logDate = parseTimestampLocal(log.created_at)?.toLocalDate()
                logDate == date
            }
            DayStats(
                label = date.format(dayFormatter),
                date = date,
                inCount = dayLogs.count { it.status_io == "IN" },
                outCount = dayLogs.count { it.status_io == "OUT" }
            )
        }
    }
}

private fun computeTopDivisi(logs: List<HistoryLog>): List<DivisiStats> {
    return logs
        .groupBy { it.registrasi_laptop?.instansi_divisi ?: "Unknown" }
        .map { (divisi, items) -> DivisiStats(divisi, items.size) }
        .sortedByDescending { it.total }
        .take(5)
}

// dihapus karena jam sibuk sudah di-fix ke 07:00 dan 16:30


@Composable
fun AnalyticsSkeleton() {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            repeat(3) {
                Box(
                    modifier = Modifier.weight(1f).height(100.dp)
                        .background(Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
                        .shimmerEffect()
                )
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        Box(
            modifier = Modifier.fillMaxWidth().height(220.dp)
                .background(Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
                .shimmerEffect()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Box(
            modifier = Modifier.fillMaxWidth().height(180.dp)
                .background(Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
                .shimmerEffect()
        )
    }
}
