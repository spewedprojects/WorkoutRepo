package com.gratus.workoutrepo;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

public class BaseActivity extends AppCompatActivity {

    protected static final String PREFS_NAME = "AppThemeSettings";
    protected static final String THEME_KEY = "SelectedTheme";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applyTheme();
        super.onCreate(savedInstanceState);
    }

    /**
     * Apply the saved theme or default to 'auto'.
     */
    protected void applyTheme() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String theme = prefs.getString(THEME_KEY, "auto"); // Default to 'auto'

        switch (theme) {
            case "light":
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case "dark":
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            case "auto":
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
    }


    /**
     * Set the theme and save the selection to SharedPreferences.
     */
    protected void setThemeAndSave(String theme) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putString(THEME_KEY, theme).apply();

        // Log the saved theme
        System.out.println("Saved theme: " + theme);

        // Apply the new theme
        applyTheme();

        // Restart the activity to reflect the theme change
        recreate();
    }
}
