package com.gratus.workoutrepo.model; //class moved

import androidx.annotation.Keep;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Keep // Add this annotation to prevent R8 renaming
public class Routine {
    public long timestamp; // New field for sorting
    public String id;
    public String title;
    public String notes;
    public List<DayWorkout> days;


    public Routine() {
        this.id = UUID.randomUUID().toString();
        this.title = "New Routine";
        this.timestamp = System.currentTimeMillis(); // Auto-set creation time
        this.notes = "";
        this.days = new ArrayList<>();
        String[] weekDays = {"MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"};
        for (String day : weekDays) {
            days.add(new DayWorkout(day));
        }
    }
}