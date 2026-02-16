package com.gratus.workoutrepo

import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.RotateAnimation
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
        val tvSubTitle = view.findViewById<TextView>(R.id.tvSubTitle)
        val recyclerView = view.findViewById<RecyclerView>(R.id.rvActivities)
        val progressBar = view.findViewById<ProgressBar>(R.id.progressBar) // Optional: Add a progress bar to your layout
        val refreshBtn = view.findViewById<ImageButton>(R.id.refresh_btn)

        tvTitle.text = "Strava Activities on $dayOfWeek"

        // Setup RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(context)

        // Animation for the refresh button
        val rotateAnim = RotateAnimation(
            0f, 360f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            duration = 1000
            repeatCount = Animation.INFINITE
        }

        fun loadData(forceRefresh: Boolean) {
            lifecycleScope.launch {
                // UI STATE: LOADING
                if (forceRefresh) {
                    refreshBtn.startAnimation(rotateAnim) // Spin the button
                    refreshBtn.isEnabled = false // Prevent double-clicking
                } else {
                    progressBar.visibility = View.VISIBLE
                    recyclerView.visibility = View.INVISIBLE
                }

                // DATA FETCH
                val activities = withContext(Dispatchers.IO) {
                    // Pass Context so we can save/load the JSON file
                    StravaRepository.getActivitiesForDay(requireContext(), dayOfWeek, forceRefresh)
                }

                // --- NEW: Update the Subtitle/Timestamp ---
                val lastSync = StravaRepository.getLastSyncTime(requireContext())
                if (lastSync > 0) {
                    val timeAgo = DateUtils.getRelativeTimeSpanString(
                        lastSync,
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS
                    )
                    // You can update a secondary TextView or just toast it
                    tvSubTitle.text = "Last Synced ($timeAgo)"
                }

                // UI STATE: FINISHED
                refreshBtn.clearAnimation()
                refreshBtn.isEnabled = true
                progressBar.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE

                if (activities.isNotEmpty()) {
                    recyclerView.adapter = StravaAdapter(activities)
                    tvTitle.text = "Strava Activities on $dayOfWeek"
                } else {
                    tvTitle.text = "No recent $dayOfWeek activities"
                    // Optional: Clear adapter to show empty state
                    recyclerView.adapter = null
                }
            }
        }

        // 1. Initial Load (Silent check of cache)
        loadData(forceRefresh = false)

        // 2. Button Click (Force Network Refresh)
        refreshBtn.setOnClickListener {
            loadData(forceRefresh = true)
        }

        // --- NEW CODE END ---
        /*** */
        ViewCompat.setOnApplyWindowInsetsListener(
            view.findViewById<View?>(R.id.bottom_sheet_root)!!,
            OnApplyWindowInsetsListener { v: View?, insets: WindowInsetsCompat? ->
                val systemBars = insets!!.getInsets(WindowInsetsCompat.Type.systemBars())
                v!!.setPadding(
                    systemBars.left,
                    0,
                    systemBars.right,
                    0
                )
                insets
            })
    }
}