package com.example.medicationtracker.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class PillFeatureExtractor(context: Context) {

    private val interpreter: Interpreter

    companion object {
        private const val MODEL_PATH = "mobilenet_v3_feature_extractor.tflite"
        private const val INPUT_SIZE = 224
        private const val FEATURE_VECTOR_SIZE = 576
        private const val PIXEL_SIZE = 3
        private const val IMAGE_STD = 255f
        private const val TARGET_BRIGHTNESS = 128f  // Target mean brightness
    }

    init {
        val model = loadModelFile(context)
        interpreter = Interpreter(model)
    }

    private fun loadModelFile(context: Context): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(MODEL_PATH)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    /**
     * Extract MobileNetV3 features with preprocessing:
     * 1. Center crop to square (guide box area)
     * 2. Brightness normalization (lighting invariant)
     * 3. Resize to 224x224
     * 4. Run model
     */
    fun extractFeatures(bitmap: Bitmap): FloatArray {
        val preprocessed = preprocessImage(bitmap)
        val inputBuffer = convertBitmapToByteBuffer(preprocessed)
        val outputFeatures = Array(1) { FloatArray(FEATURE_VECTOR_SIZE) }
        interpreter.run(inputBuffer, outputFeatures)
        return outputFeatures[0]
    }

    /**
     * Extract combined features (MobileNetV3 + Color) for dual-gate verification
     */
    fun extractCombinedFeatures(bitmap: Bitmap): FloatArray {
        val preprocessed = preprocessImage(bitmap)
        val mobilenetFeatures = extractFeatures(preprocessed)
        val colorHistogram = ColorFeatureExtractor.extractColorHistogram(preprocessed)
        return ColorFeatureExtractor.combineFeatures(mobilenetFeatures, colorHistogram)
    }

    /**
     * Preprocessing pipeline:
     * 1. Center square crop (isolates pill from background)
     * 2. Grey world brightness normalization (removes lighting effects)
     * 3. Resize to 224x224
     */
    private fun preprocessImage(bitmap: Bitmap): Bitmap {
        // Step 1: Center square crop
        val cropped = centerCropToSquare(bitmap)

        // Step 2: Resize FIRST (much faster to normalize small image!)
        val resized = Bitmap.createScaledBitmap(cropped, INPUT_SIZE, INPUT_SIZE, true)

        // Step 3: Brightness normalization on small 224x224 image (180x faster!)
        return normalizeGreyWorld(resized)
    }

    /**
     * Crop to center square - focuses on the pill inside the guide box
     * Removes the surrounding background entirely
     */
    private fun centerCropToSquare(bitmap: Bitmap): Bitmap {
        val size = minOf(bitmap.width, bitmap.height)
        val xOffset = (bitmap.width - size) / 2
        val yOffset = (bitmap.height - size) / 2
        return Bitmap.createBitmap(bitmap, xOffset, yOffset, size, size)
    }

    /**
     * Grey World normalization - assumes average color should be neutral grey.
     * This effectively cancels out different lighting conditions.
     *
     * Example: yellow indoor lighting makes everything yellow.
     * Grey world normalization removes that yellow cast so features
     * are consistent across lighting conditions.
     */
    private fun normalizeGreyWorld(bitmap: Bitmap): Bitmap {
        var rSum = 0L
        var gSum = 0L
        var bSum = 0L
        val total = bitmap.width * bitmap.height

        // Calculate mean of each channel
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val p = bitmap.getPixel(x, y)
                rSum += Color.red(p)
                gSum += Color.green(p)
                bSum += Color.blue(p)
            }
        }

        val rMean = (rSum.toFloat() / total).coerceAtLeast(1f)
        val gMean = (gSum.toFloat() / total).coerceAtLeast(1f)
        val bMean = (bSum.toFloat() / total).coerceAtLeast(1f)

        // Scale each channel so mean = TARGET_BRIGHTNESS (128)
        val rScale = TARGET_BRIGHTNESS / rMean
        val gScale = TARGET_BRIGHTNESS / gMean
        val bScale = TARGET_BRIGHTNESS / bMean

        // Apply normalization
        val result = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, true)
        for (y in 0 until result.height) {
            for (x in 0 until result.width) {
                val p = result.getPixel(x, y)
                val r = (Color.red(p) * rScale).toInt().coerceIn(0, 255)
                val g = (Color.green(p) * gScale).toInt().coerceIn(0, 255)
                val b = (Color.blue(p) * bScale).toInt().coerceIn(0, 255)
                result.setPixel(x, y, Color.rgb(r, g, b))
            }
        }
        return result
    }

    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE * INPUT_SIZE * PIXEL_SIZE)
        byteBuffer.order(ByteOrder.nativeOrder())

        val intValues = IntArray(INPUT_SIZE * INPUT_SIZE)
        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        var pixel = 0
        for (i in 0 until INPUT_SIZE) {
            for (j in 0 until INPUT_SIZE) {
                val value = intValues[pixel++]
                val r = (value shr 16 and 0xFF).toFloat()
                val g = (value shr 8 and 0xFF).toFloat()
                val b = (value and 0xFF).toFloat()
                byteBuffer.putFloat(r / IMAGE_STD)
                byteBuffer.putFloat(g / IMAGE_STD)
                byteBuffer.putFloat(b / IMAGE_STD)
            }
        }
        return byteBuffer
    }

    fun close() {
        interpreter.close()
    }
}