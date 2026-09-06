package com.iykyk.collage.pipeline

/**
 * Collage visual template styles.
 * Each template drives a completely different rendering mode inside [CollageGenerator].
 */
sealed class CollageTemplate(val displayName: String, val description: String) {

    /** Dark studio editorial — current default. Clean dark gradient, monospace labels. */
    data object Editorial : CollageTemplate("Editorial", "Dark studio look")

    /** White polaroid card style — off-white bg, subtle per-tile rotation, warmth. */
    data object Polaroid : CollageTemplate("Polaroid", "Vintage card style")

    /** Cinematic 16:9 widescreen tiles — letterbox bars, film-grain tint, bold title. */
    data object Cinematic : CollageTemplate("Cinematic", "Widescreen film style")

    /** Tight mosaic grid — no gutters, micro rounded corners, minimal overlay text. */
    data object Mosaic : CollageTemplate("Mosaic", "Tight packed grid")

    companion object {
        val all: List<CollageTemplate> = listOf(Editorial, Polaroid, Cinematic, Mosaic)
    }
}
