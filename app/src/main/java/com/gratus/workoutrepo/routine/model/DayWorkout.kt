package com.gratus.workoutrepo.routine.model

import androidx.annotation.Keep
@Keep
class DayWorkout(// "MONDAY", etc.
    @JvmField var dayName: String?
) {
    @JvmField var workoutType: String? = ""
    @JvmField var majorWorkouts: String? = ""
    @JvmField var minorWorkouts: String? = ""
    @JvmField var majorLabel: String? = ""
    @JvmField var minorLabel: String? = ""
    @JvmField var notes: String? = ""
}

// TODO -> Dynamic workout related functions and methods here