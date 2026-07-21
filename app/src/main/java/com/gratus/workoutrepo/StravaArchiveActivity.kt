package com.gratus.workoutrepo

import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.gratus.workoutrepo.strava.utils.StravaListManager
import com.gratus.workoutrepo.strava.repository.StravaRepository
import kotlinx.coroutines.launch

class StravaArchiveActivity : BaseActivity() {

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.enableEdgeToEdge()
        setContentView(R.layout.activity_strava_archive)

        val rootView = findViewById<View>(android.R.id.content)

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { v: View, insets: WindowInsetsCompat ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, 0, systemBars.right, 0)
            insets
        }

        val prefs = getSharedPreferences(BaseActivity.PREFS_NAME, MODE_PRIVATE)
        val activeSource = prefs.getString("ActiveSyncSource", "STRAVA")
        val sourceName = if ("INTERVALS_ICU" == activeSource) "Intervals.icu" else "Strava"

        val fitnessRow = findViewById<View>(R.id.fitnessRow)
        if ("INTERVALS_ICU" == activeSource) {
            fitnessRow.visibility = View.VISIBLE
            val tvFitnessValue = findViewById<TextView>(R.id.tvFitnessValue)
            val tvFatigueValue = findViewById<TextView>(R.id.tvFatigueValue)
            val tvFormValue = findViewById<TextView>(R.id.tvFormValue)
            
            // Load and display cached wellness immediately
            val cachedWellness = com.gratus.workoutrepo.archive.data.ActivityArchiveManager.getLatestWellness(this)
            if (cachedWellness != null) {
                tvFitnessValue.text = cachedWellness.ctl?.toInt()?.toString() ?: "0"
                tvFatigueValue.text = cachedWellness.atl?.toInt()?.toString() ?: "0"
                tvFormValue.text = cachedWellness.tsb?.toInt()?.toString() ?: "0"
            }

            lifecycleScope.launch {
                val wellness = com.gratus.workoutrepo.intervalsicu.repository.IntervalsRepository.getLatestWellness(this@StravaArchiveActivity)
                if (wellness != null) {
                    tvFitnessValue.text = wellness.ctl?.toInt()?.toString() ?: "0"
                    tvFatigueValue.text = wellness.atl?.toInt()?.toString() ?: "0"
                    tvFormValue.text = wellness.tsb?.toInt()?.toString() ?: "0"
                }
            }
        } else {
            fitnessRow.visibility = View.GONE
        }

        val listManager = StravaListManager(
            context = this,
            lifecycleScope = lifecycleScope,
            rootView = rootView,
            titlePrefix = "$sourceName Activities",
            fetchMasterList = { forceRefresh ->
                StravaRepository.getAllActivities(this, forceRefresh)
            }
        )
        listManager.setup()
    }
}