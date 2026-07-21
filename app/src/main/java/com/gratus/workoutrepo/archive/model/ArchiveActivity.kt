package com.gratus.workoutrepo.archive.model

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import java.util.UUID

@Keep
data class ArchiveActivity(
    val id: String = UUID.randomUUID().toString(),
    val stravaActivityId: Long? = null,
    val intervalsActivityId: String? = null,
    val source: SourceProvider,
    val name: String,
    val distance: Float,
    @SerializedName("moving_time") val movingTime: Int,
    @SerializedName("start_date_local") val startDateLocal: String,
    @SerializedName("average_watts") val averageWatts: Float?,
    @SerializedName("average_heartrate") val averageHeartrate: Float?,
    @SerializedName("total_elevation_gain") val totalElevationGain: Float?,
    val type: String,
    @SerializedName("workout_type") val workoutType: Int?,
    val description: String?,
    val lastModifiedLocal: Long = System.currentTimeMillis()
)
