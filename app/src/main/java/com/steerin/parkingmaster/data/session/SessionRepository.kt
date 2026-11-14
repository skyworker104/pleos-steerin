package com.steerin.parkingmaster.data.session

import android.content.Context
import android.util.Log
import com.steerin.parkingmaster.domain.model.ParkingSide
import com.steerin.parkingmaster.domain.model.SessionLog
import com.steerin.parkingmaster.domain.model.SessionRecord
import com.steerin.parkingmaster.domain.model.SessionSummary
import com.steerin.parkingmaster.domain.outcome.SlotObservation
import com.steerin.parkingmaster.domain.outcome.SlotOutcomeEvaluator
import com.steerin.parkingmaster.domain.scoring.ParkingScoreEvaluator
import com.steerin.parkingmaster.domain.session.SessionCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * One file per session under `filesDir/sessions/`. Everything stays on the device: parking logs
 * are driving data and never leave the car unless the driver explicitly exports them.
 */
class SessionRepository(
    context: Context,
    private val evaluator: ParkingScoreEvaluator = ParkingScoreEvaluator(),
    private val outcomeEvaluator: SlotOutcomeEvaluator = SlotOutcomeEvaluator(),
    private val maxSessions: Int = 50,
) {
    private val dir = File(context.filesDir, "sessions").apply { mkdirs() }

    private val _summaries = MutableStateFlow<List<SessionSummary>>(emptyList())
    val summaries: StateFlow<List<SessionSummary>> = _summaries.asStateFlow()

    suspend fun refresh() = withContext(Dispatchers.IO) {
        // Leftovers of a write interrupted before its rename (see save) are never valid sessions.
        dir.listFiles { f -> f.extension == "tmp" && System.currentTimeMillis() - f.lastModified() > 60_000 }.orEmpty().forEach { it.delete() }
        val list = dir.listFiles { f -> f.extension == "txt" }.orEmpty()
            .mapNotNull { f -> runCatching { toSummary(SessionCodec.decode(f.readText())) }.onFailure { Log.w(TAG, "skip ${f.name}: ${it.message}") }.getOrNull() }
            .sortedByDescending { it.savedAtEpochMs }
        _summaries.value = list
    }

    suspend fun save(log: SessionLog, side: ParkingSide, slot: SlotObservation? = null): SessionRecord = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val id = ID_FORMAT.format(Instant.ofEpochMilli(now)) + "-" + (now % 10_000).toString().padStart(4, '0')
        val record = SessionRecord(id, now, side, log, slot)
        // Write to a temp file and rename, so an interrupted write never leaves a truncated session.
        val tmp = File(dir, "$id.tmp")
        tmp.writeText(SessionCodec.encode(record))
        if (!tmp.renameTo(File(dir, "$id.txt"))) { tmp.delete(); error("rename failed for $id") }
        trim()
        refresh()
        record
    }

    suspend fun load(id: String): SessionRecord? = withContext(Dispatchers.IO) {
        val f = File(dir, "$id.txt")
        if (!f.exists()) null else runCatching { SessionCodec.decode(f.readText()) }.getOrNull()
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        File(dir, "$id.txt").delete()
        refresh()
    }

    private fun trim() {
        dir.listFiles { f -> f.extension == "txt" }.orEmpty()
            .sortedByDescending { it.lastModified() }
            .drop(maxSessions)
            .forEach { it.delete() }
    }

    private fun toSummary(r: SessionRecord): SessionSummary {
        val score = evaluator.evaluate(r.log)
        val outcome = outcomeEvaluator.evaluate(r.slot)
        return SessionSummary(
            id = r.id, savedAtEpochMs = r.savedAtEpochMs, side = r.side,
            score = score, durationSec = r.log.durationSec,
            outcome = outcome, combinedTotal = outcomeEvaluator.combined(score.total, outcome),
        )
    }

    companion object {
        private const val TAG = "SessionRepo"
        /** Immutable and thread-safe (SimpleDateFormat is not, and saves run on Dispatchers.IO). */
        private val ID_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.US).withZone(ZoneId.systemDefault())
    }
}
