package com.dev.scanlaptop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun WhatsNewDialog(
    versionName: String,
    releaseNotes: String,
    onDismiss: () -> Unit
) {
    val navyColor = Color(0xFF0F172A)
    val accentBlue = Color(0xFF2563EB)
    val bgSoftBlue = Color(0xFFEFF6FF)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
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
                    .fillMaxWidth(0.88f)
                    .wrapContentHeight(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Celebration / New Release Icon Header
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(bgSoftBlue),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.NewReleases,
                            contentDescription = "What's New",
                            tint = accentBlue,
                            modifier = Modifier.size(38.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Badge Versi
                    Surface(
                        color = bgSoftBlue,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "UPDATE TERBARU • v$versionName",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentBlue,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Apa yang Baru di Versi Ini?",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = navyColor,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Scrollable Release Notes
                    Surface(
                        color = Color(0xFFF8FAFC),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val lines = releaseNotes.split("\n").filter { it.isNotBlank() }
                            if (lines.isNotEmpty()) {
                                lines.forEach { line ->
                                    val cleanedText = line.removePrefix("- ").removePrefix("* ").trim()
                                    Row(
                                        verticalAlignment = Alignment.Top,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = Color(0xFF10B981),
                                            modifier = Modifier
                                                .size(18.dp)
                                                .padding(top = 2.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = cleanedText,
                                            fontSize = 13.sp,
                                            color = Color(0xFF334155),
                                            lineHeight = 18.sp
                                        )
                                    }
                                }
                            } else {
                                Text(
                                    text = "Aplikasi telah diperbarui dengan performa dan kestabilan yang lebih baik.",
                                    fontSize = 13.sp,
                                    color = Color(0xFF334155),
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Tombol Aksi
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accentBlue)
                    ) {
                        Text(
                            text = "Lanjutkan!",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
