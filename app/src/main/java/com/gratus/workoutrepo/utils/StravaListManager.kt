package com.gratus.workoutrepo.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.Editable
import android.text.TextWatcher
import android.text.format.DateUtils
import android.util.TypedValue
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
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointBackward
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.datepicker.CompositeDateValidator
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

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
    private var currentDateStart: LocalDate? = null
    private var currentDateEnd: LocalDate? = null
    private var currentDateFilterTitle: String = "Monthly stats"
    private var searchJob: Job? = null
    private lateinit var statsManager: StravaStatsManager

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
    private val btnFilterDate by lazy { rootView.findViewById<MaterialButton>(R.id.btn_filter_date) }
    private val btnClearFilters by lazy { rootView.findViewById<View>(R.id.btn_clear_filters) }
    private val activityStatBtn by lazy { rootView.findViewById<ImageButton>(R.id.activityStat) }
    private val rootStats by lazy { rootView.findViewById<View>(R.id.rootStats) }
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
        statsManager = StravaStatsManager(rootView)

        val prefs = context.getSharedPreferences(BaseActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val isStatsVisible = prefs.getBoolean("StravaStatsVisible", false)
        rootStats?.visibility = if (isStatsVisible) View.VISIBLE else View.GONE

        activityStatBtn?.setOnClickListener {
            val isNowVisible = rootStats?.visibility == View.GONE
            rootStats?.visibility = if (isNowVisible) View.VISIBLE else View.GONE
            prefs.edit().putBoolean("StravaStatsVisible", isNowVisible).apply()
        }

        stravaProfile?.setOnClickListener {
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
            if (!avatarUrl.isNullOrEmpty() && stravaProfile != null) {
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
        activityFilterBtn?.setOnClickListener {
            val isVisible = filterItemsContainer?.visibility == View.VISIBLE
            filterItemsContainer?.visibility = if (isVisible) View.GONE else View.VISIBLE
            if (isVisible) chipGroupContainer?.visibility = View.GONE
        }

        btnFilterType?.setOnClickListener {
            chipGroupContainer?.visibility = if (chipGroupContainer?.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        btnClearFilters?.setOnClickListener {
            currentFilterType = null
            currentDateStart = null
            currentDateEnd = null
            currentDateFilterTitle = "Monthly stats"
            chipGroupFilters?.clearCheck()
            btnFilterType?.text = "By type"
            btnFilterDate?.text = "By Date"
            btnClearFilters?.visibility = View.GONE
            applyFilters()
        }

        btnFilterDate?.setOnClickListener { showDatePicker() }

        chipGroupFilters?.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isEmpty()) {
                currentFilterType = null
                btnClearFilters?.visibility = View.GONE
            } else {
                currentFilterType = group.findViewById<Chip>(checkedIds.first())?.text.toString()
                btnClearFilters?.visibility = View.VISIBLE
            }
            applyFilters()
        }

        refreshBtn.setOnClickListener { loadData(forceRefresh = true) }

        // Start Initial Load
        loadData(forceRefresh = false)
    }

    // --- CLASS METHODS (No more circular dependency issues) ---

    private fun showDatePicker() {
        if (allActivities.isEmpty()) return

        // 1) Compute minMillis (oldest activity) and maxMillis (today in UTC)
        val minMillis = run {
            val minDateStr = allActivities.minByOrNull { it.startDateLocal }?.startDateLocal
            try {
                // If startDateLocal is ISO-8601 with offset, Instant.parse works.
                // If it's a local date string like "2023-04-01", parse as LocalDate at start of day.
                if (minDateStr != null) {
                    try {
                        Instant.parse(minDateStr).toEpochMilli()
                    } catch (e: Exception) {
                        // fallback: parse as LocalDate (yyyy-MM-dd)
                        val local = LocalDate.parse(minDateStr)
                        local.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                    }
                } else {
                    MaterialDatePicker.todayInUtcMilliseconds()
                }
            } catch (e: Exception) {
                MaterialDatePicker.todayInUtcMilliseconds()
            }
        }

        val maxMillis = MaterialDatePicker.todayInUtcMilliseconds()

        // 2) Build CalendarConstraints with explicit start and end
        val constraintsBuilder = CalendarConstraints.Builder()
            .setStart(minMillis)
            .setEnd(maxMillis)
            .setValidator(
                CompositeDateValidator.allOf(
                    listOf(
                        DateValidatorPointForward.from(minMillis),
                        DateValidatorPointBackward.now()
                    )
                )
            )

        // 3) Build the range picker and preselect a sensible default range
        //    Preselect last 7 days or clamp to min/max
        val defaultEnd = maxMillis
        val defaultStart = (maxMillis - TimeUnit.DAYS.toMillis(3)).coerceAtLeast(minMillis)
        val defaultSelection = androidx.core.util.Pair(defaultStart, defaultEnd)

        val datePicker = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText("Select Date Range")
            .setCalendarConstraints(constraintsBuilder.build())
            .setSelection(defaultSelection) // important for OK to return a Pair even if user doesn't tap
            .setTheme(R.style.ThemeOverlay_App_DatePicker)
            .build()

        // 4) Listener: handle range, single-day (start==end) and defensive null checks
        datePicker.addOnPositiveButtonClickListener { selection ->
            // selection is Pair<Long, Long>
            val startMillis = selection?.first
            val endMillis = selection?.second

            if (startMillis == null || endMillis == null) {
                // fallback to default selection
                currentDateStart = Instant.ofEpochMilli(defaultStart).atZone(ZoneId.systemDefault()).toLocalDate()
                currentDateEnd = Instant.ofEpochMilli(defaultEnd).atZone(ZoneId.systemDefault()).toLocalDate()
            } else if (startMillis == endMillis) {
                val selectedDate = Instant.ofEpochMilli(startMillis).atZone(ZoneId.systemDefault()).toLocalDate()
                showDurationDialog(selectedDate)
                return@addOnPositiveButtonClickListener
            } else {
                currentDateStart = Instant.ofEpochMilli(startMillis).atZone(ZoneId.systemDefault()).toLocalDate()
                currentDateEnd = Instant.ofEpochMilli(endMillis).atZone(ZoneId.systemDefault()).toLocalDate()
            }

            // update UI and apply filters
            currentDateFilterTitle = "${currentDateStart?.format(DateTimeFormatter.ofPattern("MMM d"))} - ${currentDateEnd?.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))}"
            btnFilterDate.text = "Date: Range"
            btnClearFilters.visibility = View.VISIBLE
            applyFilters()
        }

        // 5) Show the picker
        val activity = context as? AppCompatActivity
        activity?.let {
            datePicker.show(it.supportFragmentManager, "DATE_PICKER")
            it.supportFragmentManager.executePendingTransactions()
            datePicker.dialog?.window?.setBackgroundDrawableResource(R.drawable.calendar_bg)
        }
    }

    private fun showDurationDialog(selectedDate: LocalDate) {
        val options = arrayOf("This Day", "This Week", "This Month", "This Year")
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle("Filter Duration")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        currentDateStart = selectedDate
                        currentDateEnd = selectedDate
                        currentDateFilterTitle = selectedDate.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
                    }
                    1 -> {
                        val startOfWeek = selectedDate.minusDays(selectedDate.dayOfWeek.value.toLong() - 1)
                        val endOfWeek = startOfWeek.plusDays(6)
                        currentDateStart = startOfWeek
                        currentDateEnd = endOfWeek
                        currentDateFilterTitle = "Week of ${startOfWeek.format(DateTimeFormatter.ofPattern("MMM d"))}"
                    }
                    2 -> {
                        currentDateStart = selectedDate.withDayOfMonth(1)
                        currentDateEnd = selectedDate.withDayOfMonth(selectedDate.lengthOfMonth())
                        currentDateFilterTitle = selectedDate.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
                    }
                    3 -> {
                        currentDateStart = selectedDate.withDayOfYear(1)
                        currentDateEnd = selectedDate.withDayOfYear(selectedDate.lengthOfYear())
                        currentDateFilterTitle = selectedDate.format(DateTimeFormatter.ofPattern("yyyy"))
                    }
                }
                btnFilterDate.text = "Date: ${options[which]}"
                btnClearFilters.visibility = View.VISIBLE
                applyFilters()
            }
            .create()

        dialog.window?.setBackgroundDrawableResource(R.drawable.calendar_bg)
        dialog.show()

        // Set font on title after show()
        val titleView = dialog.findViewById<TextView>(androidx.appcompat.R.id.alertTitle)
        titleView?.apply {
            typeface = ResourcesCompat.getFont(context, R.font.atkinsonhyperlegiblenext_medium)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
        }


        // Set font on list items (the options)
        val listView = dialog.listView
        listView?.post {
            for (i in 0 until listView.childCount) {
                (listView.getChildAt(i) as? TextView)?.apply {
                    typeface = ResourcesCompat.getFont(context, R.font.atkinsonhyperlegiblenext_regular)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                }
            }
        }
    }

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

        var listFiltered = filtered
        if (currentDateStart != null && currentDateEnd != null) {
            listFiltered = filtered.filter { item ->
                try {
                    val itemDate = Instant.parse(item.startDateLocal).atZone(ZoneId.systemDefault()).toLocalDate()
                    !itemDate.isBefore(currentDateStart) && !itemDate.isAfter(currentDateEnd)
                } catch (e: Exception) {
                    false
                }
            }
        }
        
        val statsActivities = if (currentDateStart != null && currentDateEnd != null) {
            listFiltered
        } else {
            val now = LocalDate.now()
            val startOfThisMonth = now.withDayOfMonth(1)
            val endOfThisMonth = now.withDayOfMonth(now.lengthOfMonth())
            filtered.filter { item ->
                try {
                    val itemDate = Instant.parse(item.startDateLocal).atZone(ZoneId.systemDefault()).toLocalDate()
                    !itemDate.isBefore(startOfThisMonth) && !itemDate.isAfter(endOfThisMonth)
                } catch (e: Exception) {
                    false
                }
            }
        }

        val title = if (currentDateStart != null) currentDateFilterTitle else "Monthly stats"
        val startPeriod = currentDateStart ?: LocalDate.now().withDayOfMonth(1)
        val endPeriod = currentDateEnd ?: LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth())
        statsManager.updateStats(statsActivities, title, startPeriod, endPeriod)

        bindList(listFiltered, forceHardRedraw)

        if (listFiltered.isEmpty() && (currentSearchQuery.isNotEmpty() || currentFilterType != null || currentDateStart != null)) {
            textNoMatch?.visibility = View.VISIBLE
            recyclerView?.visibility = View.GONE
        } else {
            textNoMatch?.visibility = View.GONE
            recyclerView?.visibility = View.VISIBLE
        }

        btnFilterType?.text = if (currentFilterType != null) "By type: $currentFilterType (${filtered.size})" else "By type"
    }

    private fun loadData(forceRefresh: Boolean) {
        lifecycleScope.launch {
            if (forceRefresh) {
                refreshBtn?.startAnimation(rotateAnim)
                refreshBtn?.isEnabled = false
            } else {
                progressBar?.visibility = View.VISIBLE
                recyclerView?.visibility = View.INVISIBLE
            }

            val (activities, lastSync) = withContext(Dispatchers.IO) {
                val acts = fetchMasterList(forceRefresh)
                val syncTime = StravaRepository.getLastSyncTime(context)
                Pair(acts, syncTime)
            }

            if (lastSync > 0) {
                tvSubTitle?.text = "Last Synced (${DateUtils.getRelativeTimeSpanString(lastSync, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)})"
            }

            refreshBtn?.clearAnimation()
            refreshBtn?.isEnabled = true
            progressBar?.visibility = View.GONE
            recyclerView?.visibility = View.VISIBLE

            if (activities.isNotEmpty()) {
                allActivities = activities

                chipGroupFilters?.removeAllViews()
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
                    chipGroupFilters?.addView(chip)
                }

                applyFilters(forceHardRedraw = forceRefresh)
                tvTitle?.text = "$titlePrefix (${activities.size})"
            } else {
                allActivities = emptyList()
                tvTitle?.text = "No recent activities"
                recyclerView?.adapter = null
            }
        }
    }
}