package com.voiceassistant.app

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.animation.LinearInterpolator
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var orbRing: ImageView
    private lateinit var micButton: ImageButton
    private lateinit var statusText: TextView

    private var tts: TextToSpeech? = null
    private var commandProcessor: CommandProcessor? = null
    private var speechRecognizer: SpeechRecognizer? = null

    private val requiredPermissions = arrayOf(
        android.Manifest.permission.RECORD_AUDIO,
        android.Manifest.permission.CALL_PHONE,
        android.Manifest.permission.SEND_SMS,
        android.Manifest.permission.READ_CONTACTS,
        android.Manifest.permission.CAMERA,
        android.Manifest.permission.BLUETOOTH_CONNECT,
        android.Manifest.permission.READ_CALENDAR,
        android.Manifest.permission.WRITE_CALENDAR,
        android.Manifest.permission.POST_NOTIFICATIONS
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        orbRing = findViewById(R.id.orbRing)
        micButton = findViewById(R.id.micButton)
        statusText = findViewById(R.id.statusText)

        startOrbAnimation()
        requestAllPermissions()

        tts = TextToSpeech(this, this)
        commandProcessor = CommandProcessor(this, tts)

        micButton.setOnClickListener {
            checkAccessibilityAndStart()
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("mr", "IN")
        }
    }

    private fun startOrbAnimation() {
        val animator = ObjectAnimator.ofFloat(orbRing, "rotation", 0f, 360f)
        animator.duration = 3000
        animator.repeatCount = ObjectAnimator.INFINITE
        animator.interpolator = LinearInterpolator()
        animator.start()
    }

    private fun requestAllPermissions() {
        val notGranted = requiredPermissions.filter {
            ActivityCompat.checkSelfPermission(this, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (notGranted.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, notGranted.toTypedArray(), 100)
        }
    }

    private fun checkAccessibilityAndStart() {
        if (!isAccessibilityServiceEnabled()) {
            statusText.text = "कृपया Accessibility सेवा एकदा चालू करा"
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            return
        }
        val serviceIntent = Intent(this, VoiceForegroundService::class.java)
        androidx.core.content.ContextCompat.startForegroundService(this, serviceIntent)
        listenOnceForeground()
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedComponentName = "$packageName/${AssistantAccessibilityService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.contains(expectedComponentName)
    }

    private fun listenOnceForeground() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            statusText.text = "व्हॉइस रेकग्निशन उपलब्ध नाही"
            return
        }
        statusText.text = getString(R.string.listening)

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "mr-IN")

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                statusText.text = getString(R.string.tap_to_speak)
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()
                statusText.text = getString(R.string.tap_to_speak)
                if (!text.isNullOrBlank()) {
                    commandProcessor?.process(text)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        speechRecognizer?.startListening(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
        tts?.shutdown()
    }
}
