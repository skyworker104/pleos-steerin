package com.steerin.parkingmaster.domain.model

/** A saved parking session: what was recorded plus when and which side it was. */
data class SessionRecord(
    val id: String,
    val savedAtEpochMs: Long,
    val side: ParkingSide,
    val log: SessionLog,
)

/** Light-weight row for the history list (score recomputed from the log on load). */
data class SessionSummary(
    val id: String,
    val savedAtEpochMs: Long,
    val side: ParkingSide,
    val score: ParkingScore,
    val durationSec: Float,
)
