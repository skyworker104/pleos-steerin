package com.steerin.parkingmaster.presentation.voice

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.speech.tts.TextToSpeech
import android.util.Log
import com.steerin.parkingmaster.presentation.VoiceEvent
import java.util.Locale

/**
 * Audio channel for coaching. Audio-first guidance keeps the driver's eyes on mirrors and the
 * rear-view camera; the ghost ring is the glanceable confirmation, not the primary channel.
 *
 * Uses Android TextToSpeech today; swaps to the Pleos TextToSpeech SDK once it opens to
 * third-party apps (same prompt strings).
 */
class VoiceCoach(private val context: Context) {

    private var ready = false
    private var pending: String? = null
    private val tts: TextToSpeech = TextToSpeech(context) { status ->
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            val res = tts.setLanguage(Locale.KOREAN)
            if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "Korean TTS voice missing; falling back to default locale")
                tts.setLanguage(Locale.getDefault())
            }
            tts.setSpeechRate(1.0f)
            pending?.let { speak(it) }
            pending = null
        }
    }
    private val tone: ToneGenerator? = runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70) }.getOrNull()

    fun handle(event: VoiceEvent) {
        when (event) {
            is VoiceEvent.Say -> speak(context.getString(event.resId, *event.args.toTypedArray()))
            VoiceEvent.SnapTone -> tone?.startTone(ToneGenerator.TONE_PROP_ACK, 120)
        }
    }

    private fun speak(text: String) {
        if (!ready) { pending = text; return }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "steerin-${text.hashCode()}")
    }

    fun shutdown() {
        runCatching { tts.stop(); tts.shutdown() }
        runCatching { tone?.release() }
    }

    companion object {
        private const val TAG = "VoiceCoach"
    }
}
