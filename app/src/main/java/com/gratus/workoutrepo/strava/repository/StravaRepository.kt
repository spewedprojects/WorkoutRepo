package com.gratus.workoutrepo.strava.repository

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.gratus.workoutrepo.BaseActivity
import com.gratus.workoutrepo.archive.data.ActivityArchiveManager
import com.gratus.workoutrepo.archive.model.ArchiveActivity
import com.gratus.workoutrepo.archive.model.SourceProvider
import com.gratus.workoutrepo.archive.utils.SportTypeMapper
import com.gratus.workoutrepo.strava.data.StravaActivity
import com.gratus.workoutrepo.strava.network.StravaService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

object StravaRepository {

    // Setup Retrofit
    private val api: StravaService by lazy {
        Retrofit.Builder()
            .baseUrl("https://www.strava.com/api/v3/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(StravaService::class.java)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun getActivitiesForDay(context: Context, targetDayOfWeek: String, forceRefresh: Boolean = false): List<ArchiveActivity> {
        val prefs = context.getSharedPreferences(BaseActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val isAutoRefresh = prefs.getBoolean("EnableAutoRefresh", true)
        val cacheDurationHours = prefs.getLong("CacheDurationHours", 48)
        val cacheDurationMs = cacheDurationHours * 60 * 60 * 1000

        val lastSyncTime = ActivityArchiveManager.getLastSyncTime(context)
        if (forceRefresh || (isAutoRefresh && (System.currentTimeMillis() - lastSyncTime) > cacheDurationMs)) {
            val activeSource = prefs.getString("ActiveSyncSource", SourceProvider.STRAVA.name)
            if (SourceProvider.INTERVALS_ICU.name == activeSource) {
                com.gratus.workoutrepo.intervalsicu.repository.IntervalsRepository.syncActivities(context, forceDeepSync = forceRefresh)
            } else {
                fetchAndSaveActivities(context, isDeepSync = forceRefresh)
            }
        }

        return ActivityArchiveManager.getActivities(context).filter { activity ->
            try {
                val date = LocalDate.parse(activity.startDateLocal, DateTimeFormatter.ISO_DATE_TIME)
                val dayName = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                dayName.equals(targetDayOfWeek, ignoreCase = true)
            } catch (e: Exception) {
                false
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun getAllActivities(context: Context, forceRefresh: Boolean = false): List<ArchiveActivity> {
        val prefs = context.getSharedPreferences(BaseActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val isAutoRefresh = prefs.getBoolean("EnableAutoRefresh", true)
        val cacheDurationHours = prefs.getLong("CacheDurationHours", 48)
        val cacheDurationMs = cacheDurationHours * 60 * 60 * 1000

        val lastSyncTime = ActivityArchiveManager.getLastSyncTime(context)
        if (forceRefresh || (isAutoRefresh && (System.currentTimeMillis() - lastSyncTime) > cacheDurationMs)) {
            val activeSource = prefs.getString("ActiveSyncSource", SourceProvider.STRAVA.name)
            if (SourceProvider.INTERVALS_ICU.name == activeSource) {
                com.gratus.workoutrepo.intervalsicu.repository.IntervalsRepository.syncActivities(context, forceDeepSync = forceRefresh)
            } else {
                fetchAndSaveActivities(context, isDeepSync = forceRefresh)
            }
        }

        return ActivityArchiveManager.getActivities(context)
    }

    suspend fun getActivityDetails(context: Context, archiveActivity: ArchiveActivity): ArchiveActivity? {
        val prefs = context.getSharedPreferences(BaseActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val activeSource = prefs.getString("ActiveSyncSource", SourceProvider.STRAVA.name)

        // 1. If active source is INTERVALS_ICU, or activity is from INTERVALS_ICU, try Intervals first
        if (SourceProvider.INTERVALS_ICU.name == activeSource || archiveActivity.source == SourceProvider.INTERVALS_ICU || archiveActivity.intervalsActivityId != null) {
            val intervalsResult = com.gratus.workoutrepo.intervalsicu.repository.IntervalsRepository.getActivityDetailsWithDescription(context, archiveActivity)
            if (!intervalsResult.description.isNullOrBlank()) {
                return intervalsResult
            }
        }

        // 2. Try Strava if stravaActivityId is available
        val stravaId = archiveActivity.stravaActivityId
        if (stravaId != null) {
            val token = getValidToken()
            if (token != null) {
                try {
                    val detailedActivity = api.getActivityDetails(stravaId, token)
                    if (detailedActivity != null && !detailedActivity.description.isNullOrBlank()) {
                        val updatedActivity = archiveActivity.copy(
                            description = detailedActivity.description,
                            lastModifiedLocal = System.currentTimeMillis()
                        )

                        val archive = ActivityArchiveManager.getActivities(context).toMutableList()
                        val index = archive.indexOfFirst { it.id == updatedActivity.id }
                        if (index != -1) {
                            archive[index] = updatedActivity
                            ActivityArchiveManager.saveActivities(context, archive, ActivityArchiveManager.getLastSyncTime(context))
                        }
                        return updatedActivity
                    }
                } catch (e: Exception) {
                    Log.e("StravaRepo", "Failed to fetch details from Strava API, falling back to Intervals.icu if available", e)
                }
            }
        }

        // 3. Fallback: If Strava failed or was unavailable, check if an intervalsActivityId exists or can be matched
        if (archiveActivity.intervalsActivityId != null) {
            return com.gratus.workoutrepo.intervalsicu.repository.IntervalsRepository.getActivityDetailsWithDescription(context, archiveActivity)
        } else {
            val archive = ActivityArchiveManager.getActivities(context)
            val match = archive.find { it.id == archiveActivity.id } 
                ?: archive.find { ActivityArchiveManager.findExistingMatch(archiveActivity, listOf(it)) != null }
            
            if (match?.intervalsActivityId != null) {
                val updatedTarget = archiveActivity.copy(intervalsActivityId = match.intervalsActivityId)
                return com.gratus.workoutrepo.intervalsicu.repository.IntervalsRepository.getActivityDetailsWithDescription(context, updatedTarget)
            }
        }

        return archiveActivity
    }

    private suspend fun fetchAndSaveActivities(context: Context, isDeepSync: Boolean) {
        val token = getValidToken() ?: return

        try {
            val freshActivities = mutableListOf<StravaActivity>()
            val pagesToFetch = if (isDeepSync) 1..4 else 1..1

            coroutineScope {
                val deferredPages = pagesToFetch.map { pageNum ->
                    async(Dispatchers.IO) {
                        try {
                            api.getActivities(token = token, perPage = 200, page = pageNum)
                        } catch (e: Exception) {
                            Log.e("StravaRepo", "Failed to fetch page $pageNum", e)
                            emptyList<StravaActivity>()
                        }
                    }
                }
                freshActivities.addAll(deferredPages.awaitAll().flatten())
            }

            Log.d("StravaRepo", "Successfully fetched ${freshActivities.size} fresh items from API.")

            val existingArchive = ActivityArchiveManager.getActivities(context).toMutableList()
            var addedCount = 0

            val mappedFresh = freshActivities.map { stravaAct ->
                ArchiveActivity(
                    stravaActivityId = stravaAct.id,
                    intervalsActivityId = null,
                    source = SourceProvider.STRAVA,
                    name = stravaAct.name,
                    distance = stravaAct.distance,
                    movingTime = stravaAct.movingTime,
                    startDateLocal = stravaAct.startDateLocal,
                    averageWatts = stravaAct.averageWatts,
                    averageHeartrate = stravaAct.averageHeartrate,
                    totalElevationGain = stravaAct.totalElevationGain,
                    type = SportTypeMapper.mapStravaType(stravaAct.type),
                    workoutType = stravaAct.workoutType,
                    description = stravaAct.description,
                    lastModifiedLocal = System.currentTimeMillis()
                )
            }

            for (newEntry in mappedFresh) {
                val match = ActivityArchiveManager.findExistingMatch(newEntry, existingArchive)
                if (match != null) {
                    val index = existingArchive.indexOf(match)
                    val updated = match.copy(
                        stravaActivityId = newEntry.stravaActivityId,
                        description = match.description ?: newEntry.description,
                        lastModifiedLocal = if (newEntry.description != null && match.description == null) System.currentTimeMillis() else match.lastModifiedLocal
                    )
                    existingArchive[index] = updated
                } else {
                    existingArchive.add(newEntry)
                    addedCount++
                }
            }

            existingArchive.sortByDescending { it.startDateLocal }
            ActivityArchiveManager.saveActivities(context, existingArchive)
            Log.d("StravaRepo", "Merged. Added $addedCount new Strava activities.")

        } catch (e: Exception) {
            Log.e("StravaRepo", "Network Failed", e)
        }
    }

    private suspend fun getValidToken(): String? {
        if (TokenManager.accessToken != null) return TokenManager.accessToken

        return try {
            val response = api.refreshToken(
                TokenManager.CLIENT_ID,
                TokenManager.CLIENT_SECRET,
                TokenManager.refreshToken
            )
            TokenManager.accessToken = response.accessToken
            TokenManager.refreshToken = response.refreshToken
            response.accessToken
        } catch (e: Exception) {
            null
        }
    }

    fun getLastSyncTime(context: Context): Long {
        return ActivityArchiveManager.getLastSyncTime(context)
    }

    suspend fun getProfilePictureUrl(context: Context): String? {
        val prefs = context.getSharedPreferences(BaseActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val cachedUrl = prefs.getString("StravaProfileUrl", null)
        if (cachedUrl != null) return cachedUrl

        val token = getValidToken() ?: return null
        return try {
            val athlete = api.getAuthenticatedAthlete(token)
            val url = athlete.profile
            prefs.edit().putString("StravaProfileUrl", url).apply()
            url
        } catch (e: Exception) {
            null
        }
    }

    fun getExportData(context: Context): String {
        val activities = ActivityArchiveManager.getActivities(context)
        val cacheData = mapOf(
            "timestamp" to ActivityArchiveManager.getLastSyncTime(context),
            "activities" to activities
        )
        return com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(cacheData)
    }

    fun importArchive(context: Context, jsonString: String): Boolean {
        return try {
            val gson = com.google.gson.GsonBuilder().create()
            val jsonElement = com.google.gson.JsonParser.parseString(jsonString)

            val activitiesArray = when {
                jsonElement.isJsonObject && jsonElement.asJsonObject.has("activities") -> {
                    jsonElement.asJsonObject.getAsJsonArray("activities")
                }
                jsonElement.isJsonArray -> {
                    jsonElement.asJsonArray
                }
                else -> return false
            }

            val currentArchive = ActivityArchiveManager.getActivities(context).toMutableList()
            val importedActivities = mutableListOf<ArchiveActivity>()

            for (element in activitiesArray) {
                try {
                    val obj = element.asJsonObject
                    if (obj.has("source") || obj.has("stravaActivityId") || obj.has("intervalsActivityId")) {
                        val act = gson.fromJson(element, ArchiveActivity::class.java)
                        if (act.name != null && act.startDateLocal != null) {
                            importedActivities.add(act)
                        }
                    } else {
                        val stravaAct = gson.fromJson(element, StravaActivity::class.java)
                        if (stravaAct.name != null && stravaAct.startDateLocal != null) {
                            val archiveAct = ArchiveActivity(
                                stravaActivityId = stravaAct.id,
                                intervalsActivityId = null,
                                source = SourceProvider.STRAVA,
                                name = stravaAct.name,
                                distance = stravaAct.distance,
                                movingTime = stravaAct.movingTime,
                                startDateLocal = stravaAct.startDateLocal,
                                averageWatts = stravaAct.averageWatts,
                                averageHeartrate = stravaAct.averageHeartrate,
                                totalElevationGain = stravaAct.totalElevationGain,
                                type = SportTypeMapper.mapStravaType(stravaAct.type),
                                workoutType = stravaAct.workoutType,
                                description = stravaAct.description,
                                lastModifiedLocal = System.currentTimeMillis()
                            )
                            importedActivities.add(archiveAct)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("StravaRepo", "Failed to parse activity element: $element", e)
                }
            }

            if (importedActivities.isEmpty()) return false

            var addedCount = 0
            for (imported in importedActivities) {
                val match = ActivityArchiveManager.findExistingMatch(imported, currentArchive)
                if (match != null) {
                    val index = currentArchive.indexOf(match)
                    val isMatchUnknown = match.type.equals("Unknown", ignoreCase = true) || match.name.contains("Unknown", ignoreCase = true)
                    val isImportedUnknown = imported.type.equals("Unknown", ignoreCase = true) || imported.name.contains("Unknown", ignoreCase = true)

                    val updated = if (isMatchUnknown && !isImportedUnknown) {
                        imported.copy(
                            stravaActivityId = imported.stravaActivityId ?: match.stravaActivityId,
                            intervalsActivityId = imported.intervalsActivityId ?: match.intervalsActivityId,
                            description = if (!imported.description.isNullOrBlank()) imported.description else match.description
                        )
                    } else {
                        match.copy(
                            stravaActivityId = match.stravaActivityId ?: imported.stravaActivityId,
                            intervalsActivityId = match.intervalsActivityId ?: imported.intervalsActivityId,
                            description = if (!match.description.isNullOrBlank()) match.description else imported.description
                        )
                    }
                    currentArchive[index] = updated
                } else {
                    currentArchive.add(imported)
                    addedCount++
                }
            }

            currentArchive.sortByDescending { it.startDateLocal }
            ActivityArchiveManager.saveActivities(context, currentArchive)
            true
        } catch (e: Exception) {
            Log.e("StravaRepo", "Import failed", e)
            false
        }
    }
}