package com.gratus.workoutrepo.adapters

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.gratus.workoutrepo.R
import com.gratus.workoutrepo.data.StravaActivity
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class StravaAdapter(private val items: List<StravaActivity>) :
    RecyclerView.Adapter<StravaAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.tvActivityTitle)
        val summary: TextView = view.findViewById(R.id.tvActivitySummary)
        val details: TextView = view.findViewById(R.id.tvActivityDetails)
        val date: TextView = view.findViewById(R.id.tvActivityDate)
        val icon: ImageView = view.findViewById(R.id.ivActivityIcon) // The icon
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_strava_activity, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]

        // --- CHANGE 1: Click to Open in Browser ---
        holder.itemView.setOnClickListener {
            val context = holder.itemView.context
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.strava.com/activities/${item.id}"))
            context.startActivity(browserIntent)
        }

        // 1. Set Title
        holder.title.text = item.name

        // --- CHANGE 2: Show Description if available, else Type ---
        // Strava sometimes returns null description in list view.
        // Logic: If description is not empty, use it. Otherwise fall back to Type.
        if (!item.description.isNullOrBlank()) {
            holder.summary.text = item.description
            holder.summary.maxLines = 2 // Allow 2 lines for better readability
        } else {
            holder.summary.text = item.type
        }

        // 3. Format Details String: "25.3 km • 1h 05m • 210w • 115bpm • 120m"
        val km = String.format("%.1f km", item.distance / 1000)

        val hours = item.movingTime / 3600
        val minutes = (item.movingTime % 3600) / 60
        val time = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"

        // Handle optional data (Watts, HR, Elevation)
        val parts = mutableListOf(km, time)

        item.averageWatts?.let { parts.add("${it.toInt()}w") }
        item.averageHeartrate?.let { parts.add("${it.toInt()}bpm") }
        item.totalElevationGain?.let {
            if (it > 0) parts.add("${it.toInt()}mts")
        }
        holder.details.text = parts.joinToString(" • ")

        // 4. Set Date
        // Date Formatting
        try {
            val isoDate = LocalDate.parse(item.startDateLocal, DateTimeFormatter.ISO_DATE_TIME)
            holder.date.text = isoDate.format(DateTimeFormatter.ofPattern("d MMM yyyy"))
        } catch (e: Exception) {
            holder.date.text = item.startDateLocal // Fallback
        }

        // 5. Dynamic Icon Logic
        val iconRes = when (item.type) {
            "Ride", "VirtualRide", "E-BikeRide" -> R.drawable.strava_roadbike
            "Run" -> R.drawable.strava_run
            "Walk" -> R.drawable.strava_walk
            "Hike" -> R.drawable.strava_hike
            "WeightTraining", "CrossFit" -> R.drawable.strava_weighttraing // check spelling in your file
            "Soccer" -> R.drawable.strava_football
            "Workout" -> R.drawable.strava_workout
            else -> R.drawable.strava_workout // Fallback default
        }
        holder.icon.setImageResource(iconRes)
    }

    override fun getItemCount() = items.size
}