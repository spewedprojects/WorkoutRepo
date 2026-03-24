package com.gratus.workoutrepo

import android.os.Build
import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.RotateAnimation
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

import com.gratus.workoutrepo.EditorBottomSheet.clearFocusOnKeyboardHide
import com.gratus.workoutrepo.adapters.StravaAdapter
import com.gratus.workoutrepo.data.StravaActivity
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

    private var allActivities: List<StravaActivity> = emptyList()
    private var currentSearchQuery: String = ""
    private var currentFilterType: String? = null
    private var searchJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Apply the same transparent theme to remove default background and dim
        setStyle(STYLE_NORMAL, R.style.TransparentBottomSheetDialogTheme)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottomsheet_strava_actvities, container, false)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvTitle = view.findViewById<TextView>(R.id.tvTitle)
        val tvSubTitle = view.findViewById<TextView>(R.id.tvSubTitle)
        val recyclerView = view.findViewById<RecyclerView>(R.id.rvActivities)
        val progressBar = view.findViewById<ProgressBar>(R.id.progressBar) // Optional: Add a progress bar to your layout
        val refreshBtn = view.findViewById<ImageButton>(R.id.refresh_btn)

        val activitySearchInput = view.findViewById<TextInputEditText>(R.id.activitySearchInput)
        val activityFilterBtn = view.findViewById<ImageButton>(R.id.activityFilter)
        val filterItemsContainer = view.findViewById<View>(R.id.filterItemsContainer)
        val btnFilterType = view.findViewById<MaterialButton>(R.id.btn_filter_type)
        val btnClearFilters = view.findViewById<View>(R.id.btn_clear_filters)
        val chipGroupContainer = view.findViewById<View>(R.id.chipGroupContainer)
        val chipGroupFilters = view.findViewById<ChipGroup>(R.id.chipGroupFilters)
        val textNoMatch = view.findViewById<TextView>(R.id.text_NoMatch)

        clearFocusOnKeyboardHide(activitySearchInput, view)

        tvTitle.text = "Strava Activities on ${dayOfWeek}s"

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

        fun applyFilters() {
            var filtered = allActivities
            if (currentSearchQuery.isNotEmpty()) {
                filtered = filtered.filter {
                    it.name.contains(currentSearchQuery, ignoreCase = true) ||
                            (it.description?.contains(currentSearchQuery, ignoreCase = true) == true)
                }
            }
            if (currentFilterType != null) {
                filtered = filtered.filter {
                    it.type.equals(currentFilterType, ignoreCase = true)
                }
            }

            val adapter = recyclerView.adapter as? StravaAdapter
            adapter?.updateList(filtered)
            adapter?.notifyDataSetChanged()

            if (filtered.isEmpty() && (currentSearchQuery.isNotEmpty() || currentFilterType != null)) {
                textNoMatch.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
            } else if (filtered.isEmpty() && allActivities.isEmpty()) {
                textNoMatch.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
            } else {
                textNoMatch.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
            }

            if (currentFilterType != null) {
                btnFilterType.text = "By type: $currentFilterType (${filtered.size})"
            } else {
                btnFilterType.text = "By type"
            }
        }

        // 1. Define the Click Listener separately
        val onActivityClick: (Long) -> Unit = { activityId ->
            lifecycleScope.launch {
                val adapter = recyclerView.adapter as? StravaAdapter ?: return@launch

                // UI: Set "Fetching..." state
                adapter.markItemLoading(activityId)

                // Network: Fetch details
                val detailedActivity = withContext(Dispatchers.IO) {
                    StravaRepository.getActivityDetails(requireContext(), activityId)
                }

                // UI: Update ONLY the specific item if successful
                if (detailedActivity != null) {
                    // A. Get the fresh master list from Repo (This contains the new description)
                    val updatedList = withContext(Dispatchers.IO) {
                        StravaRepository.getActivitiesForDay(requireContext(), dayOfWeek)
                    }

                    // B. Update our master tracking list
                    allActivities = updatedList

                    // C. Clear the loading flag!
                    adapter.markItemLoading(null)

                    // D. Push the new master list through the Filter Engine
                    // This will automatically update the adapter with the correctly filtered items
                    applyFilters()

                    // Note: We completely removed the manual adapter.notifyItemChanged(index)
                    // because your updateList function uses DiffUtil, which automatically
                    // detects the changed description and animates it smoothly!
                } else {
                    // Optional: If fetch failed, clear loading so it goes back to "Tap to load"
                    adapter.markItemLoading(null)
                    Toast.makeText(context, "Failed to load details", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // 2. Initial Setup (Run this only once when data first loads)
        fun bindList(list: List<StravaActivity>) {
            if (recyclerView.adapter == null) {
                // First time: Attach the adapter
                val adapter = StravaAdapter(list, onActivityClick)
                recyclerView.adapter = adapter
            } else {
                // Subsequent refreshes (e.g. Pull-to-Refresh): Reload whole list
                val adapter = recyclerView.adapter as StravaAdapter
                adapter.updateList(list)
                adapter.notifyDataSetChanged()
            }
        }

        @RequiresApi(Build.VERSION_CODES.O)
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
                val (activities, lastSync) = withContext(Dispatchers.IO) {
                    val acts = StravaRepository.getActivitiesForDay(requireContext(), dayOfWeek, forceRefresh)
                    val syncTime = StravaRepository.getLastSyncTime(requireContext())
                    Pair(acts, syncTime) // Return both from the background thread
                }
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
                    allActivities = activities
                    
                    // Setup Chips based on unique types
                    val uniqueTypes = activities.map { it.type }.distinct()
                    chipGroupFilters.removeAllViews()
                    for (type in uniqueTypes) {
                        val chip = Chip(requireContext()).apply {
                            text = type
                            isCheckable = true
                            isClickable = true
                            chipCornerRadius = 12f * resources.displayMetrics.density
                            chipBackgroundColor = ContextCompat.getColorStateList(context, R.color.chip_bg_selector)

                        }
                        if (type == currentFilterType) {
                            chip.isChecked = true
                        }
                        chipGroupFilters.addView(chip)
                    }

                    bindList(activities) // Use the new function
                    applyFilters()
                    tvTitle.text = "Strava Activities on ${dayOfWeek}s (${activities.size})"
                } else {
                    allActivities = emptyList()
                    tvTitle.text = "No recent $dayOfWeek activities"
                    // Optional: Clear adapter to show empty state
                    recyclerView.adapter = null
                }
            }
        }

        // Search
        activitySearchInput.addTextChangedListener(object : TextWatcher {
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

        activitySearchInput.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(v.windowToken, 0)
                v.clearFocus()
                true
            } else {
                false
            }
        }

        activityFilterBtn.setOnClickListener {
            if (filterItemsContainer.visibility == View.VISIBLE) {
                filterItemsContainer.visibility = View.GONE
                chipGroupContainer.visibility = View.GONE
            } else {
                filterItemsContainer.visibility = View.VISIBLE
            }
        }

        btnFilterType.setOnClickListener {
            if (chipGroupContainer.visibility == View.VISIBLE) {
                chipGroupContainer.visibility = View.GONE
            } else {
                chipGroupContainer.visibility = View.VISIBLE
            }
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
                btnFilterType.text = "By type"
                btnClearFilters.visibility = View.GONE
            } else {
                val selectedChip = group.findViewById<Chip>(checkedIds.first())
                if (selectedChip != null) {
                    currentFilterType = selectedChip.text.toString()
                    btnClearFilters.visibility = View.VISIBLE
                }
            }
            applyFilters()
        }

        // 1. Initial Load (Silent check of cache)
        loadData(forceRefresh = false)

        // 2. Button Click (Force Network Refresh)
        refreshBtn.setOnClickListener {
            loadData(forceRefresh = true)
        }

        // Fix for custom background and shadow clipping
        dialog?.setOnShowListener { dialogInterface ->
            val bottomSheetDialog = dialogInterface as com.google.android.material.bottomsheet.BottomSheetDialog
            val bottomSheetInternal = bottomSheetDialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)

            bottomSheetInternal?.let { internal ->
                // 1. Clear the default Material background to respect your theme/layout
                internal.setBackgroundResource(android.R.color.transparent)

                // 2. Disable clipping on the parent to allow shadows to "bleed" out
                (internal.parent as? ViewGroup)?.let { parent ->
                    parent.setClipChildren(false)
                    parent.setClipToPadding(false)
                }
            }
        }

        // --- NEW CODE END ---
        /*** */
        ViewCompat.setOnApplyWindowInsetsListener(
            view.findViewById<View?>(R.id.bottom_sheet_root)!!
        ) { v: View?, insets: WindowInsetsCompat? ->
            val systemBars = insets!!.getInsets(WindowInsetsCompat.Type.systemBars())
            v!!.setPadding(
                systemBars.left,
                0,
                systemBars.right,
                0
            )
            insets
        }
    }
}