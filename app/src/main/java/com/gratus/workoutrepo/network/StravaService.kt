package com.gratus.workoutrepo.network

import com.gratus.workoutrepo.data.StravaActivity
import com.gratus.workoutrepo.data.TokenResponse
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface StravaService {

    // 1. Get Activities
    @GET("athlete/activities")
    suspend fun getActivities(
        @Query("access_token") token: String,
        @Query("per_page") perPage: Int = 180, // Grab last 50 to find matching days
        @Query("page") page: Int = 1
    ): List<StravaActivity>

    // 2. Refresh the Token (The OAuth magic)
    @POST("oauth/token")
    suspend fun refreshToken(
        @Query("client_id") clientId: String,
        @Query("client_secret") clientSecret: String,
        @Query("refresh_token") refreshToken: String,
        @Query("grant_type") grantType: String = "refresh_token"
    ): TokenResponse

    // --- NEW ENDPOINT ---
    @GET("activities/{id}")
    suspend fun getActivityDetails(
        @Path("id") id: Long,
        @Query("access_token") token: String
    ): StravaActivity
}