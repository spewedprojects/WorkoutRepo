package com.gratus.workoutrepo;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

public class BaseActivity extends AppCompatActivity {

    public static final String PREFS_NAME = "WorkoutRepoAppSettings";
    protected static final String THEME_KEY = "SelectedTheme";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applyTheme();
        super.onCreate(savedInstanceState);

        // Edge-to-edge setup — safe to do before setContentView
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getWindow().setNavigationBarContrastEnforced(false);
        }
    }

    /**
     * Call this from child activities after setContentView(),
     * passing the view that should receive system bar padding.
     */
    protected void applySystemBarInsets(int viewId) {
        View target = findViewById(viewId);
        if (target == null) return;
        ViewCompat.setOnApplyWindowInsetsListener(target, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
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
