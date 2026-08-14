package com.gratus.workoutrepo.utils;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.transition.AutoTransition;
import androidx.transition.TransitionManager;

public class ExpandableNoteHelper {

    private static final int MAX_COLLAPSED_LINES = 5;
    private static final int LINE_COUNT_THRESHOLD = 6;

    /**
     * Backward-compatible overload for default collapsed state.
     */
    public static void setupNoteState(TextView notesView, View expandBtn, String rawNotes) {
        setupNoteState(notesView, expandBtn, rawNotes, false);
    }

    /**
     * Centralized logic to setup the initial state of the expandable notes with a preferred expansion state.
     */
    public static void setupNoteState(TextView notesView, View expandBtn, String rawNotes, boolean isExpanded) {
        String safeNotes = (rawNotes == null) ? "" : rawNotes;
        notesView.setTag(safeNotes); // Tag it early

        String[] rawLines = safeNotes.split("\\r?\\n");
        boolean needsCollapseInitial = rawLines.length > LINE_COUNT_THRESHOLD;

        // 1. Initial display and immediate synchronous collapse check to prevent UI flicker
        if (needsCollapseInitial) {
            expandBtn.setVisibility(View.VISIBLE);
            if (isExpanded) {
                expandBtn.setRotation(0f); // Expanded
                notesView.setText(TextFormatUtils.formatNotesForDisplay(safeNotes));
            } else {
                expandBtn.setRotation(180f); // Collapsed
                notesView.setText(TextFormatUtils.getCollapsedNotes(safeNotes, MAX_COLLAPSED_LINES));
            }
        } else {
            expandBtn.setVisibility(View.GONE);
            expandBtn.setRotation(0f);
            notesView.setText(TextFormatUtils.formatNotesForDisplay(safeNotes));
        }

        // 2. Post-measurement check for wrapping/DPI impact on narrow devices
        notesView.post(() -> {
            // Guard against recycled views in RecyclerView
            if (!safeNotes.equals(notesView.getTag())) return;

            int actualLines = notesView.getLineCount();
            if (actualLines > LINE_COUNT_THRESHOLD) {
                expandBtn.setVisibility(View.VISIBLE);
                if (isExpanded) {
                    expandBtn.setRotation(0f);
                    notesView.setText(TextFormatUtils.formatNotesForDisplay(safeNotes));
                } else {
                    expandBtn.setRotation(180f);
                    notesView.setText(TextFormatUtils.getCollapsedNotes(safeNotes, MAX_COLLAPSED_LINES));
                }
            }
        });
    }

    /**
     * Centralized toggle logic with animation.
     * @return true if note is now expanded, false if collapsed
     */
    public static boolean toggleNote(TextView notesView, View expandBtn) {
        String rawNotes = (String) notesView.getTag();
        if (rawNotes == null) return false;

        ViewGroup parent = (ViewGroup) notesView.getParent();
        if (parent != null) {
            AutoTransition transition = new AutoTransition();
            transition.setDuration(200);
            TransitionManager.beginDelayedTransition(parent, transition);
        }

        boolean isCollapsed = expandBtn.getRotation() >= 90f;
        if (isCollapsed) {
            notesView.setText(TextFormatUtils.formatNotesForDisplay(rawNotes));
            expandBtn.animate().rotation(0f).setDuration(200).start();
            return true;
        } else {
            notesView.setText(TextFormatUtils.getCollapsedNotes(rawNotes, MAX_COLLAPSED_LINES));
            expandBtn.animate().rotation(180f).setDuration(200).start();
            return false;
        }
    }
}