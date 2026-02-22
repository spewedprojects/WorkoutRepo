package com.gratus.workoutrepo.adapters

import android.content.Intent
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.gratus.workoutrepo.R
import com.gratus.workoutrepo.data.StravaActivity
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import androidx.core.net.toUri
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.Locale

class StravaAdapter(private var items: List<StravaActivity>,
                    private val onActivityClick: (Long) -> Unit // <--- callback
    ) : RecyclerView.Adapter<StravaAdapter.ViewHolder>() {

    // --- NEW: Track which item is loading ---
    private var loadingActivityId: Long? = null

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.tvActivityTitle)
        val summary: TextView = view.findViewById(R.id.tvActivitySummary)
        val details: TextView = view.findViewById(R.id.tvActivityDetails)
        val date: TextView = view.findViewById(R.id.tvActivityDate)
        val icon: ImageView = view.findViewById(R.id.ivActivityIcon) // The icon
    }

    // --- NEW HELPER FUNCTION ---
    fun updateList(newItems: List<StravaActivity>) {
        val diffCallback = object : DiffUtil.Callback() {
            override fun getOldListSize() = items.size
            override fun getNewListSize() = newItems.size

            override fun areItemsTheSame(oldPos: Int, newPos: Int): Boolean {
                return items[oldPos].id == newItems[newPos].id
            }

            override fun areContentsTheSame(oldPos: Int, newPos: Int): Boolean {
                return items[oldPos] == newItems[newPos]
            }
        }

        val diffResult = DiffUtil.calculateDiff(diffCallback)
        this.items = newItems
        diffResult.dispatchUpdatesTo(this) // Magic! Smoothly animates adds/removes/updates
    }

    // --- NEW: Helper to update UI without reloading everything ---
    fun markItemLoading(id: Long?) {
        loadingActivityId = id
        notifyDataSetChanged() // Refresh the list to show the "Fetching..." text
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_strava_activity, parent, false)
        return ViewHolder(view)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]

        // 1. Long Click -> Open Browser
        holder.itemView.setOnLongClickListener {
            val intent = Intent(Intent.ACTION_VIEW,"https://www.strava.com/activities/${item.id}".toUri())
            holder.itemView.context.startActivity(intent)
            true
        }

        // 2. Normal Click -> Load Details
        holder.itemView.setOnClickListener {
            // Only fetch if description is missing
            if (item.description.isNullOrBlank()) {
                onActivityClick(item.id)
            }
        }

        // 1. Set Title
        holder.title.text = item.name

        // --- CHANGE 2: Show Description if available, else Type ---
        // Strava sometimes returns null description in list view.
        // Logic: If description is not empty, use it. Otherwise fall back to Type.
        // --- NEW LOGIC: Priority Order (Loading > Description > Placeholder) ---
        if (item.id == loadingActivityId) {
            // STATE: Loading
            holder.summary.text = "Fetching details..."
            holder.summary.alpha = 0.75f
            holder.summary.maxLines = 1
        }
        else if (!item.description.isNullOrBlank()) {
            // STATE: Description Available
            holder.summary.text = item.description
            holder.summary.alpha = 0.9f
            holder.summary.maxLines = 4 // Expand for reading
        }
        else {
            // STATE: Placeholder
            holder.summary.text = "Tap to load details..."
            holder.summary.alpha = 0.6f
            holder.summary.maxLines = 1
        }

        // 3. Format Details String: "25.3 km • 1h 05m • 210w • 115bpm • 120m"
        val parts = mutableListOf<String>()

        if (item.distance > 0) {
            val km = String.format("%.1f km", item.distance / 1000)
            parts.add(km)
        }

        // Time: Only add if > 0
        if (item.movingTime > 0) {
            val hours = item.movingTime / 3600
            val minutes = (item.movingTime % 3600) / 60
            val time = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}mins"
            parts.add(time)
        }

        item.averageWatts?.let { parts.add("${it.toInt()}w") }
        item.averageHeartrate?.let { parts.add("${it.toInt()}bpm") }
        item.totalElevationGain?.let { if (it > 0) parts.add("${it.toInt()}mts") }
        holder.details.text = parts.joinToString(" • ")

        // 4. Set Date - Date Formatting
        val dateInput = item.startDateLocal // e.g. "2026-02-22T14:05:00Z" or "2026-02-22T14:05:00" or "2026-02-22"

        val dateOutput = try {
            // Try parsing an ISO date-time with offset first
            val odt = OffsetDateTime.parse(dateInput, DateTimeFormatter.ISO_DATE_TIME)
            val fmt = DateTimeFormatter.ofPattern("d MMM yyyy '৹' HH:mm", Locale.getDefault())
            odt.format(fmt)
        } catch (e1: Exception) {
            try {
                // Try parsing a local date-time without offset
                val ldt = LocalDateTime.parse(dateInput, DateTimeFormatter.ISO_DATE_TIME)
                val fmt = DateTimeFormatter.ofPattern("d MMM yyyy '৹' HH:mm", Locale.getDefault())
                ldt.format(fmt)
            } catch (e2: Exception) {
                try {
                    // If it's date-only, show date and no time (or append 0000 if you prefer)
                    val ld = LocalDate.parse(dateInput, DateTimeFormatter.ISO_DATE)
                    val fmtDateOnly = DateTimeFormatter.ofPattern("d MMM yyyy '৹' HH:mm", Locale.getDefault())
                    // append 0000 as time when only date is available
                    ld.atStartOfDay().format(fmtDateOnly)
                } catch (e3: Exception) {
                    // final fallback: raw string
                    dateInput
                }
            }
        }

        holder.date.text = dateOutput

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