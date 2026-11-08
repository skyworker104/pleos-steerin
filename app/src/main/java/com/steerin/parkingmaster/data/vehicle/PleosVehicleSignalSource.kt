package com.steerin.parkingmaster.data.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.DoorPositionListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.SteeringWheelAngleListener
import android.content.Context
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update

/**
 * Production data path on Pleos Connect.
 *
 *  - Steering angle, gear, brake pedal, doors/trunk: Pleos Vehicle SDK 2.3.2 (`ai.pleos.playground:Vehicle`).
 *  - Vehicle speed: AAOS `CarPropertyManager.PERF_VEHICLE_SPEED` via [AaosCarSignalSource], because the
 *    Vehicle SDK has no speed domain and the official FAQ points apps at the AAOS property the cluster uses.
 *
 * Every SDK call is asynchronous (IPC); capability checks gate each listener registration so an
 * unsupported signal degrades gracefully instead of crashing the app.
 */
class PleosVehicleSignalSource(
    context: Context,
    private val aaos: AaosCarSignalSource = AaosCarSignalSource(context),
) : VehicleSignalSource {

    private val appContext = context.applicationContext
    private val vehicle = Vehicle(appContext)

    private val _status = MutableStateFlow(SourceStatus(SourceKind.PLEOS_VEHICLE_SDK))
    override val status: StateFlow<SourceStatus> = _status.asStateFlow()

    private val _signals = MutableSharedFlow<VehicleSignal>(
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val signals: Flow<VehicleSignal> = merge(_signals.asSharedFlow(), aaos.signals)

    private fun now() = SystemClock.elapsedRealtime()
    private fun emit(signal: VehicleSignal) {
        _signals.tryEmit(signal)
    }

    // --- listeners (kept as fields so they can be unregistered) ---------------------------

    private val steeringListener = object : SteeringWheelAngleListener {
        override fun onSteeringWheelAngleUpdated(value: Float?) {
            value?.let { emit(VehicleSignal.SteeringAngle(it, now())) }
        }
        override fun onFailed(e: Exception) = fail("steering", e)
    }

    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) {
            emit(VehicleSignal.GearChanged(gear.toGearPosition(), now()))
        }
        override fun onFailed(e: Exception) = fail("gear", e)
    }

    private val brakeListener = object : BrakePedalStatusListener {
        override fun onBrakePedalStatusUpdated(pressed: Boolean?) {
            pressed?.let { emit(VehicleSignal.BrakePedal(it, now())) }
        }
        override fun onFailed(e: Exception) = fail("brake", e)
    }

    private val doorAreas: Map<DoorSlot, DoorArea> = mapOf(
        DoorSlot.FRONT_LEFT to DoorArea(DoorValue.ROW_1_LEFT),
        DoorSlot.FRONT_RIGHT to DoorArea(DoorValue.ROW_1_RIGHT),
        DoorSlot.REAR_LEFT to DoorArea(DoorValue.ROW_2_LEFT),
        DoorSlot.REAR_RIGHT to DoorArea(DoorValue.ROW_2_RIGHT),
        DoorSlot.TRUNK to DoorArea(DoorValue.REAR),
    )
    private val doorListeners = HashMap<DoorSlot, DoorPositionListener>()

    private var started = false

    override fun start() {
        if (started) return
        started = true
        try {
            vehicle.initialize()
            _status.update { it.copy(connected = true, detail = "Vehicle Service connected") }
        } catch (e: Exception) {
            _status.update { it.copy(connected = false, detail = "initialize failed: ${e.message}") }
            Log.w(TAG, "Vehicle.initialize failed", e)
            return
        }
        aaos.start() // speed (+ gear when permitted) via AAOS Car API
        _status.update { it.copy(speedAvailable = aaos.speedAvailable) }
        registerSteering()
        registerGear()
        registerBrake()
        registerDoors()
    }

    private fun registerSteering() {
        val sw = vehicle.getSteeringWheel()
        sw.checkSteeringWheelAngleCapabilities(
            { config, bounds ->
                val readable = config?.accessType == AccessType.READ || config?.accessType == AccessType.READ_WRITE
                Log.i(TAG, "steering capability=$readable bounds=${bounds?.minValue}..${bounds?.maxValue}")
                if (readable) {
                    sw.getCurrentSteeringWheelAngle({ v -> v?.let { emit(VehicleSignal.SteeringAngle(it, now())) } }, { fail("steering.get", it) })
                    sw.registerSteeringWheelAngle(steeringListener)
                    _status.update { it.copy(steeringAvailable = true) }
                }
            },
            { fail("steering.capability", it) },
        )
    }

    /**
     * Gear: AAOS `GEAR_SELECTION` first (the Pleos Gear domain is marked deprecated in SDK 2.3.2 with
     * the note "use the Google GEAR_SELECTION property"); the Pleos domain remains as a fallback for
     * builds where the AAOS permission is not granted.
     */
    @Suppress("DEPRECATION")
    private fun registerGear() {
        if (aaos.gearAvailable) {
            _status.update { it.copy(gearAvailable = true) }
            return
        }
        val gear = vehicle.getGear()
        gear.getCurrentGearSelectionState(
            { g -> emit(VehicleSignal.GearChanged(g.toGearPosition(), now())); _status.update { it.copy(gearAvailable = true) } },
            { e -> fail("gear.get", e) },
        )
        gear.registerGearSelection(gearListener)
        pleosGearRegistered = true
    }

    private var pleosGearRegistered = false

    private fun registerBrake() {
        val brake = vehicle.getBrake()
        brake.checkBrakePedalStatusCapabilities(
            { config ->
                if (config?.accessType == AccessType.READ || config?.accessType == AccessType.READ_WRITE) {
                    brake.getCurrentBrakePedalStatus({ p -> p?.let { emit(VehicleSignal.BrakePedal(it, now())) } }, { fail("brake.get", it) })
                    brake.registerBrakePedalStatus(brakeListener)
                    _status.update { it.copy(brakeAvailable = true) }
                }
            },
            { fail("brake.capability", it) },
        )
    }

    private fun registerDoors() {
        val door = vehicle.getDoor()
        door.checkDoorPositionCapabilities(
            { _, areaMap ->
                val supported = areaMap?.keys ?: emptySet()
                doorAreas.forEach { (slot, area) ->
                    if (supported.none { it == area }) return@forEach
                    val listener = object : DoorPositionListener {
                        override fun onPositionUpdated(pos: Int?) {
                            pos?.let { emit(VehicleSignal.DoorState(slot, it > 0, now())) }
                        }
                        override fun onFailed(e: Exception) = fail("door.$slot", e)
                    }
                    doorListeners[slot] = listener
                    door.getCurrentDoorPosition(area, { p -> p?.let { emit(VehicleSignal.DoorState(slot, it > 0, now())) } }, { fail("door.get.$slot", it) })
                    door.registerDoorPosition(area, listener)
                }
                _status.update { it.copy(doorAvailable = doorListeners.isNotEmpty()) }
            },
            { fail("door.capability", it) },
        )
    }

    override fun stop() {
        if (!started) return
        started = false
        runCatching { vehicle.getSteeringWheel().unregisterSteeringWheelAngle(steeringListener) }
        @Suppress("DEPRECATION")
        if (pleosGearRegistered) runCatching { vehicle.getGear().unregisterGearSelection(gearListener) }
        runCatching { vehicle.getBrake().unregisterBrakePedalStatus(brakeListener) }
        val door = vehicle.getDoor()
        doorListeners.forEach { (slot, l) -> runCatching { door.unregisterDoorPosition(doorAreas.getValue(slot), l) } }
        doorListeners.clear()
        aaos.stop()
        runCatching { vehicle.release() }
        _status.update { it.copy(connected = false, detail = "released") }
    }

    private fun fail(what: String, e: Exception?) {
        Log.w(TAG, "Pleos SDK $what failed: ${e?.message}")
        _status.update { it.copy(detail = "$what: ${e?.message ?: "unavailable"}") }
    }

    private fun VehicleGear?.toGearPosition(): GearPosition = when (this) {
        VehicleGear.GEAR_PARK -> GearPosition.PARK
        VehicleGear.GEAR_REVERSE -> GearPosition.REVERSE
        VehicleGear.GEAR_NEUTRAL -> GearPosition.NEUTRAL
        null, VehicleGear.GEAR_UNKNOWN -> GearPosition.UNKNOWN
        else -> GearPosition.DRIVE // DRIVE and manual gears 1..9 all mean "forward"
    }

    companion object {
        private const val TAG = "PleosSignalSource"

        /** True when a Pleos Vehicle Service is installed (i.e. we are on a Pleos Connect AVD or vehicle). */
        fun isAvailable(context: Context): Boolean {
            val intent = android.content.Intent("ai.pleos.playground.vehicle.VEHICLE_START")
            return context.packageManager.queryIntentServices(intent, 0).isNotEmpty()
        }
    }
}
