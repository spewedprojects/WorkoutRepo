package com.gratus.workoutrepo.intervalsicu.data

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

@Keep
data class IntervalsActivity(
    @SerializedName("id") val id: String,
    @SerializedName(value = "name", alternate = ["title"]) val name: String?,
    @SerializedName("distance") val distance: Float?,
    @SerializedName(value = "moving_time", alternate = ["elapsed_time"]) val movingTime: Int?,
    @SerializedName(value = "start_date_local", alternate = ["start_date"]) val startDateLocal: String?,
    @SerializedName(value = "icu_average_watts", alternate = ["average_watts", "raw_watts"]) val averageWatts: Float?,
    @SerializedName("average_heartrate") val averageHeartrate: Float?,
    @SerializedName("total_elevation_gain") val totalElevationGain: Float?,
    @SerializedName(value = "type", alternate = ["icu_type", "sport"]) val type: String?,
    @SerializedName("description") val description: String? = null
)

@Keep
data class IntervalsMessage(
    @SerializedName("id") val id: Long,
    @SerializedName("text") val text: String?
)

@Keep
data class IntervalsWellness(
    @SerializedName("id") val id: String,
    @SerializedName("ctl") val ctl: Float?,
    @SerializedName("atl") val atl: Float?,
    @SerializedName("tsb") val tsb: Float?
)
