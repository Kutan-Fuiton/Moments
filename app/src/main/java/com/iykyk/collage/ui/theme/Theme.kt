package com.iykyk.collage.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Soothing, calm studio palette
val StudioBg = Color(0xFF0E0F14)
val StudioSurface = Color(0xFF16171F)
val StudioSurfaceElevated = Color(0xFF20222B)
val StudioBorder = Color(0xFF282A36)
val StudioAccent = Color(0xFF7C66DC) // soothing calm violet-indigo
val StudioAccentMuted = Color(0xFF26223B)
val StudioOnSurface = Color(0xFFF4F4F6)
val StudioOnSurfaceMuted = Color(0xFFA1A1AA)

// Backward-compatible aliases
val IykykPurple = StudioAccent
val IykykMagenta = StudioAccent
val IykykBg = StudioBg
val IykykSurface = StudioSurface
val IykykOnSurface = StudioOnSurface

private val DarkColors = darkColorScheme(
    primary = StudioAccent,
    secondary = StudioAccentMuted,
    background = StudioBg,
    surface = StudioSurface,
    onBackground = StudioOnSurface,
    onSurface = StudioOnSurface,
)

@Composable
fun IykykCollageTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = MaterialTheme.typography,
        content = content,
    )
}
