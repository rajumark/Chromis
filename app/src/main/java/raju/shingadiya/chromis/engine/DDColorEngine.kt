package raju.shingadiya.chromis.engine

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Core
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.opencv.imgproc.Imgproc.INTER_AREA
import java.io.File
import java.io.FileOutputStream
import java.nio.FloatBuffer

class DDColorEngine(context: Context) {

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession

    init {
        val modelFile = File(context.filesDir, "ddcolor-tiny-fp16.onnx")
        if (!modelFile.exists()) {
            context.assets.open("ddcolor-tiny-fp16.onnx").use { input ->
                FileOutputStream(modelFile).use { output ->
                    input.copyTo(output)
                }
            }
        }
        val options = OrtSession.SessionOptions().apply {
            setInterOpNumThreads(4)
            setIntraOpNumThreads(4)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        }
        session = env.createSession(modelFile.absolutePath, options)
    }

    fun colorize(inputBitmap: Bitmap): Bitmap {
        val width = inputBitmap.width
        val height = inputBitmap.height

        val pixels = IntArray(width * height)
        inputBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val rgbMat = Mat(height, width, CvType.CV_8UC3)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val px = pixels[y * width + x]
                rgbMat.put(y, x, byteArrayOf(
                    ((px shr 16) and 0xFF).toByte(),
                    ((px shr 8) and 0xFF).toByte(),
                    (px and 0xFF).toByte()
                ))
            }
        }

        val labMat = Mat()
        Imgproc.cvtColor(rgbMat, labMat, Imgproc.COLOR_RGB2Lab)
        val channels = ArrayList<Mat>(3)
        Core.split(labMat, channels)
        val origL = channels[0]

        val lResized = Mat()
        Imgproc.resize(origL, lResized, Size(512.0, 512.0), 0.0, 0.0, INTER_AREA)

        val inputBuf = FloatBuffer.allocate(1 * 3 * 512 * 512)
        for (c in 0..2) {
            for (r in 0 until 512) {
                for (col in 0 until 512) {
                    inputBuf.put(lResized.get(r, col)[0].toFloat() / 255.0f)
                }
            }
        }
        inputBuf.rewind()

        val tensor = OnnxTensor.createTensor(env, inputBuf, longArrayOf(1, 3, 512, 512))
        val output = session.run(mapOf(session.inputNames.iterator().next() to tensor))
        val ab = output[0].value as Array<Array<Array<FloatArray>>>

        var nanCount = 0
        var infCount = 0
        var minV = Float.MAX_VALUE
        var maxV = -Float.MAX_VALUE
        for (r in 0 until 512) {
            for (col in 0 until 512) {
                for (v in floatArrayOf(ab[0][0][r][col], ab[0][1][r][col])) {
                    when {
                        v.isNaN() -> nanCount++
                        v.isInfinite() -> infCount++
                        else -> {
                            if (v < minV) minV = v
                            if (v > maxV) maxV = v
                        }
                    }
                }
            }
        }
        Log.d("DDColor", "ab range=[$minV,$maxV] nan=$nanCount inf=$infCount")

        val aU8 = Mat(512, 512, CvType.CV_8UC1)
        val bU8 = Mat(512, 512, CvType.CV_8UC1)
        for (r in 0 until 512) {
            for (col in 0 until 512) {
                val a = ab[0][0][r][col]
                val b = ab[0][1][r][col]
                val aClean = if (a.isNaN() || a.isInfinite()) 128f else (a + 128f)
                val bClean = if (b.isNaN() || b.isInfinite()) 128f else (b + 128f)
                aU8.put(r, col, byteArrayOf(aClean.coerceIn(0f, 255f).toInt().toByte()))
                bU8.put(r, col, byteArrayOf(bClean.coerceIn(0f, 255f).toInt().toByte()))
            }
        }

        val hiA = Mat()
        val hiB = Mat()
        Imgproc.resize(aU8, hiA, Size(width.toDouble(), height.toDouble()), 0.0, 0.0, INTER_AREA)
        Imgproc.resize(bU8, hiB, Size(width.toDouble(), height.toDouble()), 0.0, 0.0, INTER_AREA)

        val outLab = Mat()
        Core.merge(listOf(origL, hiA, hiB), outLab)
        val outRgb = Mat()
        Imgproc.cvtColor(outLab, outRgb, Imgproc.COLOR_Lab2RGB)

        val outPixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val v = outRgb.get(y, x)
                outPixels[y * width + x] = (0xFF shl 24) or
                    (v[0].toInt().coerceIn(0, 255) shl 16) or
                    (v[1].toInt().coerceIn(0, 255) shl 8) or
                    v[2].toInt().coerceIn(0, 255)
            }
        }

        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bmp.setPixels(outPixels, 0, width, 0, 0, width, height)
        return bmp
    }
}
