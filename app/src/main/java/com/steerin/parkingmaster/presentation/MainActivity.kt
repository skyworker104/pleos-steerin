package com.steerin.parkingmaster.presentation

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.steerin.parkingmaster.presentation.ui.SteerinApp
import com.steerin.parkingmaster.presentation.ui.theme.SteerinTheme
import com.steerin.parkingmaster.presentation.ux.UxRestrictionsMonitor
import com.steerin.parkingmaster.presentation.voice.VoiceCoach
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: ParkingViewModel by viewModels()
    private lateinit var voiceCoach: VoiceCoach
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

        if (isAutomotive()) {
            uxMonitor = UxRestrictionsMonitor(this) { viewModel.setUxRestricted(it) }.also { it.start() }
            val missing = VEHICLE_PERMISSIONS.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
            if (missing.isEmpty()) viewModel.startSignals() else permissionLauncher.launch(missing.toTypedArray())
        } else {
            viewModel.startSignals()
        }
    }

    override fun onDestroy() {
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
        )
    }
}
