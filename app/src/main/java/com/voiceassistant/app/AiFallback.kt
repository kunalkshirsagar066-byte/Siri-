package com.voiceassistant.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class AiFallback(private val context: Context) {

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun ask(userText: String, onReply: (String) -> Unit) {
        executor.execute {
            val reply = try {
                callClaude(userText)
            } catch (e: Exception) {
                Log.e("AiFallback", "API call failed", e)
                "माफ करा, सध्या इंटरनेट कनेक्शन किंवा API की मध्ये अडचण आहे"
            }
            mainHandler.post { onReply(reply) }
        }
    }

    private fun callClaude(userText: String): String {
        val apiKey = context.getString(R.string.anthropic_api_key)
        if (apiKey.isBlank() || apiKey == "YOUR_API_KEY_HERE") {
            return "AI उत्तरासाठी अ‍ॅपमध्ये Anthropic API की टाकणे आवश्यक आहे"
        }

        val url = URL("https://api.anthropic.com/v1/messages")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("x-api-key", apiKey)
        conn.setRequestProperty("anthropic-version", "2023-06-01")
        conn.doOutput = true
        conn.connectTimeout = 15000
        conn.readTimeout = 20000

        val messages = JSONArray()
        val userMsg = JSONObject()
        userMsg.put("role", "user")
        userMsg.put(
            "content",
            "तू एक मोबाईल असिस्टंट आहेस. वापरकर्त्याने असं म्हटलं: \"$userText\". " +
                "मराठीत, अगदी छोटं (2 वाक्यांपेक्षा जास्त नाही) आणि उपयुक्त उत्तर दे."
        )
        messages.put(userMsg)

        val body = JSONObject()
        body.put("model", "claude-sonnet-4-6")
        body.put("max_tokens", 300)
        body.put("messages", messages)

        OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }

        val responseCode = conn.responseCode
        val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
        val responseText = stream.bufferedReader().use { it.readText() }

        if (responseCode !in 200..299) {
            Log.e("AiFallback", "API error $responseCode: $responseText")
            return "माफ करा, उत्तर मिळवता आलं नाही"
        }

        val json = JSONObject(responseText)
        val content = json.optJSONArray("content") ?: return "उत्तर समजलं नाही"
        val sb = StringBuilder()
        for (i in 0 until content.length()) {
            val block = content.getJSONObject(i)
            if (block.optString("type") == "text") {
                sb.append(block.optString("text"))
            }
        }
        return if (sb.isNotBlank()) sb.toString() else "उत्तर समजलं नाही"
    }
}
