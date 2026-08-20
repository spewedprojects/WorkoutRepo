package com.gratus.workoutrepo.archive.utils

import com.gratus.workoutrepo.archive.model.SourceProvider
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

object DateTimeUtils {

    private val DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("d MMM yy',' EEE '৹' HH:mm", Locale.getDefault())
    private val DATE_ONLY_FORMATTER = DateTimeFormatter.ofPattern("d MMM yy',' EEE", Locale.getDefault())

    /**
     * Parses a Strava ISO date/time string into a LocalDateTime.
     * Strava's `start_date_local` is already in local wall-clock time (even though it may end with 'Z').
     * Strips any trailing 'Z' or offset indicator so no timezone conversion or shifting is applied.
     */
    fun parseStravaDateTime(dateStr: String?): LocalDateTime? {
        if (dateStr.isNullOrBlank() || dateStr.length < 10) return null
        val normalized = dateStr.trim()

        return try {
            if (normalized.contains("T")) {
                val cleanIso = if (normalized.endsWith("Z", ignoreCase = true)) {
                    normalized.substring(0, normalized.length - 1)
                } else if (normalized.indexOf('+', 10) != -1) {
                    normalized.substring(0, normalized.indexOf('+', 10))
                } else if (normalized.lastIndexOf('-') > 10 && normalized.lastIndexOf('-') > normalized.indexOf('T')) {
                    normalized.substring(0, normalized.lastIndexOf('-'))
                } else {
                    normalized
                }
                LocalDateTime.parse(cleanIso, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            } else {
                LocalDate.parse(normalized.take(10), DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay()
            }
        } catch (e: Exception) {
            try {
                LocalDate.parse(normalized.take(10), DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay()
            } catch (e2: Exception) {
                null
            }
        }
    }

    /**
     * Parses an Intervals.icu ISO date/time string into a LocalDateTime.
     * If the string has an explicit UTC 'Z' or offset, it is converted to the local device system timezone.
     */
    fun parseIntervalsDateTime(dateStr: String?): LocalDateTime? {
        if (dateStr.isNullOrBlank() || dateStr.length < 10) return null
        val normalized = dateStr.trim()

        return try {
            if (normalized.endsWith("Z", ignoreCase = true)) {
                Instant.parse(normalized).atZone(ZoneId.systemDefault()).toLocalDateTime()
            } else if (normalized.contains("T")) {
                if (normalized.indexOf('+', 10) != -1 || (normalized.lastIndexOf('-') > 10 && normalized.lastIndexOf('-') > normalized.indexOf('T'))) {
                    OffsetDateTime.parse(normalized, DateTimeFormatter.ISO_DATE_TIME)
                        .atZoneSameInstant(ZoneId.systemDefault())
                        .toLocalDateTime()
                } else {
                    LocalDateTime.parse(normalized, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                }
            } else {
                LocalDate.parse(normalized.take(10), DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay()
            }
        } catch (e1: Exception) {
            try {
                OffsetDateTime.parse(normalized, DateTimeFormatter.ISO_DATE_TIME)
                    .atZoneSameInstant(ZoneId.systemDefault())
                    .toLocalDateTime()
            } catch (e2: Exception) {
                try {
                    LocalDate.parse(normalized.take(10), DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay()
                } catch (e3: Exception) {
                    null
                }
            }
        }
    }

    /**
     * Parses an ISO date/time string into a LocalDateTime, choosing the appropriate parser based on source.
     */
    fun parseToLocalDateTime(dateStr: String?, source: SourceProvider? = null): LocalDateTime? {
        return when (source) {
            SourceProvider.STRAVA -> parseStravaDateTime(dateStr)
            SourceProvider.INTERVALS_ICU -> parseIntervalsDateTime(dateStr)
            else -> parseStravaDateTime(dateStr) ?: parseIntervalsDateTime(dateStr)
        }
    }

    /**
     * Parses an ISO date string into a LocalDate.
     */
    fun parseToLocalDate(dateStr: String?, source: SourceProvider? = null): LocalDate? {
        if (dateStr.isNullOrBlank() || dateStr.length < 10) return null
        return try {
            LocalDate.parse(dateStr.take(10), DateTimeFormatter.ISO_LOCAL_DATE)
        } catch (e: Exception) {
            parseToLocalDateTime(dateStr, source)?.toLocalDate()
        }
    }

    private fun getCleanZoneCode(): String {
        val tz = java.util.TimeZone.getDefault()
        val id = tz.id ?: ""
        val isDst = tz.inDaylightTime(java.util.Date())
        val rawName = tz.getDisplayName(isDst, java.util.TimeZone.SHORT, Locale.ENGLISH)

        // If java.util.TimeZone gives a 3-5 letter uppercase abbreviation (like IST, EST, PST, JST, UTC, GMT), use it!
        if (rawName.matches(Regex("^[A-Z]{3,5}$"))) {
            return rawName
        }

        // Fallback dictionary for common international timezones where Android ICU defaults to GMT+offset:
        val offsetMillis = tz.getOffset(System.currentTimeMillis())
        return when {
            id.equals("UTC", ignoreCase = true) || id.equals("Z", ignoreCase = true) || (offsetMillis == 0 && id.contains("UTC", ignoreCase = true)) -> "UTC"
            id.contains("Kolkata", ignoreCase = true) || id.contains("Calcutta", ignoreCase = true) || offsetMillis == 19800000 -> "IST"
            id.contains("Tokyo", ignoreCase = true) || offsetMillis == 32400000 -> "JST"
            id.contains("Shanghai", ignoreCase = true) || id.contains("Hong_Kong", ignoreCase = true) -> "CST"
            id.contains("Sydney", ignoreCase = true) || id.contains("Melbourne", ignoreCase = true) -> if (isDst) "AEDT" else "AEST"
            id.contains("London", ignoreCase = true) -> if (isDst) "BST" else "GMT"
            id.contains("Paris", ignoreCase = true) || id.contains("Berlin", ignoreCase = true) || id.contains("Rome", ignoreCase = true) -> if (isDst) "CEST" else "CET"
            id.contains("New_York", ignoreCase = true) -> if (isDst) "EDT" else "EST"
            id.contains("Chicago", ignoreCase = true) -> if (isDst) "CDT" else "CST"
            id.contains("Denver", ignoreCase = true) -> if (isDst) "MDT" else "MST"
            id.contains("Los_Angeles", ignoreCase = true) -> if (isDst) "PDT" else "PST"
            else -> rawName
        }
    }

    /**
     * Formats an activity date string for UI display.
     * Displays time (e.g., "6 Mar 26, Fri ৹ 06:09 IST") ONLY when real time information is present.
     * If time is 00:00 (or date-only), displays "6 Mar 26, Fri" to avoid misleading "00:00" times.
     */
    fun formatActivityDate(dateStr: String?, source: SourceProvider? = null): String {
        if (dateStr.isNullOrBlank()) return ""

        val ldt = parseToLocalDateTime(dateStr, source) ?: return dateStr

        // Check if string originally had specific time (contains 'T' and not 00:00:00)
        val hasTime = dateStr.contains("T") && !(ldt.hour == 0 && ldt.minute == 0 && ldt.second == 0)

        return if (hasTime) {
            "${ldt.format(DATE_TIME_FORMATTER)} ${getCleanZoneCode()}"
        } else {
            ldt.format(DATE_ONLY_FORMATTER)
        }
    }
}
