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

public class WeekPagerAdapter extends RecyclerView.Adapter<WeekPagerAdapter.WeekViewHolder> {

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
        String savedMinor = WorkoutStorage.getWorkout(context, day, "workoutsMinor", minors[dayIndex]);

        holder.workoutsMajor.setText(savedMajor);
        holder.workoutsMinor.setText(savedMinor);

        // Attach long-press editors
        setupEditor(context, holder.workoutType, day, "workoutType");
        setupEditor(context, holder.workoutsMajor, day, "workoutsMajor");
        setupEditor(context, holder.workoutsMinor, day, "workoutsMinor");
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
        TextView weekDay, workoutType, workoutsMajor, workoutsMinor;

        WeekViewHolder(@NonNull View itemView) {
            super(itemView);
            weekDay = itemView.findViewById(R.id.weekDay);
            workoutType = itemView.findViewById(R.id.workoutType);
            workoutsMajor = itemView.findViewById(R.id.workoutsMajor);
            workoutsMinor = itemView.findViewById(R.id.workoutsMinor);
        }
    }

    // Helper method for editing text fields
    private void setupEditor(Context context, final TextView textView, final String day, final String fieldKey) {
        textView.setOnLongClickListener(v -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            String label = fieldKey.equals("workoutType") ? "Workout Type" : (fieldKey.equals("workoutsMajor") ? "Major" : "Minor");
            builder.setTitle("Edit " + day + " " + label);

            final EditText input = new EditText(context);
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            input.setSingleLine(false);
            input.setLines(6);
            input.setVerticalScrollBarEnabled(true);
            input.setPadding(40, 24, 40, 24);

            // Prefill editor with plain lines (no bullets)
            String currentSaved = textView.getText() == null ? "" : textView.getText().toString();
            String editable = normalizeForEdit(currentSaved);
            input.setText(editable);

            builder.setView(input);

            builder.setPositiveButton("Save", (dialog, which) -> {
                String userText = input.getText().toString();
                // Normalize and add bullets only once
                String finalText;
                if (fieldKey.equals("workoutType")) {
                    // For the short italic line (workoutType) we might want to keep single-line
                    finalText = userText.trim();
                } else {
                    finalText = normalizeForSave(userText);
                }
                // Update UI immediately
                textView.setText(finalText);
                // Persist
                WorkoutStorage.saveWorkout(context, day, fieldKey, finalText);
            });

            builder.setNegativeButton("Cancel", null);
            builder.show();
            return true;
        });
    }

}
