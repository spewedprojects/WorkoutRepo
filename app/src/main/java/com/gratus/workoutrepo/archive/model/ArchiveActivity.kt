package com.gratus.workoutrepo.archive.model

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import java.util.UUID

@Keep
data class ArchiveActivity(
    @SerializedName("id") val id: String = UUID.randomUUID().toString(),
    @SerializedName("stravaActivityId") val stravaActivityId: Long? = null,
    @SerializedName("intervalsActivityId") val intervalsActivityId: String? = null,
    @SerializedName("source") val source: SourceProvider,
    @SerializedName("name") val name: String,
    @SerializedName("distance") val distance: Float,
    @SerializedName("moving_time") val movingTime: Int,
    @SerializedName("start_date_local") val startDateLocal: String,
    @SerializedName("average_watts") val averageWatts: Float?,
    @SerializedName("average_heartrate") val averageHeartrate: Float?,
    @SerializedName("total_elevation_gain") val totalElevationGain: Float?,
    @SerializedName("type") val type: String,
    @SerializedName("workout_type") val workoutType: Int?,
    @SerializedName("description") val description: String?,
    @SerializedName("lastModifiedLocal") val lastModifiedLocal: Long = System.currentTimeMillis()
)

fun ArchiveActivity.isPlaceholder(): Boolean {
    return type.equals("Unknown", ignoreCase = true) ||
           type.equals("Strava", ignoreCase = true) ||
           name.equals("Unknown Activity", ignoreCase = true) ||
           name.equals("Strava Activity", ignoreCase = true) ||
           name.contains("Unknown", ignoreCase = true)
}
