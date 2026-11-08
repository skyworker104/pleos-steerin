package com.steerin.parkingmaster.data.outcome

import com.steerin.parkingmaster.domain.model.ParkingScore
import com.steerin.parkingmaster.domain.model.SessionLog
import com.steerin.parkingmaster.domain.outcome.SlotObservation
import com.steerin.parkingmaster.domain.outcome.SlotSource

/** What the app knows at the moment a manoeuvre ends in P. */
data class ParkEvent(val log: SessionLog, val process: ParkingScore)

/**
 * Supplier of the final slot geometry. Today Pleos Connect exposes no surround-view (SVM) or
 * parking-slot data to third-party apps, so production uses [UnavailableOutcomeSource] and the app
 * shows the process score only.
 *
 * When the platform publishes slot geometry, an `SvmOutcomeSource` implements this interface by
 * reading the latest observation at [ParkEvent.log] end time; nothing else in the app changes.
 * The fields we ask for are exactly [SlotObservation]: four gaps, yaw, confidence. No video.
 */
interface ParkingOutcomeSource {
    val kind: SlotSource?
    suspend fun observeAtPark(event: ParkEvent): SlotObservation?
}

/** Production default on today's platform: no SVM data, no result score. */
object UnavailableOutcomeSource : ParkingOutcomeSource {
    override val kind: SlotSource? = null
    override suspend fun observeAtPark(event: ParkEvent): SlotObservation? = null
}

/**
 * Demo-only source, OFF by default and always labelled "가상 데이터" on screen.
 *
 * It returns one of three fixed slot fixtures chosen by the process score, so a smooth manoeuvre
 * shows a well-centred car and a rough one shows a crooked car. The numbers are illustrative,
 * not measured; they exist so the result-score UI and scoring can be shown end to end.
 */
class SimulatedOutcomeSource : ParkingOutcomeSource {
    override val kind: SlotSource = SlotSource.SIMULATED

    override suspend fun observeAtPark(event: ParkEvent): SlotObservation =
        fixtureFor(event.process.total, event.log.endedAtMs)

    companion object {
        fun fixtureFor(processTotal: Int, atMs: Long): SlotObservation = when {
            processTotal >= 85 -> SlotObservation(atMs, leftGapCm = 41f, rightGapCm = 37f, frontGapCm = 32f, rearGapCm = 24f, yawDeg = 0.8f, confidence = 0.93f, source = SlotSource.SIMULATED)
            processTotal >= 65 -> SlotObservation(atMs, leftGapCm = 28f, rightGapCm = 52f, frontGapCm = 12f, rearGapCm = 44f, yawDeg = 2.6f, confidence = 0.9f, source = SlotSource.SIMULATED)
            else -> SlotObservation(atMs, leftGapCm = 14f, rightGapCm = 66f, frontGapCm = -6f, rearGapCm = 62f, yawDeg = 4.4f, confidence = 0.88f, source = SlotSource.SIMULATED)
        }
    }
}
