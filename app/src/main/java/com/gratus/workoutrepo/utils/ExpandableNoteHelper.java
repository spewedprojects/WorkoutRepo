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
        notesView.setTag(safeNotes); // Tag it early

        // 1. Initial display using formatted text to allow measurement
        notesView.setText(TextFormatUtils.formatNotesForDisplay(safeNotes));

        // 2. Wait for the view to render to check for wrapping/DPI impact
        notesView.post(() -> {
            int actualLines = notesView.getLineCount();
            String[] rawLines = safeNotes.split("\\r?\\n");

            // Collapse if: raw count > 7 OR rendered count > 7
            if (rawLines.length > 7 || actualLines > 6) {
                expandBtn.setVisibility(View.VISIBLE);
                expandBtn.setRotation(180f); // Default to collapsed
                notesView.setText(TextFormatUtils.getCollapsedNotes(safeNotes, 4));
            } else {
                expandBtn.setVisibility(View.GONE);
                expandBtn.setRotation(0f);
                notesView.setText(TextFormatUtils.formatNotesForDisplay(safeNotes));
            }
        });
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