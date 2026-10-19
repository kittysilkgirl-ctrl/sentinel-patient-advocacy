package com.advocacy.app

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

object SanctuaryColors {
    val Background = Color(0xFF13141E)       // Midnight Velvet
    val CardBg = Color(0xFF1D1F2D)           // Surface card
    val CardBorderCyan = Color(0xFF70E3FF)   // Neon Sky Cyan
    val CardBorderPink = Color(0xFFFF7EB3)   // Pastel Magenta
    val GlowPeach = Color(0xFFFFB399)        // Warm Peach
    val TextCream = Color(0xFFF5E6EF)        // Soft White Text
    val TextMuted = Color(0xFF94A3B8)        // Slate Neutral
    val SliderTrack = Color(0xFF282C40)
    val EmotionRed = Color(0xFFFF8B94)
    val EmotionOrange = Color(0xFFFFB385)
    val EmotionYellow = Color(0xFFFFE085)
    val EmotionGreen = Color(0xFF98E2C6)
    val EmotionCyan = Color(0xFF90E0EF)
}

data class TimelineItem(val time: String, val description: String)

@Composable
fun EmotionFace(
    emoji: String,
    label: String,
    tint: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(if (isSelected) tint.copy(alpha = 0.35f) else Color.Transparent)
                .border(
                    width = if (isSelected) 2.dp else 0.dp,
                    color = if (isSelected) tint else Color.Transparent,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(text = emoji, fontSize = 22.sp)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 9.sp,
            color = if (isSelected) tint else SanctuaryColors.TextMuted,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun PacerPulseImage(isListening: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "pacerPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Image(
        painter = painterResource(id = R.drawable.ic_soothing_pacer),
        contentDescription = "Breathing Pacer",
        modifier = Modifier
            .size(68.dp)
            .scale(if (isListening) pulseScale * 1.15f else pulseScale)
    )
}

@Composable
fun SlideToRestBar(onTriggerAlert: () -> Unit) {
    val barWidth = 320.dp
    val thumbWidth = 90.dp
    var offsetX by remember { mutableStateOf(0f) }

    Box(
        modifier = Modifier
            .width(barWidth)
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(SanctuaryColors.GlowPeach.copy(alpha = 0.3f))
            .border(1.dp, SanctuaryColors.GlowPeach.copy(alpha = 0.5f), RoundedCornerShape(28.dp)),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = "SLIDE TO REST",
            color = SanctuaryColors.GlowPeach,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .width(thumbWidth)
                .fillMaxHeight()
                .padding(4.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(SanctuaryColors.GlowPeach)
                .clickable { onTriggerAlert() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Alert Trigger",
                tint = SanctuaryColors.Background
            )
        }
    }
}

@Composable
fun BottomNavCapsule(
    onHome: () -> Unit,
    onJournal: () -> Unit,
    onPacer: () -> Unit,
    onDirectory: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(SanctuaryColors.CardBg)
            .padding(vertical = 10.dp, horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavButton(icon = Icons.Default.Home, label = "Comfort", isSelected = true, onClick = onHome)
            NavButton(icon = Icons.Default.Book, label = "Daily Cozy\nJournal", isSelected = false, onClick = onJournal)
            NavButton(icon = Icons.Default.VolumeUp, label = "Quiet\nPacer", isSelected = false, onClick = onPacer)
            NavButton(icon = Icons.Default.AccountBox, label = "Safe\nDirectory", isSelected = false, onClick = onDirectory)
        }
    }
}

@Composable
fun NavButton(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) SanctuaryColors.GlowPeach else SanctuaryColors.TextMuted,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = if (isSelected) SanctuaryColors.GlowPeach else SanctuaryColors.TextMuted,
            fontSize = 9.sp,
            textAlign = TextAlign.Center
        )
    }
}