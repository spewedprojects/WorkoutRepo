package com.gratus.workoutrepo.archive.utils

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object DateTimeUtils {

    private val DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("d MMM yy',' EEE '৹' HH:mm", Locale.getDefault())
    private val DATE_ONLY_FORMATTER = DateTimeFormatter.ofPattern("d MMM yy',' EEE", Locale.getDefault())

    /**
     * Parses an ISO date/time string (from Strava or Intervals.icu) into a LocalDateTime.
     * `startDateLocal` fields are already recorded in local time at the location of the activity.
     * Any trailing 'Z' or offset indicator is stripped so the local wall clock time (e.g. 06:09 AM) is preserved 
     * without shifting hours due to the device's system timezone offset.
     */
    fun parseToLocalDateTime(dateStr: String?): LocalDateTime? {
        if (dateStr.isNullOrBlank() || dateStr.length < 10) return null

        val normalized = dateStr.trim()

        return try {
            if (normalized.contains("T")) {
                val cleanIso = if (normalized.endsWith("Z") || normalized.endsWith("z")) {
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
        } catch (e1: Exception) {
            try {
                OffsetDateTime.parse(normalized, DateTimeFormatter.ISO_DATE_TIME).toLocalDateTime()
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
     * Parses an ISO date string into a LocalDate.
     */
    fun parseToLocalDate(dateStr: String?): LocalDate? {
        if (dateStr.isNullOrBlank() || dateStr.length < 10) return null
        return try {
            LocalDate.parse(dateStr.take(10), DateTimeFormatter.ISO_LOCAL_DATE)
        } catch (e: Exception) {
            parseToLocalDateTime(dateStr)?.toLocalDate()
        }
    }

    /**
     * Formats an activity date string for UI display.
     * Displays time (e.g., "6 Mar 26, Fri ৹ 06:09") ONLY when real time information is present.
     * If time is 00:00 (or date-only), displays "6 Mar 26, Fri" to avoid misleading "00:00" times.
     */
    fun formatActivityDate(dateStr: String?): String {
        if (dateStr.isNullOrBlank()) return ""

        val ldt = parseToLocalDateTime(dateStr) ?: return dateStr

        // Check if string originally had specific time (contains 'T' and not 00:00:00)
        val hasTime = dateStr.contains("T") && !(ldt.hour == 0 && ldt.minute == 0 && ldt.second == 0)

        return if (hasTime) {
            ldt.format(DATE_TIME_FORMATTER)
        } else {
            ldt.format(DATE_ONLY_FORMATTER)
        }
    }
}
