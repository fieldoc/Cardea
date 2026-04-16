package com.hrcoach.service.workout.notification

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.hrcoach.domain.model.ZoneStatus

/**
 * Draws the Cardea gradient HR badge to a square Bitmap.
 *
 * Every non-NO_DATA zone state keeps the full 4-stop Cardea palette
 * (red → pink → blue → cyan). Only stop positions shift — the badge
 * never becomes a solid colour alert object. Rim accent and badge
 * weighting together carry the zone signal through sweat.
 *
 * See docs/superpowers/specs/2026-04-14-lockscreen-media-notification-design.md
 * for the visual spec.
 */
class BadgeBitmapRenderer {

    /** Square bitmap dimension in pixels. 144px matches MediaStyle large-icon sweet spot. */
    private val size = 144

    private val cardeaRed = CARDEA_RED
    private val cardeaPink = CARDEA_PINK
    private val cardeaBlue = CARDEA_BLUE
    private val cardeaCyan = CARDEA_CYAN

    companion object {
        // Canonical source: ui/theme/Color.kt (GradientRed, GradientPink, GradientBlue, GradientCyan).
        // These are duplicated here as raw ints because BadgeBitmapRenderer is service-side
        // (non-Compose) and cannot import androidx.compose.ui.graphics.Color.
        // Keep these in sync with Color.kt — if the UI palette changes, update both places.
        private const val CARDEA_RED = 0xFFFF4D5A.toInt()
        private const val CARDEA_PINK = 0xFFFF2DA6.toInt()
        private const val CARDEA_BLUE = 0xFF4D61FF.toInt()
        private const val CARDEA_CYAN = 0xFF00E5FF.toInt()
    }

    fun render(
        currentHr: Int,
        zoneStatus: ZoneStatus,
        paused: Boolean,
    ): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val cornerRadius = size * 0.14f // ~20px on a 144px bitmap — matches the 14/74 ratio in the mockup
        val rect = RectF(0f, 0f, size.toFloat(), size.toFloat())

        // Clip to rounded rect so subsequent draws don't bleed past the corners
        val clipPath = Path().apply {
            addRoundRect(rect, cornerRadius, cornerRadius, Path.Direction.CW)
        }
        canvas.save()
        canvas.clipPath(clipPath)

        // 1. Base gradient
        drawGradient(canvas, rect, zoneStatus)

        // 2. Top sheen (subtle white radial highlight from top-centre)
        drawTopSheen(canvas, rect)

        // 3. Bottom shadow (subtle dark radial from bottom-centre)
        drawBottomShadow(canvas, rect)

        // 4. HR number + BPM label
        drawHrText(canvas, rect, currentHr, zoneStatus)

        // 5. Heart glyph top-right
        drawHeartGlyph(canvas, rect, paused)

        // 6. Rim accent (for ABOVE_ZONE / BELOW_ZONE only)
        if (!paused) drawRimAccent(canvas, rect, zoneStatus)

        canvas.restore()

        // 7. If paused, apply a global dark overlay on top
        if (paused) applyPausedOverlay(canvas, rect, cornerRadius)

        return bmp
    }

    /**
     * Full-size artwork for the MediaSession lockscreen player — 512×512 px.
     *
     * Larger than the 144px notification badge so Android's lockscreen media
     * controller fills its card at native resolution and palette extraction is
     * accurate. Design: same zone-driven gradient as the badge, but HR number
     * at 50 % of height (vs 38 % on the badge) and a small zone-label pill at
     * the bottom edge.
     */
    fun renderLockscreenArt(
        currentHr: Int,
        zoneStatus: ZoneStatus,
        paused: Boolean,
    ): Bitmap {
        val artSize = 512
        val bmp = Bitmap.createBitmap(artSize, artSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val rect = RectF(0f, 0f, artSize.toFloat(), artSize.toFloat())
        val cornerRadius = artSize * 0.12f  // 62px — slightly more rounded than the badge

        val clipPath = Path().apply {
            addRoundRect(rect, cornerRadius, cornerRadius, Path.Direction.CW)
        }
        canvas.save()
        canvas.clipPath(clipPath)

        drawGradient(canvas, rect, zoneStatus)
        drawTopSheen(canvas, rect)
        drawBottomShadow(canvas, rect)
        drawHeartGlyph(canvas, rect, paused)
        drawHrTextLarge(canvas, rect, currentHr, zoneStatus)
        drawZoneLabel(canvas, rect, zoneStatus, paused)
        if (!paused) drawRimAccent(canvas, rect, zoneStatus)

        canvas.restore()
        if (paused) applyPausedOverlay(canvas, rect, cornerRadius)

        return bmp
    }

    // ---------------------------------------------------------------------
    // Gradient
    // ---------------------------------------------------------------------

    private fun drawGradient(canvas: Canvas, rect: RectF, zone: ZoneStatus) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        if (zone == ZoneStatus.NO_DATA) {
            // Greyscale fallback
            val grey = LinearGradient(
                0f, 0f, rect.right, rect.bottom,
                intArrayOf(0xFF2A2A30.toInt(), 0xFF3A3A44.toInt(), 0xFF2A2A30.toInt()),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
            paint.shader = grey
            canvas.drawRect(rect, paint)
            return
        }

        // 135° diagonal (top-left → bottom-right)
        val colors: IntArray
        val positions: FloatArray
        when (zone) {
            ZoneStatus.BELOW_ZONE -> {
                // Cyan-dominant; red/pink only peek at the far top-left corner
                colors = intArrayOf(cardeaRed, cardeaPink, cardeaBlue, cardeaCyan, cardeaCyan)
                positions = floatArrayOf(0f, 0.12f, 0.38f, 0.82f, 1f)
            }
            ZoneStatus.IN_ZONE -> {
                colors = intArrayOf(cardeaRed, cardeaPink, cardeaBlue, cardeaCyan)
                positions = floatArrayOf(0f, 0.33f, 0.66f, 1f)
            }
            ZoneStatus.ABOVE_ZONE -> {
                // Red/pink dominant; blue/cyan still peek at the far bottom-right corner
                colors = intArrayOf(cardeaRed, cardeaRed, cardeaPink, cardeaBlue, cardeaCyan)
                positions = floatArrayOf(0f, 0.18f, 0.50f, 0.85f, 1f)
            }
            else -> {
                // Should be unreachable (NO_DATA handled above)
                colors = intArrayOf(cardeaRed, cardeaPink, cardeaBlue, cardeaCyan)
                positions = floatArrayOf(0f, 0.33f, 0.66f, 1f)
            }
        }

        paint.shader = LinearGradient(
            0f, 0f, rect.right, rect.bottom,
            colors, positions,
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(rect, paint)
    }

    // ---------------------------------------------------------------------
    // Sheen + shadow
    // ---------------------------------------------------------------------

    private fun drawTopSheen(canvas: Canvas, rect: RectF) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = RadialGradient(
            rect.centerX(), -rect.height() * 0.1f,
            rect.width() * 0.55f,
            intArrayOf(0x66FFFFFF, 0x00FFFFFF),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(rect, paint)
    }

    private fun drawBottomShadow(canvas: Canvas, rect: RectF) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = RadialGradient(
            rect.centerX(), rect.bottom + rect.height() * 0.1f,
            rect.width() * 0.4f,
            intArrayOf(0x40000000, 0x00000000),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(rect, paint)
    }

    // ---------------------------------------------------------------------
    // Text
    // ---------------------------------------------------------------------

    private fun drawHrText(canvas: Canvas, rect: RectF, currentHr: Int, zone: ZoneStatus) {
        val displayNum = if (currentHr <= 0 || zone == ZoneStatus.NO_DATA) "—" else currentHr.toString()

        val numberSize = rect.height() * 0.38f
        val cy = rect.centerY() + (numberSize / 3f) - rect.height() * 0.04f

        // Drop-shadow pass for the HR number: draw a soft black offset behind the text.
        // BlurMaskFilter works on software canvases (unlike setShadowLayer).
        val numberShadow = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            color = 0x66000000
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = numberSize
            maskFilter = BlurMaskFilter(2f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawText(displayNum, rect.centerX(), cy + 1f, numberShadow)

        val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = numberSize
        }
        canvas.drawText(displayNum, rect.centerX(), cy, numberPaint)

        // "BPM" label below — same shadow treatment
        val labelSize = rect.height() * 0.095f
        val labelYOffset = rect.height() * 0.16f

        val labelShadow = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            color = 0x66000000
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textSize = labelSize
            letterSpacing = 0.22f
            maskFilter = BlurMaskFilter(2f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawText("BPM", rect.centerX(), cy + labelYOffset + 1f, labelShadow)

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            color = 0xEBFFFFFF.toInt() // ~92% white
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textSize = labelSize
            letterSpacing = 0.22f
        }
        canvas.drawText("BPM", rect.centerX(), cy + labelYOffset, labelPaint)
    }

    // ---------------------------------------------------------------------
    // Heart glyph (top-right, ~11% size)
    // ---------------------------------------------------------------------

    private fun drawHeartGlyph(canvas: Canvas, rect: RectF, paused: Boolean) {
        val glyphSize = rect.width() * 0.11f
        val cx = rect.right - glyphSize * 1.3f
        val cy = rect.top + glyphSize * 0.9f

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            alpha = 178 // ~70%
        }

        if (paused) {
            // Replace heart with pause glyph (two small vertical bars)
            val barW = glyphSize * 0.22f
            val barH = glyphSize * 0.75f
            val gap = glyphSize * 0.12f
            val left1 = cx - gap - barW
            val top = cy - barH / 2f
            canvas.drawRect(left1, top, left1 + barW, top + barH, paint)
            canvas.drawRect(cx + gap, top, cx + gap + barW, top + barH, paint)
            return
        }

        // Simple two-lobed heart path
        val path = Path().apply {
            val half = glyphSize / 2f
            val topOffset = glyphSize * 0.28f
            moveTo(cx, cy + half)
            cubicTo(
                cx - glyphSize, cy - topOffset,
                cx - half, cy - glyphSize,
                cx, cy - topOffset
            )
            cubicTo(
                cx + half, cy - glyphSize,
                cx + glyphSize, cy - topOffset,
                cx, cy + half
            )
            close()
        }
        canvas.drawPath(path, paint)
    }

    // ---------------------------------------------------------------------
    // Rim accent
    // ---------------------------------------------------------------------

    private fun drawRimAccent(canvas: Canvas, rect: RectF, zone: ZoneStatus) {
        if (zone != ZoneStatus.ABOVE_ZONE && zone != ZoneStatus.BELOW_ZONE) return

        val rimColor = if (zone == ZoneStatus.ABOVE_ZONE) cardeaPink else cardeaCyan
        val inset = rect.width() * 0.14f
        val strokeWidth = rect.height() * 0.022f // ~3px on 144
        val blurRadius = rect.height() * 0.07f   // ~10px glow falloff per spec
        val y = if (zone == ZoneStatus.ABOVE_ZONE) {
            rect.top + strokeWidth
        } else {
            rect.bottom - strokeWidth
        }
        val x0 = rect.left + inset
        val x1 = rect.right - inset

        // Pass 1 — blurred glow underlay using BlurMaskFilter on the framework paint.
        // BlurMaskFilter works on software canvases (unlike setShadowLayer).
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = rimColor
            style = Paint.Style.STROKE
            this.strokeWidth = strokeWidth * 1.8f  // wider so the blur has material to spread
            strokeCap = Paint.Cap.ROUND
            maskFilter = BlurMaskFilter(blurRadius, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawLine(x0, y, x1, y, glowPaint)

        // Pass 2 — crisp line on top.
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = rimColor
            style = Paint.Style.STROKE
            this.strokeWidth = strokeWidth
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawLine(x0, y, x1, y, linePaint)
    }

    // ---------------------------------------------------------------------
    // Lockscreen art — HR text (larger scale) + zone label pill
    // ---------------------------------------------------------------------

    /** Like drawHrText but sized for the 512px lockscreen art (50 % vs 38 % of height). */
    private fun drawHrTextLarge(canvas: Canvas, rect: RectF, currentHr: Int, zone: ZoneStatus) {
        val displayNum = if (currentHr <= 0 || zone == ZoneStatus.NO_DATA) "—" else currentHr.toString()

        val numberSize = rect.height() * 0.50f
        val cy = rect.centerY() + (numberSize / 3f) - rect.height() * 0.04f

        val numberShadow = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            color = 0x66000000
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = numberSize
            maskFilter = BlurMaskFilter(4f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawText(displayNum, rect.centerX(), cy + 2f, numberShadow)

        val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = numberSize
        }
        canvas.drawText(displayNum, rect.centerX(), cy, numberPaint)

        val labelSize = rect.height() * 0.095f
        val labelYOffset = rect.height() * 0.18f

        val labelShadow = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            color = 0x66000000
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textSize = labelSize
            letterSpacing = 0.22f
            maskFilter = BlurMaskFilter(2f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawText("BPM", rect.centerX(), cy + labelYOffset + 1f, labelShadow)

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            color = 0xEBFFFFFF.toInt()
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textSize = labelSize
            letterSpacing = 0.22f
        }
        canvas.drawText("BPM", rect.centerX(), cy + labelYOffset, labelPaint)
    }

    /** Small pill label at the bottom edge of the lockscreen art showing zone / paused state. */
    private fun drawZoneLabel(canvas: Canvas, rect: RectF, zone: ZoneStatus, paused: Boolean) {
        val text = when {
            paused -> "PAUSED"
            zone == ZoneStatus.NO_DATA -> "ACQUIRING SIGNAL"
            zone == ZoneStatus.IN_ZONE -> "ON TARGET"
            zone == ZoneStatus.ABOVE_ZONE -> "ABOVE ZONE"
            zone == ZoneStatus.BELOW_ZONE -> "BELOW ZONE"
            else -> return
        }

        val textSize = rect.height() * 0.062f
        val textY = rect.bottom - rect.height() * 0.085f

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            this.textSize = textSize
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            letterSpacing = 0.10f
            textAlign = Paint.Align.CENTER
        }
        val bounds = Rect()
        textPaint.getTextBounds(text, 0, text.length, bounds)

        val pillPadH = textSize * 0.55f
        val pillPadV = textSize * 0.30f
        val pillRect = RectF(
            rect.centerX() - bounds.width() / 2f - pillPadH,
            textY - bounds.height() - pillPadV,
            rect.centerX() + bounds.width() / 2f + pillPadH,
            textY + pillPadV,
        )
        val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x40000000  // subtle dark backing
        }
        canvas.drawRoundRect(pillRect, textSize * 0.35f, textSize * 0.35f, pillPaint)

        textPaint.color = 0xCCFFFFFF.toInt()  // 80 % white
        canvas.drawText(text, rect.centerX(), textY, textPaint)
    }

    // ---------------------------------------------------------------------
    // Paused overlay — darken the whole badge
    // ---------------------------------------------------------------------

    private fun applyPausedOverlay(canvas: Canvas, rect: RectF, cornerRadius: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xB31A1A1F.toInt() // ~70% alpha over #1A1A1F
        }
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
    }
}
