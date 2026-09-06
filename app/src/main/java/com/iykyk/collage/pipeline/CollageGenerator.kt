package com.iykyk.collage.pipeline

import android.graphics.*
import com.iykyk.collage.model.PersonCluster
import kotlin.math.*

/**
 * Multi-template collage renderer.
 *
 * Templates:
 *  - [CollageTemplate.Editorial]  Dark studio matte, monospace labels (default)
 *  - [CollageTemplate.Polaroid]   Off-white card stack, warm tones, slight tile tilt
 *  - [CollageTemplate.Cinematic]  Black letterbox bars, 16:9 landscape crops, film palette
 *  - [CollageTemplate.Mosaic]     Tight zero-gutter grid, micro-rounded corners, minimal text
 */
object CollageGenerator {

    private const val CANVAS_W = 1080

    fun buildCollage(
        videoLabel: String,
        people: List<PersonCluster>,
        template: CollageTemplate = CollageTemplate.Editorial,
    ): Bitmap {
        if (people.isEmpty()) return buildEmptyCollage()
        val sorted = people.sortedByDescending { it.appearanceCount }
        return when (template) {
            is CollageTemplate.Editorial -> buildEditorial(sorted)
            is CollageTemplate.Polaroid  -> buildPolaroid(sorted)
            is CollageTemplate.Cinematic -> buildCinematic(sorted)
            is CollageTemplate.Mosaic    -> buildMosaic(sorted)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    //  Template 1 — Editorial (dark studio)
    // ─────────────────────────────────────────────────────────────────────────────

    private const val ED_MARGIN    = 48f
    private const val ED_GUTTER    = 18f
    private const val ED_CORNER    = 20f
    private const val ED_CROP      = 2.6f
    private const val ED_HEADER_H  = 154f
    private const val ED_FOOTER_H  = 84f

    private fun buildEditorial(people: List<PersonCluster>): Bitmap {
        val (placements, contentH) = edLayout(people, CANVAS_W.toFloat(), ED_HEADER_H, ED_MARGIN, ED_GUTTER)
        val totalH = (ED_HEADER_H + contentH + ED_FOOTER_H).toInt()
        val out = Bitmap.createBitmap(CANVAS_W, totalH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        // Background
        val bgGrad = LinearGradient(0f, 0f, 0f, totalH.toFloat(),
            intArrayOf(Color.parseColor("#0C0D13"), Color.parseColor("#151722"), Color.parseColor("#0F1017")),
            floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, CANVAS_W.toFloat(), totalH.toFloat(),
            Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = bgGrad })
        canvas.drawRect(18f, 18f, CANVAS_W - 18f, totalH - 18f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#1E202B"); strokeWidth = 1.5f; style = Paint.Style.STROKE })

        // Header
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F4F4F6"); textSize = 48f; letterSpacing = 0.14f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        canvas.drawText("MOMENTS", ED_MARGIN, ED_HEADER_H * 0.46f, titlePaint)
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#8E919E"); textSize = 21f; letterSpacing = 0.08f
        }
        val countTxt = "${people.size} ${if (people.size == 1) "UNIQUE SUBJECT" else "UNIQUE SUBJECTS"} DETECTED"
        canvas.drawText(countTxt, ED_MARGIN, ED_HEADER_H * 0.46f + 38f, subPaint)
        canvas.drawLine(ED_MARGIN, ED_HEADER_H - 6f, CANVAS_W - ED_MARGIN, ED_HEADER_H - 6f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#22242F"); strokeWidth = 1.5f; style = Paint.Style.STROKE })

        // Cells
        for ((person, rect, label) in placements) edCell(canvas, person, rect, label)

        // Footer
        val fy = totalH - ED_FOOTER_H + 10f
        canvas.drawLine(ED_MARGIN, fy, CANVAS_W - ED_MARGIN, fy,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#22242F"); strokeWidth = 1.5f; style = Paint.Style.STROKE })
        canvas.drawText("MOMENTS  •  ON-DEVICE VIDEO PORTRAIT COMPILATION",
            CANVAS_W / 2f, totalH - 34f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#6C6F7D"); textSize = 17f; letterSpacing = 0.16f
                textAlign = Paint.Align.CENTER
            })

        return out
    }

    private data class CellPlacement(val person: PersonCluster, val rect: RectF, val label: String)

    private fun edLayout(
        people: List<PersonCluster>,
        cw: Float, headerH: Float, mx: Float, g: Float,
    ): Pair<List<CellPlacement>, Float> {
        val uw = cw - mx * 2
        val placements = mutableListOf<CellPlacement>()
        val startY = headerH + 10f
        var cy = startY

        fun add(i: Int, rect: RectF) =
            placements.add(CellPlacement(people[i], rect, if (i + 1 < 10) "0${i+1}" else "${i+1}"))

        when (people.size) {
            1 -> {
                val tw = min(uw, 740f); val th = tw * 1.15f
                val lf = (cw - tw) / 2f
                add(0, RectF(lf, cy, lf + tw, cy + th)); cy += th + 16f
            }
            2 -> {
                val tw = (uw - g) / 2f; val th = tw * 1.25f
                for (i in 0..1) { val lf = mx + i * (tw + g); add(i, RectF(lf, cy, lf + tw, cy + th)) }
                cy += th + 16f
            }
            3 -> {
                val hw = uw * 0.72f; val hh = hw * 0.92f
                val hl = (cw - hw) / 2f
                add(0, RectF(hl, cy, hl + hw, cy + hh)); cy += hh + g
                val sw = (uw - g) / 2f; val sh = sw * 1.10f
                for (i in 0..1) { val lf = mx + i * (sw + g); add(i+1, RectF(lf, cy, lf + sw, cy + sh)) }
                cy += sh + 16f
            }
            4 -> {
                val tw = (uw - g) / 2f; val th = tw * 1.12f
                for ((idx, _) in people.withIndex()) {
                    val c = idx % 2; val r = idx / 2
                    val lf = mx + c * (tw + g); val tp = startY + r * (th + g)
                    add(idx, RectF(lf, tp, lf + tw, tp + th))
                }
                cy = startY + 2 * th + g + 16f
            }
            5 -> {
                val rw1 = (uw - g) / 2f; val rh1 = rw1 * 1.08f
                for (i in 0..1) { val lf = mx + i * (rw1 + g); add(i, RectF(lf, cy, lf + rw1, cy + rh1)) }
                cy += rh1 + g
                val rw2 = (uw - g * 2) / 3f; val rh2 = rw2 * 1.18f
                for (i in 0..2) { val lf = mx + i * (rw2 + g); add(i+2, RectF(lf, cy, lf + rw2, cy + rh2)) }
                cy += rh2 + 16f
            }
            else -> {
                val cols = 3; val tw = (uw - g * (cols - 1)) / cols; val th = tw * 1.16f
                val rows = ceil(people.size.toDouble() / cols).toInt()
                for ((idx, _) in people.withIndex()) {
                    val c = idx % cols; val r = idx / cols
                    val lf = mx + c * (tw + g); val tp = startY + r * (th + g)
                    add(idx, RectF(lf, tp, lf + tw, tp + th))
                }
                cy = startY + rows * th + (rows - 1) * g + 16f
            }
        }
        return placements to (cy - startY)
    }

    private fun edCell(canvas: Canvas, person: PersonCluster, rect: RectF, label: String) {
        val rep = person.representative ?: return
        val crop = generousCrop(rep.frameBitmap, rep.boundingBox, ED_CROP)
        val scaled = Bitmap.createScaledBitmap(crop, rect.width().toInt(), rect.height().toInt(), true)

        val path = Path().apply { addRoundRect(rect, ED_CORNER, ED_CORNER, Path.Direction.CW) }
        canvas.save(); canvas.clipPath(path)
        canvas.drawBitmap(scaled, rect.left, rect.top, null)

        // Bottom scrim
        val scrimH = rect.height() * 0.32f
        canvas.drawRect(rect.left, rect.bottom - scrimH, rect.right, rect.bottom,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(0f, rect.bottom - scrimH, 0f, rect.bottom,
                    Color.TRANSPARENT, Color.parseColor("#CC08090E"), Shader.TileMode.CLAMP) })
        canvas.restore()

        // Border
        canvas.drawRoundRect(rect, ED_CORNER, ED_CORNER,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = 1.5f; color = Color.parseColor("#343644") })

        // Info pill
        val count = person.appearanceCount
        val info = "$label  •  $count ${if (count == 1) "MOMENT" else "MOMENTS"}"
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E4E4E7")
            textSize = (rect.width() * 0.055f).coerceIn(14f, 19f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            letterSpacing = 0.06f
        }
        val tw = tp.measureText(info); val ph = 10f; val pv = 5f
        val pillH = tp.textSize + pv * 2; val pillW = tw + ph * 2
        val pl = rect.left + 12f; val pb = rect.bottom - 12f; val pt = pb - pillH
        val pr = RectF(pl, pt, pl + pillW, pb)
        canvas.drawRoundRect(pr, 6f, 6f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#B30C0D12") })
        canvas.drawRoundRect(pr, 6f, 6f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#3A3C49"); style = Paint.Style.STROKE; strokeWidth = 1f })
        canvas.drawText(info, pl + ph, pb - pv - 2f, tp)

        safeRecycle(scaled, crop, rep.frameBitmap)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    //  Template 2 — Polaroid (off-white card stack)
    // ─────────────────────────────────────────────────────────────────────────────

    private fun buildPolaroid(people: List<PersonCluster>): Bitmap {
        val cols = if (people.size <= 2) people.size else if (people.size <= 6) 2 else 3
        val rows = ceil(people.size.toDouble() / cols).toInt()
        val cardW = (CANVAS_W - 80f - (cols - 1) * 24f) / cols
        val photoH = cardW * 1.1f
        val captionH = 56f
        val cardH = photoH + captionH
        val totalH = (rows * (cardH + 24f) + 120f).toInt()

        val out = Bitmap.createBitmap(CANVAS_W, totalH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        // Warm cream background
        canvas.drawColor(Color.parseColor("#F0EBE1"))
        // Subtle texture dots
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#DDD7CC"); style = Paint.Style.FILL }
        for (dy in 0..totalH step 18) for (dx in 0..CANVAS_W step 18)
            canvas.drawCircle(dx.toFloat(), dy.toFloat(), 1f, dotPaint)

        // Title
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#2C2420"); textSize = 44f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Moments", CANVAS_W / 2f, 68f, titlePaint)
        canvas.drawText("${people.size} people  •  ${people.sumOf { it.appearanceCount }} moments",
            CANVAS_W / 2f, 100f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#8B7355"); textSize = 20f; textAlign = Paint.Align.CENTER })

        val startY = 118f
        for ((idx, person) in people.withIndex()) {
            val c = idx % cols; val r = idx / cols
            val cx = 40f + c * (cardW + 24f)
            val cy = startY + r * (cardH + 24f)

            // Soft drop shadow
            canvas.drawRoundRect(RectF(cx + 4f, cy + 4f, cx + cardW + 4f, cy + cardH + 4f),
                6f, 6f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#33000000") })

            // Card
            canvas.drawRoundRect(RectF(cx, cy, cx + cardW, cy + cardH),
                6f, 6f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })

            // Photo
            val rep = person.representative
            if (rep != null) {
                val crop = generousCrop(rep.frameBitmap, rep.boundingBox, 2.2f)
                val scaled = Bitmap.createScaledBitmap(crop, cardW.toInt(), photoH.toInt(), true)
                val photoRect = RectF(cx, cy, cx + cardW, cy + photoH)
                val photoPath = Path().apply {
                    addRoundRect(photoRect, floatArrayOf(6f, 6f, 6f, 6f, 0f, 0f, 0f, 0f), Path.Direction.CW)
                }
                canvas.save(); canvas.clipPath(photoPath)
                canvas.drawBitmap(scaled, cx, cy, null)
                canvas.restore()
                safeRecycle(scaled, crop, rep.frameBitmap)
            }

            // Caption area
            val capY = cy + photoH
            canvas.drawText(
                "#${idx + 1}   ${person.appearanceCount}×",
                cx + cardW / 2f, capY + 34f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#5C4D3C"); textSize = 18f; textAlign = Paint.Align.CENTER
                    typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
                }
            )
        }
        return out
    }

    // ─────────────────────────────────────────────────────────────────────────────
    //  Template 3 — Cinematic (16:9 letterbox)
    // ─────────────────────────────────────────────────────────────────────────────

    private fun buildCinematic(people: List<PersonCluster>): Bitmap {
        val tileW = CANVAS_W.toFloat()
        val tileH = tileW * 9f / 16f   // 16:9
        val barH = tileH * 0.12f       // letterbox bar height
        val totalH = (people.size * (tileH + 8f) + 100f).toInt()

        val out = Bitmap.createBitmap(CANVAS_W, totalH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.BLACK)

        // Title
        canvas.drawText("MOMENTS",
            CANVAS_W / 2f, 60f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#E8E0D0"); textSize = 42f; textAlign = Paint.Align.CENTER
                letterSpacing = 0.3f; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            })
        canvas.drawText("${people.size} PORTRAITS  —  SCENE ANALYSIS",
            CANVAS_W / 2f, 88f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#6B5E4E"); textSize = 17f; textAlign = Paint.Align.CENTER; letterSpacing = 0.2f })

        var curY = 100f
        for ((idx, person) in people.withIndex()) {
            val rep = person.representative
            if (rep != null) {
                val crop = generousCrop(rep.frameBitmap, rep.boundingBox, 3.0f)
                val scaled = Bitmap.createScaledBitmap(crop, tileW.toInt(), tileH.toInt(), true)

                // Mild film-grain tint (sepia-ish overlay at 12% opacity)
                canvas.drawBitmap(scaled, 0f, curY, null)
                canvas.drawRect(0f, curY, tileW, curY + tileH,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1F8B7355") })

                safeRecycle(scaled, crop, rep.frameBitmap)
            } else {
                canvas.drawRect(0f, curY, tileW, curY + tileH,
                    Paint().apply { color = Color.parseColor("#1A1A1A") })
            }

            // Letterbox bars
            val barPaint = Paint().apply { color = Color.BLACK }
            canvas.drawRect(0f, curY, tileW, curY + barH, barPaint)
            canvas.drawRect(0f, curY + tileH - barH, tileW, curY + tileH, barPaint)

            // Person label in bottom bar
            val count = person.appearanceCount
            canvas.drawText(
                "${(idx + 1).toString().padStart(2, '0')}   ${count} SCENE${if (count != 1) "S" else ""}",
                40f, curY + tileH - barH / 2f + 7f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#E8E0D0"); textSize = 20f
                    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD); letterSpacing = 0.1f
                }
            )

            // Thin horizontal rule
            curY += tileH
            canvas.drawRect(0f, curY, tileW, curY + 8f, Paint().apply { color = Color.BLACK })
            curY += 8f
        }
        return out
    }

    // ─────────────────────────────────────────────────────────────────────────────
    //  Template 4 — Mosaic (tight zero-gutter grid)
    // ─────────────────────────────────────────────────────────────────────────────

    private fun buildMosaic(people: List<PersonCluster>): Bitmap {
        val cols = when {
            people.size <= 2 -> people.size
            people.size <= 4 -> 2
            people.size <= 9 -> 3
            else -> 4
        }
        val rows = ceil(people.size.toDouble() / cols).toInt()
        val tileW = CANVAS_W / cols
        val tileH = tileW
        val headerH = 80
        val totalH = rows * tileH + headerH

        val out = Bitmap.createBitmap(CANVAS_W, totalH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.parseColor("#0A0A0A"))

        // Compact header
        canvas.drawText("MOMENTS",
            CANVAS_W / 2f, 48f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE; textSize = 32f; textAlign = Paint.Align.CENTER; letterSpacing = 0.25f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            })

        for ((idx, person) in people.withIndex()) {
            val c = idx % cols; val r = idx / cols
            val left = (c * tileW).toFloat()
            val top = (headerH + r * tileH).toFloat()
            val right = left + tileW; val bottom = top + tileH

            val rep = person.representative
            if (rep != null) {
                val crop = generousCrop(rep.frameBitmap, rep.boundingBox, 2.4f)
                val scaled = Bitmap.createScaledBitmap(crop, tileW, tileH, true)

                // 3px black gutter between tiles via clipping inset
                val path = Path().apply {
                    addRoundRect(RectF(left + 1.5f, top + 1.5f, right - 1.5f, bottom - 1.5f),
                        4f, 4f, Path.Direction.CW)
                }
                canvas.save(); canvas.clipPath(path)
                canvas.drawBitmap(scaled, left, top, null)
                canvas.restore()
                safeRecycle(scaled, crop, rep.frameBitmap)
            } else {
                canvas.drawRect(left, top, right, bottom,
                    Paint().apply { color = Color.parseColor("#1A1A1A") })
            }

            // Tiny index badge (top-left corner)
            canvas.drawText("#${idx + 1}",
                left + 10f, top + 22f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE; textSize = 16f
                    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                    setShadowLayer(4f, 1f, 1f, Color.parseColor("#CC000000"))
                })
        }
        return out
    }

    // ─────────────────────────────────────────────────────────────────────────────
    //  Shared utilities
    // ─────────────────────────────────────────────────────────────────────────────

    private fun generousCrop(frame: Bitmap, box: RectF, factor: Float): Bitmap {
        if (frame.isRecycled) return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val cx = (box.left + box.right) / 2f
        val cy = (box.top + box.bottom) / 2f
        val faceW = max(1f, box.right - box.left)
        val faceH = max(1f, box.bottom - box.top)
        val side = min(
            min(frame.width.toFloat(), frame.height.toFloat()),
            max(faceW, faceH) * factor,
        ).coerceAtLeast(16f)
        val left = (cx - side / 2f).coerceIn(0f, max(0f, frame.width - side))
        val top  = (cy - side / 2f).coerceIn(0f, max(0f, frame.height - side))
        val sideInt = min(side.toInt(), min(frame.width - left.toInt(), frame.height - top.toInt())).coerceAtLeast(1)
        return try {
            Bitmap.createBitmap(frame, left.toInt(), top.toInt(), sideInt, sideInt)
        } catch (e: Exception) {
            Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        }
    }

    private fun safeRecycle(scaled: Bitmap?, crop: Bitmap?, repFrame: Bitmap?) {
        if (scaled != null && scaled !== crop && scaled !== repFrame && !scaled.isRecycled) {
            scaled.recycle()
        }
        if (crop != null && crop !== repFrame && !crop.isRecycled) {
            crop.recycle()
        }
    }

    private fun buildEmptyCollage(): Bitmap {
        val out = Bitmap.createBitmap(CANVAS_W, 600, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.parseColor("#0C0D13"))
        canvas.drawText("No faces detected in video", CANVAS_W / 2f, 300f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#A1A1AA"); textSize = 32f; textAlign = Paint.Align.CENTER })
        return out
    }
}
