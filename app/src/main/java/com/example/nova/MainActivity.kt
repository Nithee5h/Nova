package com.example.nova

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.nova.databinding.ActivityMainBinding
import com.example.nova.llm.LlmEngine
import com.example.nova.llm.LlmEngine_Validator
import com.example.nova.net.ChatMessage
import com.example.nova.ui.ChatAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import androidx.lifecycle.lifecycleScope
import org.tensorflow.lite.task.vision.detector.Detection
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity :
    AppCompatActivity(),
    MyVoskService.Listener,
    ObjectDetector.DetectorListener {

    private val TAG = "MainActivity"
    private val REQ_PERMS = 1001
    private val LLM_TIMEOUT_MS = 60_000L // 60s: first on-device reply can exceed 25s

    private enum class Turn { IDLE, THINKING, SPEAKING }
    private var turn: Turn = Turn.IDLE

    private var modelReady: Boolean = false
    private var activeModelPath: String? = null

    private lateinit var binding: ActivityMainBinding
    private lateinit var chatAdapter: ChatAdapter

    private var myVoskService: MyVoskService? = null
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var objectDetector: ObjectDetector
    private var textToSpeech: TextToSpeech? = null
    private var ttsReady = false

    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null

    private var llmJob: Job? = null

    // Wake words
    private var isAwake = false
    private val wakeWords = listOf("hey nova", "ok nova", "hello", "nova")

    // Detection chip auto-hide + debounce
    private val uiHandler = Handler(Looper.getMainLooper())
    private val hideDetectChip = Runnable { binding.detectChip.visibility = View.GONE }
    private var lastSpokenLabel: String? = null
    private var lastSpeakAt: Long = 0L
    private val SPEAK_DEBOUNCE_MS = 2000L

    // Vision Mode flag
    private var inVisionMode = false

    // SAF picker to import .task into app-private storage
    private val pickTaskFile = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) copyPickedModelToInternal(uri)
        else binding.resultTextView.text = "No file selected."
    }

    /* ========================== Activity ========================== */

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Use the toolbar as ActionBar so menu inflation works
        setSupportActionBar(binding.topAppBar)
        supportActionBar?.title = getString(R.string.app_name)

        // Chat list
        chatAdapter = ChatAdapter()
        binding.chatList.apply {
            layoutManager = LinearLayoutManager(this@MainActivity).apply { stackFromEnd = true }
            adapter = chatAdapter
        }

        // Composer
        fun sendCurrentInput() {
            val text = binding.inputEditText.text.toString().trim()
            if (text.isEmpty()) return
            binding.inputEditText.setText("")
            onUserTyped(text)
        }
        binding.sendButton.setOnClickListener { sendCurrentInput() }
        binding.sendButton.setOnLongClickListener {
            chatAdapter.clear()
            Toast.makeText(this, "Chat cleared", Toast.LENGTH_SHORT).show()
            true
        }
        binding.inputEditText.setOnEditorActionListener { _, _, _ ->
            sendCurrentInput(); true
        }

        // Vision overlay close
        binding.closeVision.setOnClickListener { exitVisionMode() }

        // TTS with turn control
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.language = Locale.US
                textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        turn = Turn.SPEAKING
                        runCatching { myVoskService?.stopListening() }
                    }
                    override fun onDone(utteranceId: String?) {
                        turn = Turn.IDLE
                        runCatching { myVoskService?.startListening() }
                    }
                    @Deprecated("Deprecated")
                    override fun onError(utteranceId: String?) {
                        turn = Turn.IDLE
                        runCatching { myVoskService?.startListening() }
                    }
                    override fun onError(utteranceId: String?, errorCode: Int) {
                        turn = Turn.IDLE
                        runCatching { myVoskService?.startListening() }
                    }
                })
                ttsReady = true
            }
        }

        objectDetector = ObjectDetector(this, this)
        cameraExecutor = Executors.newSingleThreadExecutor()

        chooseModelOrPrompt()

        binding.resultTextView.setOnClickListener {
            if (!modelReady) pickTaskFile.launch(arrayOf("*/*"))
        }

        ensurePermissionsOrRequest()
    }

    override fun onResume() {
        super.onResume()
        if (hasAllPerms()) {
            if (myVoskService == null) initVoskService()
        }
    }

    override fun onPause() {
        super.onPause()
        if (inVisionMode) stopCamera()
        llmJob?.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        uiHandler.removeCallbacks(hideDetectChip)
        runCatching { myVoskService?.close() }
        if (::cameraExecutor.isInitialized) cameraExecutor.shutdown()
        runCatching { textToSpeech?.shutdown() }
        objectDetector.close()
        llmJob?.cancel()
        LlmEngine.close()
    }

    /* ========================== Menu ========================== */

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        return try {
            menuInflater.inflate(R.menu.main_menu, menu)
            true
        } catch (t: Throwable) {
            Log.e(TAG, "Menu inflate failed: ${t.message}", t)
            true
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_camera -> { enterVisionMode(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /* ========================== Vision Mode ========================== */

    private fun enterVisionMode() {
        if (!hasAllPerms()) {
            ensurePermissionsOrRequest()
            return
        }
        inVisionMode = true
        binding.visionOverlay.visibility = View.VISIBLE
        startCamera()
        if (ttsReady) speakNow("Camera on")
    }

    private fun exitVisionMode() {
        inVisionMode = false
        binding.visionOverlay.visibility = View.GONE
        stopCamera()
        binding.detectChip.visibility = View.GONE
        if (ttsReady) speakNow("Camera off")
    }

    /* ========================== Model import / selection ========================== */

    private fun chooseModelOrPrompt() {
        val dir = File(filesDir, "models").apply { mkdirs() }

        // Try existing in app storage
        val candidates = dir.listFiles { f -> f.isFile && f.name.endsWith(".task", true) }
            ?.sortedByDescending { it.length() }
            .orEmpty()

        for (f in candidates) {
            val err = LlmEngine_Validator.validate(f.absolutePath)
            if (err == null && LlmEngine.init(this, f.absolutePath)) {
                activeModelPath = f.absolutePath
                modelReady = true
                val mb = String.format(Locale.US, "%.1f", f.length() / 1024.0 / 1024.0)
                binding.resultTextView.text = "Model ready: ${f.name} (${mb} MB). Say a wake word…"
                // Warm-up in background
                lifecycleScope.launch(Dispatchers.IO) {
                    runCatching { withTimeout(15_000L) { LlmEngine.warmUp() } }
                }
                return
            }
        }

        // Attempt auto-import newest from Downloads
        importLatestTaskFromDownloads()?.let { imported ->
            val err = LlmEngine_Validator.validate(imported)
            if (err == null && LlmEngine.init(this, imported)) {
                activeModelPath = imported
                modelReady = true
                val f = File(imported)
                val mb = String.format(Locale.US, "%.1f", f.length() / 1024.0 / 1024.0)
                binding.resultTextView.text =
                    "Model ready (imported): ${f.name} (${mb} MB). Say a wake word…"
                // Warm-up in background
                lifecycleScope.launch(Dispatchers.IO) {
                    runCatching { withTimeout(15_000L) { LlmEngine.warmUp() } }
                }
                return
            } else {
                Log.e(TAG, "Downloads import invalid: $err")
                runCatching { File(imported).delete() }
            }
        }

        // Ask user to pick
        modelReady = false
        binding.resultTextView.text = "Pick your Gemma .task file to import…"
        pickTaskFile.launch(arrayOf("*/*"))
    }

    private fun importLatestTaskFromDownloads(): String? {
        runCatching {
            val dl = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (dl?.exists() == true) {
                val latest = dl.listFiles { f -> f.isFile && f.name.endsWith(".task", true) }
                    ?.maxByOrNull { it.lastModified() }
                if (latest != null && latest.length() > 1_048_576L) {
                    val destDir = File(filesDir, "models").apply { mkdirs() }
                    val dest = File(destDir, latest.name)
                    latest.inputStream().use { inp ->
                        dest.outputStream().use { out -> inp.copyTo(out, 1 shl 20) }
                    }
                    return dest.absolutePath
                }
            }
        }

        return runCatching {
            val proj = arrayOf(
                MediaStore.Downloads._ID,
                MediaStore.Downloads.DISPLAY_NAME,
                MediaStore.Downloads.SIZE,
                MediaStore.Downloads.DATE_MODIFIED
            )
            val sel = "${MediaStore.Downloads.DISPLAY_NAME} LIKE ?"
            val args = arrayOf("%.task")
            val sort = "${MediaStore.Downloads.DATE_MODIFIED} DESC"

            contentResolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI, proj, sel, args, sort
            )?.use { c ->
                if (c.moveToFirst()) {
                    val id = c.getLong(c.getColumnIndexOrThrow(MediaStore.Downloads._ID))
                    val name = c.getString(c.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME))
                    val size = c.getLong(c.getColumnIndexOrThrow(MediaStore.Downloads.SIZE))
                    if (size > 1_048_576L) {
                        val uri = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                            .buildUpon().appendPath(id.toString()).build()
                        val destDir = File(filesDir, "models").apply { mkdirs() }
                        val dest = File(destDir, name ?: "import_${System.currentTimeMillis()}.task")
                        contentResolver.openInputStream(uri)?.use { inp ->
                            dest.outputStream().use { out -> inp.copyTo(out, 1 shl 20) }
                        }
                        return@runCatching dest.absolutePath
                    }
                }
            }
            null
        }.getOrNull()
    }

    private fun copyPickedModelToInternal(uri: Uri) {
        val destDir = File(filesDir, "models").apply { mkdirs() }

        val displayName = runCatching {
            contentResolver.query(
                uri,
                arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                null, null, null
            )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull()?.takeIf { !it.isNullOrBlank() } ?: "import_${System.currentTimeMillis()}.task"

        val destFile = File(destDir, displayName)
        modelReady = false
        binding.resultTextView.text = "Importing ${displayName}…"

        var expectedSize: Long? = null
        runCatching {
            contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)
                ?.use { c ->
                    val idx = c.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (idx >= 0 && c.moveToFirst()) {
                        val s = c.getLong(idx)
                        if (s > 0) expectedSize = s
                    }
                }
        }

        try {
            contentResolver.openInputStream(uri).use { input ->
                destFile.outputStream().use { output ->
                    if (input != null) input.copyTo(output, 1 shl 20)
                }
            }
        } catch (e: Exception) {
            destFile.delete()
            binding.resultTextView.text = "Copy failed: ${e.message}"
            return
        }

        if (!destFile.exists() || destFile.length() < 1024L * 1024L) {
            val len = destFile.length()
            destFile.delete()
            binding.resultTextView.text = "Copy failed: file too small ($len bytes)."
            return
        }
        expectedSize?.let { want ->
            if (destFile.length() != want) {
                val msg = "Copy truncated: got ${destFile.length()} bytes, expected $want."
                destFile.delete()
                binding.resultTextView.text = msg
                return
            }
        }

        val err = LlmEngine_Validator.validate(destFile.absolutePath)
        if (err != null) {
            destFile.delete()
            binding.resultTextView.text = "Bad .task: $err"
            return
        }

        activeModelPath = destFile.absolutePath
        LlmEngine.init(this, activeModelPath!!)
        modelReady = true
        val mb = String.format(Locale.US, "%.1f", destFile.length() / 1024.0 / 1024.0)
        binding.resultTextView.text = "Model imported: ${destFile.name} (${mb} MB). Say a wake word…"

        // Warm-up in background
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { withTimeout(15_000L) { LlmEngine.warmUp() } }
        }
    }

    /* ========================== Permissions ========================== */

    private fun hasAllPerms(): Boolean {
        val cam = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val mic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        return cam && mic
    }

    private fun ensurePermissionsOrRequest() {
        if (hasAllPerms()) {
            initVoskService()
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
        } else {
            Toast.makeText(this, "Camera & mic permissions are required", Toast.LENGTH_LONG).show()
        }
    }

    /* ========================== Vosk ========================== */

    private fun initVoskService() {
        if (myVoskService == null) myVoskService = MyVoskService(this, this)
    }

    override fun onReady() {
        Log.d("Vosk", "Vosk ready")
        turn = Turn.IDLE
        myVoskService?.startListening()
        runOnUiThread {
            if (modelReady) {
                Toast.makeText(this, "Speak now", Toast.LENGTH_SHORT).show()
            } else {
                binding.resultTextView.text = "Say a wake word; to get answers, import a .task (tap here)."
            }
        }
    }

    override fun onPartialResult(hypothesis: String) {
        if (turn != Turn.IDLE) return
        val t = hypothesis.trim()
        runOnUiThread { binding.resultTextView.text = t }

        if (!isAwake && t.isNotEmpty()) {
            val lower = t.lowercase(Locale.getDefault())
            if (wakeWords.any { lower.contains(it) }) {
                isAwake = true
                if (ttsReady) speakNow("I'm listening")
            }
        }
    }

    override fun onFinalResult(result: String) {
        if (turn != Turn.IDLE) return
        val text = result.trim()
        if (text.isEmpty()) return

        if (!isAwake) {
            runOnUiThread { binding.resultTextView.text = "Say a wake word to begin." }
            return
        }
        if (!modelReady) {
            runOnUiThread { binding.resultTextView.text = "Model not loaded yet. Tap to pick your .task file." }
            if (ttsReady) speakNow("Please import the model first.")
            return
        }

        appendAndScroll(ChatMessage("user", text))

        // “open <app>” intent
        extractOpenAppName(text)?.let { appName ->
            val ok = openAppByName(appName)
            if (!ok && ttsReady) speakNow("I couldn't find $appName.")
            return
        }

        turn = Turn.THINKING
        runCatching { myVoskService?.stopListening() }
        runOnUiThread { binding.resultTextView.text = "Thinking…" }
        askLlm(text)
    }

    override fun onError(e: Exception) {
        Log.e("Vosk", "Error: ${e.message}", e)
        if (turn == Turn.IDLE) {
            runOnUiThread { binding.resultTextView.text = "Vosk Error: ${e.message}" }
            runCatching { myVoskService?.startListening() }
        }
    }

    /* ========================== Chat helpers ========================== */

    private fun onUserTyped(text: String) {
        appendAndScroll(ChatMessage(role = "user", content = text))

        if (!modelReady) {
            val warn = "Model not loaded yet. Tap status to import a .task file."
            appendAndScroll(ChatMessage(role = "assistant", content = warn))
            if (ttsReady) speakNow(warn)
            return
        }

        turn = Turn.THINKING
        runCatching { myVoskService?.stopListening() }
        runOnUiThread { binding.resultTextView.text = "Thinking…" }
        askLlm(text)
    }

    private fun appendAndScroll(msg: ChatMessage) {
        chatAdapter.append(msg)
        binding.chatList.post {
            binding.chatList.scrollToPosition(chatAdapter.itemCount - 1)
        }
    }

    /* ========================== Open app command ========================== */

    private fun extractOpenAppName(textRaw: String): String? {
        val lower = textRaw.lowercase(Locale.getDefault()).trim()
        val verbs = listOf("open", "launch", "start")
        val cleaned = lower
            .replace(Regex("\\b(please|now|the|a|an)\\b"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        for (v in verbs) {
            val r = Regex("\\b$v\\s+([\\w .+&-]{2,})")
            r.find(cleaned)?.let { return it.groupValues[1].removeSuffix(" app").trim() }
        }
        Regex("([\\w .+&-]{2,})\\s+app").find(cleaned)?.let { return it.groupValues[1].trim() }
        return null
    }

    private fun openAppByName(appNameRaw: String): Boolean {
        val pm = packageManager
        val appName = appNameRaw.trim()
        if (appName.isEmpty()) return false

        val launcherIntent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
        val activities = pm.queryIntentActivities(launcherIntent, 0)

        val match = activities.firstOrNull {
            it.loadLabel(pm)?.toString()?.trim()?.equals(appName, ignoreCase = true) == true
        } ?: activities.firstOrNull {
            it.loadLabel(pm)?.toString()?.trim()?.lowercase(Locale.getDefault())
                ?.contains(appName.lowercase(Locale.getDefault())) == true
        } ?: return false

        val pkg = match.activityInfo?.packageName ?: return false
        val launch = pm.getLaunchIntentForPackage(pkg) ?: return false

        return try {
            startActivity(launch)
            if (ttsReady) speakNow("Opening $appName")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to launch $pkg", t)
            false
        }
    }

    /* ========================== LLM bridge ========================== */

    private fun makeGemmaPrompt(userText: String) = buildString {
        append("<start_of_turn>user\n")
        append(userText.trim())
        append("<end_of_turn>\n")
        append("<start_of_turn>model\n")
    }

    private fun askLlm(userText: String) {
        if (activeModelPath.isNullOrBlank()) {
            binding.resultTextView.text = "Model not loaded yet."
            return
        }
        llmJob?.cancel()
        val prompt = makeGemmaPrompt(userText)

        llmJob = lifecycleScope.launch {
            var spoke = false
            try {
                val reply = runCatching {
                    withTimeout(LLM_TIMEOUT_MS) {
                        withContext(Dispatchers.IO) { LlmEngine.generateOnce(prompt) }
                    }
                }.getOrElse { "[error: ${it.message ?: it::class.java.simpleName}]" }

                runOnUiThread { binding.resultTextView.text = reply }

                val assistantMsg = if (reply.startsWith("[")) {
                    "Sorry, I couldn't compose a reply. ${reply.removePrefix("[").removeSuffix("]")}"
                } else reply

                appendAndScroll(ChatMessage("assistant", assistantMsg))

                if (ttsReady && assistantMsg.isNotBlank()) {
                    spoke = true
                    speakNow(assistantMsg)
                }
            } finally {
                if (!spoke) {
                    turn = Turn.IDLE
                    runCatching { myVoskService?.startListening() }
                }
            }
        }
    }

    private fun speakNow(text: String) {
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "utt_${System.currentTimeMillis()}")
    }

    /* ========================== CameraX + Detection ========================== */

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            cameraProvider?.unbindAll()

            preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }

            imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build().also { analyzer ->
                    analyzer.setAnalyzer(cameraExecutor) { imageProxy ->
                        try {
                            val bmp = imageProxyToBitmap(imageProxy)
                            if (bmp != null) objectDetector.detect(bmp)
                        } catch (t: Throwable) {
                            Log.e(TAG, "analyze failed", t)
                        } finally {
                            imageProxy.close()
                        }
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
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun stopCamera() {
        runCatching { cameraProvider?.unbindAll() }
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
            yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 90, out)
            val bytes = out.toByteArray()
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (t: Throwable) {
            Log.e(TAG, "imageProxyToBitmap failed", t)
            null
        }
    }

    private fun emojiFor(label: String): String {
        val k = label.lowercase(Locale.getDefault())
        return when {
            "laptop" in k || "computer" in k || "keyboard" in k -> "💻"
            "phone" in k || "cell" in k || "mobile" in k -> "📱"
            "person" in k || "face" in k || "man" in k || "woman" in k -> "🧑"
            "bottle" in k -> "🧴"
            "cup" in k || "mug" in k -> "☕"
            "book" in k -> "📚"
            "chair" in k -> "🪑"
            "tv" in k || "monitor" in k -> "📺"
            "mouse" in k -> "🖱️"
            "backpack" in k || "bag" in k -> "🎒"
            "remote" in k -> "🎛️"
            else -> "👀"
        }
    }

    override fun onResults(results: List<Detection>) {
        if (!inVisionMode || results.isEmpty()) return
        val label = results[0].categories[0].label
        val emoji = emojiFor(label)

        val now = System.currentTimeMillis()
        val canSpeak = label != lastSpokenLabel || (now - lastSpeakAt) >= SPEAK_DEBOUNCE_MS
        if (ttsReady && canSpeak) {
            lastSpokenLabel = label
            lastSpeakAt = now
            speakNow("I see a $label")
        }

        runOnUiThread {
            binding.detectEmoji.text = emoji
            binding.detectText.text = "I see a $label"
            binding.detectChip.visibility = View.VISIBLE
            uiHandler.removeCallbacks(hideDetectChip)
            uiHandler.postDelayed(hideDetectChip, 2000)
        }
    }

    override fun onError(error: String) {
        Log.e("Detector", error)
        runOnUiThread { binding.resultTextView.text = "Detector Error: $error" }
    }
}
