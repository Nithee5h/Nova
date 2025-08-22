package com.example.nova.net

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

object LlmClient {

    // --- IMPORTANT ---
    // Emulator -> "http://10.0.2.2:8000/chat"
    // Physical device -> "http://<YOUR-MAC-LAN-IP>:8000/chat"
    private const val SERVER_URL = "\"http://10.0.2.2:8000/chat\""

    private val client = OkHttpClient.Builder()
        .retryOnConnectionFailure(true)
        .build()

    private fun ChatRequest.toJson(): String {
        val arr = JSONArray()
        for (m in messages) arr.put(JSONObject(mapOf("role" to m.role, "content" to m.content)))
        return JSONObject(
            mapOf(
                "messages" to arr,
                "max_tokens" to max_tokens,
                "temperature" to temperature,
                "top_p" to top_p,
                "stream" to stream
            )
        ).toString()
    }

    /** Streams plain-text tokens from the FastAPI server. */
    fun chatStream(req: ChatRequest): Flow<String> = callbackFlow {
        val body = req.toJson().toRequestBody("application/json".toMediaType())
        val call = client.newCall(Request.Builder().url("http://<YOUR-MAC-IP>:8000/chat").post(body).build())

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                trySend("[error: ${e.message}]"); close(e)
            }
            override fun onResponse(call: Call, response: Response) {
                response.body?.source()?.use { src ->
                    try {
                        while (!src.exhausted()) {
                            val line = src.readUtf8Line() ?: continue
                            if (line.isNotEmpty()) trySend(line)
                        }
                    } finally { close() }
                } ?: close()
            }
        })

        awaitClose { call.cancel() }
    }
}
