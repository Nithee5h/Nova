package com.example.nova

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.core.BaseOptions
import org.tensorflow.lite.task.vision.detector.Detection
import org.tensorflow.lite.task.vision.detector.ObjectDetector as TFLiteObjectDetector

class ObjectDetector(
    private val context: Context,
    private val listener: DetectorListener
) {
    private var objectDetector: TFLiteObjectDetector? = null
    private val main = Handler(Looper.getMainLooper())

    init { setupObjectDetector() }

    private fun setupObjectDetector() {
        val base = BaseOptions.builder().setNumThreads(3).build()
        val opts = TFLiteObjectDetector.ObjectDetectorOptions.builder()
            .setBaseOptions(base)
            .setScoreThreshold(0.5f)
            .setMaxResults(5)
            .build()
        try {
            objectDetector = TFLiteObjectDetector.createFromFileAndOptions(
                context, "efficientdet_lite0.tflite", opts
            )
            Log.d(TAG, "ObjectDetector ready")
        } catch (e: Exception) {
            postError("Model setup failed: ${e.message}")
            Log.e(TAG, "Error setting up object detector", e)
        }
    }

    fun detect(image: Bitmap) {
        val det = objectDetector
        if (det == null) {
            postError("Detector not initialized")
            return
        }
        try {
            val tensor = ImageProcessor.Builder().build()
                .process(TensorImage.fromBitmap(image))
            val results = det.detect(tensor)
            // ALWAYS deliver on main thread
            main.post { listener.onResults(results) }
        } catch (t: Throwable) {
            Log.e(TAG, "detect() failed", t)
            postError("Detection failed: ${t.message}")
        }
    }

    fun close() {
        objectDetector?.close()
        objectDetector = null
    }

    private fun postError(msg: String) {
        main.post { listener.onError(msg) }
    }

    interface DetectorListener {
        fun onError(error: String)
        fun onResults(results: List<Detection>)
    }

    companion object { private const val TAG = "ObjectDetector" }
}
