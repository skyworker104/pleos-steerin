package com.steerin.parkingmaster.presentation.ui

/**
 * Hub emblem of the steering wheel: Hyundai-style oval with a slanted H (issue #4; the team
 * confirmed the emblem may be used for this Hyundai Motor Group contest entry). Geometry is in
 * units of the wheel radius, y pointing down, shared by the Compose wheel and the overlay view.
 * Drawn from simple strokes; replace with the official vector artwork if it is supplied.
 */
internal object WheelEmblem {
    const val OVAL_RX = 0.30f
    const val OVAL_RY = 0.19f
    /** Line segments x1, y1, x2, y2: left stem, right stem (both leaning right), rising crossbar. */
    val LINES = floatArrayOf(
        -0.15f, 0.115f, -0.07f, -0.115f,
        0.07f, 0.115f, 0.15f, -0.115f,
        -0.11f, 0.03f, 0.11f, -0.03f,
    )
    /** Spokes run from the oval edge to just inside the rim. */
    const val SPOKE_OUTER = 0.97f
}
