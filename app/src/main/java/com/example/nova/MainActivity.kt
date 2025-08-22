package com.example.nova

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.nova.databinding.ActivityMainBinding
import org.tensorflow.lite.task.vision.detector.Detection
import java.io.ByteArrayOutputStream
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity :
    AppCompatActivity(),
    MyVoskService.Listener,
    ObjectDetector.DetectorListener {

    private val TAG = "MainActivity"
    private val REQ_PERMS = 1001

    private var myVoskService: MyVoskService? = null
    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var objectDetector: ObjectDetector

    private var textToSpeech: TextToSpeech? = null
    private var ttsReady = false

    // CameraX references so we can cleanly unbind onPause
    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1) Make preview more robust: use TextureView-based implementation
        binding.viewFinder.implementationMode = PreviewView.ImplementationMode.COMPATIBLE

        // TTS
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.language = Locale.US
                ttsReady = true
            }
        }

        objectDetector = ObjectDetector(this, this)
        cameraExecutor = Executors.newSingleThreadExecutor()

        ensurePermissionsOrRequest()
    }

    // Keep bind/unbind tied to visible lifecycle
    override fun onResume() {
        super.onResume()
        if (hasAllPerms()) {
            if (myVoskService == null) initVoskService()
            startCamera() // safe to call repeatedly; we unbind first
        }
    }

    override fun onPause() {
        super.onPause()
        // Unbind to avoid “BufferQueue abandoned / configure failed” when app stops
        cameraProvider?.unbindAll()
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { myVoskService?.close() }
        if (::cameraExecutor.isInitialized) cameraExecutor.shutdown()
        runCatching { textToSpeech?.shutdown() }
        objectDetector.close()
    }

    /* ---------------- Permissions ---------------- */

    private fun hasAllPerms(): Boolean {
        val cam = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val mic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        return cam && mic
    }

    private fun ensurePermissionsOrRequest() {
        if (hasAllPerms()) {
            initVoskService()
            // Camera starts in onResume so we don’t bind while paused
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
                REQ_PERMS
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_PERMS && hasAllPerms()) {
            initVoskService()
            startCamera()
        } else {
            Toast.makeText(this, "Camera & mic permissions are required", Toast.LENGTH_LONG).show()
        }
    }

    /* ---------------- Vosk ---------------- */

    private fun initVoskService() {
        if (myVoskService == null) {
            myVoskService = MyVoskService(this, this)
        }
    }

    override fun onReady() {
        Log.d("Vosk", "Vosk ready")
        myVoskService?.startListening()
        runOnUiThread {
            Toast.makeText(this, "Speak now", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onPartialResult(hypothesis: String) {
        Log.d("Vosk", "Partial: $hypothesis")
        runOnUiThread { binding.resultTextView.text = hypothesis }
    }

    override fun onFinalResult(result: String) {
        Log.d("Vosk", "Final: $result")
        runOnUiThread { binding.resultTextView.text = result }
    }

    override fun onError(e: Exception) {
        Log.e("Vosk", "Error: ${e.message}", e)
        runOnUiThread { binding.resultTextView.text = "Vosk Error: ${e.message}" }
    }

    /* ---------------- CameraX ---------------- */

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            // Always unbind first to avoid leftover surfaces
            cameraProvider?.unbindAll()

            preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }

            imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build().also { analyzer ->
                    analyzer.setAnalyzer(cameraExecutor) { imageProxy ->
                        val bmp = imageProxyToBitmap(imageProxy)
                        if (bmp != null) objectDetector.detect(bmp)
                        imageProxy.close()
                    }
                }

            try {
                cameraProvider?.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalyzer
                )
            } catch (exc: Exception) {
                Log.e(TAG, "bindToLifecycle failed", exc)
                // Try a gentle retry after the view settles
                binding.viewFinder.postDelayed({ safeRebind() }, 250)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun safeRebind() {
        try {
            cameraProvider?.unbindAll()
            if (preview == null) {
                preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                }
            }
            if (imageAnalyzer == null) {
                imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build().also { analyzer ->
                        analyzer.setAnalyzer(cameraExecutor) { imageProxy ->
                            val bmp = imageProxyToBitmap(imageProxy)
                            if (bmp != null) objectDetector.detect(bmp)
                            imageProxy.close()
                        }
                    }
            }
            cameraProvider?.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageAnalyzer
            )
        } catch (e: Exception) {
            Log.e(TAG, "safeRebind failed", e)
        }
    }

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
        return try {
            val yBuffer = image.planes[0].buffer
            val uBuffer = image.planes[1].buffer
            val vBuffer = image.planes[2].buffer

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            val nv21 = ByteArray(ySize + uSize + vSize)
            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)

            val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 100, out)
            val bytes = out.toByteArray()
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (t: Throwable) {
            Log.e(TAG, "imageProxyToBitmap failed", t)
            null
        }
    }

    /* ---------------- Detector callbacks ---------------- */

    override fun onResults(results: List<Detection>) {
        if (results.isNotEmpty()) {
            val label = results[0].categories[0].label
            val msg = "I see a $label"
            Log.d("Detector", msg)
            runOnUiThread {
                binding.resultTextView.text = msg
                if (ttsReady) {
                    textToSpeech?.let { tts ->
                        if (tts.isSpeaking) tts.stop()
                        tts.speak(msg, TextToSpeech.QUEUE_FLUSH, null, null)
                    }
                }
            }
        }
    }

    override fun onError(error: String) {
        Log.e("Detector", error)
        runOnUiThread { binding.resultTextView.text = "Detector Error: $error" }
    }
}
