package com.gratus.workoutrepo.adapters; //class moved

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import com.gratus.workoutrepo.EditorBottomSheet;
import com.gratus.workoutrepo.R;
import com.gratus.workoutrepo.storage.WorkoutStorage;
import com.gratus.workoutrepo.utils.TextFormatUtils;

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
        String savedType = WorkoutStorage.getWorkout(context, day, "workoutType", "");
        holder.workoutType.setText(savedType);

        // LOAD RAW, DISPLAY FORMATTED
        // Default fallbacks are now raw text from arrays above
        String savedMajor = WorkoutStorage.getWorkout(context, day, "workoutsMajor", majors[dayIndex]);
        holder.workoutsMajor.setText(TextFormatUtils.formatBulletsForDisplay(savedMajor));

        String savedMinor = WorkoutStorage.getWorkout(context, day, "workoutsMinor", minors[dayIndex]);
        holder.workoutsMinor.setText(TextFormatUtils.formatBulletsForDisplay(savedMinor));

        String savedNotesRaw = WorkoutStorage.getWorkout(context, day, "notes", "");
        holder.notesDetails.setText(TextFormatUtils.formatNotesForDisplay(savedNotesRaw));


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


    @Override
    public int getItemCount() {
        return VIRTUAL_COUNT;
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
        TextView targetView = switch (fieldKey) {
            case "workoutType" -> holder.workoutType;
            case "workoutsMajor" -> holder.workoutsMajor;
            case "notes" -> holder.notesDetails;
            default -> holder.workoutsMinor;
        };

        // long press opens EditorBottomSheet
        targetView.setOnLongClickListener(v -> {
            // GET RAW DATA FOR EDITING
            // We read directly from storage to get the clean, unformatted text
            String editable = WorkoutStorage.getWorkout(holder.itemView.getContext(), day, fieldKey, "");

            // Fallback for defaults if empty (so user sees default text to edit)
            if (editable.isEmpty() && !fieldKey.equals("workoutType") && !fieldKey.equals("notes")) {
                int dayIndex = adapterPos % 7;
                if(dayIndex < 0) dayIndex += 7;
                if(fieldKey.equals("workoutsMajor")) editable = majors[dayIndex];
                else if(fieldKey.equals("workoutsMinor")) editable = minors[dayIndex];
            }

            // Clean labels for the Bottom Sheet title
            String readableField = fieldKey;
            if(fieldKey.equals("workoutsMajor")) readableField = "Major Workouts";
            if(fieldKey.equals("workoutsMinor")) readableField = "Minor Workouts";
            if(fieldKey.equals("workoutType")) readableField = "Type";
            if(fieldKey.equals("notes")) readableField = "Notes";

            // Pass 'day' as the first arg, and 'readableField' as second arg to make title "Edit MONDAY Major"
            EditorBottomSheet sheet = EditorBottomSheet.newInstance(day, readableField, editable, adapterPos);

            sheet.setOnSaveListener((editedText, pos) -> {
                String finalText = editedText == null ? "" : editedText.trim();

                // SAVE CLEAN DATA
                if (fieldKey.equals("workoutsMajor") || fieldKey.equals("workoutsMinor")) {
                    finalText = TextFormatUtils.cleanTextForStorage(finalText);
                }
                // Notes and Type are saved raw (Notes preserves "- ")

                WorkoutStorage.saveWorkout(holder.itemView.getContext(), day, fieldKey, finalText);

                // UPDATE UI WITH FORMATTED DATA
                switch (fieldKey) {
                    case "workoutType" -> holder.workoutType.setText(finalText);
                    case "workoutsMajor" ->
                            holder.workoutsMajor.setText(TextFormatUtils.formatBulletsForDisplay(finalText));
                    case "workoutsMinor" ->
                            holder.workoutsMinor.setText(TextFormatUtils.formatBulletsForDisplay(finalText));
                    case "notes" ->
                            holder.notesDetails.setText(TextFormatUtils.formatNotesForDisplay(finalText));
                }
            });

            FragmentManager fm = activity.getSupportFragmentManager();
            sheet.show(fm, "editor");
            return true;
        });
    }

}
