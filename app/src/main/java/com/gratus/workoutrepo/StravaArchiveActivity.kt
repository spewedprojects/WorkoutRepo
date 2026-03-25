package com.gratus.workoutrepo

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.text.format.DateUtils
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.gratus.workoutrepo.adapters.StravaAdapter
import com.gratus.workoutrepo.data.StravaActivity
import com.gratus.workoutrepo.repository.StravaRepository
import com.gratus.workoutrepo.utils.FilterEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StravaArchiveActivity : BaseActivity() {

    private var allActivities: List<StravaActivity> = emptyList()
    private var currentSearchQuery: String = ""
    private var currentFilterType: String? = null
    private var searchJob: Job? = null

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.enableEdgeToEdge()
        setContentView(R.layout.activity_strava_archive)

        ViewCompat.setOnApplyWindowInsetsListener(
            findViewById<View?>(R.id.strava_archive_activity)
        ) { v: View?, insets: WindowInsetsCompat? ->
            val systemBars = insets!!.getInsets(WindowInsetsCompat.Type.systemBars())
            v!!.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val tvTitle2 = findViewById<TextView>(R.id.tvTitle2)
        val tvSubTitle2 = findViewById<TextView>(R.id.tvSubTitle2)
        val recyclerView = findViewById<RecyclerView>(R.id.rvActivities2)
        val progressBar2 = findViewById<ProgressBar>(R.id.progressBar2)
        val refreshBtn2 = findViewById<ImageButton>(R.id.refresh_btn2)

        val activitySearchInput2 = findViewById<TextInputEditText>(R.id.activitySearchInput2)
        val activityFilterBtn2 = findViewById<ImageButton>(R.id.activityFilter2)
        val filterItemsContainer2 = findViewById<View>(R.id.filterItemsContainer2)
        val btnFilterType2 = findViewById<MaterialButton>(R.id.btn_filter_type2)
        val btnClearFilters2 = findViewById<View>(R.id.btn_clear_filters2)
        val chipGroupContainer2 = findViewById<View>(R.id.chipGroupContainer2)
        val chipGroupFilters2 = findViewById<ChipGroup>(R.id.chipGroupFilters2)
        val textNoMatch2 = findViewById<TextView>(R.id.text_NoMatch2)
        val stravaProfile2 = findViewById<ImageButton>(R.id.stravaProfile2)

        EditorBottomSheet.clearFocusOnKeyboardHide(activitySearchInput2, findViewById(android.R.id.content))

        tvTitle2.text = "Strava Activities"

        recyclerView.layoutManager = LinearLayoutManager(this)

        stravaProfile2.setOnClickListener {
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val url = prefs.getString("CustomStravaUrl", "https://www.strava.com/athletes/32298220")
            try {
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Invalid URL saved", Toast.LENGTH_SHORT).show()
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
            adapter?.notifyDataSetChanged()

            if (filtered.isEmpty() && (currentSearchQuery.isNotEmpty() || currentFilterType != null)) {
                textNoMatch2.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
            } else if (filtered.isEmpty() && allActivities.isEmpty()) {
                textNoMatch2.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
            } else {
                textNoMatch2.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
            }

            if (currentFilterType != null) {
                btnFilterType2.text = "By type: $currentFilterType (${filtered.size})"
            } else {
                btnFilterType2.text = "By type"
            }
        }

        val onActivityClick: (Long) -> Unit = { activityId ->
            lifecycleScope.launch {
                val adapter = recyclerView.adapter as? StravaAdapter ?: return@launch
                adapter.markItemLoading(activityId)

                val detailedActivity = withContext(Dispatchers.IO) {
                    StravaRepository.getActivityDetails(this@StravaArchiveActivity, activityId)
                }

                if (detailedActivity != null) {
                    val updatedList = withContext(Dispatchers.IO) {
                        StravaRepository.getAllActivities(this@StravaArchiveActivity)
                    }
                    allActivities = updatedList
                    adapter.markItemLoading(null)
                    applyFilters()
                } else {
                    adapter.markItemLoading(null)
                    Toast.makeText(this@StravaArchiveActivity, "Failed to load details", Toast.LENGTH_SHORT).show()
                }
            }
        }

        fun bindList(list: List<StravaActivity>) {
            if (recyclerView.adapter == null) {
                val adapter = StravaAdapter(list, onActivityClick)
                recyclerView.adapter = adapter
            } else {
                val adapter = recyclerView.adapter as StravaAdapter
                adapter.updateList(list)
                adapter.notifyDataSetChanged()
            }
        }

        @RequiresApi(Build.VERSION_CODES.O)
        fun loadData(forceRefresh: Boolean) {
            lifecycleScope.launch {
                if (forceRefresh) {
                    refreshBtn2.isEnabled = false
                } else {
                    progressBar2.visibility = View.VISIBLE
                    recyclerView.visibility = View.INVISIBLE
                }

                val (activities, lastSync) = withContext(Dispatchers.IO) {
                    val acts = StravaRepository.getAllActivities(this@StravaArchiveActivity, forceRefresh)
                    val syncTime = StravaRepository.getLastSyncTime(this@StravaArchiveActivity)
                    Pair(acts, syncTime)
                }
                
                if (lastSync > 0) {
                    val timeAgo = DateUtils.getRelativeTimeSpanString(
                        lastSync,
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS
                    )
                    tvSubTitle2.text = "Last Synced ($timeAgo)"
                }

                refreshBtn2.isEnabled = true
                progressBar2.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE

                if (activities.isNotEmpty()) {
                    allActivities = activities
                    
                    val uniqueTypes = activities.map { it.type }.distinct()
                    chipGroupFilters2.removeAllViews()
                    for (type in uniqueTypes) {
                        val chip = Chip(this@StravaArchiveActivity).apply {
                            text = type
                            isCheckable = true
                            isClickable = true
                            chipCornerRadius = 12f * resources.displayMetrics.density
                            chipBackgroundColor = ContextCompat.getColorStateList(this@StravaArchiveActivity, R.color.chip_bg_selector)
                        }
                        if (type == currentFilterType) {
                            chip.isChecked = true
                        }
                        chipGroupFilters2.addView(chip)
                    }

                    bindList(activities)
                    applyFilters()
                    tvTitle2.text = "Strava Activities (${activities.size})"
                } else {
                    allActivities = emptyList()
                    tvTitle2.text = "No recent activities"
                    recyclerView.adapter = null
                }
            }
        }

        activitySearchInput2.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                currentSearchQuery = s?.toString()?.trim() ?: ""
                searchJob?.cancel()
                searchJob = lifecycleScope.launch {
                    delay(300)
                    applyFilters()
                }
            }
        })

        activitySearchInput2.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(v.windowToken, 0)
                v.clearFocus()
                true
            } else {
                false
            }
        }

        activityFilterBtn2.setOnClickListener {
            if (filterItemsContainer2.visibility == View.VISIBLE) {
                filterItemsContainer2.visibility = View.GONE
                chipGroupContainer2.visibility = View.GONE
            } else {
                filterItemsContainer2.visibility = View.VISIBLE
            }
        }

        btnFilterType2.setOnClickListener {
            if (chipGroupContainer2.visibility == View.VISIBLE) {
                chipGroupContainer2.visibility = View.GONE
            } else {
                chipGroupContainer2.visibility = View.VISIBLE
            }
        }

        btnClearFilters2.setOnClickListener {
            currentFilterType = null
            chipGroupFilters2.clearCheck()
            btnFilterType2.text = "By type"
            btnClearFilters2.visibility = View.GONE
            applyFilters()
        }

        chipGroupFilters2.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isEmpty()) {
                currentFilterType = null
                btnFilterType2.text = "By type"
                btnClearFilters2.visibility = View.GONE
            } else {
                val selectedChip = group.findViewById<Chip>(checkedIds.first())
                if (selectedChip != null) {
                    currentFilterType = selectedChip.text.toString()
                    btnClearFilters2.visibility = View.VISIBLE
                }
            }
            applyFilters()
        }

        loadData(forceRefresh = false)

        refreshBtn2.setOnClickListener {
            loadData(forceRefresh = true)
        }
    }
}