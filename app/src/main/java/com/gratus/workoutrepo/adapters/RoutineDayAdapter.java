package com.gratus.workoutrepo.adapters; //class moved

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.gratus.workoutrepo.R;
import com.gratus.workoutrepo.model.DayWorkout;
import com.gratus.workoutrepo.utils.TextFormatUtils;

import com.gratus.workoutrepo.RoutinesActivity;
import com.gratus.workoutrepo.model.Routine;

import java.util.List;

public class RoutineDayAdapter extends RecyclerView.Adapter<RoutineDayAdapter.DayViewHolder> {

    private final Routine routine;
    private final boolean isEditMode;
    private final RoutinesActivity.RoutineActionListener listener;
    private final List<DayWorkout> days;

    public RoutineDayAdapter(Routine routine, boolean isEditMode, RoutinesActivity.RoutineActionListener listener) {
        this.routine = routine;
        this.isEditMode = isEditMode;
        this.listener = listener;
        this.days = routine.days;
    }

    @NonNull
    @Override
    public DayViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // We reuse the layout idea from SharedPrefsViewer but need a new XML for clean binding
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_day_prefs, parent, false);
        return new DayViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull DayViewHolder holder, int position) {
        DayWorkout day = days.get(position);
        holder.dayTitle.setText(day.dayName);
        holder.workoutType.setText(day.workoutType.isEmpty() ? "(type)" : day.workoutType);

        // Bind dynamic labels
        holder.majorLabel.setText(day.majorLabel == null || day.majorLabel.isEmpty() ? "Major:" : day.majorLabel);
        holder.minorLabel.setText(day.minorLabel == null || day.minorLabel.isEmpty() ? "Minor:" : day.minorLabel);

        // Populate the 3 columns
        // Use Utils for formatting
        holder.textMajor.setText(TextFormatUtils.formatBulletsForDisplay(day.majorWorkouts));
        holder.textMinor.setText(TextFormatUtils.formatBulletsForDisplay(day.minorWorkouts));
        holder.textNotes.setText(TextFormatUtils.formatNotesForDisplay(day.notes));

        if (isEditMode) {
            holder.majorLabel.setOnClickListener(v -> listener.onEditRoutineField(routine, day.dayName, "majorLabel", day.majorLabel == null || day.majorLabel.isEmpty() ? "Major:" : day.majorLabel));
            holder.minorLabel.setOnClickListener(v -> listener.onEditRoutineField(routine, day.dayName, "minorLabel", day.minorLabel == null || day.minorLabel.isEmpty() ? "Minor:" : day.minorLabel));
            holder.textMajor.setOnClickListener(v -> listener.onEditRoutineField(routine, day.dayName, "workoutsMajor", day.majorWorkouts));
            holder.textMinor.setOnClickListener(v -> listener.onEditRoutineField(routine, day.dayName, "workoutsMinor", day.minorWorkouts));
            holder.textNotes.setOnClickListener(v -> listener.onEditRoutineField(routine, day.dayName, "notes", day.notes));
        } else {
            holder.majorLabel.setOnClickListener(null);
            holder.minorLabel.setOnClickListener(null);
            holder.textMajor.setOnClickListener(null);
            holder.textMinor.setOnClickListener(null);
            holder.textNotes.setOnClickListener(null);
        }
    }

    @Override
    public int getItemCount() {
        return days.size();
    }

    static class DayViewHolder extends RecyclerView.ViewHolder {
        TextView dayTitle, workoutType, textMajor, textMinor, textNotes, majorLabel, minorLabel;

        DayViewHolder(View v) {
            super(v);
            dayTitle = v.findViewById(R.id.day_title);
            workoutType = v.findViewById(R.id.day_workouttype);
            textMajor = v.findViewById(R.id.col1);
            textMinor = v.findViewById(R.id.col2);
            textNotes = v.findViewById(R.id.col3);
            majorLabel = v.findViewById(R.id.major_label);
            minorLabel = v.findViewById(R.id.minor_label);
        }
    }
}