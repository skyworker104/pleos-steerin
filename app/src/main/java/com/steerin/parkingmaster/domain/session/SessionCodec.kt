package com.steerin.parkingmaster.domain.session

import com.steerin.parkingmaster.data.vehicle.GearPosition
import com.steerin.parkingmaster.domain.model.ParkingSide
import com.steerin.parkingmaster.domain.model.Sample
import com.steerin.parkingmaster.domain.model.SessionLog
import com.steerin.parkingmaster.domain.model.SessionRecord

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
            samples += Sample(
                timestampMs = p[0].toLong(),
                steeringDeg = p[1].toFloat(),
                speedKmh = p[2].toFloat(),
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
        )
    }

    private fun gearCode(g: GearPosition) = when (g) {
        GearPosition.PARK -> "P"; GearPosition.REVERSE -> "R"; GearPosition.NEUTRAL -> "N"; GearPosition.DRIVE -> "D"; GearPosition.UNKNOWN -> "U"
    }

    private fun gearFromCode(c: String) = when (c) {
        "P" -> GearPosition.PARK; "R" -> GearPosition.REVERSE; "N" -> GearPosition.NEUTRAL; "D" -> GearPosition.DRIVE; else -> GearPosition.UNKNOWN
    }
}
