package com.gratus.workoutrepo.routine.model; //class moved

import androidx.annotation.Keep;

@Keep // Add this annotation
public class DayWorkout {
    public String dayName; // "MONDAY", etc.
    public String workoutType;
    public String majorWorkouts;
    public String minorWorkouts;
    public String majorLabel;
    public String minorLabel;
    public String notes;

    public DayWorkout(String dayName) {
        this.dayName = dayName;
        this.workoutType = "";
        this.majorWorkouts = "";
        this.minorWorkouts = "";
        this.majorLabel = "";
        this.minorLabel = "";
        this.notes = "";
    }
}