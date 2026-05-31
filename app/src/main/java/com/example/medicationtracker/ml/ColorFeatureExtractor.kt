package com.example.medicationtracker.ml

import android.graphics.Bitmap
import android.graphics.Color

object ColorFeatureExtractor {

    private const val HUE_BINS = 16
    private const val SAT_BINS = 4
    private const val VAL_BINS = 4
    private const val HISTOGRAM_SIZE = HUE_BINS * SAT_BINS * VAL_BINS // 256

    fun extractColorHistogram(bitmap: Bitmap): FloatArray {
        val histogram = FloatArray(HISTOGRAM_SIZE)
        val size = 64
        val resized = Bitmap.createScaledBitmap(bitmap, size, size, true)

        // Tight center crop - only middle 40% of image
        val start = (size * 0.30).toInt()  // 30% margin each side
        val end = size - start

        // Collect ALL pixels with their HSV values
        val hsv = FloatArray(3)
        data class PixelHSV(val hue: Float, val sat: Float, val value: Float)
        val allPixels = mutableListOf<PixelHSV>()

        for (y in start until end) {
            for (x in start until end) {
                val pixel = resized.getPixel(x, y)
                Color.RGBToHSV(
                    Color.red(pixel),
                    Color.green(pixel),
                    Color.blue(pixel),
                    hsv
                )
                allPixels.add(PixelHSV(hsv[0], hsv[1], hsv[2]))
            }
        }

        // Sort by saturation DESCENDING
        // High saturation = pill color (red, yellow, green, black)
        // Low saturation = white/grey background
        val sorted = allPixels.sortedByDescending { it.sat }

        // Use top 40% most saturated pixels = pill's actual color
        val useCount = maxOf(10, sorted.size * 40 / 100)
        val usePixels = sorted.take(useCount)

        // Build histogram from pill-color pixels
        for (px in usePixels) {
            val hueBin = ((px.hue / 360f) * HUE_BINS).toInt().coerceIn(0, HUE_BINS - 1)
            val satBin = (px.sat * SAT_BINS).toInt().coerceIn(0, SAT_BINS - 1)
            val valBin = (px.value * VAL_BINS).toInt().coerceIn(0, VAL_BINS - 1)
            val index = hueBin * (SAT_BINS * VAL_BINS) + satBin * VAL_BINS + valBin
            histogram[index]++
        }

        // Normalize
        val total = usePixels.size.toFloat()
        if (total > 0) {
            for (i in histogram.indices) histogram[i] /= total
        }

        return histogram
    }

    fun combineFeatures(
        mobilenetFeatures: FloatArray,
        colorHistogram: FloatArray,
        colorWeight: Float = 5.0f
    ): FloatArray {
        val combined = FloatArray(mobilenetFeatures.size + colorHistogram.size)
        mobilenetFeatures.copyInto(combined, 0)
        for (i in colorHistogram.indices) {
            combined[mobilenetFeatures.size + i] = colorHistogram[i] * colorWeight
        }
        return combined
    }
}