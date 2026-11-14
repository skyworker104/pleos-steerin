package com.steerin.parkingmaster.presentation.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SpeechSchedulerTest {

    private val played = ArrayList<Pair<Long, String>>()
    private val s = SpeechScheduler { id, text -> played += id to text }
    private fun lastId() = played.last().first
    private fun texts() = played.map { it.second }

    @Test
    fun `door and speed warnings raised together are both spoken in order`() {
        s.submit("door", urgent = true)
        s.submit("speed", urgent = true)
        assertEquals(listOf("door"), texts()) // speed must not cut the door warning
        s.onDone(lastId())
        assertEquals(listOf("door", "speed"), texts())
    }

    @Test
    fun `a coaching prompt never interrupts a warning and only the latest one plays after it`() {
        s.submit("door", urgent = true)
        s.submit("turn in", urgent = false)
        s.submit("straighten", urgent = false)
        assertEquals(listOf("door"), texts())
        s.onDone(lastId())
        assertEquals(listOf("door", "straighten"), texts())
    }

    @Test
    fun `a warning interrupts a coaching prompt which is replayed afterwards`() {
        s.submit("turn in", urgent = false)
        s.submit("door", urgent = true)
        assertEquals(listOf("turn in", "door"), texts())
        s.onDone(lastId())
        assertEquals(listOf("turn in", "door", "turn in"), texts())
    }

    @Test
    fun `coaching prompts replace each other and stale completions are ignored`() {
        s.submit("a", urgent = false)
        val first = lastId()
        s.submit("b", urgent = false)
        s.onDone(first) // completion timer of the replaced utterance
        s.submit("door", urgent = true)
        assertEquals(listOf("a", "b", "door"), texts())
        s.onDone(lastId())
        assertEquals(listOf("a", "b", "door", "b"), texts())
        s.onDone(lastId())
        assertFalse(s.isSpeaking)
    }

    @Test
    fun `duplicate queued warnings are spoken once`() {
        s.submit("door", urgent = true)
        s.submit("speed", urgent = true)
        s.submit("speed", urgent = true)
        s.onDone(lastId()); s.onDone(lastId())
        assertEquals(listOf("door", "speed"), texts())
        assertFalse(s.isSpeaking)
    }

    @Test
    fun `estimated duration covers the longest warning`() {
        // "도어 또는 트렁크가 열려 있습니다. 확인 후 진행하세요." -> 24 syllables/punct, ~5 s
        val ms = SpeechScheduler.estimatedDurationMs("도어 또는 트렁크가 열려 있습니다. 확인 후 진행하세요.")
        assertEquals(true, ms in 4_500L..6_500L)
    }
}
