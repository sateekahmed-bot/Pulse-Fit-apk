package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.dao.MeasurementDao
import com.example.data.dao.NutritionDao
import com.example.data.dao.PersonTrackingDao
import com.example.data.dao.TrainingPlanDao
import com.example.data.dao.UserDao
import com.example.data.dao.WorkoutDao
import com.example.data.model.BodyMeasurement
import com.example.data.model.DailyTarget
import com.example.data.model.ExerciseSet
import com.example.data.model.MealEntry
import com.example.data.model.PersonTrackRecord
import com.example.data.model.PlanDayWorkout
import com.example.data.model.TrainingPlan
import com.example.data.model.UserProfile
import com.example.data.model.WorkoutExercise
import com.example.data.model.WorkoutSession

@Database(
    entities = [
        WorkoutSession::class,
        WorkoutExercise::class,
        ExerciseSet::class,
        MealEntry::class,
        DailyTarget::class,
        UserProfile::class,
        TrainingPlan::class,
        PlanDayWorkout::class,
        BodyMeasurement::class,
        PersonTrackRecord::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao
    abstract fun nutritionDao(): NutritionDao
    abstract fun userDao(): UserDao
    abstract fun trainingPlanDao(): TrainingPlanDao
    abstract fun measurementDao(): MeasurementDao
    abstract fun personTrackingDao(): PersonTrackingDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pulsefit_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
