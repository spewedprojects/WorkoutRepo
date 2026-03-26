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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StravaListManager(
    private val context: Context,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val rootView: View,
    private val titlePrefix: String, // e.g., "Strava Activities" or "Strava Activities on Mondays"
    private val fetchMasterList: suspend (forceRefresh: Boolean) -> List<StravaActivity>
) {
    private var allActivities: List<StravaActivity> = emptyList()
    private var currentSearchQuery: String = ""
    private var currentFilterType: String? = null
    private var searchJob: Job? = null

    fun setup() {
        val tvTitle = rootView.findViewById<TextView>(R.id.tvTitle)
        val tvSubTitle = rootView.findViewById<TextView>(R.id.tvSubTitle)
        val recyclerView = rootView.findViewById<RecyclerView>(R.id.rvActivities)
        val progressBar = rootView.findViewById<ProgressBar>(R.id.progressBar)
        val refreshBtn = rootView.findViewById<ImageButton>(R.id.refresh_btn)

        val activitySearchInput = rootView.findViewById<TextInputEditText>(R.id.activitySearchInput)
        val activityFilterBtn = rootView.findViewById<ImageButton>(R.id.activityFilter)
        val filterItemsContainer = rootView.findViewById<View>(R.id.filterItemsContainer)
        val btnFilterType = rootView.findViewById<MaterialButton>(R.id.btn_filter_type)
        val btnClearFilters = rootView.findViewById<View>(R.id.btn_clear_filters)
        val chipGroupContainer = rootView.findViewById<View>(R.id.chipGroupContainer)
        val chipGroupFilters = rootView.findViewById<ChipGroup>(R.id.chipGroupFilters)
        val textNoMatch = rootView.findViewById<TextView>(R.id.text_NoMatch)
        val stravaProfile = rootView.findViewById<ImageButton>(R.id.stravaProfile)

        EditorBottomSheet.clearFocusOnKeyboardHide(activitySearchInput, rootView)

        recyclerView.layoutManager = LinearLayoutManager(context)

        val rotateAnim = RotateAnimation(
            0f, 360f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f
        ).apply { duration = 1000; repeatCount = Animation.INFINITE }

        stravaProfile.setOnClickListener {
            val prefs = context.getSharedPreferences(BaseActivity.PREFS_NAME, Context.MODE_PRIVATE)
            val url = prefs.getString("CustomStravaUrl", "https://www.strava.com/athletes/32298220")
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (e: Exception) {
                Toast.makeText(context, "Invalid URL saved", Toast.LENGTH_SHORT).show()
            }
        }

        fun applyFilters() {
            val filtered = FilterEngine.filterItems(
                items = allActivities,
                searchQuery = currentSearchQuery,
                searchableTextSelector = { listOf(it.name, it.description) },
                typeFilter = currentFilterType,
                typeSelector = { it.type }
            )

            val adapter = recyclerView.adapter as? StravaAdapter
            adapter?.updateList(filtered)

            if (filtered.isEmpty() && (currentSearchQuery.isNotEmpty() || currentFilterType != null)) {
                textNoMatch.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
            } else {
                textNoMatch.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
            }

            btnFilterType.text = if (currentFilterType != null) "By type: $currentFilterType (${filtered.size})" else "By type"
        }

        val onActivityClick: (Long) -> Unit = { activityId ->
            lifecycleScope.launch {
                val adapter = recyclerView.adapter as? StravaAdapter ?: return@launch
                adapter.markItemLoading(activityId)

                val detailedActivity = withContext(Dispatchers.IO) {
                    StravaRepository.getActivityDetails(context, activityId)
                }

                if (detailedActivity != null) {
                    allActivities = withContext(Dispatchers.IO) { fetchMasterList(false) }
                    adapter.markItemLoading(null)
                    applyFilters()
                } else {
                    adapter.markItemLoading(null)
                    Toast.makeText(context, "Failed to load details", Toast.LENGTH_SHORT).show()
                }
            }
        }

        fun bindList(list: List<StravaActivity>) {
            if (recyclerView.adapter == null) {
                recyclerView.adapter = StravaAdapter(list, onActivityClick)
            } else {
                (recyclerView.adapter as StravaAdapter).updateList(list)
            }
        }

        fun loadData(forceRefresh: Boolean) {
            lifecycleScope.launch {
                if (forceRefresh) {
                    refreshBtn.startAnimation(rotateAnim)
                    refreshBtn.isEnabled = false
                } else {
                    progressBar.visibility = View.VISIBLE
                    recyclerView.visibility = View.INVISIBLE
                }

                val (activities, lastSync) = withContext(Dispatchers.IO) {
                    val acts = fetchMasterList(forceRefresh) // DYNAMIC CALL HERE
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
                    activities.map { it.type }.distinct().forEach { type ->
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

                    bindList(activities)
                    applyFilters()
                    tvTitle.text = "$titlePrefix (${activities.size})"
                } else {
                    allActivities = emptyList()
                    tvTitle.text = "No recent activities"
                    recyclerView.adapter = null
                }
            }
        }

        // --- Listeners ---
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

        // Init
        loadData(forceRefresh = false)
    }
}