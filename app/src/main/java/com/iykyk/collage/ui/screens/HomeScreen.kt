package com.iykyk.collage.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iykyk.collage.ui.theme.*

/**
 * Opening Page: A soothing, editorial introduction to Moments.
 * Features ambient background lighting, delicate geometric motifs,
 * and balanced copy.
 */
@Composable
fun HomeScreen(onVideoPicked: (uri: Uri, label: String) -> Unit) {
    val context = LocalContext.current

    val visualMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            val label = queryDisplayName(context, uri)
            onVideoPicked(uri, label)
        }
    }

    val getContentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            val label = queryDisplayName(context, uri)
            onVideoPicked(uri, label)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        StudioBg,
                        Color(0xFF141624),
                        Color(0xFF18152B),
                        StudioBg,
                    )
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Decorative background design: subtle ambient glow & geometric rings
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerOffset = Offset(size.width / 2f, size.height * 0.35f)

            // Radiant ambient aura
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0x357C66DC),
                        Color(0x157C66DC),
                        Color.Transparent,
                    ),
                    center = centerOffset,
                    radius = size.width * 0.75f,
                ),
                center = centerOffset,
                radius = size.width * 0.75f,
            )

            // Faint concentric aesthetic rings
            drawCircle(
                color = Color(0x12FFFFFF),
                radius = size.width * 0.40f,
                center = centerOffset,
                style = Stroke(width = 1f),
            )
            drawCircle(
                color = Color(0x0AFFFFFF),
                radius = size.width * 0.62f,
                center = centerOffset,
                style = Stroke(width = 1f),
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(horizontal = 28.dp)
                .fillMaxWidth(),
        ) {
            // Calm Privacy Badge
            Surface(
                shape = RoundedCornerShape(50),
                color = StudioSurface.copy(alpha = 0.85f),
                border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorder),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981))
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "On-device AI • Completely private",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = StudioOnSurfaceMuted,
                    )
                }
            }

            Spacer(Modifier.height(34.dp))

            // Project Title
            Text(
                text = "Moments",
                fontSize = 52.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp,
                color = StudioOnSurface,
            )

            Spacer(Modifier.height(14.dp))

            // Metaphorical reflection
            Text(
                text = "Every face carries a story across time.",
                fontSize = 17.sp,
                fontWeight = FontWeight.Normal,
                fontFamily = FontFamily.Serif,
                color = StudioAccent,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(10.dp))

            // Instructive sentence
            Text(
                text = "Select any video to discover who's in it and compose an editorial portrait collection.",
                fontSize = 14.sp,
                color = StudioOnSurfaceMuted,
                textAlign = TextAlign.Center,
                lineHeight = 21.sp,
                modifier = Modifier.padding(horizontal = 12.dp),
            )

            Spacer(Modifier.height(44.dp))

            // Primary Soothing CTA with subtle gradient border
            Button(
                onClick = {
                    try {
                        visualMediaLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                        )
                    } catch (e: Exception) {
                        getContentLauncher.launch("video/*")
                    }
                },
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = StudioAccent,
                    contentColor = Color.White,
                ),
                modifier = Modifier
                    .height(58.dp)
                    .fillMaxWidth(0.85f),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp),
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "Select Video",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.2.sp,
                )
            }
        }
    }
}

private fun queryDisplayName(context: Context, uri: Uri): String {
    var name: String? = null
    if (uri.scheme == "content") {
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) name = cursor.getString(idx)
                }
            }
        } catch (ignored: Exception) {}
    }
    if (name == null) {
        name = uri.lastPathSegment?.substringAfterLast('/')
    }
    return name?.substringBeforeLast('.') ?: "Video ${System.currentTimeMillis() % 1000}"
}
