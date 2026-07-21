package com.gratus.workoutrepo.intervalsicu.repository

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.gratus.workoutrepo.archive.data.ActivityArchiveManager
import com.gratus.workoutrepo.archive.model.ArchiveActivity
import com.gratus.workoutrepo.archive.model.SourceProvider
import com.gratus.workoutrepo.archive.utils.SportTypeMapper
import com.gratus.workoutrepo.intervalsicu.network.IntervalsService
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.concurrent.TimeUnit

object IntervalsRepository {
    private const val TAG = "IntervalsRepository"
    private const val BASE_URL = "https://intervals.icu/api/v1/"
    private const val PREFS_FILENAME = "intervals_secure_prefs"
    private const val KEY_API_KEY = "intervals_api_key"

    private var cachedApiKey: String? = null
    private var service: IntervalsService? = null

    private fun getSecurePrefs(context: Context) = EncryptedSharedPreferences.create(
        PREFS_FILENAME,
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveApiKey(context: Context, key: String) {
        getSecurePrefs(context).edit().putString(KEY_API_KEY, key).apply()
        cachedApiKey = key
        service = buildService(key)
    }

    fun getApiKey(context: Context): String? {
        if (cachedApiKey == null) {
            cachedApiKey = getSecurePrefs(context).getString(KEY_API_KEY, null)
            cachedApiKey?.let { service = buildService(it) }
        }
        return cachedApiKey
    }

    private fun buildService(apiKey: String): IntervalsService {
        val authString = "API_KEY:$apiKey"
        val encodedAuth = Base64.getEncoder().encodeToString(authString.toByteArray())

        val interceptor = Interceptor { chain ->
            val request: Request = chain.request().newBuilder()
                .addHeader("Authorization", "Basic $encodedAuth")
                .build()
            
            val response = chain.proceed(request)
            
            val rateLimitLimit = response.header("X-RateLimit-Limit")
            val rateLimitRemaining = response.header("X-RateLimit-Remaining")
            if (rateLimitLimit != null && rateLimitRemaining != null) {
                Log.d(TAG, "Rate Limits - Limit: $rateLimitLimit, Remaining: $rateLimitRemaining")
            }
            
            response
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(IntervalsService::class.java)
    }

    suspend fun syncActivities(context: Context, forceDeepSync: Boolean = false) {
        val apiKey = getApiKey(context) ?: return
        val currentService = service ?: buildService(apiKey)

        try {
            val archive = ActivityArchiveManager.getActivities(context)
            
            val prefs = context.getSharedPreferences(com.gratus.workoutrepo.BaseActivity.PREFS_NAME, Context.MODE_PRIVATE)
            val durationYears = prefs.getInt("IntervalsDurationYears", 2).coerceAtLeast(1)
            val configuredStartDate = LocalDate.now().minusYears(durationYears.toLong())

            val formatter = DateTimeFormatter.ISO_LOCAL_DATE

            fun parseStartDateToLocalDate(dateStr: String): LocalDate? {
                return try {
                    if (dateStr.length >= 10) {
                        LocalDate.parse(dateStr.take(10), DateTimeFormatter.ISO_LOCAL_DATE)
                    } else null
                } catch (e: Exception) {
                    null
                }
            }

            val oldestDate: LocalDate = if (archive.isNotEmpty()) {
                val latestActivity = archive.maxByOrNull { it.startDateLocal }
                val latestDate = latestActivity?.let { parseStartDateToLocalDate(it.startDateLocal) }
                
                if (latestDate != null) {
                    if (forceDeepSync) {
                        val earliestActivity = archive.minByOrNull { it.startDateLocal }
                        val earliestDate = earliestActivity?.let { parseStartDateToLocalDate(it.startDateLocal) }
                        if (earliestDate != null && configuredStartDate.isBefore(earliestDate)) {
                            configuredStartDate
                        } else {
                            latestDate.minusDays(1)
                        }
                    } else {
                        latestDate.minusDays(1)
                    }
                } else {
                    configuredStartDate
                }
            } else {
                configuredStartDate
            }

            val oldest = oldestDate.format(formatter)
            Log.d(TAG, "Fetching Intervals.icu activities with oldest=$oldest (archiveSize=${archive.size}, forceDeepSync=$forceDeepSync)")
            val freshIntervals = currentService.getActivities(oldest = oldest)
            
            val newArchiveEntries = freshIntervals.map { intervalsAct ->
                val mappedType = SportTypeMapper.mapIntervalsType(intervalsAct.type ?: "Unknown")
                ArchiveActivity(
                    stravaActivityId = null,
                    intervalsActivityId = intervalsAct.id,
                    source = SourceProvider.INTERVALS_ICU,
                    name = intervalsAct.name ?: "$mappedType Activity",
                    distance = intervalsAct.distance ?: 0f,
                    movingTime = intervalsAct.movingTime ?: 0,
                    startDateLocal = intervalsAct.startDateLocal ?: "",
                    averageWatts = intervalsAct.averageWatts,
                    averageHeartrate = intervalsAct.averageHeartrate,
                    totalElevationGain = intervalsAct.totalElevationGain,
                    type = SportTypeMapper.mapIntervalsType(intervalsAct.type ?: "Unknown"),
                    workoutType = null,
                    description = intervalsAct.description,
                    lastModifiedLocal = System.currentTimeMillis()
                )
            }
            
            val finalArchive = archive.toMutableList()
            var addedCount = 0
            
            for (newEntry in newArchiveEntries) {
                val match = ActivityArchiveManager.findExistingMatch(newEntry, finalArchive)
                if (match != null) {
                    val index = finalArchive.indexOf(match)
                    val isMatchUnknown = match.type.equals("Unknown", ignoreCase = true) || match.name.contains("Unknown", ignoreCase = true)
                    val isNewUnknown = newEntry.type.equals("Unknown", ignoreCase = true) || newEntry.name.contains("Unknown", ignoreCase = true)

                    val updated = if (isMatchUnknown && !isNewUnknown) {
                        newEntry.copy(
                            stravaActivityId = newEntry.stravaActivityId ?: match.stravaActivityId,
                            intervalsActivityId = newEntry.intervalsActivityId ?: match.intervalsActivityId,
                            description = if (!newEntry.description.isNullOrBlank()) newEntry.description else match.description
                        )
                    } else {
                        match.copy(
                            intervalsActivityId = match.intervalsActivityId ?: newEntry.intervalsActivityId,
                            stravaActivityId = match.stravaActivityId ?: newEntry.stravaActivityId,
                            description = if (!match.description.isNullOrBlank()) match.description else newEntry.description,
                            name = if (isMatchUnknown && !isNewUnknown) newEntry.name else match.name,
                            type = if (isMatchUnknown && !isNewUnknown) newEntry.type else match.type
                        )
                    }
                    finalArchive[index] = updated
                } else {
                    finalArchive.add(newEntry)
                    addedCount++
                }
            }

            finalArchive.sortByDescending { it.startDateLocal }
            ActivityArchiveManager.saveActivities(context, finalArchive)
            Log.d(TAG, "Successfully added $addedCount new Intervals.icu activities.")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync Intervals.icu activities", e)
        }
    }

    suspend fun getActivityDetailsWithDescription(context: Context, archiveActivity: ArchiveActivity): ArchiveActivity {
        val apiKey = getApiKey(context) ?: return archiveActivity
        val currentService = service ?: buildService(apiKey)

        var intervalsId = archiveActivity.intervalsActivityId
        var targetActivity = archiveActivity

        // If intervalsActivityId is missing, attempt to find matching activity from Intervals.icu by date range
        if (intervalsId == null) {
            try {
                if (archiveActivity.startDateLocal.length >= 10) {
                    val actDate = LocalDate.parse(archiveActivity.startDateLocal.take(10), DateTimeFormatter.ISO_LOCAL_DATE)
                    val oldest = actDate.minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE)
                    val newest = actDate.plusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE)

                    val fetchedList = currentService.getActivities(oldest = oldest, newest = newest)
                    val candidateEntries = fetchedList.map { intervalsAct ->
                        val mappedType = SportTypeMapper.mapIntervalsType(intervalsAct.type ?: "Unknown")
                        ArchiveActivity(
                            stravaActivityId = null,
                            intervalsActivityId = intervalsAct.id,
                            source = SourceProvider.INTERVALS_ICU,
                            name = intervalsAct.name ?: "$mappedType Activity",
                            distance = intervalsAct.distance ?: 0f,
                            movingTime = intervalsAct.movingTime ?: 0,
                            startDateLocal = intervalsAct.startDateLocal ?: "",
                            averageWatts = intervalsAct.averageWatts,
                            averageHeartrate = intervalsAct.averageHeartrate,
                            totalElevationGain = intervalsAct.totalElevationGain,
                            type = SportTypeMapper.mapIntervalsType(intervalsAct.type ?: "Unknown"),
                            workoutType = null,
                            description = intervalsAct.description,
                            lastModifiedLocal = System.currentTimeMillis()
                        )
                    }

                    val match = candidateEntries.find { ActivityArchiveManager.findExistingMatch(archiveActivity, listOf(it)) != null }
                    if (match?.intervalsActivityId != null) {
                        intervalsId = match.intervalsActivityId
                        targetActivity = archiveActivity.copy(
                            intervalsActivityId = intervalsId,
                            description = archiveActivity.description ?: match.description
                        )
                        Log.d(TAG, "Successfully dynamically matched Intervals.icu activity ID: $intervalsId for ${archiveActivity.name}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to dynamically match Intervals.icu activity for ${archiveActivity.name}", e)
            }
        }

        if (intervalsId == null) {
            return targetActivity
        }

        try {
            var fetchedDesc: String? = null
            var fetchedName: String? = targetActivity.name
            var fetchedWatts: Float? = targetActivity.averageWatts
            var fetchedType: String = targetActivity.type

            // 1. Fetch single activity details (contains description edited on Intervals.icu website)
            try {
                val singleActivity = currentService.getActivity(intervalsId)
                if (!singleActivity.description.isNullOrBlank()) {
                    fetchedDesc = singleActivity.description
                }
                if (!singleActivity.name.isNullOrBlank()) {
                    fetchedName = singleActivity.name
                }
                if (singleActivity.averageWatts != null) {
                    fetchedWatts = singleActivity.averageWatts
                }
                if (!singleActivity.type.isNullOrBlank()) {
                    fetchedType = SportTypeMapper.mapIntervalsType(singleActivity.type)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not fetch single activity details for $intervalsId, falling back to messages", e)
            }

            // 2. Fetch chat messages/comments
            try {
                val messages = currentService.getActivityMessages(intervalsId)
                val concatenatedMsg = messages.joinToString("\n\n") { it.text ?: "" }.trim()
                if (concatenatedMsg.isNotEmpty()) {
                    fetchedDesc = if (!fetchedDesc.isNullOrBlank()) {
                        "$fetchedDesc\n\n$concatenatedMsg"
                    } else {
                        concatenatedMsg
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not fetch activity messages for $intervalsId", e)
            }

            val finalDescription = if (!fetchedDesc.isNullOrBlank()) fetchedDesc else targetActivity.description

            val updatedActivity = targetActivity.copy(
                name = fetchedName ?: targetActivity.name,
                averageWatts = fetchedWatts,
                type = fetchedType,
                description = finalDescription,
                lastModifiedLocal = System.currentTimeMillis()
            )
            
            val currentArchive = ActivityArchiveManager.getActivities(context).toMutableList()
            val index = currentArchive.indexOfFirst { it.id == updatedActivity.id }
            if (index != -1) {
                currentArchive[index] = updatedActivity
                ActivityArchiveManager.saveActivities(context, currentArchive)
            }
            
            return updatedActivity
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch details/descriptions for activity $intervalsId", e)
        }
        
        return targetActivity
    }

    suspend fun getLatestWellness(context: Context): com.gratus.workoutrepo.intervalsicu.data.IntervalsWellness? {
        val cached = ActivityArchiveManager.getLatestWellness(context)

        val apiKey = getApiKey(context) ?: return cached
        val currentService = service ?: buildService(apiKey)

        return try {
            val prefs = context.getSharedPreferences(com.gratus.workoutrepo.BaseActivity.PREFS_NAME, Context.MODE_PRIVATE)
            val durationYears = prefs.getInt("IntervalsDurationYears", 2).coerceAtLeast(1)
            val oldest = LocalDate.now().minusYears(durationYears.toLong()).format(DateTimeFormatter.ISO_LOCAL_DATE)

            val wellnessList = currentService.getWellness(oldest = oldest)
            if (wellnessList.isNotEmpty()) {
                ActivityArchiveManager.saveWellnessList(context, wellnessList)
            }
            ActivityArchiveManager.getLatestWellness(context)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch wellness data from network, returning cache", e)
            cached
        }
    }
}
