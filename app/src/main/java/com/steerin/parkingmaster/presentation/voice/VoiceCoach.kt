package com.steerin.parkingmaster.presentation.voice

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import com.steerin.parkingmaster.presentation.VoiceEvent
import java.util.Locale

/** Minimal contract shared by the Pleos and Android speech back-ends. */
interface VoiceEngine {
    val ready: Boolean
    val unavailable: Boolean
    fun start()
    fun speak(text: String)
    fun shutdown()
}

/** Android platform TextToSpeech (phones, generic emulators). */
class AndroidTtsEngine(private val context: Context) : VoiceEngine {
    @Volatile override var ready = false
        private set
    @Volatile override var unavailable = false
        private set
    private var pending: String? = null
    private var tts: TextToSpeech? = null

    override fun start() {
        tts = TextToSpeech(context) { status ->
            ready = status == TextToSpeech.SUCCESS
            unavailable = !ready
            if (ready) {
                val res = tts?.setLanguage(Locale.KOREAN)
                if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.w(TAG, "Korean voice missing; using default locale")
                    tts?.setLanguage(Locale.getDefault())
                }
                tts?.setSpeechRate(1.0f)
                pending?.let { speak(it) }
                pending = null
            } else {
                Log.w(TAG, "Android TTS unavailable (status=$status)")
            }
        }
    }

    override fun speak(text: String) {
        if (!ready) { pending = text; return }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "steerin-${text.hashCode()}")
    }

    override fun shutdown() {
        runCatching { tts?.stop(); tts?.shutdown() }
    }

    companion object { private const val TAG = "AndroidTts" }
}

/**
 * Audio channel for coaching. Audio-first guidance keeps the driver's eyes on mirrors and the
 * rear-view camera; the ghost ring is the glanceable confirmation, not the primary channel.
 *
 * Engine selection: Pleos TextToSpeech SDK (ON_DEVICE) when the Pleos speech service exists,
 * otherwise Android TextToSpeech. If the preferred engine fails to initialise, the other is used.
 */
class VoiceCoach(private val context: Context) {

    private val engines: List<VoiceEngine> = run {
        val android = AndroidTtsEngine(context)
        buildList {
            // A prompt queued on Pleos TTS is handed to Android TTS if Pleos fails to initialise.
            if (PleosTtsEngine.isAvailable(context)) add(PleosTtsEngine(context) { queued -> queued?.let(android::speak) })
            add(android)
        }
    }.onEach { it.start() }

    private val tone: ToneGenerator? = runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70) }.getOrNull()

    /** Called on the main thread only (Activity lifecycleScope), so no locking is needed. */
    private val handler = Handler(Looper.getMainLooper())
    private val scheduler: SpeechScheduler = SpeechScheduler { id, text ->
        speak(text)
        handler.postDelayed({ scheduler.onDone(id) }, SpeechScheduler.estimatedDurationMs(text))
    }

    fun handle(event: VoiceEvent) {
        when (event) {
            is VoiceEvent.Say -> scheduler.submit(context.getString(event.resId, *event.args.toTypedArray()), event.urgent)
            VoiceEvent.SnapTone -> tone?.startTone(ToneGenerator.TONE_PROP_ACK, 120)
        }
    }

    private fun speak(text: String) {
        // First engine that is ready wins; while none is ready yet, queue on the first one that
        // has not failed so the prompt plays as soon as initialisation completes.
        val target = engines.firstOrNull { it.ready } ?: engines.firstOrNull { !it.unavailable } ?: return
        Log.i(TAG, "speak via ${target::class.simpleName}: $text")
        target.speak(text)
    }

    fun shutdown() {
        handler.removeCallbacksAndMessages(null)
        scheduler.clear()
        engines.forEach { it.shutdown() }
        runCatching { tone?.release() }
    }

    companion object { private const val TAG = "VoiceCoach" }
}
