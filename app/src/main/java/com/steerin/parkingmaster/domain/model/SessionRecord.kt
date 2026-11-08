package com.steerin.parkingmaster.domain.model

import com.steerin.parkingmaster.domain.outcome.OutcomeScore
import com.steerin.parkingmaster.domain.outcome.SlotObservation

/** A saved parking session: what was recorded plus when and which side it was. */
data class SessionRecord(
    val id: String,
    val savedAtEpochMs: Long,
    val side: ParkingSide,
    val log: SessionLog,
    /** Final slot geometry, when an outcome source supplied one (SVM, or the labelled demo source). */
    val slot: SlotObservation? = null,
)

/** Light-weight row for the history list (score recomputed from the log on load). */
data class SessionSummary(
    val id: String,
    val savedAtEpochMs: Long,
    val side: ParkingSide,
    val score: ParkingScore,
    val durationSec: Float,
    /** Result score; null when no slot data was available for this session. */
    val outcome: OutcomeScore? = null,
    /** Process/result blend, equal to score.total when [outcome] is null. */
    val combinedTotal: Int = score.total,
)
