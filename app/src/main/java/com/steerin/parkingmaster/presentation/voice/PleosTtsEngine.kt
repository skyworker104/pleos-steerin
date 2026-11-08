package com.steerin.parkingmaster.presentation.voice

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.util.Log
import java.util.Locale

/**
 * Voice output through the Pleos TextToSpeech SDK in ON_DEVICE mode.
 *
 * ON_DEVICE needs no Playground app registration and no network, which is what a parking coach
 * needs in an underground car park. Pleos Connect images ship no Android TTS engine, so without
 * this the coaching would be silent on the real platform.
 */
class PleosTtsEngine(context: Context) : VoiceEngine {

    private val tts = TextToSpeech(context.applicationContext, Mode.ON_DEVICE)

    @Volatile override var ready: Boolean = false
        private set
    @Volatile private var failed = false
    private var pending: String? = null

    private val events = object : EventListener {
        override fun onStart() = Unit
        override fun onReady() = Unit
        override fun onDone() = Unit
        override fun onStop() = Unit
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onError(errorType: ErrorType) {
            Log.w(TAG, "Pleos TTS error: $errorType")
        }
    }

    override fun start() {
        // The SDK builds its internal engine inside initialize(); every other call must come after onSuccess.
        tts.initialize(object : OnInitializeListener {
            override fun onSuccess() {
                Log.i(TAG, "Pleos TTS ready (ON_DEVICE)")
                runCatching { tts.addEventListener(events) }
                runCatching {
                    tts.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build(),
                    )
                }
                runCatching { tts.setLocale(Locale.KOREA) }
                ready = true
                pending?.let { speak(it) }
                pending = null
            }

            override fun onFailure(errorType: ErrorType) {
                Log.w(TAG, "Pleos TTS init failed: $errorType")
                failed = true
                ready = false
            }
        })
    }

    override val unavailable: Boolean get() = failed

    override fun speak(text: String) {
        if (!ready) {
            pending = text
            return
        }
        runCatching {
            tts.stop()
            tts.speak(text, true)
        }.onFailure { Log.w(TAG, "speak failed: ${it.message}") }
    }

    override fun shutdown() {
        runCatching { tts.removeEventListener(events) }
        runCatching { tts.stop() }
        runCatching { tts.release() }
    }

    companion object {
        private const val TAG = "PleosTts"

        /** True when the Pleos speech service (CaaS) is installed, i.e. on a Pleos Connect image. */
        fun isAvailable(context: Context): Boolean =
            context.packageManager.queryIntentServices(Intent("ai.pleos.playground.caas.intent.action.SPEECH_START"), 0).isNotEmpty()
    }
}
