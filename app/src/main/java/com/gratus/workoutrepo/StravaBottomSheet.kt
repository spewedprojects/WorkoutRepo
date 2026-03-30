package com.gratus.workoutrepo

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.gratus.workoutrepo.utils.StravaListManager
import com.gratus.workoutrepo.repository.StravaRepository

class StravaBottomSheet(
    private val dayOfWeek: String = "Monday"
) : BottomSheetDialogFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //setStyle(STYLE_NORMAL, R.style.TransparentBottomSheetDialogTheme)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.bottomsheet_strava_actvities, container, false)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // --- THIS IS ALL YOU NEED NOW ---
        val listManager = StravaListManager(
            context = requireContext(),
            lifecycleScope = lifecycleScope,
            rootView = view,
            titlePrefix = "Strava Activities on ${dayOfWeek}s",
            fetchMasterList = { forceRefresh ->
                // BottomSheet fetches only specific day
                StravaRepository.getActivitiesForDay(requireContext(), dayOfWeek, forceRefresh)
            }
        )
        listManager.setup()

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