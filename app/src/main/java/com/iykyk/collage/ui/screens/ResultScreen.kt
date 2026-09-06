package com.iykyk.collage.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.iykyk.collage.data.SaveShareUtil
import com.iykyk.collage.model.FaceObservation
import com.iykyk.collage.model.PersonCluster
import com.iykyk.collage.model.VideoResult
import com.iykyk.collage.pipeline.CollageTemplate
import com.iykyk.collage.ui.theme.*
import com.iykyk.collage.viewmodel.CollageViewModel
import com.iykyk.collage.viewmodel.SaveState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ResultScreen(
    result: VideoResult,
    viewModel: CollageViewModel = viewModel(),
    saveState: SaveState = SaveState.Idle,
    onSave: () -> Unit = {},
    onResetSaveState: () -> Unit = {},
    onProcessAnother: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val selectedTemplate by viewModel.selectedTemplate.collectAsState()
    val isRegenerating by viewModel.isRegenerating.collectAsState()

    LaunchedEffect(saveState) {
        if (saveState is SaveState.Saved) {
            delay(2500)
            onResetSaveState()
        } else if (saveState is SaveState.Failed) {
            Toast.makeText(context, saveState.message, Toast.LENGTH_LONG).show()
            onResetSaveState()
        }
    }

    val isSaved = saveState is SaveState.Saved
    val isSaving = saveState is SaveState.Saving

    val saveButtonBg by animateColorAsState(
        targetValue = if (isSaved) Color(0xFF10B981) else StudioAccent,
        animationSpec = tween(300),
        label = "saveBtnColor",
    )

    val maxAppearances = remember(result.people) {
        result.people.maxOfOrNull { it.appearanceCount }?.coerceAtLeast(1) ?: 1
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(StudioBg, Color(0xFF131522), StudioBg))
            )
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(vertical = 24.dp),
        ) {
            item {
                Text(
                    text = "Portrait Collection",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = StudioOnSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (result.people.isEmpty()) "No faces detected in video"
                    else "${result.people.size} people identified • ${result.people.sumOf { it.appearanceCount }} total moments",
                    fontSize = 14.sp,
                    color = StudioOnSurfaceMuted,
                )
                Spacer(Modifier.height(18.dp))

                // ── Collage Preview Card ─────────────────────────────────────────
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = StudioSurface,
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, StudioBorder, RoundedCornerShape(18.dp)),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Crossfade(
                            targetState = result.collageBitmap,
                            animationSpec = tween(400),
                            label = "collageCrossfade",
                        ) { bitmap ->
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Portrait collage",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp)),
                            )
                        }
                        if (isRegenerating) {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .background(Color(0x99000000), RoundedCornerShape(18.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    color = StudioAccent,
                                    modifier = Modifier.size(40.dp),
                                    strokeWidth = 3.dp,
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // ── Template Picker ──────────────────────────────────────────────
                Text(
                    text = "Style",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = StudioOnSurfaceMuted,
                    letterSpacing = 0.08.sp,
                )
                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CollageTemplate.all.forEach { template ->
                        val isSelected = selectedTemplate == template
                        val chipBg = if (isSelected) StudioAccent.copy(alpha = 0.18f) else StudioSurface
                        val chipBorder = if (isSelected) StudioAccent else StudioBorder
                        val textColor = if (isSelected) StudioAccent else StudioOnSurface

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = chipBg,
                            border = androidx.compose.foundation.BorderStroke(
                                if (isSelected) 1.5.dp else 1.dp, chipBorder
                            ),
                            modifier = Modifier
                                .clickable(enabled = !isRegenerating) {
                                    viewModel.changeTemplate(template)
                                },
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = template.displayName,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = textColor,
                                )
                                Text(
                                    text = template.description,
                                    fontSize = 10.sp,
                                    color = StudioOnSurfaceMuted,
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))

                // ── Action Buttons ───────────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = { if (!isSaving && !isSaved) onSave() },
                        colors = ButtonDefaults.buttonColors(containerColor = saveButtonBg),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) {
                        AnimatedContent(targetState = saveState, label = "saveContent") { state ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                when (state) {
                                    is SaveState.Saving -> {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            color = Color.White,
                                            strokeWidth = 2.dp,
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text("Saving...", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                    }
                                    is SaveState.Saved -> {
                                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Saved to Photos", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    }
                                    else -> Text("Save to Photos", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                }
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val uri = SaveShareUtil.prepareShareUri(context, result.collageBitmap)
                                val intent = Intent.createChooser(SaveShareUtil.shareIntent(uri), "Share portrait collection")
                                context.startActivity(intent)
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f).height(48.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorder),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = StudioOnSurface),
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Share", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    }
                }

                Spacer(Modifier.height(32.dp))

                if (result.people.isNotEmpty()) {
                    Text(
                        text = "Who's in the clip",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = StudioOnSurface,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${result.people.size} distinct people identified",
                        fontSize = 13.sp,
                        color = StudioOnSurfaceMuted,
                    )
                    Spacer(Modifier.height(14.dp))
                }
            }

            if (result.people.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = StudioSurface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorder),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("No distinct faces found", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = StudioOnSurface)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Please try another video where faces are clearly visible and well-lit.",
                                fontSize = 13.sp,
                                color = StudioOnSurfaceMuted,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            } else {
                val sorted = result.people.sortedByDescending { it.appearanceCount }
                itemsIndexed(sorted) { index, person ->
                    PersonCard(person = person, memberIndex = index + 1, maxAppearances = maxAppearances)
                    Spacer(Modifier.height(10.dp))
                }
            }

            item {
                Spacer(Modifier.height(18.dp))
                OutlinedButton(
                    onClick = onProcessAnother,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = StudioOnSurfaceMuted),
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Process another video", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(36.dp))
            }
        }
    }
}

@Composable
private fun PersonCard(
    person: PersonCluster,
    memberIndex: Int,
    maxAppearances: Int,
) {
    val faceThumbnail = remember(person.id) { cropFaceThumbnail(person.representative) }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = StudioSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.size(56.dp), contentAlignment = Alignment.BottomEnd) {
                if (faceThumbnail != null) {
                    Image(
                        bitmap = faceThumbnail.asImageBitmap(),
                        contentDescription = "Best shot of Person $memberIndex",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, StudioBorder, RoundedCornerShape(12.dp)),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp))
                            .background(StudioSurfaceElevated)
                            .border(1.dp, StudioBorder, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("$memberIndex", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = StudioOnSurfaceMuted)
                    }
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xCC111216),
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, StudioBorder),
                    modifier = Modifier.offset(x = 2.dp, y = 2.dp),
                ) {
                    Text(
                        text = "#$memberIndex",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = StudioOnSurface,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Person $memberIndex", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = StudioOnSurface)
                    Text(
                        "${person.appearanceCount} appearance${if (person.appearanceCount == 1) "" else "s"}",
                        fontSize = 12.sp, color = StudioOnSurfaceMuted,
                    )
                }

                Spacer(Modifier.height(7.dp))

                val presenceFraction = (person.appearanceCount.toFloat() / maxAppearances).coerceIn(0.1f, 1f)
                LinearProgressIndicator(
                    progress = presenceFraction,
                    modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                    color = StudioAccent,
                    trackColor = StudioBorder,
                )

                Spacer(Modifier.height(6.dp))

                val momentsPreview = person.appearances.take(3).joinToString(", ") { app -> formatTimestamp(app.startMs) }
                val extra = person.appearances.size - 3
                Text(
                    text = if (extra > 0) "Seen at $momentsPreview (+$extra more)" else "Seen at $momentsPreview",
                    fontSize = 11.sp,
                    color = StudioOnSurfaceMuted.copy(alpha = 0.8f),
                )
            }
        }
    }
}

private fun cropFaceThumbnail(observation: FaceObservation?): Bitmap? {
    if (observation == null) return null
    val frame = observation.frameBitmap
    if (frame.isRecycled) return null
    val box = observation.boundingBox
    val faceW = maxOf(1f, box.right - box.left)
    val faceH = maxOf(1f, box.bottom - box.top)
    val side = kotlin.math.min(
        frame.width.toFloat(),
        kotlin.math.min(frame.height.toFloat(), maxOf(faceW, faceH) * 1.4f),
    ).coerceAtLeast(16f)
    val cx = (box.left + box.right) / 2f
    val cy = (box.top + box.bottom) / 2f
    val left = (cx - side / 2f).coerceIn(0f, maxOf(0f, frame.width - side))
    val top = (cy - side / 2f).coerceIn(0f, maxOf(0f, frame.height - side))
    val sideInt = kotlin.math.min(side.toInt(), kotlin.math.min(frame.width - left.toInt(), frame.height - top.toInt())).coerceAtLeast(1)
    return try {
        val cropped = Bitmap.createBitmap(frame, left.toInt(), top.toInt(), sideInt, sideInt)
        val scaled = Bitmap.createScaledBitmap(cropped, 128, 128, true)
        if (cropped !== frame && cropped !== scaled && !cropped.isRecycled) {
            cropped.recycle()
        }
        scaled
    } catch (e: Exception) { null }
}

private fun formatTimestamp(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%d:%02d".format(min, sec)
}
