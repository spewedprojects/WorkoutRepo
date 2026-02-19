package com.gratus.workoutrepo.repository

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.gratus.workoutrepo.data.StravaActivity
import com.gratus.workoutrepo.network.StravaService
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object StravaRepository {

    // --- CACHE VARIABLES ---
    private var cachedActivities: List<StravaActivity>? = null
    private var lastCacheTime: Long = 0
    // NEW: File Name instead of Prefs Name
    private const val CACHE_FILE_NAME = "strava_activities_cache.json"

    // Setup Retrofit
    private val api: StravaService by lazy {
        Retrofit.Builder()
            .baseUrl("https://www.strava.com/api/v3/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(StravaService::class.java)
    }

    /**
     * SMART FUNCTION: Checks cache first, then network.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun getActivitiesForDay(context: Context, targetDayOfWeek: String, forceRefresh: Boolean = false): List<StravaActivity> {

        // 1. Initialize Cache from Disk if Memory is empty
        if (cachedActivities == null) {
            loadFromDisk(context)
        }

        // 2. Decide: Fetch or Use Cache?
        // We fetch if: Forced (Pull-to-refresh) OR Auto-Refresh is Enabled AND (Cache is Null OR Cache is Old)
        val prefs = context.getSharedPreferences("MeditationTrackerPrefs", Context.MODE_PRIVATE)
        val isAutoRefresh = prefs.getBoolean("EnableAutoRefresh", true)
        val cacheDurationHours = prefs.getLong("CacheDurationHours", 48)
        val cacheDurationMs = cacheDurationHours * 60 * 60 * 1000

        if (forceRefresh || (isAutoRefresh && shouldFetchFromNetwork(cacheDurationMs))) {
            fetchAndSaveActivities(context)
        }

        // 3. Return Filtered List (from whatever we have now)
        return cachedActivities?.filter { activity ->
            try {
                val date = LocalDate.parse(activity.startDateLocal, DateTimeFormatter.ISO_DATE_TIME)
                val dayName = date.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH)
                dayName.equals(targetDayOfWeek, ignoreCase = true)
            } catch (e: Exception) {
                false
            }
        } ?: emptyList()
    }

    // --- NEW: FILE BASED STORAGE ---

    private fun saveToDisk(context: Context, list: List<StravaActivity>, time: Long) {
        try {
            // 1. Create a wrapper object to hold data + timestamp
            val cacheData = CacheData(time, list)
            val jsonString = Gson().toJson(cacheData)

            // 2. Write to a real file
            val file = File(context.filesDir, CACHE_FILE_NAME)
            val writer = FileWriter(file)
            writer.write(jsonString)
            writer.close()

            Log.d("StravaRepo", "Saved ${list.size} items to file: ${file.absolutePath}")

        } catch (e: Exception) {
            Log.e("StravaRepo", "Failed to save file", e)
        }
    }

    private fun loadFromDisk(context: Context) {
        try {
            val file = File(context.filesDir, CACHE_FILE_NAME)
            if (!file.exists()) return

            // 1. Read the file
            val reader = FileReader(file)
            val type = object : TypeToken<CacheData>() {}.type
            val cacheData: CacheData = Gson().fromJson(reader, type)
            reader.close()

            // 2. Restore memory variables
            cachedActivities = cacheData.activities
            lastCacheTime = cacheData.timestamp

            Log.d("StravaRepo", "Loaded ${cachedActivities?.size} activities from File")

        } catch (e: Exception) {
            Log.e("StravaRepo", "Failed to load file", e)
        }
    }

    // --- HELPER CLASS ---
    // We need this tiny class to bundle the timestamp inside the JSON file
    private data class CacheData(
        val timestamp: Long,
        val activities: List<StravaActivity>
    )

    private fun shouldFetchFromNetwork(cacheDurationMs: Long): Boolean {
        if (cachedActivities == null) return true
        val now = System.currentTimeMillis()
        return (now - lastCacheTime) > cacheDurationMs
    }

    // 1. Fetch Single Detail (Lazy Load) - Same as before
    suspend fun getActivityDetails(context: Context, activityId: Long): StravaActivity? {
        val token = getValidToken() ?: return null

        return try {
            val detailedActivity = api.getActivityDetails(activityId, token)

            // UPDATE LOGIC: Find and Replace inside our BIG list
            // If the list is null, we start a new list with just this item
            val currentList = cachedActivities ?: emptyList()

            val updatedList = if (currentList.any { it.id == activityId }) {
                currentList.map { if (it.id == activityId) detailedActivity else it }
            } else {
                currentList + detailedActivity
            }

            cachedActivities = updatedList
            saveToDisk(context, updatedList, lastCacheTime)

            detailedActivity
        } catch (e: Exception) {
            Log.e("StravaRepo", "Failed to fetch details", e)
            null
        }
    }

    // 2. THE NEW ACCUMULATOR LOGIC
    private suspend fun fetchAndSaveActivities(context: Context) {
        val token = getValidToken() ?: return

        try {
            // A. Fetch Latest 180 from Strava
            val freshActivities = api.getActivities(token = token, perPage = 180)

            // B. Load what we already have (The "Bank")
            if (cachedActivities == null) loadFromDisk(context)
            val existingBank = cachedActivities ?: emptyList()

            // C. MERGE: Fresh + Bank
            // We use a Map to handle duplicates automatically (Key = ID)
            val mergedMap = HashMap<Long, StravaActivity>()

            // 1. Put all OLD activities in the map first
            for (oldItem in existingBank) {
                mergedMap[oldItem.id] = oldItem
            }

            // 2. Overlay the NEW activities
            for (newItem in freshActivities) {
                // If we already have this ID, we need to be careful not to delete our saved Description
                val existing = mergedMap[newItem.id]

                val itemToSave = if (existing?.description != null && newItem.description.isNullOrBlank()) {
                    // PRESERVE: Old one has description, new one doesn't. Keep the description.
                    newItem.copy(description = existing.description)
                } else {
                    // OVERWRITE: New one is newer/better.
                    newItem
                }
                mergedMap[newItem.id] = itemToSave
            }

            // 3. Convert back to List and SORT by Date (Newest first)
            val finalSortedList = mergedMap.values.sortedByDescending { it.startDateLocal }

            // D. Save the Giant List
            cachedActivities = finalSortedList
            lastCacheTime = System.currentTimeMillis()
            saveToDisk(context, finalSortedList, lastCacheTime)

        } catch (e: Exception) {
            Log.e("StravaRepo", "Network Failed", e)
        }
    }

    private suspend fun getValidToken(): String? {
        // If we already have a token, return it (Simple check)
        if (TokenManager.accessToken != null) return TokenManager.accessToken

        // Otherwise, refresh it using the hardcoded Refresh Token
        return try {
            val response = api.refreshToken(
                TokenManager.CLIENT_ID,
                TokenManager.CLIENT_SECRET,
                TokenManager.refreshToken
            )
            // Save the new keys
            TokenManager.accessToken = response.accessToken
            TokenManager.refreshToken = response.refreshToken
            Log.d("StravaRepo", "Token Refreshed Successfully!")

            response.accessToken
        } catch (e: Exception) {
            Log.e("StravaRepo", "Token Refresh Failed", e)
            null
        }
    }

    // Optional: Call this if the user does a "Pull to Refresh"
    fun clearCache() {
        cachedActivities = null
    }

    fun getLastSyncTime(context: Context): Long {
        // 1. If we already have the time in memory, return it (Fast)
        if (lastCacheTime > 0) {
            return lastCacheTime
        }

        // 2. If memory is empty (App restart), load the file from disk
        // This populates both 'cachedActivities' and 'lastCacheTime'
        loadFromDisk(context)

        // 3. Return the loaded time (or 0 if file doesn't exist)
        return lastCacheTime
    }
}