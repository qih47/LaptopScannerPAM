package com.dev.scanlaptop.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dev.scanlaptop.data.HistoryLog
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.ZoneId

@Composable
fun MiniSparkline(
    historyList: List<HistoryLog>,
    modifier: Modifier = Modifier
) {
    // Kelompokkan transaksi per hari (7 hari terakhir)
    val dayCounts = remember(historyList) {
        val counts = mutableMapOf<String, Int>()
        val today = ZonedDateTime.now(ZoneId.of("Asia/Jakarta"))
        
        // Inisialisasi 7 hari terakhir dengan 0
        for (i in 6 downTo 0) {
            val dateStr = today.minusDays(i.toLong()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            counts[dateStr] = 0
        }
        
        // Hitung transaksi
        historyList.forEach { log ->
            try {
                val logDate = log.created_at.substring(0, 10) // Ambil YYYY-MM-DD
                if (counts.containsKey(logDate)) {
                    counts[logDate] = counts[logDate]!! + 1
                }
            } catch (e: Exception) {}
        }
        
        counts.values.toList()
    }
    
    val maxCount = dayCounts.maxOrNull()?.coerceAtLeast(1) ?: 1
    
    Surface(
        color = Color(0xFFE3F2FD),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(0.4f)) {
                Text(
                    text = "Aktivitas 7 Hari",
                    fontSize = 11.sp,
                    color = Color(0xFF1565C0),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${dayCounts.sum()} transaksi",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF0D47A1)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Draw sparkline
            Canvas(modifier = Modifier.weight(0.6f).height(32.dp)) {
                val width = size.width
                val height = size.height
                
                val stepX = width / (dayCounts.size - 1).coerceAtLeast(1)
                
                val path = Path()
                dayCounts.forEachIndexed { index, count ->
                    val x = index * stepX
                    // Balik Y karena 0 di atas
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
                
                // Draw points
                dayCounts.forEachIndexed { index, count ->
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
