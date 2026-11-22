package com.example.data.processing

import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import com.example.domain.model.NormalizedPoint
import com.example.domain.model.QuadPoints
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class EdgeDetectionResult(
    val quad: QuadPoints,
    val confidence: Float,
    val isAutoDetected: Boolean
)

/**
 * Fast, pure-Kotlin offline document boundary detector.
 * Computes Sobel edge gradients on a downscaled luminance grid and locates the dominant
 * outer document quadrilateral. Gracefully falls back to a safe inset rectangle if contrast is low.
 */
object DocumentEdgeDetector {

    fun detectFromBitmap(bitmap: Bitmap): EdgeDetectionResult {
        return runCatching {
            val targetW = 160
            val targetH = max(60, (bitmap.height * (targetW.toFloat() / max(1, bitmap.width))).toInt().coerceAtMost(240))
            val scaled = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
            val pixels = IntArray(targetW * targetH)
            scaled.getPixels(pixels, 0, targetW, 0, 0, targetW, targetH)
            if (scaled != bitmap) scaled.recycle()

            val luma = IntArray(targetW * targetH)
            for (i in pixels.indices) {
                val p = pixels[i]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                luma[i] = (r * 77 + g * 150 + b * 29) shr 8
            }
            detectFromLumaGrid(luma, targetW, targetH)
        }.getOrElse {
            EdgeDetectionResult(QuadPoints.DEFAULT_INSET, 0f, false)
        }
    }

    fun detectFromImageProxy(imageProxy: ImageProxy): EdgeDetectionResult {
        return runCatching {
            val yPlane = imageProxy.planes.firstOrNull()
                ?: return EdgeDetectionResult(QuadPoints.DEFAULT_INSET, 0f, false)
            val buffer = yPlane.buffer
            val rowStride = yPlane.rowStride
            val pixelStride = yPlane.pixelStride
            val srcW = imageProxy.width
            val srcH = imageProxy.height
            val rotation = imageProxy.imageInfo.rotationDegrees

            val gridW = 120
            val gridH = 160
            val luma = IntArray(gridW * gridH)

            for (gy in 0 until gridH) {
                for (gx in 0 until gridW) {
                    val nx = gx.toFloat() / (gridW - 1)
                    val ny = gy.toFloat() / (gridH - 1)
                    val (sx, sy) = when (rotation) {
                        90 -> (ny * (srcW - 1)).toInt() to ((1f - nx) * (srcH - 1)).toInt()
                        180 -> ((1f - nx) * (srcW - 1)).toInt() to ((1f - ny) * (srcH - 1)).toInt()
                        270 -> ((1f - ny) * (srcW - 1)).toInt() to (nx * (srcH - 1)).toInt()
                        else -> (nx * (srcW - 1)).toInt() to (ny * (srcH - 1)).toInt()
                    }
                    val idx = sy.coerceIn(0, srcH - 1) * rowStride + sx.coerceIn(0, srcW - 1) * pixelStride
                    luma[gy * gridW + gx] = if (idx in 0 until buffer.capacity()) {
                        buffer.get(idx).toInt() and 0xFF
                    } else 128
                }
            }
            detectFromLumaGrid(luma, gridW, gridH)
        }.getOrElse {
            EdgeDetectionResult(QuadPoints.DEFAULT_INSET, 0f, false)
        }
    }

    private fun detectFromLumaGrid(luma: IntArray, w: Int, h: Int): EdgeDetectionResult {
        val grad = IntArray(w * h)
        var gradSum = 0L
        var maxGrad = 0

        for (y in 2 until h - 2) {
            for (x in 2 until w - 2) {
                val tl = luma[(y - 1) * w + (x - 1)]
                val tc = luma[(y - 1) * w + x]
                val tr = luma[(y - 1) * w + (x + 1)]
                val ml = luma[y * w + (x - 1)]
                val mr = luma[y * w + (x + 1)]
                val bl = luma[(y + 1) * w + (x - 1)]
                val bc = luma[(y + 1) * w + x]
                val br = luma[(y + 1) * w + (x + 1)]

                val gx = -tl + tr - 2 * ml + 2 * mr - bl + br
                val gy = -tl - 2 * tc - tr + bl + 2 * bc + br
                val mag = abs(gx) + abs(gy)
                grad[y * w + x] = mag
                gradSum += mag
                if (mag > maxGrad) maxGrad = mag
            }
        }

        val meanGrad = (gradSum / max(1, (w - 4) * (h - 4))).toInt()
        val threshold = max(40, (meanGrad * 1.3f).toInt())
        if (maxGrad < threshold) {
            return EdgeDetectionResult(QuadPoints.DEFAULT_INSET, 0f, false)
        }

        // Find strong edge points in each quadrant by projecting inward from frame borders
        val margin = 2
        val centerX = w / 2
        val centerY = h / 2

        val tlCandidates = mutableListOf<Pair<Int, Int>>()
        val trCandidates = mutableListOf<Pair<Int, Int>>()
        val brCandidates = mutableListOf<Pair<Int, Int>>()
        val blCandidates = mutableListOf<Pair<Int, Int>>()

        // Scan horizontal rays from left and right
        for (y in margin until h - margin step 2) {
            for (x in margin until centerX) {
                if (grad[y * w + x] >= threshold) {
                    if (y < centerY) tlCandidates.add(x to y) else blCandidates.add(x to y)
                    break
                }
            }
            for (x in w - margin - 1 downTo centerX) {
                if (grad[y * w + x] >= threshold) {
                    if (y < centerY) trCandidates.add(x to y) else brCandidates.add(x to y)
                    break
                }
            }
        }

        // Scan vertical rays from top and bottom
        for (x in margin until w - margin step 2) {
            for (y in margin until centerY) {
                if (grad[y * w + x] >= threshold) {
                    if (x < centerX) tlCandidates.add(x to y) else trCandidates.add(x to y)
                    break
                }
            }
            for (y in h - margin - 1 downTo centerY) {
                if (grad[y * w + x] >= threshold) {
                    if (x < centerX) blCandidates.add(x to y) else brCandidates.add(x to y)
                    break
                }
            }
        }

        if (tlCandidates.isEmpty() || trCandidates.isEmpty() || brCandidates.isEmpty() || blCandidates.isEmpty()) {
            return EdgeDetectionResult(QuadPoints.DEFAULT_INSET, 0.15f, false)
        }

        // Pick extremal outer-corner points minimizing distance to each frame corner
        val tl = pickCorner(tlCandidates, 0, 0)
        val tr = pickCorner(trCandidates, w - 1, 0)
        val br = pickCorner(brCandidates, w - 1, h - 1)
        val bl = pickCorner(blCandidates, 0, h - 1)

        val quad = QuadPoints(
            topLeft = NormalizedPoint((tl.first.toFloat() / w).coerceIn(0.01f, 0.49f), (tl.second.toFloat() / h).coerceIn(0.01f, 0.49f)),
            topRight = NormalizedPoint((tr.first.toFloat() / w).coerceIn(0.51f, 0.99f), (tr.second.toFloat() / h).coerceIn(0.01f, 0.49f)),
            bottomRight = NormalizedPoint((br.first.toFloat() / w).coerceIn(0.51f, 0.99f), (br.second.toFloat() / h).coerceIn(0.51f, 0.99f)),
            bottomLeft = NormalizedPoint((bl.first.toFloat() / w).coerceIn(0.01f, 0.49f), (bl.second.toFloat() / h).coerceIn(0.51f, 0.99f))
        )

        val area = polygonArea(quad)
        if (area < 0.05f || area > 0.96f) {
            return EdgeDetectionResult(QuadPoints.DEFAULT_INSET, 0.2f, false)
        }

        val topWidth = abs(quad.topRight.x - quad.topLeft.x)
        val bottomWidth = abs(quad.bottomRight.x - quad.bottomLeft.x)
        val leftHeight = abs(quad.bottomLeft.y - quad.topLeft.y)
        val rightHeight = abs(quad.bottomRight.y - quad.topRight.y)
        val symmetry = min(topWidth, bottomWidth) / max(0.01f, max(topWidth, bottomWidth)) *
            min(leftHeight, rightHeight) / max(0.01f, max(leftHeight, rightHeight))

        val confidence = (0.40f + 0.60f * symmetry).coerceIn(0f, 1f)
        return if (confidence >= 0.40f) {
            EdgeDetectionResult(quad, confidence, true)
        } else {
            EdgeDetectionResult(QuadPoints.DEFAULT_INSET, confidence, false)
        }
    }

    private fun pickCorner(points: List<Pair<Int, Int>>, targetX: Int, targetY: Int): Pair<Int, Int> {
        val sorted = points.sortedBy { (x, y) ->
            val dx = x - targetX
            val dy = y - targetY
            dx * dx + dy * dy
        }
        // Take median of closest 3 points to reduce single-pixel noise
        val topK = sorted.take(min(3, sorted.size))
        val avgX = topK.sumOf { it.first } / topK.size
        val avgY = topK.sumOf { it.second } / topK.size
        return avgX to avgY
    }

    private fun polygonArea(q: QuadPoints): Float {
        val pts = q.toList()
        var sum = 0f
        for (i in pts.indices) {
            val j = (i + 1) % pts.size
            sum += pts[i].x * pts[j].y - pts[j].x * pts[i].y
        }
        return abs(sum) * 0.5f
    }

    fun isStable(a: QuadPoints, b: QuadPoints, tolerance: Float = 0.035f): Boolean {
        val pa = a.toList()
        val pb = b.toList()
        return pa.zip(pb).all { (p1, p2) ->
            val dx = p1.x - p2.x
            val dy = p1.y - p2.y
            sqrt(dx * dx + dy * dy) <= tolerance
        }
    }
}
