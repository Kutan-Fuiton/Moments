package com.iykyk.collage

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iykyk.collage.ui.screens.HomeScreen
import com.iykyk.collage.ui.screens.ProcessingScreen
import com.iykyk.collage.ui.screens.ResultScreen
import com.iykyk.collage.ui.theme.IykykBg
import com.iykyk.collage.ui.theme.IykykCollageTheme
import com.iykyk.collage.ui.theme.IykykMagenta
import com.iykyk.collage.ui.theme.IykykPurple
import com.iykyk.collage.viewmodel.CollageViewModel
import com.iykyk.collage.viewmodel.UiState

class MainActivity : ComponentActivity() {

    private val viewModel: CollageViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            IykykCollageTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CollageMainScreen(viewModel)
                }
            }
        }
    }
}

@Composable
private fun CollageMainScreen(viewModel: CollageViewModel) {
    val state by viewModel.uiState.collectAsState()
    val saveState by viewModel.saveState.collectAsState()

    when (val s = state) {
        is UiState.Idle -> HomeScreen { uri, label -> viewModel.processVideo(uri, label) }
        is UiState.Processing -> ProcessingScreen(
            progress = s.progress,
            videoLabel = s.videoLabel,
        )
        is UiState.Result -> ResultScreen(
            result = s.result,
            viewModel = viewModel,
            saveState = saveState,
            onSave = { viewModel.saveCollage(s.result.collageBitmap) },
            onResetSaveState = { viewModel.resetSaveState() },
            onProcessAnother = { viewModel.reset() },
        )
        is UiState.Error -> FriendlyErrorScreen(rawMessage = s.message) { viewModel.reset() }
    }
}

@Composable
private fun FriendlyErrorScreen(rawMessage: String, onRetry: () -> Unit) {
    val (title, description) = when {
        rawMessage.contains("model", ignoreCase = true) ||
        rawMessage.contains("tflite", ignoreCase = true) ||
        rawMessage.contains("interpreter", ignoreCase = true) -> {
            "AI Model Notice" to "Unable to initialize the face recognition engine. Please verify device storage and restart the app."
        }
        rawMessage.contains("frame", ignoreCase = true) ||
        rawMessage.contains("short", ignoreCase = true) ||
        rawMessage.contains("duration", ignoreCase = true) -> {
            "Video Not Readable" to "We couldn't extract usable frames from this video. Please select a clear video clip (at least 3-5 seconds)."
        }
        rawMessage.contains("face", ignoreCase = true) -> {
            "No Faces Detected" to "No clear faces were detected in this clip. Try a video with better lighting and front-facing views."
        }
        else -> {
            "Unable to Process" to "Something unexpected occurred while analyzing your video. Please try with another video clip."
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(IykykBg, IykykPurple.copy(alpha = 0.3f), IykykBg)
                )
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0x3320123A)),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(24.dp)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(IykykMagenta.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        tint = IykykMagenta,
                        modifier = Modifier.size(32.dp),
                    )
                }

                Spacer(Modifier.height(20.dp))

                Text(
                    text = title,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = description,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp,
                )

                Spacer(Modifier.height(28.dp))

                Button(
                    onClick = onRetry,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = IykykMagenta),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Try another video", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
