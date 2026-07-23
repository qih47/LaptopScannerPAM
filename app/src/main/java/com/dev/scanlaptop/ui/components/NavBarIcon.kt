package com.dev.scanlaptop.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Item navigasi di bottom bar dengan animasi pill dan efek scale.
 */
@Composable
fun NavBarIcon(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    navyColor: Color,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Animasi warna ikon dan teks
    val targetColor = if (isSelected) navyColor else Color(0xFF64748B)
    val animatedColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = 300)
    )

    // Animasi scale (membal/spring) saat ditekan atau aktif
    val targetScale = if (isPressed) 0.85f else if (isSelected) 1.05f else 1f
    val animatedScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    )

    // Animasi ukuran Pill background (hanya lebar yang berubah untuk efek pop)
    val targetPillWidth = if (isSelected) 64.dp else 32.dp
    val pillColor = if (isSelected) navyColor.copy(alpha = 0.15f) else Color.Transparent

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null, // Hilangkan ripple default
                onClick = onClick
            )
            .padding(vertical = 4.dp)
            .scale(animatedScale) // Aplikasikan scale ke keseluruhan elemen
    ) {
        // Icon dengan Pill Background
        Box(
            modifier = Modifier
                .width(targetPillWidth) // Tidak dianimasikan width-nya biar gak patah2 layout, cukup visualnya
                .height(32.dp)
                .background(pillColor, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = animatedColor,
                modifier = Modifier.size(24.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = label,
            fontSize = 11.sp,
            color = animatedColor,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}
