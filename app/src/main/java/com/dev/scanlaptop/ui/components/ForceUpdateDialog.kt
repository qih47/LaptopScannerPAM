package com.dev.scanlaptop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.verticalScroll

@Composable
fun ForceUpdateDialog(
    versionName: String,
    releaseNotes: String,
    isDownloading: Boolean,
    isForceUpdate: Boolean,
    onUpdateClick: () -> Unit,
    onIgnoreClick: () -> Unit = {}
) {
    Dialog(
        onDismissRequest = { if (!isForceUpdate) onIgnoreClick() },
        properties = DialogProperties(
            dismissOnBackPress = !isForceUpdate,
            dismissOnClickOutside = !isForceUpdate,
            usePlatformDefaultWidth = false
        )
    ) {
        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
        val screenWidthDp = configuration.screenWidthDp
        val scaleFactor = (screenWidthDp / 412f).coerceIn(0.75f, 1f)
        val currentDensity = androidx.compose.ui.platform.LocalDensity.current
        val adaptiveDensity = androidx.compose.ui.unit.Density(
            density = currentDensity.density * scaleFactor,
            fontScale = currentDensity.fontScale * scaleFactor
        )

        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides adaptiveDensity) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .wrapContentHeight(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(androidx.compose.foundation.rememberScrollState())
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = "Update Available",
                        tint = Color(0xFF0D47A1),
                        modifier = Modifier.size(64.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = if (isForceUpdate) "Update Aplikasi Wajib" else "Update Aplikasi Tersedia",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF1E293B),
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = if (isForceUpdate) 
                            "Versi $versionName telah tersedia. Anda harus memperbarui aplikasi untuk dapat melanjutkan." 
                        else 
                            "Versi $versionName telah tersedia. Perbarui sekarang untuk fitur yang lebih baik.",
                        fontSize = 14.sp,
                        color = Color(0xFF64748B),
                        textAlign = TextAlign.Center
                    )
                    
                    if (releaseNotes.isNotBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            color = Color(0xFFF1F5F9),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = releaseNotes,
                                fontSize = 12.sp,
                                color = Color(0xFF334155),
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    if (isDownloading) {
                        CircularProgressIndicator(
                            color = Color(0xFF0D47A1),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Mengunduh...",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                    } else {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = onUpdateClick,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D47A1)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Update Sekarang", fontWeight = FontWeight.Bold, color = Color.White)
                            }
                            
                            if (!isForceUpdate) {
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = onIgnoreClick,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF64748B)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Nanti Saja", fontWeight = FontWeight.Bold)
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
