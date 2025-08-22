// app/src/main/java/com/example/nova/CommandRouter.kt
package com.example.nova

object CommandRouter {

    enum class Action {
        START, STOP, TAKE_PICTURE, OPEN_SETTINGS, HELLO, UNKNOWN
    }

    fun match(textRaw: String): Action {
        val text = textRaw.lowercase().trim()

        return when {
            text.matches(Regex("^(start|go|begin)\\b.*")) -> Action.START
            text.matches(Regex("^(stop|halt|cancel)\\b.*")) -> Action.STOP
            text.contains("take picture") || text.contains("take a picture") ||
                    text.contains("capture") || text.contains("snap") -> Action.TAKE_PICTURE
            text.contains("open settings") || text.contains("settings") -> Action.OPEN_SETTINGS
            text.contains("hello") || text.contains("hi") -> Action.HELLO
            else -> Action.UNKNOWN
        }
    }
}
