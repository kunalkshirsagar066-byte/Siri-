package com.voiceassistant.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import java.util.Locale

class VoiceForegroundService : Service(), TextToSpeech.OnInitListener {

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var commandProcessor: CommandProcessor? = null
    private var isListening = false

    private val CHANNEL_ID = "assistant_channel"
    private val NOTIF_ID = 101

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(this, this)
        commandProcessor = CommandProcessor(this, tts)
        createNotificationChannel()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("mr", "IN")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification())
        startListeningLoop()
        return START_STICKY
    }

    private fun buildNotification(): android.app.Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Siri ऐकत आहे")
            .setContentText("व्हॉइस कमांड्ससाठी तयार")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Assistant Service", NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun startListeningLoop() {
        if (isListening) return
        isListening = true
        listenOnce()
    }

    private fun listenOnce() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "mr-IN")
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: android.os.Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                listenOnce()
            }

            override fun onResults(results: android.os.Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()
                if (!text.isNullOrBlank()) {
                    commandProcessor?.process(text)
                }
                listenOnce()
            }

            override fun onPartialResults(partialResults: android.os.Bundle?) {}
            override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
        })

        speechRecognizer?.startListening(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        isListening = false
        speechRecognizer?.destroy()
        tts?.shutdown()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
