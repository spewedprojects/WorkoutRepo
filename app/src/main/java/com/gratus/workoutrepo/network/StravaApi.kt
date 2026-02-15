package com.gratus.workoutrepo.network

import com.gratus.workoutrepo.data.StravaActivity
import com.gratus.workoutrepo.data.TokenResponse
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface StravaApi {

    // Get list of activities
    @GET("athlete/activities")
    suspend fun getActivities(
        @Query("access_token") token: String,
        @Query("per_page") perPage: Int = 50, // Grab last 50 to ensure we find matching days
        @Query("page") page: Int = 1
    ): List<StravaActivity>

    // Refresh the token
    @POST("oauth/token")
    suspend fun refreshToken(
        @Query("client_id") clientId: String,
        @Query("client_secret") clientSecret: String,
        @Query("refresh_token") refreshToken: String,
        @Query("grant_type") grantType: String = "refresh_token"
    ): TokenResponse
}