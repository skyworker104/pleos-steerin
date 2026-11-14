package com.steerin.parkingmaster.presentation.voice

/**
 * Decides what is spoken when prompts overlap. Pure Kotlin so it is unit-testable.
 *
 * Both speech engines play one utterance at a time and the coach always flushes, so without this
 * a door warning and an over-speed warning raised by the same signal would cut each other off.
 *
 *  - An **urgent** utterance (safety warning) is never interrupted. Urgent prompts raised while one
 *    is playing are queued in order (duplicates dropped).
 *  - A **normal** coaching prompt raised while an urgent one plays is held; only the latest is kept,
 *    since an older instruction is stale by then. It plays after the urgent queue drains.
 *  - A normal prompt playing is replaced by the next normal prompt (stale guidance is useless). An
 *    urgent prompt interrupts it, and the interrupted instruction is replayed afterwards unless a
 *    newer one has arrived by then.
 *
 * The caller reports completion with [onDone] and the id returned through [play]; ids of utterances
 * that were already replaced are ignored.
 */
class SpeechScheduler(private val play: (id: Long, text: String) -> Unit) {

    private var nextId = 1L
    private var currentId: Long? = null
    private var currentUrgent = false
    private var currentText: String? = null
    private val urgentQueue = ArrayDeque<String>()
    private var heldPrompt: String? = null

    val isSpeaking: Boolean get() = currentId != null

    fun submit(text: String, urgent: Boolean) {
        if (currentId != null && currentUrgent) {
            if (urgent) { if (text !in urgentQueue) urgentQueue.addLast(text) } else heldPrompt = text
            return
        }
        if (urgent && currentId != null) heldPrompt = currentText // replay the interrupted instruction
        start(text, urgent)
    }

    fun onDone(id: Long) {
        if (id != currentId) return
        currentId = null
        currentText = null
        val next = urgentQueue.removeFirstOrNull()
        when {
            next != null -> start(next, urgent = true)
            heldPrompt != null -> { val p = heldPrompt!!; heldPrompt = null; start(p, urgent = false) }
        }
    }

    fun clear() {
        currentId = null
        currentText = null
        urgentQueue.clear()
        heldPrompt = null
    }

    private fun start(text: String, urgent: Boolean) {
        val id = nextId++
        currentId = id
        currentUrgent = urgent
        currentText = text
        play(id, text)
    }

    companion object {
        /**
         * Conservative playback length used as the completion signal: neither engine reports
         * completion per utterance reliably (Pleos TTS events carry no utterance id). Korean
         * speech at rate 1.0 is roughly 5 syllables per second.
         */
        fun estimatedDurationMs(text: String): Long {
            val syllables = text.count { !it.isWhitespace() }
            return (400L + syllables * 200L).coerceIn(800L, 10_000L)
        }
    }
}
