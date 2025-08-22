package com.example.nova

interface VoskListener {
    fun onReady()
    fun onPartial(text: String)
    fun onCommand(text: String)
    fun onError(message: String, throwable: Throwable? = null)
}
