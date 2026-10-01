package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Entity(tableName = "person_track_records")
data class PersonTrackRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val activityType: String = "Outdoor Run", // "Outdoor Run", "Outdoor Walk", "Cycling", "Hiking", "Cardio"
    val timestamp: Long = System.currentTimeMillis(),
    val durationSeconds: Int = 0,
    val distanceMeters: Double = 0.0,
    val steps: Int = 0,
    val avgHeartRate: Int = 0,
    val maxHeartRate: Int = 0,
    val caloriesBurned: Int = 0,
    val avgPaceMinPerKm: Double = 0.0,
    val routePointsJson: String = "", // Serialized coordinates "lat,lng,alt;lat,lng,alt"
    val motionType: String = "Running",
    val dateKey: String = getCurrentDateKey()
) {
    fun getFormattedDistance(): String {
        return if (distanceMeters >= 1000) {
            String.format(Locale.getDefault(), "%.2f km", distanceMeters / 1000.0)
        } else {
            String.format(Locale.getDefault(), "%.0f m", distanceMeters)
        }
    }

    fun getFormattedDuration(): String {
        val mins = durationSeconds / 60
        val secs = durationSeconds % 60
        val hrs = mins / 60
        return if (hrs > 0) {
            String.format(Locale.getDefault(), "%d:%02d:%02d", hrs, mins % 60, secs)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", mins, secs)
        }
    }

    fun getFormattedPace(): String {
        if (avgPaceMinPerKm <= 0.0 || avgPaceMinPerKm > 60.0) return "--:--"
        val mins = avgPaceMinPerKm.toInt()
        val secs = ((avgPaceMinPerKm - mins) * 60).toInt()
        return String.format(Locale.getDefault(), "%d'%02d\"/km", mins, secs)
    }

    companion object {
        fun getCurrentDateKey(): String {
            return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        }
    }
}

data class RoutePoint(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 0.0,
    val timestamp: Long = System.currentTimeMillis(),
    val speedKmh: Float = 0f
)

enum class MotionActivity(val displayName: String, val iconEmoji: String, val baseMet: Float) {
    RUNNING("Running", "🏃", 9.8f),
    WALKING("Walking", "🚶", 3.8f),
    CYCLING("Cycling", "🚴", 7.5f),
    HIKING("Hiking", "🥾", 6.0f),
    INDOOR_CARDIO("Cardio", "⚡", 8.0f)
}

enum class HeartRateZone(
    val title: String,
    val percentageRange: String,
    val description: String,
    val colorHex: Long
) {
    RESTING("Resting", "< 60%", "Recovery & Rest", 0xFF64B5F6),
    WARM_UP("Warm-Up", "60 - 70%", "Aerobic Base Building", 0xFF81C784),
    FAT_BURN("Fat Burn", "70 - 80%", "Optimal Weight Loss", 0xFFFFB74D),
    CARDIO("Cardio", "80 - 90%", "Cardiovascular Endurance", 0xFFFF7043),
    PEAK("Peak Max", "90 - 100%", "High Intensity Anaerobic", 0xFFE53935)
}

data class PersonBiometrics(
    val weightKg: Float = 75f,
    val heightCm: Float = 178f,
    val age: Int = 26,
    val gender: String = "Male",
    val restingHeartRate: Int = 65
) {
    val bmi: Float = weightKg / ((heightCm / 100f) * (heightCm / 100f))
    val bmiCategory: String = when {
        bmi < 18.5f -> "Underweight"
        bmi < 24.9f -> "Normal Weight"
        bmi < 29.9f -> "Overweight"
        else -> "Obese"
    }

    // Max Heart Rate formula (Gellish formula: 207 - 0.7 * age)
    val maxHeartRate: Int = (207 - (0.7 * age)).toInt()

    fun getZoneForBpm(bpm: Int): HeartRateZone {
        val pct = (bpm.toFloat() / maxHeartRate.toFloat()) * 100f
        return when {
            pct < 60f -> HeartRateZone.RESTING
            pct < 70f -> HeartRateZone.WARM_UP
            pct < 80f -> HeartRateZone.FAT_BURN
            pct < 90f -> HeartRateZone.CARDIO
            else -> HeartRateZone.PEAK
        }
    }
}
