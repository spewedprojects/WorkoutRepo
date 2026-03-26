package com.gratus.workoutrepo

import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.gratus.workoutrepo.utils.StravaListManager
import com.gratus.workoutrepo.repository.StravaRepository

class StravaArchiveActivity : BaseActivity() {

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.enableEdgeToEdge()
        setContentView(R.layout.activity_strava_archive)

        val rootView = findViewById<View>(android.R.id.content)

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { v: View, insets: WindowInsetsCompat ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // --- THIS IS ALL YOU NEED NOW ---
        val listManager = StravaListManager(
            context = this,
            lifecycleScope = lifecycleScope,
            rootView = rootView,
            titlePrefix = "Strava Activities",
            fetchMasterList = { forceRefresh ->
                // Archive fetches EVERYTHING
                StravaRepository.getAllActivities(this, forceRefresh)
            }
        )
        listManager.setup()
    }
}