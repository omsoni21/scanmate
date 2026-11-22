package com.example.data.processing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import com.example.domain.model.DocumentFilter
import com.example.domain.model.ImageQuality
import com.example.domain.model.QuadPoints
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Pure Android native offline image processor:
 * - Perspective warp via Matrix.setPolyToPoly
 * - Rotation
 * - 7 Document filters (including adaptive local thresholding & document whitening)
 * - Brightness, Contrast, and 3x3 Unsharp-Mask Sharpness adjustments
 */
object ImageProcessor {

    suspend fun processFullPipeline(
        originalBitmap: Bitmap,
        quad: QuadPoints,
        rotationDegrees: Int,
        filter: DocumentFilter,
        brightness: Float,
        contrast: Float,
        sharpness: Float,
        quality: ImageQuality,
        maxDimensionOverridePx: Int? = null,
        enhancementIntensity: Float = 1.0f
    ): Bitmap = withContext(Dispatchers.Default) {
        val warped = warpPerspectiveAndRotate(
            source = originalBitmap,
            quad = quad,
            rotationDegrees = rotationDegrees,
            quality = quality,
            maxDimensionOverridePx = maxDimensionOverridePx
        )
        val filtered = applyEnhancement(
            source = warped,
            filter = filter,
            brightness = brightness,
            contrast = contrast,
            sharpness = sharpness,
            enhancementIntensity = enhancementIntensity
        )
        if (filtered != warped) warped.recycle()
        filtered
    }

    fun warpPerspectiveAndRotate(
        source: Bitmap,
        quad: QuadPoints,
        rotationDegrees: Int,
        quality: ImageQuality,
        maxDimensionOverridePx: Int? = null
    ): Bitmap {
        val w = source.width.toFloat()
        val h = source.height.toFloat()

        val tlx = quad.topLeft.x * w
        val tly = quad.topLeft.y * h
        val trx = quad.topRight.x * w
        val try_ = quad.topRight.y * h
        val brx = quad.bottomRight.x * w
        val bry = quad.bottomRight.y * h
        val blx = quad.bottomLeft.x * w
        val bly = quad.bottomLeft.y * h

        val topEdge = hypot((trx - tlx).toDouble(), (try_ - tly).toDouble()).toFloat()
        val bottomEdge = hypot((brx - blx).toDouble(), (bry - bly).toDouble()).toFloat()
        val leftEdge = hypot((blx - tlx).toDouble(), (bly - tly).toDouble()).toFloat()
        val rightEdge = hypot((brx - trx).toDouble(), (bry - try_).toDouble()).toFloat()

        var outW = max(topEdge, bottomEdge).roundToInt().coerceAtLeast(120)
        var outH = max(leftEdge, rightEdge).roundToInt().coerceAtLeast(120)

        val maxSide = maxDimensionOverridePx?.coerceAtLeast(240) ?: quality.maxDimensionPx
        if (max(outW, outH) > maxSide) {
            val ratio = maxSide.toFloat() / max(outW, outH)
            outW = max(120, (outW * ratio).roundToInt())
            outH = max(120, (outH * ratio).roundToInt())
        }

        val srcPoly = floatArrayOf(tlx, tly, trx, try_, brx, bry, blx, bly)
        val dstPoly = floatArrayOf(0f, 0f, outW.toFloat(), 0f, outW.toFloat(), outH.toFloat(), 0f, outH.toFloat())

        val matrix = Matrix()
        val ok = matrix.setPolyToPoly(srcPoly, 0, dstPoly, 0, 4)

        val unwarped = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.WHITE)
        }
        val canvas = Canvas(unwarped)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        if (ok) {
            canvas.drawBitmap(source, matrix, paint)
        } else {
            val fallback = Bitmap.createScaledBitmap(source, outW, outH, true)
            canvas.drawBitmap(fallback, 0f, 0f, paint)
            fallback.recycle()
        }

        val normalizedRot = ((rotationDegrees % 360) + 360) % 360
        return if (normalizedRot != 0) {
            val swap = normalizedRot == 90 || normalizedRot == 270
            val rotW = if (swap) unwarped.height else unwarped.width
            val rotH = if (swap) unwarped.width else unwarped.height
            val rotated = Bitmap.createBitmap(rotW, rotH, Bitmap.Config.ARGB_8888).apply {
                eraseColor(android.graphics.Color.WHITE)
            }
            val rotCanvas = Canvas(rotated)
            rotCanvas.translate(rotW / 2f, rotH / 2f)
            rotCanvas.rotate(normalizedRot.toFloat())
            rotCanvas.translate(-unwarped.width / 2f, -unwarped.height / 2f)
            rotCanvas.drawBitmap(unwarped, 0f, 0f, paint)
            unwarped.recycle()
            rotated
        } else {
            unwarped
        }
    }

    fun applyEnhancement(
        source: Bitmap,
        filter: DocumentFilter,
        brightness: Float,
        contrast: Float,
        sharpness: Float,
        enhancementIntensity: Float = 1.0f
    ): Bitmap {
        val intensity = enhancementIntensity.coerceIn(0.15f, 1.0f)

        // Step 1: Apply base filter transformation scaled by enhancement intensity
        var current = when (filter) {
            DocumentFilter.ORIGINAL -> source.copy(Bitmap.Config.ARGB_8888, true)
            DocumentFilter.GRAYSCALE -> applyColorMatrix(
                source,
                ColorMatrix().apply { setSaturation((1f - intensity).coerceIn(0f, 1f)) }
            )
            DocumentFilter.COLOR -> {
                val cm = ColorMatrix().apply { setSaturation(1f + 0.25f * intensity) }
                val contrastMatrix = contrastBrightnessMatrix(1f + 0.12f * intensity, 8f * intensity)
                cm.postConcat(contrastMatrix)
                applyColorMatrix(source, cm)
            }
            DocumentFilter.HIGH_CONTRAST -> {
                applyColorMatrix(source, contrastBrightnessMatrix(1f + 0.45f * intensity, -20f * intensity))
            }
            DocumentFilter.AUTO_ENHANCE -> applyAutoContrastStretch(source, intensity)
            DocumentFilter.DOCUMENT -> applyDocumentCleanFilter(source, intensity)
            DocumentFilter.BLACK_AND_WHITE -> applyAdaptiveThresholdFilter(source, intensity)
        }

        // Step 2: Apply user brightness / contrast adjustments scaled by intensity
        val effectiveContrast = 1f + (contrast - 1f) * intensity
        val effectiveBrightness = brightness * intensity
        if (kotlin.math.abs(effectiveBrightness) > 0.01f || kotlin.math.abs(effectiveContrast - 1f) > 0.01f) {
            val adj = applyColorMatrix(
                current,
                contrastBrightnessMatrix(effectiveContrast, effectiveBrightness * 120f)
            )
            current.recycle()
            current = adj
        }

        // Step 3: Apply 3x3 sharpening convolution kernel if effective sharpness exceeds threshold
        val effectiveSharpness = sharpness * intensity
        if (effectiveSharpness > 0.05f) {
            val sharpened = applySharpenKernel(current, effectiveSharpness.coerceIn(0f, 1f))
            current.recycle()
            current = sharpened
        }

        return current
    }

    private fun applyColorMatrix(source: Bitmap, cm: ColorMatrix): Bitmap {
        val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(cm)
        }
        canvas.drawBitmap(source, 0f, 0f, paint)
        return out
    }

    private fun contrastBrightnessMatrix(contrast: Float, brightnessOffset: Float): ColorMatrix {
        val c = contrast.coerceIn(0.4f, 2.5f)
        val t = (1f - c) * 128f + brightnessOffset
        return ColorMatrix(
            floatArrayOf(
                c, 0f, 0f, 0f, t,
                0f, c, 0f, 0f, t,
                0f, 0f, c, 0f, t,
                0f, 0f, 0f, 1f, 0f
            )
        )
    }

    private fun applyAutoContrastStretch(source: Bitmap, intensity: Float): Bitmap {
        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)

        val step = if (intensity < 0.7f) 4 else 2
        val hist = IntArray(256)
        var totalSamples = 0
        for (i in pixels.indices step step) {
            val p = pixels[i]
            val luma = (((p shr 16) and 0xFF) * 77 + ((p shr 8) and 0xFF) * 150 + (p and 0xFF) * 29) shr 8
            hist[luma]++
            totalSamples++
        }

        val safeSamples = max(1, totalSamples)
        val lowTarget = (safeSamples * 0.02f).toInt()
        val highTarget = (safeSamples * 0.98f).toInt()

        var acc = 0
        var low = 0
        var high = 255
        for (i in 0..255) {
            acc += hist[i]
            if (acc >= lowTarget) {
                low = i
                break
            }
        }
        acc = 0
        for (i in 0..255) {
            acc += hist[i]
            if (acc >= highTarget) {
                high = i
                break
            }
        }

        val range = max(24, high - low)
        val lut = IntArray(256) { v ->
            (((v - low) * 255f) / range).roundToInt().coerceIn(0, 255)
        }

        for (i in pixels.indices) {
            val p = pixels[i]
            val sr = (p shr 16) and 0xFF
            val sg = (p shr 8) and 0xFF
            val sb = p and 0xFF
            val r = if (intensity >= 0.99f) lut[sr] else (sr + (lut[sr] - sr) * intensity).roundToInt().coerceIn(0, 255)
            val g = if (intensity >= 0.99f) lut[sg] else (sg + (lut[sg] - sg) * intensity).roundToInt().coerceIn(0, 255)
            val b = if (intensity >= 0.99f) lut[sb] else (sb + (lut[sb] - sb) * intensity).roundToInt().coerceIn(0, 255)
            pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }

        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun applyDocumentCleanFilter(source: Bitmap, intensity: Float): Bitmap {
        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)

        // Estimate background paper brightness using 80th percentile luminance
        val step = if (intensity < 0.7f) 6 else 3
        val hist = IntArray(256)
        var totalSamples = 0
        for (i in pixels.indices step step) {
            val p = pixels[i]
            val l = (((p shr 16) and 0xFF) * 77 + ((p shr 8) and 0xFF) * 150 + (p and 0xFF) * 29) shr 8
            hist[l]++
            totalSamples++
        }
        val target = max(1, totalSamples) * 0.80f
        var acc = 0
        var whitePoint = 220
        for (i in 0..255) {
            acc += hist[i]
            if (acc >= target) {
                whitePoint = i.coerceIn(135, 245)
                break
            }
        }

        val blackPoint = (whitePoint * 0.25f).toInt()
        val span = max(40, whitePoint - blackPoint)

        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val tr = (((r - blackPoint) * 255f) / span).roundToInt().coerceIn(0, 255)
            val tg = (((g - blackPoint) * 255f) / span).roundToInt().coerceIn(0, 255)
            val tb = (((b - blackPoint) * 255f) / span).roundToInt().coerceIn(0, 255)
            val nr = if (intensity >= 0.99f) tr else (r + (tr - r) * intensity).roundToInt().coerceIn(0, 255)
            val ng = if (intensity >= 0.99f) tg else (g + (tg - g) * intensity).roundToInt().coerceIn(0, 255)
            val nb = if (intensity >= 0.99f) tb else (b + (tb - b) * intensity).roundToInt().coerceIn(0, 255)
            pixels[i] = (0xFF shl 24) or (nr shl 16) or (ng shl 8) or nb
        }
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }

    /**
     * Integral-image adaptive thresholding (Bradley-Roth algorithm) for crisp B&W text under uneven lighting.
     * In Battery Saver mode (intensity < 0.7f), falls back to a fast single-pass global threshold + blend
     * to avoid 2D integral allocation and reduce CPU work.
     */
    private fun applyAdaptiveThresholdFilter(source: Bitmap, intensity: Float): Bitmap {
        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)

        if (intensity < 0.7f) {
            var sumLuma = 0L
            var sampleCount = 0
            for (i in pixels.indices step 4) {
                val p = pixels[i]
                val v = (((p shr 16) and 0xFF) * 77 + ((p shr 8) and 0xFF) * 150 + (p and 0xFF) * 29) shr 8
                sumLuma += v
                sampleCount++
            }
            val meanThreshold = ((sumLuma / max(1, sampleCount)).toFloat() * 0.90f).toInt().coerceIn(48, 220)
            for (i in pixels.indices) {
                val p = pixels[i]
                val v = (((p shr 16) and 0xFF) * 77 + ((p shr 8) and 0xFF) * 150 + (p and 0xFF) * 29) shr 8
                val target = if (v >= meanThreshold) 255 else 24
                val blended = (v + (target - v) * intensity).roundToInt().coerceIn(0, 255)
                pixels[i] = (0xFF shl 24) or (blended shl 16) or (blended shl 8) or blended
            }
            return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
        }

        val luma = IntArray(w * h)
        val integral = LongArray(w * h)

        for (y in 0 until h) {
            var rowSum = 0L
            for (x in 0 until w) {
                val idx = y * w + x
                val p = pixels[idx]
                val v = (((p shr 16) and 0xFF) * 77 + ((p shr 8) and 0xFF) * 150 + (p and 0xFF) * 29) shr 8
                luma[idx] = v
                rowSum += v
                integral[idx] = rowSum + if (y > 0) integral[(y - 1) * w + x] else 0L
            }
        }

        val radius = max(12, min(w, h) / 18)
        val thresholdFactor = 0.88f

        for (y in 0 until h) {
            val y1 = max(0, y - radius)
            val y2 = min(h - 1, y + radius)
            for (x in 0 until w) {
                val x1 = max(0, x - radius)
                val x2 = min(w - 1, x + radius)
                val count = (x2 - x1 + 1) * (y2 - y1 + 1)

                var sum = integral[y2 * w + x2]
                if (y1 > 0) sum -= integral[(y1 - 1) * w + x2]
                if (x1 > 0) sum -= integral[y2 * w + (x1 - 1)]
                if (y1 > 0 && x1 > 0) sum += integral[(y1 - 1) * w + (x1 - 1)]

                val idx = y * w + x
                val isWhite = (luma[idx] * count) > (sum * thresholdFactor)
                pixels[idx] = if (isWhite) 0xFFFFFFFF.toInt() else 0xFF111827.toInt()
            }
        }

        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun applySharpenKernel(source: Bitmap, amount: Float): Bitmap {
        val w = source.width
        val h = source.height
        val src = IntArray(w * h)
        val dst = IntArray(w * h)
        source.getPixels(src, 0, w, 0, 0, w, h)
        System.arraycopy(src, 0, dst, 0, src.size)

        val strength = amount * 1.4f
        val centerWeight = 1f + 4f * strength

        for (y in 1 until h - 1) {
            val rowOffset = y * w
            for (x in 1 until w - 1) {
                val idx = rowOffset + x
                val c = src[idx]
                val t = src[idx - w]
                val b = src[idx + w]
                val l = src[idx - 1]
                val r = src[idx + 1]

                val cr = ((c shr 16) and 0xFF) * centerWeight -
                    (((t shr 16) and 0xFF) + ((b shr 16) and 0xFF) + ((l shr 16) and 0xFF) + ((r shr 16) and 0xFF)) * strength
                val cg = ((c shr 8) and 0xFF) * centerWeight -
                    (((t shr 8) and 0xFF) + ((b shr 8) and 0xFF) + ((l shr 8) and 0xFF) + ((r shr 8) and 0xFF)) * strength
                val cb = (c and 0xFF) * centerWeight -
                    ((t and 0xFF) + (b and 0xFF) + (l and 0xFF) + (r and 0xFF)) * strength

                dst[idx] = (0xFF shl 24) or
                    (cr.roundToInt().coerceIn(0, 255) shl 16) or
                    (cg.roundToInt().coerceIn(0, 255) shl 8) or
                    cb.roundToInt().coerceIn(0, 255)
            }
        }
        return Bitmap.createBitmap(dst, w, h, Bitmap.Config.ARGB_8888)
    }
}
