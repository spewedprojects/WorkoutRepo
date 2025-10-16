package com.gratus.workoutrepo;

import android.content.Context;
import android.content.SharedPreferences;

public class WorkoutStorage {
    private static final String PREF_NAME = "WorkoutPrefs";

    public static void saveWorkout(Context context, String dayKey, String fieldKey, String value) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(dayKey + "_" + fieldKey, value).apply();
    }

    public static String getWorkout(Context context, String dayKey, String fieldKey, String defaultValue) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getString(dayKey + "_" + fieldKey, defaultValue);
    }
}

