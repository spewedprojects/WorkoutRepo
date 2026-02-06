package com.gratus.workoutrepo.storage; //class moved

import android.content.Context;
import com.gratus.workoutrepo.data.RoutineRepository;
import com.gratus.workoutrepo.model.DayWorkout;
import com.gratus.workoutrepo.model.Routine;

public class WorkoutStorage {

    private static DayWorkout getDay(Routine routine, String dayKey) {
        for (DayWorkout d : routine.days) {
            if (d.dayName.equalsIgnoreCase(dayKey)) return d;
        }
        return null;
    }

    public static void saveWorkout(Context context, String dayKey, String fieldKey, String value) {
        // 1. Get the BUFFER (Active Routine)
        Routine active = RoutineRepository.getActiveRoutine(context);
        DayWorkout day = getDay(active, dayKey);

        if (day != null) {
            // 2. Update memory
            switch (fieldKey) {
                case "workoutType": day.workoutType = value; break;
                case "workoutsMajor": day.majorWorkouts = value; break;
                case "workoutsMinor": day.minorWorkouts = value; break;
                case "notes": day.notes = value; break;
            }

            // 3. Save to BUFFER (Main Page always stays safe and updated)
            RoutineRepository.saveActiveRoutine(context, active);

            // 4. SYNC CHECK: Only update the Library file if it actually exists!
            // This prevents "resurrecting" a deleted routine.
            // If the user deleted "Routine A", this will return false, and we stop here.
            // The user continues editing on the Main Page (buffer only) until they Apply a new routine.
            if (RoutineRepository.isRoutineSaved(context, active.id)) {
                RoutineRepository.saveRoutineToLibrary(context, active);
            }
        }
    }

    public static String getWorkout(Context context, String dayKey, String fieldKey, String defaultValue) {
        Routine active = RoutineRepository.getActiveRoutine(context);
        DayWorkout day = getDay(active, dayKey);

        if (day != null) {
            switch (fieldKey) {
                case "workoutType": return (day.workoutType == null || day.workoutType.isEmpty()) ? defaultValue : day.workoutType;
                case "workoutsMajor": return (day.majorWorkouts == null || day.majorWorkouts.isEmpty()) ? defaultValue : day.majorWorkouts;
                case "workoutsMinor": return (day.minorWorkouts == null || day.minorWorkouts.isEmpty()) ? defaultValue : day.minorWorkouts;
                case "notes": return (day.notes == null || day.notes.isEmpty()) ? defaultValue : day.notes;
            }
        }
        return defaultValue;
    }
}