package com.gratus.workoutrepo.strava.data

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

// 1. The Activity Object (What Strava sends back)
@Keep
data class StravaActivity(
    // The unique ID of the activity
    @SerializedName("id")
    val id: Long,

    // The title of the activity (e.g., "Morning Ride")
    @SerializedName("name")
    val name: String,

    // Distance in meters (Strava always sends meters)
    @SerializedName("distance")
    val distance: Float,

    // Moving time in seconds
    @SerializedName("moving_time")
    val movingTime: Int,

    // The local start time (e.g., "2026-02-15T08:00:00Z")
    // We need this to determine the Day of the Week
    @SerializedName("start_date_local")
    val startDateLocal: String,

    // Average power in Watts (nullable because not all rides have power data)
    @SerializedName("average_watts")
    val averageWatts: Float?,

    @SerializedName("average_heartrate")
    val averageHeartrate: Float?,

    @SerializedName("total_elevation_gain")
    val totalElevationGain: Float?,

    // Activity type (e.g., "Ride", "Run", "WeightTraining")
    @SerializedName("type")
    val type: String,

    // NEW: Strava's internal tag for workouts/races
    @SerializedName("workout_type")
    val workoutType: Int?,

    // NEW: The user's description/notes
    @SerializedName("description")
    val description: String?
)

// 2. The Token Object (For refreshing authentication)
@Keep
data class TokenResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("refresh_token") val refreshToken: String,
    @SerializedName("expires_at") val expiresAt: Long
)

// Add this to StravaModels.kt
@Keep
data class StravaAthlete(
    @SerializedName("id") val id: Long,
    @SerializedName("firstname") val firstname: String?,
    @SerializedName("lastname") val lastname: String?,
    @SerializedName("profile") val profile: String? // This is the URL to your profile picture
)