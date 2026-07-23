package com.dev.scanlaptop.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Komponen Bottom Sheet untuk filter pada Overdue.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverdueFilterSheet(
    filterType: String,
    thresholdHours: Int,
    navyColor: Color,
    sheetState: SheetState,
    onFilterTypeChange: (String) -> Unit,
    onThresholdChange: (Int) -> Unit,
    onDismissRequest: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .padding(bottom = 32.dp)
                .fillMaxWidth()
        ) {
            Text(
                "Filter Data Mengendap",
                fontWeight = FontWeight.Black,
                fontSize = 20.sp,
                color = navyColor
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Filter Tipe
            Text(
                "Berdasarkan Tipe",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color(0xFF1E293B)
            )
            Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                listOf("ALL" to "Semua", "IN" to "Mengendap Masuk", "OUT" to "Mengendap Keluar").forEach { (status, label) ->
                    FilterChip(
                        selected = filterType == status,
                        onClick = { onFilterTypeChange(status) },
                        label = {
                            Text(
                                label,
                                fontSize = 12.sp,
                                color = if (filterType == status) Color.White else Color(0xFF1E293B)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = navyColor,
                            selectedLabelColor = Color.White
                        ),
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Filter Waktu (Threshold)
            Text(
                "Berdasarkan Batas Waktu",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color(0xFF1E293B)
            )
            Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                listOf(0 to "> 0 Jam (Test)", 4 to "> 4 Jam", 8 to "> 8 Jam", 24 to "> 24 Jam").forEach { (hours, label) ->
                    FilterChip(
                        selected = thresholdHours == hours,
                        onClick = { onThresholdChange(hours) },
                        label = {
                            Text(
                                label,
                                fontSize = 12.sp,
                                color = if (thresholdHours == hours) Color.White else Color(0xFF1E293B)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = navyColor,
                            selectedLabelColor = Color.White
                        ),
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }
        }
    }
}
