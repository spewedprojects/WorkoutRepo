package com.gratus.workoutrepo.utils;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.transition.AutoTransition;
import androidx.transition.TransitionManager;
import com.gratus.workoutrepo.utils.TextFormatUtils;

public class ExpandableNoteHelper {

    /**
     * Centralized logic to setup the initial state of the expandable notes.
     */
    public static void setupNoteState(TextView notesView, View expandBtn, String rawNotes) {
        String safeNotes = (rawNotes == null) ? "" : rawNotes;
        String[] lines = safeNotes.split("\\r?\\n");

        if (lines.length > 7) {
            expandBtn.setVisibility(View.VISIBLE);
            expandBtn.setRotation(180f); // Collapsed state
            notesView.setText(TextFormatUtils.getCollapsedNotes(safeNotes, 5));
        } else {
            expandBtn.setVisibility(View.GONE);
            expandBtn.setRotation(0f);
            notesView.setText(TextFormatUtils.formatNotesForDisplay(safeNotes));
        }

        // Tag the view with the latest raw notes to prevent listener desync
        notesView.setTag(safeNotes);
    }

    /**
     * Centralized toggle logic with animation.
     */
    public static void toggleNote(TextView notesView, View expandBtn) {
        String rawNotes = (String) notesView.getTag();
        if (rawNotes == null) return;

        ViewGroup parent = (ViewGroup) notesView.getParent();
        AutoTransition transition = new AutoTransition();
        transition.setDuration(250);
        TransitionManager.beginDelayedTransition(parent, transition);

        boolean isCollapsed = expandBtn.getRotation() == 180f;
        if (isCollapsed) {
            notesView.setText(TextFormatUtils.formatNotesForDisplay(rawNotes));
            expandBtn.animate().rotation(0f).setDuration(200).start();
        } else {
            notesView.setText(TextFormatUtils.getCollapsedNotes(rawNotes, 5));
            expandBtn.animate().rotation(180f).setDuration(200).start();
        }
    }
}