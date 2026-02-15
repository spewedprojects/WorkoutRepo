package com.gratus.workoutrepo

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.gratus.workoutrepo.adapters.StravaAdapter
import com.gratus.workoutrepo.repository.StravaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StravaBottomSheet(
    private val dayOfWeek: String // Pass "Monday" etc. here
) : BottomSheetDialogFragment() {

    // If Java complains about "No Zero Argument Constructor" during runtime (rare for this simple use),
    // add a default constructor too:
    constructor() : this("Monday")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottomsheet_strava_actvities, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvTitle = view.findViewById<TextView>(R.id.tvTitle)
        val recyclerView = view.findViewById<RecyclerView>(R.id.rvActivities)
        val progressBar = ProgressBar(context) // Optional: Add a progress bar to your layout

        tvTitle.text = "Strava Activities on $dayOfWeek"

        // Setup RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(context)

        // Launch Coroutine to fetch data
        lifecycleScope.launch {
            // Show loading state if you have one

            val activities = withContext(Dispatchers.IO) {
                StravaRepository.getActivitiesForDay(dayOfWeek)
            }

            // Update UI
            if (activities.isNotEmpty()) {
                recyclerView.adapter = StravaAdapter(activities)
            } else {
                tvTitle.text = "No activities found on recent ${dayOfWeek}s"
            }
        }
    }
}