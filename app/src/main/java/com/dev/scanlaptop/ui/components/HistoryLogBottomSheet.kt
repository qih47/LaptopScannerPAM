package com.dev.scanlaptop.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.DeviceHub
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dev.scanlaptop.data.HistoryLog

import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults

@Composable
fun HistoryLogBottomSheetContent(log: HistoryLog, onDetailClick: () -> Unit = {}) {
    val statusColor = when (log.status_io) {
        "IN" -> Color(0xFF1B5E20) // Hijau
        "OUT" -> Color(0xFFB71C1C) // Merah
        else -> Color(0xFFB71C1C) // Merah (Ditolak/dll)
    }
    val statusLabel = when (log.status_io) {
        "IN" -> "TRANSAKSI MASUK"
        "OUT" -> "TRANSAKSI KELUAR"
        else -> "DITOLAK"
    }
    val isEntry = log.status_io == "IN"
    
    // Parse tanggal manual
    val formattedDate = try {
        val parsedDate = java.time.ZonedDateTime.parse(log.created_at)
        val formatter = java.time.format.DateTimeFormatter.ofPattern("dd MMMM yyyy  |  HH:mm", java.util.Locale("id", "ID"))
        parsedDate.format(formatter)
    } catch (e: Exception) {
        if (log.created_at.length >= 16) {
            "${log.created_at.take(10).replace("-", "/")}  |  ${log.created_at.substring(11, 16)}"
        } else log.created_at
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .padding(bottom = 24.dp)
    ) {
        Text("DETAIL TRANSAKSI", fontWeight = FontWeight.Black, fontSize = 16.sp, color = Color(0xFF1A237E))
        Spacer(modifier = Modifier.height(24.dp))
        
        Surface(color = statusColor, shape = RoundedCornerShape(8.dp)) {
            Text(statusLabel, color = Color.White, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp), fontWeight = FontWeight.Black, fontSize = 12.sp)
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (isEntry) Icons.AutoMirrored.Filled.Login else Icons.AutoMirrored.Filled.Logout, null, tint = statusColor, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text("Waktu Pindai", color = Color.Black.copy(0.6f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(formattedDate, color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.Black)
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Person, null, tint = Color(0xFF1A237E), modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text("Petugas Pemindai", color = Color.Black.copy(0.6f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(log.users?.nama_lengkap ?: log.petugas_npp ?: "-", color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.Black)
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.DeviceHub, null, tint = Color(0xFF1A237E), modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text("Laptop UUID", color = Color.Black.copy(0.6f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(log.laptop_uuid, color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = onDetailClick,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A237E)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Lihat Detail Laptop", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}
