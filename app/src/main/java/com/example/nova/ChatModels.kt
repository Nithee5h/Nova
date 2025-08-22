package com.example.nova.net

data class ChatMessage(val role: String, val content: String)

data class ChatRequest(
    val messages: List<ChatMessage>,
    val max_tokens: Int = 256,
    val temperature: Double = 0.7,
    val top_p: Double = 0.95,
    val stream: Boolean = true
)
