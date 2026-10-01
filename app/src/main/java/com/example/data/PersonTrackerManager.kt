package com.example.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.core.content.ContextCompat
import com.example.data.model.HeartRateZone
import com.example.data.model.MotionActivity
import com.example.data.model.PersonBiometrics
import com.example.data.model.RoutePoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

data class LiveTrackingState(
    val isTracking: Boolean = false,
    val isPaused: Boolean = false,
    val activity: MotionActivity = MotionActivity.RUNNING,
    val durationSeconds: Int = 0,
    val distanceMeters: Double = 0.0,
    val currentSpeedKmh: Float = 0f,
    val currentPaceMinPerKm: Double = 0.0,
    val avgPaceMinPerKm: Double = 0.0,
    val stepCount: Int = 0,
    val cadenceSpm: Int = 0,
    val heartRateBpm: Int = 74,
    val heartRateZone: HeartRateZone = HeartRateZone.WARM_UP,
    val caloriesBurned: Int = 0,
    val routePoints: List<RoutePoint> = emptyList(),
    val hasGpsFix: Boolean = false,
    val gpsAccuracyMeters: Float = 0f,
    val detectedMotion: String = "Stationary",
    val elevationGainMeters: Double = 0.0
)

class PersonTrackerManager(
    private val context: Context,
    private val scope: CoroutineScope
) : SensorEventListener, LocationListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    private val _trackingState = MutableStateFlow(LiveTrackingState())
    val trackingState: StateFlow<LiveTrackingState> = _trackingState.asStateFlow()

    private var timerJob: Job? = null
    private var lastLocation: Location? = null
    private var initialStepCount = -1
    private var lastStepTimestamp = 0L
    private val recentStepTimes = ArrayDeque<Long>()

    // Accelerometer peak detection fallback
    private var lastAccMagnitude = 9.8f
    private var accPeakDetected = false

    private var userWeightKg = 75f
    private var userAge = 26
    private var biometrics = PersonBiometrics()

    fun updateBiometrics(weightKg: Float, heightCm: Float, age: Int, gender: String) {
        userWeightKg = weightKg
        userAge = age
        biometrics = PersonBiometrics(weightKg = weightKg, heightCm = heightCm, age = age, gender = gender)
    }

    fun startTracking(activity: MotionActivity) {
        if (_trackingState.value.isTracking) return

        initialStepCount = -1
        lastLocation = null
        recentStepTimes.clear()

        _trackingState.update {
            it.copy(
                isTracking = true,
                isPaused = false,
                activity = activity,
                durationSeconds = 0,
                distanceMeters = 0.0,
                currentSpeedKmh = 0f,
                currentPaceMinPerKm = 0.0,
                avgPaceMinPerKm = 0.0,
                stepCount = 0,
                cadenceSpm = 0,
                heartRateBpm = 75,
                heartRateZone = biometrics.getZoneForBpm(75),
                caloriesBurned = 0,
                routePoints = emptyList(),
                detectedMotion = "Starting..."
            )
        }

        registerSensors()
        startLocationUpdates()
        startTimer()
    }

    fun pauseTracking() {
        if (!_trackingState.value.isTracking || _trackingState.value.isPaused) return
        _trackingState.update { it.copy(isPaused = true, detectedMotion = "Paused") }
    }

    fun resumeTracking() {
        if (!_trackingState.value.isTracking || !_trackingState.value.isPaused) return
        _trackingState.update { it.copy(isPaused = false, detectedMotion = "Resumed") }
    }

    fun stopTracking(): LiveTrackingState {
        val finalState = _trackingState.value
        unregisterSensors()
        stopLocationUpdates()
        timerJob?.cancel()
        timerJob = null

        _trackingState.update {
            it.copy(
                isTracking = false,
                isPaused = false,
                detectedMotion = "Completed"
            )
        }
        return finalState
    }

    fun setManualHeartRate(bpm: Int) {
        val clampedBpm = bpm.coerceIn(40, 220)
        val zone = biometrics.getZoneForBpm(clampedBpm)
        _trackingState.update {
            it.copy(
                heartRateBpm = clampedBpm,
                heartRateZone = zone
            )
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(1000L)
                if (_trackingState.value.isTracking && !_trackingState.value.isPaused) {
                    _trackingState.update { current ->
                        val newDuration = current.durationSeconds + 1

                        // Calculate average pace (min/km)
                        val distKm = current.distanceMeters / 1000.0
                        val avgPace = if (distKm > 0.05) (newDuration / 60.0) / distKm else 0.0

                        // Calculate calories: MET * weightKg * (time_hours)
                        // If heart rate is available, refine with Keytel formula
                        val hours = newDuration / 3600.0
                        val baseCalories = (current.activity.baseMet * userWeightKg * hours).toInt()

                        // Calculate cadence: steps in last 60 seconds
                        val now = System.currentTimeMillis()
                        while (recentStepTimes.isNotEmpty() && (now - recentStepTimes.first()) > 60000) {
                            recentStepTimes.removeFirst()
                        }
                        val cadence = recentStepTimes.size

                        current.copy(
                            durationSeconds = newDuration,
                            avgPaceMinPerKm = avgPace,
                            caloriesBurned = baseCalories.coerceAtLeast(1),
                            cadenceSpm = cadence
                        )
                    }
                }
            }
        }
    }

    private fun registerSensors() {
        sensorManager?.let { sm ->
            // Try Step Detector
            val stepDetector = sm.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
            if (stepDetector != null) {
                sm.registerListener(this, stepDetector, SensorManager.SENSOR_DELAY_UI)
            }

            // Try Step Counter
            val stepCounter = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
            if (stepCounter != null) {
                sm.registerListener(this, stepCounter, SensorManager.SENSOR_DELAY_UI)
            }

            // Accelerometer fallback for steps and motion detection
            val accelerometer = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            if (accelerometer != null) {
                sm.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
            }

            // Heart Rate Sensor if available
            val heartRateSensor = sm.getDefaultSensor(Sensor.TYPE_HEART_RATE)
            if (heartRateSensor != null) {
                sm.registerListener(this, heartRateSensor, SensorManager.SENSOR_DELAY_UI)
            }
        }
    }

    private fun unregisterSensors() {
        sensorManager?.unregisterListener(this)
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val lm = locationManager ?: return
        val hasFine = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) return

        try {
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 2f, this)
            }
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 3000L, 3f, this)
            }
        } catch (_: SecurityException) {
        }
    }

    private fun stopLocationUpdates() {
        locationManager?.removeUpdates(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || !_trackingState.value.isTracking || _trackingState.value.isPaused) return

        when (event.sensor.type) {
            Sensor.TYPE_STEP_DETECTOR -> {
                onStepDetected()
            }
            Sensor.TYPE_STEP_COUNTER -> {
                val totalSteps = event.values[0].toInt()
                if (initialStepCount < 0) {
                    initialStepCount = totalSteps
                }
                val sessionSteps = (totalSteps - initialStepCount).coerceAtLeast(0)
                _trackingState.update { it.copy(stepCount = sessionSteps) }
            }
            Sensor.TYPE_HEART_RATE -> {
                val bpm = event.values[0].toInt()
                if (bpm > 30) {
                    setManualHeartRate(bpm)
                }
            }
            Sensor.TYPE_ACCELEROMETER -> {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                val magnitude = sqrt((x * x + y * y + z * z).toDouble()).toFloat()

                // Peak detection step counting fallback
                val delta = magnitude - lastAccMagnitude
                if (delta > 2.2f && !accPeakDetected) {
                    accPeakDetected = true
                    onStepDetected()
                } else if (delta < -1.5f) {
                    accPeakDetected = false
                }
                lastAccMagnitude = magnitude

                // Motion state detection
                val motion = when {
                    magnitude < 10.5f && magnitude > 9.2f -> "Stationary"
                    magnitude in 10.5f..13.5f -> "Walking"
                    magnitude in 13.5f..19.0f -> "Running"
                    else -> "Vigorous Motion"
                }
                _trackingState.update { it.copy(detectedMotion = motion) }
            }
        }
    }

    private fun onStepDetected() {
        val now = System.currentTimeMillis()
        recentStepTimes.addLast(now)
        lastStepTimestamp = now

        _trackingState.update { current ->
            val newSteps = current.stepCount + 1
            // Estimate stride length: heightCm * 0.415 for men / 0.413 for women (approx 0.72m)
            val strideMeters = (biometrics.heightCm * 0.414f) / 100f
            // If GPS is not active, increment distance from steps
            val addedDistance = if (!current.hasGpsFix) strideMeters.toDouble() else 0.0
            current.copy(
                stepCount = newSteps,
                distanceMeters = current.distanceMeters + addedDistance
            )
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onLocationChanged(location: Location) {
        if (!_trackingState.value.isTracking || _trackingState.value.isPaused) return

        val prev = lastLocation
        var addedDist = 0.0
        var elevationDiff = 0.0

        if (prev != null) {
            val dist = prev.distanceTo(location).toDouble()
            // Filter noise: skip jumps > 50m in 2 seconds unless high speed
            if (dist in 1.0..50.0) {
                addedDist = dist
                if (location.hasAltitude() && prev.hasAltitude()) {
                    val altDiff = location.altitude - prev.altitude
                    if (altDiff > 0) elevationDiff = altDiff
                }
            }
        }
        lastLocation = location

        val speedKmh = if (location.hasSpeed()) location.speed * 3.6f else 0f
        val currentPace = if (speedKmh > 1.0f) 60.0 / speedKmh else 0.0

        val newPoint = RoutePoint(
            latitude = location.latitude,
            longitude = location.longitude,
            altitude = location.altitude,
            timestamp = location.time,
            speedKmh = speedKmh
        )

        _trackingState.update { current ->
            val updatedPoints = current.routePoints + newPoint
            val updatedDist = current.distanceMeters + addedDist

            current.copy(
                hasGpsFix = true,
                gpsAccuracyMeters = location.accuracy,
                currentSpeedKmh = speedKmh,
                currentPaceMinPerKm = currentPace,
                distanceMeters = updatedDist,
                routePoints = updatedPoints,
                elevationGainMeters = current.elevationGainMeters + elevationDiff
            )
        }
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {
        if (provider == LocationManager.GPS_PROVIDER) {
            _trackingState.update { it.copy(hasGpsFix = false) }
        }
    }
}
