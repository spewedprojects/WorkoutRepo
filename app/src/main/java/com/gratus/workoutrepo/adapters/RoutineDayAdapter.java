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

import java.util.List;

public class RoutineDayAdapter extends RecyclerView.Adapter<RoutineDayAdapter.DayViewHolder> {

    private final List<DayWorkout> days;

    public RoutineDayAdapter(List<DayWorkout> days) {
        this.days = days;
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

        // Populate the 3 columns
        // Use Utils for formatting
        holder.textMajor.setText(TextFormatUtils.formatBulletsForDisplay(day.majorWorkouts));
        holder.textMinor.setText(TextFormatUtils.formatBulletsForDisplay(day.minorWorkouts));
        holder.textNotes.setText(TextFormatUtils.formatNotesForDisplay(day.notes));
    }

    @Override
    public int getItemCount() {
        return days.size();
    }

    static class DayViewHolder extends RecyclerView.ViewHolder {
        TextView dayTitle, workoutType, textMajor, textMinor, textNotes;

        DayViewHolder(View v) {
            super(v);
            dayTitle = v.findViewById(R.id.day_title);
            workoutType = v.findViewById(R.id.day_workouttype);
            textMajor = v.findViewById(R.id.col1);
            textMinor = v.findViewById(R.id.col2);
            textNotes = v.findViewById(R.id.col3);
        }
    }
}