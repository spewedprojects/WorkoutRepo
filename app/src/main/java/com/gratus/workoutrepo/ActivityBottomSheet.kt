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
import com.gratus.workoutrepo.strava.utils.StravaListManager
import com.gratus.workoutrepo.strava.repository.StravaRepository

class ActivityBottomSheet(
    private val dayOfWeek: String = "Monday"
) : BottomSheetDialogFragment() {

    private fun isUseDialog(): Boolean {
        val prefs = context?.getSharedPreferences(BaseActivity.PREFS_NAME, android.content.Context.MODE_PRIVATE)
        return prefs?.getBoolean(BaseActivity.PREF_USE_DIALOG, false) ?: false
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): android.app.Dialog {
        return if (isUseDialog()) {
            androidx.appcompat.app.AppCompatDialog(requireContext(), theme)
        } else {
            super.onCreateDialog(savedInstanceState)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val layoutRes = if (isUseDialog()) R.layout.dialog_activities else R.layout.bottomsheet_actvities
        return inflater.inflate(layoutRes, container, false)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // --- THIS IS ALL YOU NEED NOW ---
        val listManager = StravaListManager(
            context = requireContext(),
            lifecycleScope = lifecycleScope,
            rootView = view,
            titlePrefix = "Activities on ${dayOfWeek}s",
            fetchMasterList = { forceRefresh ->
                // BottomSheet fetches only specific day
                StravaRepository.getActivitiesForDay(requireContext(), dayOfWeek, forceRefresh)
            }
        )
        listManager.setup()

        if (isUseDialog()) {
            dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
        } else {
            // Fix for custom background and shadow clipping
            dialog?.setOnShowListener { dialogInterface ->
                val bottomSheetDialog = dialogInterface as? com.google.android.material.bottomsheet.BottomSheetDialog
                val bottomSheetInternal = bottomSheetDialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)

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

            view.findViewById<View?>(R.id.bottom_sheet_root)?.let { root ->
                ViewCompat.setOnApplyWindowInsetsListener(root) { v: View, insets: WindowInsetsCompat ->
                    val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                    v.setPadding(
                        systemBars.left,
                        0,
                        systemBars.right,
                        0
                    )
                    insets
                }
            }
        }
    }
}