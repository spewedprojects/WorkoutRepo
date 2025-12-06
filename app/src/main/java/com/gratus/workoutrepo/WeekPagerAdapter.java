package com.gratus.workoutrepo;

import android.content.Context;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

public class WeekPagerAdapter extends RecyclerView.Adapter<WeekPagerAdapter.WeekViewHolder> {

    private final FragmentActivity activity;

    // real data for 7 days
    private final String[] weekDays = {
            "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"
    };

    private final String[] workoutTypes = {
            "(Pre-Squat day prep)",
            "(Heavy squat day)",         // replace with your text
            "(Volume/Hypertrophy)",
            "(Upper-body/power)",
            "(De-load/active recovery)",
            "(Cycling/long ride)",
            "(Mobility)"
    };

    private final String[] majors = {
            "• Quarter squats\n• Isometrics\n• Weighted Jumps\n• Weighted single leg squats",
            "• Back Squat variations\n• Pause squats\n• Front squats",
            "• Lunges\n• Romanian deadlifts\n• Single leg work",
            "• Pull-ups\n• Dips\n• Overhead press",
            "• Light full-body movements",
            "• Long ride\n• Threshold intervals",
            "• Mobility drills\n• Foam rolling"
    };

    private final String[] minors = {
            "• Banded warm-ups\n• Jumps\n• Pull-ups\n• Dips",
            "• Good mornings\n• Calf raises",
            "• Step-ups\n• Core work",
            "• Rows\n• Face pulls",
            "• Stretching\n• Walk",
            "• Recovery nutrition",
            "• Stretching\n• Light mobility"
    };

    // Large count to simulate infinite loop; use Integer.MAX_VALUE (or large number)
    private static final int VIRTUAL_COUNT = Integer.MAX_VALUE;

    public WeekPagerAdapter(@NonNull FragmentActivity activity) {
        this.activity = activity;
    }

    @NonNull
    @Override
    public WeekViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.card_week, parent, false);
        return new WeekViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull WeekViewHolder holder, int position) {
        int dayIndex = position % 7;
        if (dayIndex < 0) dayIndex += 7; // safety

        String day = weekDays[dayIndex];
        Context context = holder.itemView.getContext();

        // Set the weekday title and workout type
        holder.weekDay.setText(weekDays[dayIndex]);
        holder.workoutType.setText(
                WorkoutStorage.getWorkout(context, day, "workoutType", workoutTypes[dayIndex]));

        // Load saved major/minor (or defaults)
        String savedMajor = WorkoutStorage.getWorkout(context, day, "workoutsMajor", majors[dayIndex]);
        holder.workoutsMajor.setText(savedMajor);
        String savedMinor = WorkoutStorage.getWorkout(context, day, "workoutsMinor", minors[dayIndex]);
        holder.workoutsMinor.setText(savedMinor);

        // Load saved notes (raw stored text) and display formatted version
        String savedNotesRaw = WorkoutStorage.getWorkout(context, day, "notes", "");
        holder.notesDetails.setText(formatNotesForDisplay(savedNotesRaw));


        // Remove previous listeners to avoid duplicate callbacks (important when recycling)
        holder.workoutType.setOnLongClickListener(null);
        holder.workoutsMajor.setOnLongClickListener(null);
        holder.workoutsMinor.setOnLongClickListener(null);
        holder.notesDetails.setOnLongClickListener(null); // for notes block

        // Attach long-press editors
        setupEditor(holder, "workoutType", day, position);
        setupEditor(holder, "workoutsMajor", day, position);
        setupEditor(holder, "workoutsMinor", day, position);
        setupEditor(holder, "notes", day, position); // for notes block
    }

    /**
     * Convert raw stored notes (which may contain lines that start with "- ")
     * into a display-friendly string where lines beginning with "- " become
     * bullet lines showing "• <text>". Other lines are left as plain text.
     */
    private String formatNotesForDisplay(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "";

        String[] lines = raw.split("\\r?\\n", -1); // preserve empty lines
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.startsWith("- ")) {
                String after = line.substring(2).trim();
                sb.append("\u2022");            // bullet character
                if (!after.isEmpty()) sb.append(" ").append(after);
            } else {
                sb.append(line);
            }
            if (i < lines.length - 1) sb.append("\n");
        }
        return sb.toString();
    }

    @Override
    public int getItemCount() {
        return VIRTUAL_COUNT;
    }

    private String normalizeForSave(String raw) {
        // Convert CRLF to LF, trim edges, remove any existing leading bullets or hyphens
        if (raw == null) return "";
        String[] lines = raw.replace("\r\n", "\n").replace("\r", "\n").split("\n");
        StringBuilder out = new StringBuilder();
        for (String line : lines) {
            String t = line.trim();
            if (t.isEmpty()) continue;
            // Remove leading bullet-like characters (•, -, *, •• etc) and whitespace
            t = t.replaceAll("^[\\u2022\\*\\-\\s]+", "");
            out.append("• ").append(t).append("\n");
        }
        return out.toString().trim(); // final text with bullets, no trailing newline
    }

    private String normalizeForEdit(String savedWithBullets) {
        // Turn bullet lines into plain lines for the edit box (no bullets)
        if (savedWithBullets == null) return "";
        String[] lines = savedWithBullets.replace("\r\n", "\n").replace("\r", "\n").split("\n");
        StringBuilder out = new StringBuilder();
        for (String line : lines) {
            String t = line.trim();
            if (t.isEmpty()) continue;
            // remove leading bullets/hyphens
            t = t.replaceAll("^[\\u2022\\*\\-\\s]+", "");
            out.append(t).append("\n");
        }
        return out.toString().trim();
    }

    static class WeekViewHolder extends RecyclerView.ViewHolder {
        TextView weekDay, workoutType, workoutsMajor, workoutsMinor, notesDetails;

        WeekViewHolder(@NonNull View itemView) {
            super(itemView);
            weekDay = itemView.findViewById(R.id.weekDay);
            workoutType = itemView.findViewById(R.id.workoutType);
            workoutsMajor = itemView.findViewById(R.id.workoutsMajor);
            workoutsMinor = itemView.findViewById(R.id.workoutsMinor);
            notesDetails = itemView.findViewById(R.id.notesDetails);
        }
    }

    // Helper method for editing text fields
    // -------------------------
    // Editor wiring
    // -------------------------
    private void setupEditor(@NonNull WeekViewHolder holder, @NonNull String fieldKey, @NonNull String day, int adapterPos) {
        TextView targetView;
        if (fieldKey.equals("workoutType")) targetView = holder.workoutType;
        else if (fieldKey.equals("workoutsMajor")) targetView = holder.workoutsMajor;
        else if (fieldKey.equals("notes")) targetView = holder.notesDetails;
        else targetView = holder.workoutsMinor;

        // long press opens EditorBottomSheet
        targetView.setOnLongClickListener(v -> {
            // prepare editable text for the bottom sheet
            String editable;
            if (fieldKey.equals("notes")) {
                // For notes, prefill the editor with the raw stored value so "- " remains visible while editing
                editable = WorkoutStorage.getWorkout(holder.itemView.getContext(), day, "notes", "");
            } else {
                // For other fields, compute editable text by stripping bullets
                String current = targetView.getText() == null ? "" : targetView.getText().toString();
                editable = normalizeForEdit(current);
            }

            EditorBottomSheet sheet = EditorBottomSheet.newInstance(day, fieldKey, editable, adapterPos);

            // set callback to receive edited text
            sheet.setOnSaveListener((editedText, pos) -> {
                // Normalize and save centrally here
                String finalText;
                if (fieldKey.equals("workoutType")) {
                    // plain text for workoutType
                    finalText = editedText == null ? "" : editedText.trim();
                } else if (fieldKey.equals("notes")) {
                    // For notes: always store the raw trimmed text exactly as the user typed it.
                    // This preserves any leading "- " the user intentionally typed.
                    finalText = editedText == null ? "" : editedText.trim();
                } else {
                    // for workoutsMajor/workoutsMinor we keep bullet formatting
                    finalText = normalizeForSave(editedText);
                }

                // persist
                WorkoutStorage.saveWorkout(holder.itemView.getContext(), day, fieldKey, finalText);

                // update the visible holder views (we have access to holder here)
                // IMPORTANT: because RecyclerView recycles views, check that holder is still bound to same adapter position if needed.
                // For simplicity, we update the text on this holder instance.
                if (fieldKey.equals("workoutType")) {
                    holder.workoutType.setText(finalText);
                } else if (fieldKey.equals("workoutsMajor")) {
                    holder.workoutsMajor.setText(finalText);
                } else if (fieldKey.equals("notes")) {
                    holder.notesDetails.setText(formatNotesForDisplay(finalText));
                } else {
                    holder.workoutsMinor.setText(finalText);
                }
            });

            // show the bottom sheet using activity's fragment manager
            FragmentManager fm = activity.getSupportFragmentManager();
            sheet.show(fm, "editor-" + day + "-" + fieldKey);
            return true;
        });
    }

}
