package com.gratus.workoutrepo.archive.utils

import android.util.Log

object SportTypeMapper {
    private const val TAG = "SportTypeMapper"

    fun mapStravaType(stravaType: String): String {
        // Passthrough for now, Strava types usually match the UI expectations well.
        return stravaType
    }

    fun mapIntervalsType(intervalsType: String?): String {
        if (intervalsType.isNullOrBlank() || intervalsType.equals("Unknown", ignoreCase = true)) {
            return "Unknown"
        }
        return when (intervalsType) {
            "Ride", "GravelRide", "MountainBikeRide" -> "Ride"
            "EBikeRide", "E-BikeRide" -> "E-BikeRide"
            "VirtualRide" -> "VirtualRide"
            "Run", "VirtualRun" -> intervalsType
            "Swim", "Walk", "Hike", "Yoga", "Workout" -> intervalsType
            "WeightTraining", "Crossfit" -> "WeightTraining"
            else -> {
                Log.w(TAG, "Unrecognized Intervals.icu sport type: $intervalsType. Proceeding with passthrough.")
                intervalsType
            }
        }
    }
}
