package com.steerin.parkingmaster.presentation

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.steerin.parkingmaster.domain.session.DrivingState
import com.steerin.parkingmaster.presentation.overlay.CoachNotifier
import com.steerin.parkingmaster.presentation.overlay.GhostOverlayWindow
import com.steerin.parkingmaster.presentation.ui.SteerinApp
import com.steerin.parkingmaster.presentation.ui.theme.SteerinTheme
import com.steerin.parkingmaster.R
import com.steerin.parkingmaster.presentation.ux.UxRestrictionsMonitor
import com.steerin.parkingmaster.presentation.voice.VoiceCoach
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: ParkingViewModel by viewModels()
    private lateinit var voiceCoach: VoiceCoach
    private lateinit var notifier: CoachNotifier
    private lateinit var overlay: GhostOverlayWindow
    private var uxMonitor: UxRestrictionsMonitor? = null

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        viewModel.startSignals()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        voiceCoach = VoiceCoach(this)

        setContent {
            SteerinTheme {
                SteerinApp(viewModel)
            }
        }

        // Voice is collected for the whole Activity lifetime (not only while STARTED): on Pleos Connect the
        // system rear-view camera takes the foreground as soon as R is engaged, and audio-first coaching
        // must keep working behind it. Signals keep flowing because the ViewModel outlives the window.
        lifecycleScope.launch {
            viewModel.voice.collect { if (viewModel.ui.value.voiceEnabled) voiceCoach.handle(it) }
        }

        // Coaching over the rear-view camera: while another app is in front (Activity not resumed)
        // mirror the instruction into a heads-up notification and, where allowed, the ghost overlay.
        notifier = CoachNotifier(this)
        overlay = GhostOverlayWindow(this)
        // Re-evaluated on every UI change *and* every lifecycle change, so the banner appears the moment
        // the camera takes the foreground (even with the car standing still) and clears on return.
        lifecycleScope.launch {
            combine(viewModel.ui, lifecycle.currentStateFlow) { ui, life -> ui to life.isAtLeast(Lifecycle.State.RESUMED) }
                .collect { (ui, inFront) ->
                    val d = ui.tracker.driving
                    if (d is DrivingState.Coaching && !inFront) {
                        val text = getString(ParkingViewModel.headsUpText(d, ui.tracker))
                        notifier.show(text, d.snapped)
                        overlay.show(ui.tracker.snapshot.steeringDeg, d.target.targetDeg, d.target.toleranceDeg, d.snapped, text)
                    } else {
                        notifier.dismiss()
                        overlay.hide()
                    }
                }
        }

        if (isAutomotive()) {
            uxMonitor = UxRestrictionsMonitor(this) { viewModel.setUxRestricted(it) }.also { it.start() }
            val missing = VEHICLE_PERMISSIONS.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
            if (missing.isEmpty()) viewModel.startSignals() else permissionLauncher.launch(missing.toTypedArray())
        } else {
            viewModel.startSignals()
        }
    }

    override fun onDestroy() {
        overlay.hide()
        notifier.dismiss()
        uxMonitor?.stop()
        voiceCoach.shutdown()
        super.onDestroy()
    }

    private fun isAutomotive() = packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)

    companion object {
        /** Runtime permissions requested on Pleos Connect / AAOS. Unknown ones are simply denied. */
        val VEHICLE_PERMISSIONS = arrayOf(
            "pleos.car.permission.READ_CAR_STEERING",
            "pleos.car.permission.CAR_DRIVE",
            "pleos.car.permission.CAR_DOORS",
            "pleos.car.permission.CAR_SAFETY",
            "pleos.car.permission.CAR_SPEED",
            "pleos.car.permission.CAR_INFO",
            "android.car.permission.CAR_SPEED",
            "android.car.permission.CAR_POWERTRAIN",
            "android.permission.POST_NOTIFICATIONS",
        )
    }
}
