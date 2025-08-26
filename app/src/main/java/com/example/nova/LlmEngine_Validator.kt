package com.example.nova.llm

import java.io.File

/** Light validator — MediaPipe GenAI .task may be a FlatBuffer (not a ZIP). */
object LlmEngine_Validator {
    fun validate(path: String): String? {
        val f = File(path)
        if (!f.exists()) return "Model file not found at: $path"
        if (!f.isFile) return "Model path is not a file: $path"
        if (f.length() < 1_048_576L) return "Model file too small (${f.length()} bytes)."
        if (!f.name.endsWith(".task", ignoreCase = true)) return "Expected a .task file; got: ${f.name}"
        return null
    }
}
