package com.gratus.workoutrepo.archive.data

import android.content.Context
import android.util.Log
import androidx.annotation.Keep
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import com.gratus.workoutrepo.BaseActivity
import com.gratus.workoutrepo.archive.model.ArchiveActivity
import com.gratus.workoutrepo.archive.model.SourceProvider
import com.gratus.workoutrepo.archive.model.isPlaceholder
import com.gratus.workoutrepo.intervalsicu.data.IntervalsWellness
import com.gratus.workoutrepo.strava.data.StravaActivity
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.abs

object ActivityArchiveManager {
    private const val TAG = "ActivityArchiveManager"
    private const val CACHE_FILE_NAME = "activities_cache.json"
    private const val WELLNESS_CACHE_FILE_NAME = "wellness_cache.json"
    private const val LEGACY_CACHE_FILE_NAME = "strava_activities_cache.json"
    
    // Idempotency flag in shared preferences
    private const val PREF_MIGRATION_COMPLETED = "MigrationCompleted_v1"

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    private var cachedActivities: List<ArchiveActivity>? = null
    private var cachedWellnessList: List<IntervalsWellness>? = null
    private var lastCacheTime: Long = 0

    @Keep
    private data class CacheData(
        @SerializedName("timestamp") val timestamp: Long,
        @SerializedName("activities") val activities: List<ArchiveActivity>
    )

    @Keep
    private data class LegacyCacheData(
        @SerializedName("timestamp") val timestamp: Long,
        @SerializedName("lastEditTime") val lastEditTime: Long = 0,
        @SerializedName("activities") val activities: List<StravaActivity>
    )

    fun getActivities(context: Context): List<ArchiveActivity> {
        if (cachedActivities == null) {
            loadFromDisk(context)
        }
        return cachedActivities ?: emptyList()
    }

    fun getLastSyncTime(context: Context): Long {
        if (lastCacheTime == 0L) {
            loadFromDisk(context)
        }
        return lastCacheTime
    }

    @Synchronized
    fun saveActivities(context: Context, list: List<ArchiveActivity>, time: Long = System.currentTimeMillis()) {
        try {
            val cleanedList = deduplicateUnknownActivities(list)
            val cacheData = CacheData(time, cleanedList)
            val jsonString = gson.toJson(cacheData)
            
            // ATOMIC WRITE
            val tempFile = File(context.filesDir, "$CACHE_FILE_NAME.tmp")
            val targetFile = File(context.filesDir, CACHE_FILE_NAME)
            
            val writer = FileWriter(tempFile)
            writer.write(jsonString)
            writer.flush()
            writer.close()
            
            if (targetFile.exists() && !targetFile.delete()) {
                Log.e(TAG, "Failed to delete old target file before renaming")
            }
            if (!tempFile.renameTo(targetFile)) {
                Log.e(TAG, "Failed to rename temp file to target file")
            } else {
                Log.d(TAG, "Successfully atomically saved ${cleanedList.size} activities.")
                cachedActivities = cleanedList
                lastCacheTime = time
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to atomically save file", e)
        }
    }

    @Synchronized
    fun loadFromDisk(context: Context) {
        val prefs = context.getSharedPreferences(BaseActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val migrationCompleted = prefs.getBoolean(PREF_MIGRATION_COMPLETED, false)

        if (!migrationCompleted) {
            Log.d(TAG, "Migration not completed. Attempting to migrate legacy data...")
            migrateFromLegacy(context)
            prefs.edit().putBoolean(PREF_MIGRATION_COMPLETED, true).apply()
        }

        try {
            val file = File(context.filesDir, CACHE_FILE_NAME)
            if (!file.exists()) {
                cachedActivities = emptyList()
                return
            }

            val reader = FileReader(file)
            val type = object : TypeToken<CacheData>() {}.type
            val cacheData: CacheData = gson.fromJson(reader, type)
            reader.close()

            cachedActivities = cacheData.activities.sortedByDescending { it.startDateLocal }
            lastCacheTime = cacheData.timestamp
            Log.d(TAG, "Loaded ${cachedActivities?.size} activities from new archive.")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to load new archive file", e)
        }
    }

    private fun migrateFromLegacy(context: Context) {
        val legacyFile = File(context.filesDir, LEGACY_CACHE_FILE_NAME)
        if (!legacyFile.exists()) {
            Log.d(TAG, "No legacy file found to migrate.")
            return
        }

        try {
            val reader = FileReader(legacyFile)
            val type = object : TypeToken<LegacyCacheData>() {}.type
            val legacyData: LegacyCacheData = gson.fromJson(reader, type)
            reader.close()

            val migratedList = legacyData.activities.map { legacy ->
                ArchiveActivity(
                    stravaActivityId = legacy.id,
                    intervalsActivityId = null,
                    source = SourceProvider.STRAVA,
                    name = legacy.name,
                    distance = legacy.distance,
                    movingTime = legacy.movingTime,
                    startDateLocal = legacy.startDateLocal,
                    averageWatts = legacy.averageWatts,
                    averageHeartrate = legacy.averageHeartrate,
                    totalElevationGain = legacy.totalElevationGain,
                    type = legacy.type,
                    workoutType = legacy.workoutType,
                    description = legacy.description,
                    lastModifiedLocal = System.currentTimeMillis()
                )
            }
            
            saveActivities(context, migratedList, legacyData.timestamp)
            Log.d(TAG, "Successfully migrated ${migratedList.size} activities.")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to migrate legacy data", e)
        }
    }

    private fun parseToLocalDateTime(dateStr: String?): LocalDateTime? {
        if (dateStr == null || dateStr.length < 10) return null
        return try {
            java.time.OffsetDateTime.parse(dateStr, DateTimeFormatter.ISO_DATE_TIME).toLocalDateTime()
        } catch (e1: Exception) {
            try {
                LocalDateTime.parse(dateStr, DateTimeFormatter.ISO_DATE_TIME)
            } catch (e2: Exception) {
                try {
                    java.time.LocalDate.parse(dateStr.take(10), DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay()
                } catch (e3: Exception) {
                    null
                }
            }
        }
    }

    /**
     * De-dup matching logic to find if a candidate activity already exists in the archive.
     * Matches on type + startDateLocal (within a tolerance window) + movingTime (within ~5% tolerance).
     */
    fun findExistingMatch(candidate: ArchiveActivity, archive: List<ArchiveActivity>): ArchiveActivity? {
        // Try strict ID match first
        val strictMatch = archive.find {
            (candidate.stravaActivityId != null && candidate.stravaActivityId == it.stravaActivityId) ||
            (candidate.intervalsActivityId != null && candidate.intervalsActivityId == it.intervalsActivityId)
        }
        if (strictMatch != null) return strictMatch

        val candidateTime = parseToLocalDateTime(candidate.startDateLocal)

        // Heuristic matching
        return archive.find { existing ->
            val typeMatches = existing.type.equals(candidate.type, ignoreCase = true) || 
                              existing.isPlaceholder() || 
                              candidate.isPlaceholder()
            if (!typeMatches) return@find false

            val existingTime = parseToLocalDateTime(existing.startDateLocal)

            if (candidateTime != null && existingTime != null) {
                val hoursDiff = abs(ChronoUnit.HOURS.between(candidateTime, existingTime))
                if (hoursDiff > 12) return@find false

                val candidateMoving = candidate.movingTime.toDouble()
                val existingMoving = existing.movingTime.toDouble()
                
                if (existingMoving == 0.0) return@find candidateMoving == 0.0
                
                val diffPercent = abs(candidateMoving - existingMoving) / existingMoving
                diffPercent <= 0.05
            } else {
                val candidateDateStr = candidate.startDateLocal.take(10)
                val existingDateStr = existing.startDateLocal.take(10)
                candidateDateStr == existingDateStr && existing.movingTime == candidate.movingTime
            }
        }
    }

    /**
     * Scans the archive list and removes dead "Unknown Activity" or "Strava Activity" placeholders
     * if a more detailed activity exists for the same local date and time.
     */
    fun deduplicateUnknownActivities(archive: List<ArchiveActivity>): List<ArchiveActivity> {
        if (archive.isEmpty()) return archive

        val result = archive.toMutableList()
        val toRemove = mutableSetOf<ArchiveActivity>()

        for (i in result.indices) {
            val itemA = result[i]
            if (toRemove.contains(itemA)) continue

            val isPlaceholderA = itemA.isPlaceholder()

            for (j in i + 1 until result.size) {
                val itemB = result[j]
                if (toRemove.contains(itemB)) continue

                val isPlaceholderB = itemB.isPlaceholder()

                // Only compare when one is placeholder and the other is detailed
                if (isPlaceholderA == isPlaceholderB) continue

                // Check if they match via date/time/duration heuristics or IDs
                val match = findExistingMatch(itemA, listOf(itemB))
                if (match != null) {
                    val placeholderItem = if (isPlaceholderA) itemA else itemB
                    val detailedItem = if (isPlaceholderA) itemB else itemA
                    val detailedIndex = result.indexOf(detailedItem)

                    if (detailedIndex != -1) {
                        // Merge IDs & description from placeholder item into detailed item
                        val mergedDetailed = result[detailedIndex].copy(
                            stravaActivityId = result[detailedIndex].stravaActivityId ?: placeholderItem.stravaActivityId,
                            intervalsActivityId = result[detailedIndex].intervalsActivityId ?: placeholderItem.intervalsActivityId,
                            description = if (!result[detailedIndex].description.isNullOrBlank()) result[detailedIndex].description else placeholderItem.description
                        )
                        result[detailedIndex] = mergedDetailed
                    }
                    toRemove.add(placeholderItem)
                }
            }
        }

        result.removeAll(toRemove)
        return result
    }

    @Synchronized
    fun getWellnessList(context: Context): List<IntervalsWellness> {
        if (cachedWellnessList != null) {
            return cachedWellnessList!!
        }

        try {
            val file = File(context.filesDir, WELLNESS_CACHE_FILE_NAME)
            if (!file.exists()) {
                cachedWellnessList = emptyList()
                return emptyList()
            }

            val reader = FileReader(file)
            val type = object : TypeToken<List<IntervalsWellness>>() {}.type
            try {
                val list: List<IntervalsWellness>? = gson.fromJson(reader, type)
                reader.close()
                cachedWellnessList = list?.sortedBy { it.id } ?: emptyList()
            } catch (e1: Exception) {
                reader.close()
                // Try legacy single object parse fallback
                try {
                    val singleReader = FileReader(file)
                    val single: IntervalsWellness? = gson.fromJson(singleReader, IntervalsWellness::class.java)
                    singleReader.close()
                    cachedWellnessList = if (single != null) listOf(single) else emptyList()
                } catch (e2: Exception) {
                    cachedWellnessList = emptyList()
                }
            }
            return cachedWellnessList!!
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load wellness cache file", e)
            cachedWellnessList = emptyList()
            return emptyList()
        }
    }

    @Synchronized
    fun saveWellnessList(context: Context, newWellnessList: List<IntervalsWellness>) {
        if (newWellnessList.isEmpty()) return
        try {
            val currentList = getWellnessList(context).toMutableList()
            for (item in newWellnessList) {
                val index = currentList.indexOfFirst { it.id == item.id }
                if (index != -1) {
                    currentList[index] = item
                } else {
                    currentList.add(item)
                }
            }

            val sortedList = currentList.sortedBy { it.id }
            val jsonString = gson.toJson(sortedList)
            val tempFile = File(context.filesDir, "$WELLNESS_CACHE_FILE_NAME.tmp")
            val targetFile = File(context.filesDir, WELLNESS_CACHE_FILE_NAME)

            val writer = FileWriter(tempFile)
            writer.write(jsonString)
            writer.flush()
            writer.close()

            if (targetFile.exists() && !targetFile.delete()) {
                Log.e(TAG, "Failed to delete old wellness target file")
            }
            if (!tempFile.renameTo(targetFile)) {
                Log.e(TAG, "Failed to rename wellness temp file")
            } else {
                Log.d(TAG, "Successfully saved ${sortedList.size} daily wellness entries to cache.")
                cachedWellnessList = sortedList
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save wellness file", e)
        }
    }

    fun getLatestWellness(context: Context): IntervalsWellness? {
        return getWellnessList(context).maxByOrNull { it.id }
    }

    fun getWellnessForDate(context: Context, targetDateStr: String?): IntervalsWellness? {
        val list = getWellnessList(context)
        if (list.isEmpty()) return null

        if (targetDateStr == null) {
            return list.maxByOrNull { it.id }
        }

        val exact = list.find { it.id == targetDateStr }
        if (exact != null) return exact

        return list.filter { it.id <= targetDateStr }.maxByOrNull { it.id }
    }
}
