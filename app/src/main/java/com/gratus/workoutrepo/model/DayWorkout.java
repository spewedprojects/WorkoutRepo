package com.gratus.workoutrepo.model; //class moved

import androidx.annotation.Keep;

@Keep // Add this annotation
public class DayWorkout {
    public String dayName; // "MONDAY", etc.
    public String workoutType;
    public String major;
    public String minor;
    public String notes;

    public DayWorkout(String dayName) {
        this.dayName = dayName;
        this.workoutType = "";
        this.major = "";
        this.minor = "";
        this.notes = "";
    }
}