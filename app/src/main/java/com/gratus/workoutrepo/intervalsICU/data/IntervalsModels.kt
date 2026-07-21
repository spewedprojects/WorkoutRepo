package com.gratus.workoutrepo.intervalsicu.data

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

@Keep
data class IntervalsActivity(
    val id: String,
    val name: String?,
    val distance: Float?,
    @SerializedName("moving_time") val movingTime: Int?,
    @SerializedName("start_date_local") val startDateLocal: String?,
    @SerializedName(value = "icu_average_watts", alternate = ["average_watts", "raw_watts"]) val averageWatts: Float?,
    @SerializedName("average_heartrate") val averageHeartrate: Float?,
    @SerializedName("total_elevation_gain") val totalElevationGain: Float?,
    val type: String?,
    val description: String? = null
)

@Keep
data class IntervalsMessage(
    val id: Long,
    val text: String?
)

@Keep
data class IntervalsWellness(
    val id: String,
    val ctl: Float?,
    val atl: Float?,
    val tsb: Float?
)
