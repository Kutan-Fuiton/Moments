package com.iykyk.collage.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iykyk.collage.model.ProcessingProgress
import com.iykyk.collage.model.ProcessingStage
import com.iykyk.collage.ui.theme.IykykBg
import com.iykyk.collage.ui.theme.IykykMagenta
import com.iykyk.collage.ui.theme.IykykPurple

private val STAGE_ORDER = listOf(
    ProcessingStage.ExtractingFrames,
    ProcessingStage.DetectingFaces,
    ProcessingStage.Clustering,
    ProcessingStage.SelectingShots,
    ProcessingStage.BuildingCollage,
)

/**
 * Shows clear, honest per-stage progress (not just a spinner) — the assignment explicitly
 * asks for this. Each stage lights up as it's reached and the active one shows a live
 * frame counter (e.g. "Detecting & embedding faces — 84 / 150").
 */
@Composable
fun ProcessingScreen(progress: ProcessingProgress, videoLabel: String) {
    val activeIndex = STAGE_ORDER.indexOfFirst { it::class == progress.stage::class }
    val animatedFraction by animateFloatAsState(
        targetValue = progress.fraction,
        animationSpec = tween(250),
        label = "progressFraction",
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAlpha",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(IykykBg, IykykPurple.copy(alpha = 0.25f), IykykBg))
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0x331E1035)),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(24.dp))
                .padding(2.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
            ) {
                Text(
                    text = "Analyzing Video",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = IykykMagenta,
                    letterSpacing = 1.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = videoLabel,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )

                Spacer(Modifier.height(28.dp))

                STAGE_ORDER.forEachIndexed { index, stage ->
                    val isActive = index == activeIndex
                    val isDone = (activeIndex > index) || (progress.stage is ProcessingStage.Done)
                    StageRow(
                        label = stage.label,
                        isActive = isActive,
                        isDone = isDone,
                        pulseAlpha = pulseAlpha,
                        current = if (isActive) progress.current else 0,
                        total = if (isActive) progress.total else 0,
                    )
                    if (index < STAGE_ORDER.lastIndex) {
                        Spacer(Modifier.height(18.dp))
                    }
                }

                if (activeIndex == 1 && progress.total > 0) {
                    Spacer(Modifier.height(24.dp))
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                "Scanning moments",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            )
                            Text(
                                "${(animatedFraction * 100).toInt()}%",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = IykykMagenta,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { animatedFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = IykykMagenta,
                            trackColor = Color(0x33FFFFFF),
                        )
                    }
                }

                Spacer(Modifier.height(28.dp))

                HorizontalDivider(color = Color(0x1FFFFFFF), thickness = 1.dp)

                Spacer(Modifier.height(14.dp))

                Text(
                    text = "🔒 100% on-device • Never leaves your phone",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun StageRow(
    label: String,
    isActive: Boolean,
    isDone: Boolean,
    pulseAlpha: Float,
    current: Int,
    total: Int,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(50))
                .background(
                    when {
                        isDone -> IykykMagenta
                        isActive -> IykykMagenta.copy(alpha = pulseAlpha)
                        else -> Color(0x22FFFFFF)
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (isDone) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            } else if (isActive) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White)
                )
            }
        }

        Spacer(Modifier.width(14.dp))

        Text(
            text = if (isActive && total > 0) "$label — $current / $total" else label,
            fontSize = 15.sp,
            fontWeight = if (isActive) FontWeight.Bold else if (isDone) FontWeight.Medium else FontWeight.Normal,
            color = when {
                isDone -> MaterialTheme.colorScheme.onBackground
                isActive -> Color.White
                else -> MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f)
            },
        )
    }
}
