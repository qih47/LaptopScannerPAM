package com.dev.scanlaptop.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dev.scanlaptop.data.HistoryLog

/**
 * Card riwayat transaksi yang ditampilkan di Dashboard.
 * Mendukung expand/collapse untuk daftar perangkat.
 */
@Composable
fun HistoryCardPro(
    log: HistoryLog,
    navyColor: Color,
    onClick: () -> Unit,
    fetchPairedTransaction: suspend () -> HistoryLog? = { null }
) {
    var isExpanded by remember { mutableStateOf(false) }
    var pairedLog by remember { mutableStateOf<HistoryLog?>(null) }
    var isLoadingPaired by remember { mutableStateOf(false) }
    var hasFetchedPaired by remember { mutableStateOf(false) }
    val statusColor = if (log.status_io == "IN") Color(0xFF10B981) else Color(0xFFEF4444)
    val statusText = if (log.status_io == "IN") "MASUK" else "KELUAR"
    val showNewLabel = log.isOpen == 0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = if (showNewLabel) BorderStroke(1.dp, Color(0xFFFFD600)) else null
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Icon Status
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = statusColor.copy(alpha = 0.1f),
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (log.status_io == "IN") Icons.Default.Login else Icons.Default.Logout,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = log.registrasi_laptop?.nama_pengguna ?: "User Tidak Diketahui",
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp,
                            color = Color(0xFF1E293B),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (showNewLabel) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(color = Color(0xFFFFD600), shape = RoundedCornerShape(4.dp)) {
                                Text(
                                    "NEW",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                    color = Color(0xFF1E293B),
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                    Text(
                        text = log.registrasi_laptop?.instansi_divisi ?: "-",
                        fontSize = 12.sp,
                        color = navyColor,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = log.created_at.take(16).replace("T", "  •  "),
                        fontSize = 11.sp,
                        color = Color(0xFF64748B),
                        fontWeight = FontWeight.Medium
                    )
                }

                // Badge Status
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusColor.copy(alpha = 0.1f),
                    border = BorderStroke(1.dp, statusColor)
                ) {
                    Text(
                        statusText,
                        fontWeight = FontWeight.Black,
                        color = statusColor,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            // Expand perangkat
            if (log.details.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = Color(0xFFF1F5F9))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isExpanded = !isExpanded }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${log.details.size} PERANGKAT DIPROSES",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = navyColor
                    )
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = navyColor
                    )
                }
                AnimatedVisibility(visible = isExpanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        log.details.forEach { device ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFF8FAFC), RoundedCornerShape(8.dp))
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Laptop,
                                    null,
                                    modifier = Modifier.size(14.dp),
                                    tint = Color(0xFF64748B)
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "${device.merk} ${device.tipe}".uppercase(),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = Color.Black,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "S/N: ${device.no_seri}".uppercase(),
                                        fontSize = 9.sp,
                                        color = Color(0xFF64748B),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val rawKepemilikan = log.registrasi_laptop?.kepemilikan?.trim() ?: ""
                                    val kepemilikanText = if (rawKepemilikan.isEmpty() || rawKepemilikan == "-") "Belum Terverifikasi" else rawKepemilikan.replace("_", " ").split(" ").joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
                                    Text(
                                        "Kepemilikan: $kepemilikanText",
                                        fontSize = 9.sp,
                                        color = if (kepemilikanText == "Belum Terverifikasi") Color(0xFFEF4444) else Color(0xFF3B82F6), // Merah jika belum terverifikasi
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                val petugasName = log.users?.nama_lengkap ?: log.petugas_npp ?: "-"
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp)) {
                                    Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(10.dp), tint = Color(0xFF64748B))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = petugasName,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF64748B),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    
                        // Mini Timeline (Paired Transaction)
                        LaunchedEffect(Unit) {
                            if (!hasFetchedPaired) {
                                isLoadingPaired = true
                                pairedLog = fetchPairedTransaction()
                                hasFetchedPaired = true
                                isLoadingPaired = false
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        if (isLoadingPaired) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = navyColor, strokeWidth = 2.dp)
                            }
                        } else if (pairedLog != null) {
                            val pairedStatusText = if (pairedLog!!.status_io == "IN") "MASUK" else "KELUAR"
                            val pairedIcon = if (pairedLog!!.status_io == "IN") "🟢" else "🔴"
                            val currentIcon = if (log.status_io == "IN") "🟢" else "🔴"
                            
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFF1F5F9), RoundedCornerShape(8.dp))
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = "TIMELINE TRANSAKSI",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF64748B),
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                                
                                // Current transaction is IN (so paired is OUT), or current is OUT (so paired is IN)
                                // We want to show them chronologically. IN is usually before OUT.
                                val (firstLog, secondLog) = if (log.status_io == "IN") {
                                    Pair(log, pairedLog!!)
                                } else {
                                    Pair(pairedLog!!, log)
                                }
                                
                                val firstPetugas = firstLog.users?.nama_lengkap ?: firstLog.petugas_npp ?: "-"
                                val secondPetugas = secondLog.users?.nama_lengkap ?: secondLog.petugas_npp ?: "-"
                                val timeMasuk = firstLog.created_at.take(16).replace("T", " ")
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "🟢 MASUK: $timeMasuk",
                                        fontSize = 10.sp,
                                        fontWeight = if (firstLog.uuid == log.uuid) FontWeight.Bold else FontWeight.Normal,
                                        color = Color(0xFF1E293B),
                                        modifier = Modifier.weight(1f)
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(10.dp), tint = Color(0xFF64748B))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = firstPetugas,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF64748B),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                
                                val timeKeluar = secondLog.created_at.take(16).replace("T", " ")
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "🔴 KELUAR: $timeKeluar",
                                        fontSize = 10.sp,
                                        fontWeight = if (secondLog.uuid == log.uuid) FontWeight.Bold else FontWeight.Normal,
                                        color = Color(0xFF1E293B),
                                        modifier = Modifier.weight(1f)
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(10.dp), tint = Color(0xFF64748B))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = secondPetugas,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF64748B),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
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

/**
 * Chip statistik kecil di header dashboard.
 */
@Composable
fun StatusChipMini(label: String, count: Int, color: Color) {
    Surface(
        color = Color.White.copy(alpha = 0.2f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(8.dp).background(color, CircleShape))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                "$label: $count",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}
