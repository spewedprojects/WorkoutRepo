package com.gratus.workoutrepo;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
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
            "(Upper body / power)",
            "(Deload / active recovery)",
            "(Cycling / long ride)",
            "(Mobility / prep for week)"
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
        holder.weekDay.setText(weekDays[dayIndex]);
        holder.workoutType.setText(workoutTypes[dayIndex]);
        holder.workoutsMajor.setText(majors[dayIndex]);
        holder.workoutsMinor.setText(minors[dayIndex]);
    }

    @Override
    public int getItemCount() {
        return VIRTUAL_COUNT;
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
}
