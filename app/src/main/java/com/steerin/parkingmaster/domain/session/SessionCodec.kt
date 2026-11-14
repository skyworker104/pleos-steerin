package com.steerin.parkingmaster.domain.session

import com.steerin.parkingmaster.data.vehicle.GearPosition
import com.steerin.parkingmaster.domain.model.ParkingSide
import com.steerin.parkingmaster.domain.model.Sample
import com.steerin.parkingmaster.domain.model.SessionLog
import com.steerin.parkingmaster.domain.model.SessionRecord
import com.steerin.parkingmaster.domain.outcome.SlotObservation
import com.steerin.parkingmaster.domain.outcome.SlotSource

/**
 * Plain-text, line-oriented session format. Pure Kotlin (no org.json, no serialization plugin)
 * so it is unit-testable on the JVM and trivially diffable:
 *
 * ```
 * steerin-session 1
 * id=20261108-131500-ab12
 * savedAt=1762578900000
 * side=RIGHT
 * start=1000
 * end=25000
 * slot=41.0,37.0,32.0,24.0,0.8,0.93,SIMULATED   (optional: left,right,front,rear,yaw,confidence,source)
 * samples
 * ts,steer,speed,gear,brake,door
 * 1000,0.0,0.0,R,1,0
 * ```
 */
object SessionCodec {
    private const val MAGIC = "steerin-session 1"

    fun encode(record: SessionRecord): String = buildString {
        appendLine(MAGIC)
        appendLine("id=${record.id}")
        appendLine("savedAt=${record.savedAtEpochMs}")
        appendLine("side=${record.side.name}")
        appendLine("start=${record.log.startedAtMs}")
        appendLine("end=${record.log.endedAtMs}")
        record.slot?.let { appendLine("slot=${encodeSlot(it)}") }
        appendLine("samples")
        appendLine("ts,steer,speed,gear,brake,door")
        for (s in record.log.samples) {
            append(s.timestampMs).append(',')
            append(s.steeringDeg).append(',')
            append(s.speedKmh).append(',')
            append(gearCode(s.gear)).append(',')
            append(if (s.brakePressed) 1 else 0).append(',')
            append(if (s.anyDoorOpen) 1 else 0).append('\n')
        }
    }

    fun decode(text: String): SessionRecord {
        val lines = text.lineSequence().iterator()
        require(lines.hasNext() && lines.next().trim() == MAGIC) { "not a steerin session file" }
        val header = HashMap<String, String>()
        while (lines.hasNext()) {
            val line = lines.next().trim()
            if (line == "samples") break
            val i = line.indexOf('=')
            if (i > 0) header[line.substring(0, i)] = line.substring(i + 1)
        }
        if (lines.hasNext()) lines.next() // column header
        val samples = ArrayList<Sample>()
        while (lines.hasNext()) {
            val line = lines.next().trim()
            if (line.isEmpty()) continue
            val p = line.split(',')
            if (p.size < 6) continue
            // A single corrupt row is skipped, not allowed to discard the whole session.
            val ts = p[0].toLongOrNull() ?: continue
            val steer = p[1].toFloatOrNull() ?: continue
            val speed = p[2].toFloatOrNull() ?: continue
            samples += Sample(
                timestampMs = ts,
                steeringDeg = steer,
                speedKmh = speed,
                gear = gearFromCode(p[3]),
                brakePressed = p[4] == "1",
                anyDoorOpen = p[5] == "1",
            )
        }
        val log = SessionLog(
            startedAtMs = header.getValue("start").toLong(),
            endedAtMs = header.getValue("end").toLong(),
            samples = samples,
        )
        return SessionRecord(
            id = header.getValue("id"),
            savedAtEpochMs = header.getValue("savedAt").toLong(),
            side = ParkingSide.valueOf(header["side"] ?: ParkingSide.RIGHT.name),
            log = log,
            slot = header["slot"]?.let { decodeSlot(it, log.endedAtMs) },
        )
    }

    private fun encodeSlot(o: SlotObservation) =
        listOf(o.leftGapCm, o.rightGapCm, o.frontGapCm, o.rearGapCm, o.yawDeg, o.confidence).joinToString(",") + "," + o.source.name

    /** Tolerant: a malformed slot line drops the slot, never the whole session. */
    private fun decodeSlot(v: String, atMs: Long): SlotObservation? = runCatching {
        val p = v.split(',')
        SlotObservation(
            timestampMs = atMs,
            leftGapCm = p[0].toFloat(), rightGapCm = p[1].toFloat(), frontGapCm = p[2].toFloat(), rearGapCm = p[3].toFloat(),
            yawDeg = p[4].toFloat(), confidence = p[5].toFloat(), source = SlotSource.valueOf(p[6]),
        )
    }.getOrNull()

    private fun gearCode(g: GearPosition) = when (g) {
        GearPosition.PARK -> "P"; GearPosition.REVERSE -> "R"; GearPosition.NEUTRAL -> "N"; GearPosition.DRIVE -> "D"; GearPosition.UNKNOWN -> "U"
    }

    private fun gearFromCode(c: String) = when (c) {
        "P" -> GearPosition.PARK; "R" -> GearPosition.REVERSE; "N" -> GearPosition.NEUTRAL; "D" -> GearPosition.DRIVE; else -> GearPosition.UNKNOWN
    }
}
