package com.gratus.workoutrepo.data;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.gratus.workoutrepo.model.DayWorkout;
import com.gratus.workoutrepo.model.Routine;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

public class RoutineRepository {
    private static final String ACTIVE_ROUTINE_FILENAME = "active_routine.json";
    private static Routine cachedActiveRoutine;

    // Helper for Pretty JSON
    private static Gson getGson() {
        return new GsonBuilder().setPrettyPrinting().create();
    }

    // --- Active Routine Logic (Main Page) ---

    public static Routine getActiveRoutine(Context context) {
        if (cachedActiveRoutine != null) return cachedActiveRoutine;

        File file = new File(context.getFilesDir(), ACTIVE_ROUTINE_FILENAME);
        if (!file.exists()) {
            // MIGRATION STEP: Try to find old data in "WorkoutPrefs"
            Routine legacy = migrateLegacyData(context);

            if (legacy != null) {
                // Found old data! Save it as our new Active Routine
                cachedActiveRoutine = legacy;
                saveActiveRoutine(context, legacy);
                // Also save to library so it appears in the Browse list
                saveRoutineToLibrary(context, legacy);
            } else {
                // No old data, start fresh
                cachedActiveRoutine = new Routine();
                cachedActiveRoutine.title = "Routine A"; // Give it a nice name
                saveActiveRoutine(context, cachedActiveRoutine);

                // FIX: Save to library immediately so it persists as a real routine
                // This ensures WorkoutStorage.saveWorkout will sync edits to it.
                saveRoutineToLibrary(context, cachedActiveRoutine);
            }
        } else {
            try (FileReader reader = new FileReader(file)) {
                cachedActiveRoutine = new Gson().fromJson(reader, Routine.class);
            } catch (Exception e) {
                e.printStackTrace();
                cachedActiveRoutine = new Routine();
            }
        }
        return cachedActiveRoutine;
    }

    public static void saveActiveRoutine(Context context, Routine routine) {
        cachedActiveRoutine = routine;
        File file = new File(context.getFilesDir(), ACTIVE_ROUTINE_FILENAME);
        try (FileWriter writer = new FileWriter(file)) {
            getGson().toJson(routine, writer); // Use pretty gson
        } catch (Exception e) { e.printStackTrace(); }
    }

    // --- Helper for Safety Check (Used by WorkoutStorage) ---
    public static boolean isRoutineSaved(Context context, String routineId) {
        File dir = new File(context.getFilesDir(), "saved_routines");
        File file = new File(dir, "routine_" + routineId + ".json");
        return file.exists();
    }

    // --- MIGRATION LOGIC ---
    private static Routine migrateLegacyData(Context context) {
        // Open the SPECIFIC old file "WorkoutPrefs"
        SharedPreferences prefs = context.getSharedPreferences("WorkoutPrefs", Context.MODE_PRIVATE);

        // Check if it has data (e.g., if MONDAY_workoutType exists)
        if (!prefs.contains("MONDAY_workoutType") && !prefs.contains("MONDAY_workoutsMajor")) {
            return null; // No legacy data found
        }

        Routine legacy = new Routine();
        legacy.title = "Migrated Routine"; // Distinct name so user knows
        legacy.notes = "Restored from previous version.";

        for (DayWorkout day : legacy.days) {
            String d = day.dayName; // e.g. "MONDAY"

            // Map keys exactly as they were in your old WorkoutStorage
            day.workoutType = prefs.getString(d + "_workoutType", "");
            day.major = prefs.getString(d + "_workoutsMajor", "");
            day.minor = prefs.getString(d + "_workoutsMinor", "");
            day.notes = prefs.getString(d + "_notes", "");
        }

        // Optional: clear old prefs so it doesn't run again?
        // Better to leave them as backup for now.
        return legacy;
    }

    // --- Saved Routines Logic (Library) ---

    public static List<Routine> getAllSavedRoutines(Context context) {
        List<Routine> routines = new ArrayList<>();
        File dir = new File(context.getFilesDir(), "saved_routines");
        if (!dir.exists()) dir.mkdirs();

        File[] files = dir.listFiles();
        if (files != null) {
            Gson gson = getGson(); // Use pretty gson
            for (File f : files) {
                if (f.getName().endsWith(".json")) {
                    try (FileReader reader = new FileReader(f)) {
                        routines.add(gson.fromJson(reader, Routine.class));
                    } catch (Exception e) { e.printStackTrace(); }
                }
            }
        }

        // NEW: Sort by timestamp (Oldest creation first)
        // If 'timestamp' is 0 (migrated data), it puts it at the start, which is correct.
        routines.sort((r1, r2) -> Long.compare(r1.timestamp, r2.timestamp));

        return routines;
    }

    public static void saveRoutineToLibrary(Context context, Routine routine) {
        File dir = new File(context.getFilesDir(), "saved_routines");
        if (!dir.exists()) dir.mkdirs();
        File file = new File(dir, "routine_" + routine.id + ".json");
        try (FileWriter writer = new FileWriter(file)) {
            getGson().toJson(routine, writer); // Use pretty gson
        } catch (Exception e) { e.printStackTrace(); }
    }

    public static void deleteRoutine(Context context, String routineId) {
        File dir = new File(context.getFilesDir(), "saved_routines");
        File file = new File(dir, "routine_" + routineId + ".json");
        if(file.exists()) file.delete();
    }
}