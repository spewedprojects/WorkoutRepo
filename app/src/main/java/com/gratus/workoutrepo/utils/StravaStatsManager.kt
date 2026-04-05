package com.gratus.workoutrepo.utils

import android.view.View
import android.widget.TextView
import com.gratus.workoutrepo.R
import com.gratus.workoutrepo.data.StravaActivity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class StravaStatsManager(rootView: View) {

    private val tvStatTitle = rootView.findViewById<TextView?>(R.id.tvStatTitle)
    private val tvMonthTotalActivity = rootView.findViewById<TextView?>(R.id.tvMonthTotalActivity)
    
    private val tvDaysValue = rootView.findViewById<TextView?>(R.id.tvDaysValue)
    private val tvHoursValue = rootView.findViewById<TextView?>(R.id.tvHoursValue)
    private val tvConsistencyValue = rootView.findViewById<TextView?>(R.id.tvConsistencyValue)
    private val tvDistanceValue = rootView.findViewById<TextView?>(R.id.tvDistanceValue)

    /**
     * Updates the UI stats
     * @param activities The filtered list of activities for the selected period
     * @param title The title representing the period (e.g. "Monthly stats", "April stats", etc.)
     * @param periodStart The Start Date of the selected period. If null, defaults to calculating from latest to oldest in list or fallback to 30 days.
     * @param periodEnd The End Date of the selected period.
     */
    fun updateStats(
        activities: List<StravaActivity>, 
        title: String, 
        periodStart: LocalDate?, 
        periodEnd: LocalDate?
    ) {
        tvStatTitle?.text = title
        tvMonthTotalActivity?.text = "Total activities: ${activities.size}"

        if (activities.isEmpty()) {
            tvDaysValue?.text = "0"
            tvHoursValue?.text = "0h"
            tvConsistencyValue?.text = "0%"
            tvDistanceValue?.text = "0"
            return
        }

        // 1. Calculate unique days active
        val activeDates = activities.mapNotNull { activity ->
            try {
                Instant.parse(activity.startDateLocal).atZone(ZoneId.systemDefault()).toLocalDate()
            } catch (e: Exception) {
                null
            }
        }.toSet()
        val numDaysActive = activeDates.size

        // 2. Calculate Total Hours
        val totalSeconds = activities.sumOf { it.movingTime.toLong() }
        val hours = totalSeconds / 3600
        val remainingMinutes = (totalSeconds % 3600) / 60
        val totalHoursText = if (hours > 0) {
            if (hours >= 100) "${hours}h" else "${hours}h ${remainingMinutes}m"
        } else {
            "${remainingMinutes}m"
        }

        // 3. Calculate Distance
        val totalDistanceMeters = activities.sumOf { it.distance.toDouble() }
        val totalDistanceKm = totalDistanceMeters / 1000.0
        val formattedDistance = if (totalDistanceKm > 100) {
            String.format("%.0fk", totalDistanceKm)
        } else {
            String.format("%.1f", totalDistanceKm) + "km"
        }

        // 4. Calculate Consistency
        val totalDaysInPeriod = if (periodStart != null && periodEnd != null) {
            ChronoUnit.DAYS.between(periodStart, periodEnd) + 1
        } else {
            // Default active span logic if no strict range is provided
            val minDate = activeDates.minOrNull()
            val maxDate = activeDates.maxOrNull()
            if (minDate != null && maxDate != null) {
                maxOf(1, ChronoUnit.DAYS.between(minDate, maxDate) + 1)
            } else {
                30L
            }
        }
        val consistency = if (totalDaysInPeriod > 0) {
            (numDaysActive.toDouble() / totalDaysInPeriod.toDouble()) * 100
        } else {
            0.0
        }

        tvDaysValue?.text = numDaysActive.toString()
        tvHoursValue?.text = totalHoursText
        tvConsistencyValue?.text = String.format("%.0f%%", consistency)
        tvDistanceValue?.text = formattedDistance
    }
}
