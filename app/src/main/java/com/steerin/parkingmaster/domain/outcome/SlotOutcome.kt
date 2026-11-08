package com.steerin.parkingmaster.domain.outcome

/**
 * Where the data came from. Only [SVM] is real; [SIMULATED] exists so the result-score path can be
 * demonstrated before Pleos exposes surround-view data, and is always labelled as such on screen.
 */
enum class SlotSource { SVM, SIMULATED }

/**
 * Parking-slot geometry at the moment the car is put in P, as an SVM / parking-assist ECU could
 * publish it. This is the *derived* result we ask the platform for, not raw camera video:
 * a handful of numbers carries no faces or licence plates and costs nothing to process.
 *
 * Gaps are measured from the vehicle body to the slot line on that side, in centimetres.
 * A negative gap means the body is over the line.
 *  - [frontGapCm]: front bumper to the slot entry line (negative = nose sticking out).
 *  - [rearGapCm]: rear bumper to the slot end line / wheel stopper.
 *  - [yawDeg]: vehicle centre axis vs slot centre axis (0 = parallel to the lines).
 */
data class SlotObservation(
    val timestampMs: Long,
    val leftGapCm: Float,
    val rightGapCm: Float,
    val frontGapCm: Float,
    val rearGapCm: Float,
    val yawDeg: Float,
    /** 0..1. Below the evaluator's threshold the result score is not shown at all. */
    val confidence: Float,
    val source: SlotSource,
) {
    val insideSlot: Boolean get() = minOf(leftGapCm, rightGapCm, frontGapCm, rearGapCm) >= 0f
}

enum class OutcomeCategory(val maxPoints: Int) {
    IN_SLOT(30),
    CENTERING(30),
    DEPTH(20),
    ALIGNMENT(20),
}

data class OutcomeCategoryScore(val category: OutcomeCategory, val points: Int)

/** Result-quality score (0..100) computed from a [SlotObservation]. */
data class OutcomeScore(
    val total: Int,
    val categories: List<OutcomeCategoryScore>,
    val observation: SlotObservation,
) {
    val weakestCategory: OutcomeCategory?
        get() = categories.minByOrNull { it.points.toFloat() / it.category.maxPoints }
            ?.takeIf { it.points < it.category.maxPoints }?.category
}
