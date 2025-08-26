package com.example.nova.llm

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference

/**
 * Minimal wrapper around MediaPipe GenAI LLM Inference.
 * Works with .task files stored on disk (e.g. /storage/emulated/0/Download/xxx.task).
 */
object LlmEngine {

    private const val TAG = "LlmEngine"
    private var engine: LlmInference? = null

    /**
     * Initialize the engine with a .task bundle located at [taskPath].
     * Returns true if the engine is ready.
     */
    fun init(ctx: Context, taskPath: String): Boolean {
        close()
        return try {
            // Use setModelPath() because the file is on disk (not in assets)
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(taskPath)
                // Keep conservative to reduce latency/heap on low-end phones
                .setMaxTokens(64)
                .build()

            engine = LlmInference.createFromOptions(ctx, options)
            Log.d(TAG, "Engine initialized OK with: $taskPath")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to initialize engine with $taskPath", t)
            false
        }
    }

    /** Close the current engine safely. */
    fun close() {
        try { engine?.close() } catch (_: Throwable) { /* ignore */ }
        engine = null
    }

    /**
     * One-shot generation. Returns either the model text or a short "[error: ...]" string.
     * NOTE: This is a blocking call — run it off the main thread.
     */
    fun generateOnce(prompt: String): String {
        val e = engine ?: return "[error: model not initialized]"
        return try {
            e.generateResponse(prompt)
        } catch (t: Throwable) {
            "[error: ${t.message ?: t::class.java.simpleName}]"
        }
    }

    /**
     * Optional: first-call warm-up to trigger model load & JIT so later calls are faster.
     * Safe to ignore failures.
     */
    fun warmUp() {
        val e = engine ?: return
        try {
            e.generateResponse("<start_of_turn>user\nhi\n<end_of_turn>\n<start_of_turn>model\n")
        } catch (_: Throwable) {
            // ignore warm-up errors
        }
    }
}
