package com.gratus.workoutrepo.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_strava_activity, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]

        holder.title.text = item.name

        // Formatting helper
        val km = String.format("%.1f km", item.distance / 1000)
        val hours = item.movingTime / 3600
        val minutes = (item.movingTime % 3600) / 60
        val time = "${hours}h ${minutes}m"
        val watts = item.averageWatts?.toInt() ?: 0

        holder.details.text = "$km • $time • ${watts}w avg"
        holder.summary.text = item.type // Or description if you fetch it

        // Format Date Pretty
        val isoDate = LocalDate.parse(item.startDateLocal, DateTimeFormatter.ISO_DATE_TIME)
        holder.date.text = isoDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy"))
    }

    override fun getItemCount() = items.size
}