package com.gratus.workoutrepo.intervalsicu.network

import com.gratus.workoutrepo.intervalsicu.data.IntervalsActivity
import com.gratus.workoutrepo.intervalsicu.data.IntervalsMessage
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface IntervalsService {
    @GET("athlete/0/activities")
    suspend fun getActivities(
        @Query("oldest") oldest: String,
        @Query("newest") newest: String? = null
    ): List<IntervalsActivity>

    @GET("activity/{id}")
    suspend fun getActivity(
        @Path("id") activityId: String
    ): IntervalsActivity

    @GET("activity/{id}/messages")
    suspend fun getActivityMessages(
        @Path("id") activityId: String
    ): List<IntervalsMessage>

    @GET("athlete/0/wellness")
    suspend fun getWellness(
        @Query("oldest") oldest: String,
        @Query("newest") newest: String? = null
    ): List<com.gratus.workoutrepo.intervalsicu.data.IntervalsWellness>
}
