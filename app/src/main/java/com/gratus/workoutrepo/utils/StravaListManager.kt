package com.gratus.workoutrepo.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.Editable
import android.text.TextWatcher
import android.text.format.DateUtils
import android.view.View
import android.view.animation.Animation
import android.view.animation.RotateAnimation
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.gratus.workoutrepo.BaseActivity
import com.gratus.workoutrepo.EditorBottomSheet
import com.gratus.workoutrepo.R
import com.gratus.workoutrepo.adapters.StravaAdapter
import com.gratus.workoutrepo.data.StravaActivity
import com.gratus.workoutrepo.repository.StravaRepository

import com.bumptech.glide.Glide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StravaListManager(
    private val context: Context,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val rootView: View,
    private val titlePrefix: String,
    private val fetchMasterList: suspend (forceRefresh: Boolean) -> List<StravaActivity>
) {
    // State Variables
    private var allActivities: List<StravaActivity> = emptyList()
    private var currentSearchQuery: String = ""
    private var currentFilterType: String? = null
    private var searchJob: Job? = null

    // UI Elements (Using 'lazy' so they are found exactly when needed)
    private val tvTitle by lazy { rootView.findViewById<TextView>(R.id.tvTitle) }
    private val tvSubTitle by lazy { rootView.findViewById<TextView>(R.id.tvSubTitle) }
    private val recyclerView by lazy { rootView.findViewById<RecyclerView>(R.id.rvActivities) }
    private val progressBar by lazy { rootView.findViewById<ProgressBar>(R.id.progressBar) }
    private val refreshBtn by lazy { rootView.findViewById<ImageButton>(R.id.refresh_btn) }
    private val activitySearchInput by lazy { rootView.findViewById<TextInputEditText>(R.id.activitySearchInput) }
    private val activityFilterBtn by lazy { rootView.findViewById<ImageButton>(R.id.activityFilter) }
    private val filterItemsContainer by lazy { rootView.findViewById<View>(R.id.filterItemsContainer) }
    private val btnFilterType by lazy { rootView.findViewById<MaterialButton>(R.id.btn_filter_type) }
    private val btnClearFilters by lazy { rootView.findViewById<View>(R.id.btn_clear_filters) }
    private val chipGroupContainer by lazy { rootView.findViewById<View>(R.id.chipGroupContainer) }
    private val chipGroupFilters by lazy { rootView.findViewById<ChipGroup>(R.id.chipGroupFilters) }
    private val textNoMatch by lazy { rootView.findViewById<TextView>(R.id.text_NoMatch) }
    private val stravaProfile by lazy { rootView.findViewById<ImageButton>(R.id.stravaProfile) }

    private val rotateAnim = RotateAnimation(0f, 360f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f).apply {
        duration = 1000
        repeatCount = Animation.INFINITE
    }

    // --- MAIN SETUP ---
    fun setup() {
        EditorBottomSheet.clearFocusOnKeyboardHide(activitySearchInput, rootView)
        recyclerView.layoutManager = LinearLayoutManager(context)

        stravaProfile.setOnClickListener {
            val prefs = context.getSharedPreferences(BaseActivity.PREFS_NAME, Context.MODE_PRIVATE)
            val url = prefs.getString("CustomStravaUrl", "https://www.strava.com/athletes/32298220")
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (e: Exception) {
                Toast.makeText(context, "Invalid URL saved", Toast.LENGTH_SHORT).show()
            }
        }

        // Inside setup() in StravaListManager.kt
        lifecycleScope.launch {
            val avatarUrl = StravaRepository.getProfilePictureUrl(context)
            if (!avatarUrl.isNullOrEmpty()) {
                // Use Glide to load the URL into the ImageButton
                com.bumptech.glide.Glide.with(context)
                    .load(avatarUrl)
                    .circleCrop() // Makes it a perfect circle
                    .placeholder(R.drawable.account_circle) // Default icon while loading
                    .into(stravaProfile)
            }
        }

        // Search Listener
        activitySearchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                currentSearchQuery = s?.toString()?.trim() ?: ""
                searchJob?.cancel()
                searchJob = lifecycleScope.launch { delay(300); applyFilters() }
            }
        })

        activitySearchInput.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .hideSoftInputFromWindow(v.windowToken, 0)
                v.clearFocus()
                true
            } else false
        }

        // Filter UI Listeners
        activityFilterBtn.setOnClickListener {
            val isVisible = filterItemsContainer.visibility == View.VISIBLE
            filterItemsContainer.visibility = if (isVisible) View.GONE else View.VISIBLE
            if (isVisible) chipGroupContainer.visibility = View.GONE
        }

        btnFilterType.setOnClickListener {
            chipGroupContainer.visibility = if (chipGroupContainer.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        btnClearFilters.setOnClickListener {
            currentFilterType = null
            chipGroupFilters.clearCheck()
            btnFilterType.text = "By type"
            btnClearFilters.visibility = View.GONE
            applyFilters()
        }

        chipGroupFilters.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isEmpty()) {
                currentFilterType = null
                btnClearFilters.visibility = View.GONE
            } else {
                currentFilterType = group.findViewById<Chip>(checkedIds.first())?.text.toString()
                btnClearFilters.visibility = View.VISIBLE
            }
            applyFilters()
        }

        refreshBtn.setOnClickListener { loadData(forceRefresh = true) }

        // Start Initial Load
        loadData(forceRefresh = false)
    }

    // --- CLASS METHODS (No more circular dependency issues) ---

    private fun onActivityClick(activityId: Long) {
        lifecycleScope.launch {
            val adapter = recyclerView.adapter as? StravaAdapter ?: return@launch
            adapter.markItemLoading(activityId)

            val detailedActivity = withContext(Dispatchers.IO) {
                StravaRepository.getActivityDetails(context, activityId)
            }

            if (detailedActivity != null) {
                allActivities = withContext(Dispatchers.IO) { fetchMasterList(false) }
                adapter.markItemLoading(null)
                applyFilters() // Safe to call here
            } else {
                adapter.markItemLoading(null)
                Toast.makeText(context, "Failed to load details", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun bindList(list: List<StravaActivity>, forceHardRedraw: Boolean = false) {
        if (recyclerView.adapter == null || forceHardRedraw) {
            recyclerView.adapter = StravaAdapter(list, ::onActivityClick) // Safe to reference here
        } else {
            (recyclerView.adapter as StravaAdapter).updateList(list)
        }
    }

    private fun applyFilters(forceHardRedraw: Boolean = false) {
        var filtered = FilterEngine.filterItems(
            items = allActivities,
            searchQuery = currentSearchQuery,
            searchableTextSelector = { listOf(it.name, it.description) },
            typeFilter = if (currentFilterType == "Race") null else currentFilterType,
            typeSelector = { it.type }
        )

        if (currentFilterType == "Race") {
            filtered = filtered.filter { it.workoutType == 1 || it.workoutType == 11 }
        }

        bindList(filtered, forceHardRedraw)

        if (filtered.isEmpty() && (currentSearchQuery.isNotEmpty() || currentFilterType != null)) {
            textNoMatch.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            textNoMatch.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }

        btnFilterType.text = if (currentFilterType != null) "By type: $currentFilterType (${filtered.size})" else "By type"
    }

    private fun loadData(forceRefresh: Boolean) {
        lifecycleScope.launch {
            if (forceRefresh) {
                refreshBtn.startAnimation(rotateAnim)
                refreshBtn.isEnabled = false
            } else {
                progressBar.visibility = View.VISIBLE
                recyclerView.visibility = View.INVISIBLE
            }

            val (activities, lastSync) = withContext(Dispatchers.IO) {
                val acts = fetchMasterList(forceRefresh)
                val syncTime = StravaRepository.getLastSyncTime(context)
                Pair(acts, syncTime)
            }

            if (lastSync > 0) {
                tvSubTitle.text = "Last Synced (${DateUtils.getRelativeTimeSpanString(lastSync, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)})"
            }

            refreshBtn.clearAnimation()
            refreshBtn.isEnabled = true
            progressBar.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE

            if (activities.isNotEmpty()) {
                allActivities = activities

                chipGroupFilters.removeAllViews()
                val uniqueTypes = activities.map { it.type }.distinct().toMutableList()

                if (activities.any { it.workoutType == 1 || it.workoutType == 11 }) {
                    uniqueTypes.add(0, "Race")
                }

                uniqueTypes.forEach { type ->
                    val chip = Chip(context).apply {
                        text = type
                        isCheckable = true
                        isClickable = true
                        chipCornerRadius = 12f * context.resources.displayMetrics.density
                        chipBackgroundColor = ContextCompat.getColorStateList(context, R.color.chip_bg_selector)
                        isChecked = type == currentFilterType
                    }
                    chipGroupFilters.addView(chip)
                }

                applyFilters(forceHardRedraw = forceRefresh)
                tvTitle.text = "$titlePrefix (${activities.size})"
            } else {
                allActivities = emptyList()
                tvTitle.text = "No recent activities"
                recyclerView.adapter = null
            }
        }
    }
}