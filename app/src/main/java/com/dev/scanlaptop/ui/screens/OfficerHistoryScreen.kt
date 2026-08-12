package com.dev.scanlaptop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dev.scanlaptop.data.HistoryLog
import com.dev.scanlaptop.ui.components.HistoryCardPro
import com.dev.scanlaptop.ui.viewmodel.OfficerHistoryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfficerHistoryScreen(
    viewModel: OfficerHistoryViewModel,
    npp: String,
    onBack: () -> Unit,
    onItemClick: (HistoryLog) -> Unit
) {
    val logs by viewModel.logs.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    LaunchedEffect(npp) {
        viewModel.loadHistory(npp)
    }

    val navyColor = Color(0xFF0D47A1)

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier
                    .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF0D47A1), Color(0xFF001233)))),
                title = { Text("Riwayat Pindaian", fontWeight = FontWeight.ExtraBold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color(0xFFF1F5F9)
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = navyColor
                    )
                }
                errorMessage != null -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = errorMessage ?: "Terjadi kesalahan",
                            color = Color.Red,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { viewModel.loadHistory(npp) },
                            colors = ButtonDefaults.buttonColors(containerColor = navyColor)
                        ) {
                            Text("Coba Lagi")
                        }
                    }
                }
                logs.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "Belum ada riwayat pindaian.",
                            color = Color.Gray,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Text(
                                "Total Pindaian: ${logs.size}",
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp,
                                color = navyColor,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        items(logs, key = { it.uuid ?: it.hashCode() }) { log ->
                            HistoryCardPro(
                                log = log,
                                navyColor = navyColor,
                                onClick = { onItemClick(log) }
                            )
                        }
                    }
                }
            }
        }
    }
}
