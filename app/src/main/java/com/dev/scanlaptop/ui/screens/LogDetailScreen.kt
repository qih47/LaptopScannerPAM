package com.dev.scanlaptop.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dev.scanlaptop.data.HistoryLog
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// Fungsi format tanggal tetap dipertahankan
fun formatTanggalIndo(isoString: String?): String {
    if (isoString == null) return "-"
    return try {
        val parsedDate = ZonedDateTime.parse(isoString)
        val formatter = DateTimeFormatter.ofPattern("dd MMMM yyyy  |  HH:mm", Locale("id", "ID"))
        parsedDate.format(formatter)
    } catch (e: Exception) {
        if (isoString.length >= 16) {
            "${isoString.take(10).replace("-", "/")}  |  ${isoString.substring(11, 16)}"
        } else {
            isoString
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogDetailScreen(
    log: HistoryLog,
    onBack: () -> Unit
) {
    val isEntry = log.status_io == "IN"
    val statusColor = if (isEntry) Color(0xFF1B5E20) else Color(0xFFB71C1C)
    val statusLabel = if (isEntry) "TRANSAKSI MASUK" else "TRANSAKSI KELUAR"

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("DETAIL TRANSAKSI", fontWeight = FontWeight.Black, fontSize = 16.sp, color = Color(0xFF1A237E)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color(0xFF1A237E))
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.White)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color(0xFFF0F2F5))
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // --- HEADER STATUS (ICON & WAKTU) ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                border = BorderStroke(1.dp, Color(0xFFEEEEEE))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp, horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        color = statusColor,
                        shape = CircleShape,
                        modifier = Modifier.size(90.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isEntry) Icons.AutoMirrored.Filled.Login else Icons.AutoMirrored.Filled.Logout,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(42.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = statusLabel,
                        fontWeight = FontWeight.Black,
                        fontSize = 22.sp,
                        color = statusColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = formatTanggalIndo(log.created_at),
                        color = Color(0xFF000000),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // --- DETAIL PERANGKAT YANG DIBAWA (BARU) ---
            // Bagian ini menampilkan list device dari tabel detail
            InfoSection(title = "DAFTAR BARANG YANG DIBAWA") {
                if (log.details.isEmpty()) {
                    Text("Tidak ada rincian perangkat.", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                } else {
                    log.details.forEach { detail ->
                        DetailLogItem(
                            label = detail.merk?.uppercase() ?: "PERANGKAT",
                            value = "${detail.tipe?.uppercase()} (SN: ${detail.no_seri?.uppercase()})",
                            icon = Icons.Default.Laptop
                        )
                        if (log.details.last() != detail) {
                            HorizontalDivider(Modifier.padding(vertical = 4.dp), thickness = 0.5.dp, color = Color(0xFFEEEEEE))
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // --- INFORMASI PETUGAS & LOKASI ---
            InfoSection(title = "LOGISTIK & PEMERIKSAAN") {
                DetailLogItem("Lokasi Pemeriksaan", log.lokasi?.uppercase(), Icons.Default.Place)
                val namaPetugas = log.users?.nama_lengkap ?: log.petugas_npp ?: "Petugas Keamanan"
                DetailLogItem("Petugas Pemeriksa", "${namaPetugas.uppercase()} (${log.petugas_npp ?: "-"})", Icons.Default.VerifiedUser)
                DetailLogItem("Catatan Transaksi", log.keterangan?.uppercase(), Icons.Default.ChatBubble)
            }

            Spacer(Modifier.height(16.dp))

            // --- IDENTITAS PEMILIK ---
            InfoSection(title = "DATA IDENTITAS PEMILIK") {
                DetailLogItem("Nama Lengkap", log.registrasi_laptop?.nama_pengguna?.uppercase(), Icons.Default.Person)
                DetailLogItem("Divisi / Instansi", log.registrasi_laptop?.instansi_divisi?.uppercase(), Icons.Default.Business)
                DetailLogItem("NPP / No. ID Card", log.registrasi_laptop?.nomor_id_card?.uppercase(), Icons.Default.Badge)

                // --- LOGIKA GABUNGAN JENIS & GOLONGAN ---
                val jenisRaw = log.registrasi_laptop?.jenis ?: "-"
                val golonganRaw = log.registrasi_laptop?.golongan ?: ""

                val statusDisplay = if (jenisRaw.contains("non_pegawai", ignoreCase = true)) {
                    val processedGolongan = if (golonganRaw.equals("PKL_magang", ignoreCase = true)) "PKL/MAGANG" else golonganRaw.ifBlank { "Tamu" }.uppercase()
                    "NON PEGAWAI / $processedGolongan"
                } else {
                    jenisRaw.replace("_", " ").uppercase()
                }

                DetailLogItem("Golongan Status", statusDisplay, Icons.Default.Category)

                val kepemilikanRaw = log.registrasi_laptop?.kepemilikan ?: "-"
                val kepemilikanDisplay = if (kepemilikanRaw == "-") "-" else kepemilikanRaw.replace("_", " ").uppercase()
                DetailLogItem("Status Kepemilikan", kepemilikanDisplay, Icons.Default.CheckCircle)

                HorizontalDivider(Modifier.padding(vertical = 12.dp), thickness = 1.dp, color = Color(0xFFEEEEEE))

                DetailLogItem("No. Registrasi Sistem", log.registrasi_laptop?.no_registrasi?.uppercase(), Icons.Default.AppRegistration)
                DetailLogItem("Nomor Dokumen", log.registrasi_laptop?.no_dokumen?.uppercase(), Icons.Default.Description)
                DetailLogItem("Tanggal Dokumen", log.registrasi_laptop?.tanggal_dokumen, Icons.Default.CalendarToday)
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun InfoSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.5.dp, Color(0xFFE0E0E0))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFF1A237E),
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
fun DetailLogItem(label: String, value: String?, icon: ImageVector) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = Color(0xFFF1F3F9),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, modifier = Modifier.size(20.dp), tint = Color(0xFF1A237E))
            }
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(
                text = label.uppercase(),
                color = Color(0xFF000000).copy(alpha = 0.6f),
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.5.sp
            )
            Text(
                text = value?.takeIf { it.isNotBlank() } ?: "-",
                fontWeight = FontWeight.Black,
                fontSize = 15.sp,
                color = Color(0xFF000000)
            )
        }
    }
}