package com.dev.scanlaptop.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dev.scanlaptop.data.HistoryLog
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

@Composable
fun MiniSparkline(
    historyList: List<HistoryLog>,
    modifier: Modifier = Modifier,
    selectedPeriod: Int = 7,
    isLoading: Boolean = false,
    totalRemoteCount: Int = 0,
    onPeriodSelect: (Int) -> Unit = {}
) {
    val periods = remember { listOf(1, 7, 30, 0) }
    val initialPage = remember {
        val idx = periods.indexOf(selectedPeriod)
        if (idx >= 0) idx else 1
    }
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { periods.size })

    // Saat pengguna menggeser chart card ke kiri/kanan, sesuaikan filter periode (hanya saat scroll berhenti)
    LaunchedEffect(pagerState.settledPage) {
        val newPeriod = periods[pagerState.settledPage]
        if (newPeriod != selectedPeriod) {
            onPeriodSelect(newPeriod)
        }
    }

    // Sinkronisasi animasi gulir saat selectedPeriod diubah dari luar (misal: filter atas)
    LaunchedEffect(selectedPeriod) {
        val targetPage = periods.indexOf(selectedPeriod)
        if (targetPage >= 0 && pagerState.currentPage != targetPage) {
            pagerState.animateScrollToPage(targetPage)
        }
    }

    Column(modifier = modifier) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            val period = periods[page]
            val periodLabel = when (period) {
                1 -> "Aktivitas Hari Ini"
                7 -> "Aktivitas 7 Hari"
                30 -> "Aktivitas 1 Bulan"
                else -> "Semua Waktu"
            }

            // Hitung distribusi data untuk sparkline pada periode aktif
            val sparklineCounts = remember(historyList, period) {
                val today = ZonedDateTime.now(ZoneId.of("Asia/Jakarta")).toLocalDate()
                val numPoints = when (period) {
                    1 -> 6
                    7 -> 7
                    30 -> 10
                    else -> 12
                }
                val counts = IntArray(numPoints) { 0 }
                val startDaysAgo = when (period) {
                    1 -> 0
                    7 -> 6
                    30 -> 29
                    else -> 36500
                }
                historyList.forEach { log ->
                    try {
                        val zdt = ZonedDateTime.parse(log.created_at)
                            .withZoneSameInstant(ZoneId.of("Asia/Jakarta"))
                        val logDate = zdt.toLocalDate()
                        val diffDays = java.time.temporal.ChronoUnit.DAYS.between(logDate, today).toInt()
                        if (diffDays in 0..startDaysAgo) {
                            val idx = ((startDaysAgo - diffDays) * numPoints) / (startDaysAgo + 1)
                            val clampedIdx = idx.coerceIn(0, numPoints - 1)
                            counts[clampedIdx]++
                        }
                    } catch (e: Exception) {}
                }
                counts.toList()
            }
            val maxCount = (sparklineCounts.maxOrNull() ?: 1).coerceAtLeast(1)
            val totalCount = if (totalRemoteCount > 0) totalRemoteCount else sparklineCounts.sum()

            Surface(
                color = Color(0xFFE3F2FD),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = periodLabel,
                            fontSize = 11.sp,
                            color = Color(0xFF1565C0),
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (isLoading) "Menghitung..." else "$totalCount Transaksi",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF0D47A1)
                            )
                            if (isLoading) {
                                Spacer(modifier = Modifier.width(6.dp))
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    strokeWidth = 2.dp,
                                    color = Color(0xFF0D47A1)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Canvas(
                        modifier = Modifier
                            .weight(1.4f)
                            .height(30.dp)
                    ) {
                        val width = size.width
                        val height = size.height
                        val stepX = width / (sparklineCounts.size - 1).coerceAtLeast(1)
                        val path = Path()

                        sparklineCounts.forEachIndexed { index, count ->
                            val x = index * stepX
                            val y = height - ((count.toFloat() / maxCount) * height)
                            if (index == 0) {
                                path.moveTo(x, y)
                            } else {
                                path.lineTo(x, y)
                            }
                        }

                        drawPath(
                            path = path,
                            color = Color(0xFF1976D2),
                            style = Stroke(
                                width = 2.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )

                        sparklineCounts.forEachIndexed { index, count ->
                            val x = index * stepX
                            val y = height - ((count.toFloat() / maxCount) * height)
                            drawCircle(
                                color = Color(0xFF0D47A1),
                                radius = 3.dp.toPx(),
                                center = Offset(x, y)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Indicator dots untuk HorizontalPager
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(periods.size) { index ->
                val isActive = pagerState.currentPage == index
                val color = if (isActive) Color(0xFF0D47A1) else Color(0xFFBBDEFB)
                val width = if (isActive) 16.dp else 6.dp
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .size(width = width, height = 4.dp)
                        .background(color, RoundedCornerShape(2.dp))
                )
            }
        }
    }
}
