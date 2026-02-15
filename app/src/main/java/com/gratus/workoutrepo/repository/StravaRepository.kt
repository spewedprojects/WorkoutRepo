package com.gratus.workoutrepo.repository

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.gratus.workoutrepo.data.StravaActivity
import com.gratus.workoutrepo.network.StravaApi
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object StravaRepository {

    // --- CONFIGURATION ---
    private const val CLIENT_ID = "YOUR_CLIENT_ID"
    private const val CLIENT_SECRET = "YOUR_CLIENT_SECRET"

    // Get this ONCE manually via Postman/Browser. It usually doesn't expire unless revoked.
    private var currentRefreshToken = "YOUR_INITIAL_REFRESH_TOKEN"
    private var currentAccessToken: String? = null

    // --- SETUP RETROFIT ---
    private val api: StravaApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://www.strava.com/api/v3/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(StravaApi::class.java)
    }

    /**
     * MAIN FUNCTION: Returns activities filtered by the specific day of week
     * @param targetDayOfWeek: "Monday", "Tuesday", etc.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun getActivitiesForDay(targetDayOfWeek: String): List<StravaActivity> {
        // 1. Ensure we have a valid token
        ensureValidToken()

        // 2. Fetch recent activities (e.g., last 50)
        // If 50 isn't enough to find recent Mondays, increase this number
        val allActivities = try {
            api.getActivities(token = currentAccessToken!!, perPage = 50)
        } catch (e: Exception) {
            Log.e("StravaRepo", "Error fetching activities", e)
            return emptyList()
        }

        // 3. Filter strictly by Day of Week
        return allActivities.filter { activity ->
            val activityDate = parseDate(activity.startDateLocal)
            // Get day name (e.g., "Monday")
            val activityDayName = activityDate.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH)

            // Compare (Case insensitive)
            activityDayName.equals(targetDayOfWeek, ignoreCase = true)
        }
    }

    // --- HELPER: Token Management ---
    private suspend fun ensureValidToken() {
        // Simple logic: If we don't have an access token, or if we suspect it's old, refresh it.
        // For a robust app, check 'expires_at'. For personal use, refreshing on every app launch 
        // or null check is often sufficient.

        if (currentAccessToken == null) {
            try {
                val response = api.refreshToken(CLIENT_ID, CLIENT_SECRET, currentRefreshToken)
                currentAccessToken = response.accessToken
                currentRefreshToken = response.refreshToken // Save new refresh token
                Log.d("StravaRepo", "Token Refreshed!")
            } catch (e: Exception) {
                Log.e("StravaRepo", "Failed to refresh token", e)
            }
        }
    }

    // --- HELPER: Date Parsing ---
    @RequiresApi(Build.VERSION_CODES.O)
    private fun parseDate(dateString: String): LocalDate {
        // Strava format: "2018-02-16T14:52:54Z"
        // We only care about the date part for Day of Week
        return LocalDate.parse(dateString, DateTimeFormatter.ISO_DATE_TIME)
    }
}