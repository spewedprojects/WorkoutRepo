package com.gratus.workoutrepo;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.constraintlayout.motion.widget.MotionLayout;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.viewpager2.widget.ViewPager2;

import java.util.Calendar;

import com.gratus.workoutrepo.adapters.WeekPagerAdapter;

public class MainActivity extends BaseActivity {

    private long backPressedTime;
    private Toast backToast;
    private MotionLayout motionLayout;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applyTheme();
        super.onCreate(savedInstanceState);

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        motionLayout = findViewById(R.id.main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        findViewById(R.id.browseWorkouts).setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, RoutinesActivity.class))
        );

        HorizontalScrollView scrollView = findViewById(R.id.btn_scroll);

        scrollView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                // Get the container (LinearLayout inside the scrollview)
                ViewGroup container = (ViewGroup) scrollView.getChildAt(0);

                // Each item has fixed width (58dp in your XML)
                int itemWidth = container.getChildAt(0).getWidth();
                int scrollX = scrollView.getScrollX();

                // Decide which item we are closer to: 0 (GitHub) or 1 (Theme container)
                int page = (scrollX + itemWidth / 2) / itemWidth;
                int targetX = page * itemWidth;

                scrollView.post(() -> scrollView.smoothScrollTo(targetX, 0));
                return false;
            }
            return false;
        });

        setupThemeButtons();
        setupButtons();
        setupWeekPager();
        setupOnBackPressed();
    }

    private void setupButtons() {
        ImageButton githubicon = findViewById(R.id.githubIcon);
        ImageButton stravaaccess = findViewById(R.id.stravaAccess);

        githubicon.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.github.com/spewedprojects/WorkoutRepo"));
            v.getContext().startActivity(intent);
        });
        stravaaccess.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.strava.com/athletes/32298220"));
            v.getContext().startActivity(intent);
        });

        ImageButton guideBtn = findViewById(R.id.guide_btn);
        guideBtn.setOnClickListener(v -> {
            // Check current state by ID or Progress
            if (motionLayout.getCurrentState() == R.id.end_visible) {
                motionLayout.transitionToStart(); // Animates to guide_hidden
            } else {
                motionLayout.transitionToEnd();   // Animates to guide_visible
            }
        });
    }

    private void setupWeekPager() {
        // inside onCreate(...)
        ViewPager2 weekPager = findViewById(R.id.weekPager);
        WeekPagerAdapter adapter = new WeekPagerAdapter(this);
        weekPager.setAdapter(adapter);

        // Calculate current day index (0 = Monday, 6 = Sunday)
        Calendar calendar = Calendar.getInstance();
        int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
        int dayIndex = switch (dayOfWeek) {
            case Calendar.MONDAY -> 0;
            case Calendar.TUESDAY -> 1;
            case Calendar.WEDNESDAY -> 2;
            case Calendar.THURSDAY -> 3;
            case Calendar.FRIDAY -> 4;
            case Calendar.SATURDAY -> 5;
            case Calendar.SUNDAY -> 6;
            default -> 0;
        };

        // Start near the middle so user can swipe in both directions; align to Monday
        int middle = Integer.MAX_VALUE / 2;
        int startPosition = middle - (middle % 7) + dayIndex; // makes it land on index 0 (MONDAY)
        weekPager.setCurrentItem(startPosition, false);

        // optional: page change callback (if you want to react to page changes)
        weekPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                // if you want to show a toast or update other UI, compute day:
                int dayIndex = position % 7;
                // do something if needed...
            }
        });
    }

    /**
     * Set up the theme toggle buttons.
     */
    private void setupThemeButtons() {
        ImageButton lightButton = findViewById(R.id.btn_light);
        ImageButton darkButton = findViewById(R.id.btn_dark);
        ImageButton autoButton = findViewById(R.id.btn_auto);

        if (lightButton == null || darkButton == null || autoButton == null) {
            return;
        }

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String currentTheme = prefs.getString(THEME_KEY, "auto");

        updateButtonVisibility(currentTheme, lightButton, darkButton, autoButton);

        lightButton.setOnClickListener(v -> setThemeAndSave("light"));
        darkButton.setOnClickListener(v -> setThemeAndSave("dark"));
        autoButton.setOnClickListener(v -> setThemeAndSave("auto"));
    }

    /**
     * Update the visibility of the theme buttons based on the current theme.
     */
    private void updateButtonVisibility(String currentTheme, ImageButton lightButton, ImageButton darkButton, ImageButton autoButton) {
        switch (currentTheme) {
            case "light":
                lightButton.setVisibility(View.GONE);
                darkButton.setVisibility(View.VISIBLE);
                autoButton.setVisibility(View.GONE);
                System.out.println("LIGHT mode - GONE (Light button and Auto button), VISIBLE (Dark button)");
                break;
            case "dark":
                lightButton.setVisibility(View.GONE);
                darkButton.setVisibility(View.GONE);
                autoButton.setVisibility(View.VISIBLE);
                System.out.println("DARK mode - GONE (Light button and Dark button), VISIBLE (Auto button)");
                break;
            case "auto":
                lightButton.setVisibility(View.VISIBLE);
                darkButton.setVisibility(View.GONE);
                autoButton.setVisibility(View.GONE);
                System.out.println("AUTO mode - GONE (Dark button and Auto button), VISIBLE (Light button)");
                break;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // This forces the adapter to reload data from the JSON file
        // which might have changed if you clicked "Apply" in the Routines screen
        if (findViewById(R.id.weekPager) != null) {
            androidx.viewpager2.widget.ViewPager2 vp = findViewById(R.id.weekPager);
            if (vp.getAdapter() != null) {
                vp.getAdapter().notifyDataSetChanged();
            }
        }
    }

    private void setupOnBackPressed() {
        // --- IMPROVED IMPLEMENTATION for double back to exit the app ---
        OnBackPressedCallback callback = new OnBackPressedCallback(true /* enabled by default */) {
            @Override
            public void handleOnBackPressed() {
                if (backPressedTime + 2000 > System.currentTimeMillis()) {
                    backToast.cancel();
                    // Call finish() to close the activity
                    finish();
                } else {
                    backToast = Toast.makeText(getBaseContext(), "Press BACK again to exit", Toast.LENGTH_SHORT);
                    backToast.show();
                }
                backPressedTime = System.currentTimeMillis();
            }
        };
        // Add the callback to the dispatcher
        getOnBackPressedDispatcher().addCallback(this, callback);
    }

}