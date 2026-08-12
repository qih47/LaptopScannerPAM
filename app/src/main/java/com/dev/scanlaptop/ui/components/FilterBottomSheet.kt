package com.dev.scanlaptop.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Bottom sheet filter untuk Dashboard (status dan waktu).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterBottomSheet(
    statusFilter: String,
    timeFilter: String,
    navyColor: Color,
    sheetState: SheetState,
    onStatusChange: (String) -> Unit,
    onTimeChange: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .padding(bottom = 32.dp)
                .fillMaxWidth()
        ) {
            Text(
                "Filter Data",
                fontWeight = FontWeight.Black,
                fontSize = 20.sp,
                color = navyColor
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Filter Status
            Text(
                "Berdasarkan Status",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color(0xFF1E293B)
            )
            Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                listOf(
                    "ALL" to "Semua Status",
                    "IN" to "Masuk",
                    "OUT" to "Keluar"
                ).forEach { (code, labelText) ->
                    FilterChip(
                        selected = statusFilter == code,
                        onClick = { onStatusChange(code) },
                        label = {
                            Text(
                                labelText,
                                color = if (statusFilter == code) Color.White else Color(0xFF1E293B)
                            )
                        },
                        modifier = Modifier.padding(end = 8.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = navyColor,
                            selectedLabelColor = Color.White,
                            labelColor = Color(0xFF1E293B)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Filter Waktu
            Text(
                "Berdasarkan Waktu",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color(0xFF1E293B)
            )
            Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                listOf(
                    "ALL" to "Semua Waktu",
                    "TODAY" to "Hari Ini",
                    "WEEK" to "7 Hari",
                    "MONTH" to "1 Bulan"
                ).forEach { (code, labelText) ->
                    FilterChip(
                        selected = timeFilter == code,
                        onClick = { onTimeChange(code) },
                        label = {
                            Text(
                                labelText,
                                color = if (timeFilter == code) Color.White else Color(0xFF1E293B)
                            )
                        },
                        modifier = Modifier.padding(end = 8.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = navyColor,
                            selectedLabelColor = Color.White,
                            labelColor = Color(0xFF1E293B)
                        )
                    )
                }
            }
        }
    }
}
