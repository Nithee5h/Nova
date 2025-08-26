package com.example.nova

object CommandRouter {

    /** Add OPEN_APP to existing actions */
    enum class Action {
        START, STOP, TAKE_PICTURE, OPEN_SETTINGS, HELLO, OPEN_APP, UNKNOWN
    }

    /**
     * High-level intent classifier.
     * Keep this fast/forgiving; specifics (like which app) are extracted separately.
     */
    fun match(textRaw: String): Action {
        val text = textRaw.lowercase().trim()

        // --- Existing intents ---
        if (text.matches(Regex("^(start|go|begin)\\b.*"))) return Action.START
        if (text.matches(Regex("^(stop|halt|cancel)\\b.*"))) return Action.STOP
        if (text.contains("take picture") || text.contains("take a picture")
            || text.contains("capture") || text.contains("snap")
        ) return Action.TAKE_PICTURE
        if (text.contains("open settings") || text.contains("settings")) return Action.OPEN_SETTINGS
        if (text.contains("hello") || text.contains("hi")) return Action.HELLO

        // --- New: app launching intents ---
        // Accept "open X", "launch X", "start X", "open the X app", "open youtube", etc.
        if (looksLikeOpenApp(text)) return Action.OPEN_APP

        return Action.UNKNOWN
    }

    /**
     * Extract a human app label candidate from natural language.
     * Returns null if we can't confidently find one.
     *
     * Examples it will parse:
     *  - "open youtube" -> "youtube"
     *  - "launch google maps" -> "google maps"
     *  - "start the whatsapp app" -> "whatsapp"
     *  - "open camera app" -> "camera"
     */
    fun extractAppName(textRaw: String): String? {
        var t = textRaw.lowercase().trim()

        // Remove polite fillers / extras to improve matching
        t = t.replace(Regex("\\b(please|now|the|a|an)\\b"), " ").replace(Regex("\\s+"), " ").trim()

        // 1) verb + name   (open|launch|start) <name>
        val verb = "(open|launch|start)"
        val name = "([\\w .+&-]{2,})" // allow spaces, symbols common in app labels
        val r1 = Regex("\\b$verb\\s+$name\\b")
        r1.find(t)?.let { m ->
            var candidate = m.groupValues[2].trim()
            candidate = candidate.removeSuffix(" app").trim()
            return candidate.ifEmpty { null }
        }

        // 2) name + "app"   e.g., "youtube app", "google maps app"
        val r2 = Regex("\\b$name\\s+app\\b")
        r2.find(t)?.let { m ->
            val candidate = m.groupValues[1].trim()
            if (candidate.isNotEmpty()) return candidate
        }

        // 3) last-resort: after verb, until punctuation
        val r3 = Regex("\\b$verb\\s+([^,.!?]+)")
        r3.find(t)?.let { m ->
            val candidate = m.groupValues[2].trim().removeSuffix(" app").trim()
            if (candidate.isNotEmpty()) return candidate
        }

        return null
    }

    /* ---------- helpers ---------- */

    private fun looksLikeOpenApp(text: String): Boolean {
        // Quick verb presence check
        if (!(text.contains("open ") || text.contains("launch ") || text.contains("start "))) return false

        // Avoid false positives like "open settings" (handled above) or "start/stop recording"
        if (text.contains("settings")) return true // still considered OPEN_APP earlier, but we map explicitly
        if (text.contains("record") || text.contains("timer")) return false

        // If we can extract an app name, it's an OPEN_APP
        return extractAppName(text) != null
    }
}
