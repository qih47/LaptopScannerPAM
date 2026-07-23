package com.dev.scanlaptop.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackBottomSheet(
    status: String, // "IN", "OUT", "ERROR", "OFFLINE"
    message: String,
    onDismiss: () -> Unit,
    onScanAgain: (() -> Unit)? = null  // null = tidak tampilkan tombol scan lagi
) {
    val (bgColor, icon, title, titleColor) = when (status) {
        "IN"      -> listOf(Color(0xFFE8F5E9), Icons.Filled.Check,    "BERHASIL MASUK",        Color(0xFF2E7D32))
        "OUT"     -> listOf(Color(0xFFFFEBEE), Icons.Filled.Check,    "BERHASIL KELUAR",       Color(0xFFC62828))
        "OFFLINE" -> listOf(Color(0xFFFFF3E0), Icons.Filled.CloudOff, "MASUK ANTREAN OFFLINE", Color(0xFFEF6C00))
        else      -> listOf(Color(0xFFFAFAFA), Icons.Filled.Close,    "TRANSAKSI DITOLAK",     Color(0xFF424242))
    }

    // Tombol "Scan Lagi" hanya relevan untuk transaksi sukses / offline
    val showScanAgain = onScanAgain != null && status != "ERROR"

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = bgColor as Color,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 8.dp, bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ─── Ikon status ───────────────────────────────────────
            Surface(
                shape = CircleShape,
                color = titleColor as Color,
                modifier = Modifier.size(80.dp)
            ) {
                Icon(
                    imageVector = icon as androidx.compose.ui.graphics.vector.ImageVector,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = title as String,
                fontWeight = FontWeight.Black,
                fontSize = 24.sp,
                color = titleColor,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                color = Color.DarkGray
            )

            Spacer(modifier = Modifier.height(32.dp))

            if (showScanAgain) {
                // ─── Dua tombol: Scan Lagi + Tutup ────────────────
                Button(
                    onClick = { onScanAgain!!() },
                    colors = ButtonDefaults.buttonColors(containerColor = titleColor),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        Icons.Rounded.QrCodeScanner,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("SCAN LAGI", fontWeight = FontWeight.Black, fontSize = 16.sp, color = Color.White)
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, titleColor.copy(alpha = 0.5f))
                ) {
                    Text("TUTUP", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = titleColor)
                }
            } else {
                // ─── Satu tombol: hanya Tutup (untuk ERROR) ───────
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = titleColor),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("TUTUP", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                }
            }
        }
    }
}
