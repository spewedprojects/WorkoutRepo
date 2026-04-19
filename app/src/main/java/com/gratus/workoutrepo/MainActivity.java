package com.gratus.workoutrepo;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ScrollView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.constraintlayout.motion.widget.MotionLayout;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.LifecycleOwnerKt;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.gms.security.ProviderInstaller;
import javax.net.ssl.SSLContext;

import java.util.Calendar;

import com.gratus.workoutrepo.adapters.GuideAdapter;
import com.gratus.workoutrepo.adapters.WeekPagerAdapter;
import com.gratus.workoutrepo.utils.StravaArchiveManager;

import kotlinx.coroutines.BuildersKt;
import kotlinx.coroutines.Dispatchers;

public class MainActivity extends BaseActivity {

    private long backPressedTime;
    private Toast backToast;
    private MotionLayout motionLayout;
    // --- NEW: Keep a strong reference to the listener ---
    private SharedPreferences.OnSharedPreferenceChangeListener prefListener;

    private ActivityResultLauncher<String> exportLauncher;
    private ActivityResultLauncher<String[]> importLauncher;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // --- NEW: Fix SSL Handshake errors on older Emulators ---
        // try {
        // ProviderInstaller.installIfNeeded(this);
        // } catch (Exception e) {
        // e.printStackTrace();
        // }
        // --------------------------------------------------------

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        motionLayout = findViewById(R.id.main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        findViewById(R.id.browseWorkouts)
                .setOnClickListener(v -> startActivity(new Intent(MainActivity.this, RoutinesActivity.class)));

        ScrollView scrollView = findViewById(R.id.btn_scroll);

        // Run after layout so child sizes are known
        scrollView.post(() -> {
            ViewGroup container = (ViewGroup) scrollView.getChildAt(0);
            int itemHeight = container.getChildAt(0).getHeight();

            // Scroll to the second item (index 1)
            int targetY = 2 * itemHeight;
            scrollView.scrollTo(0, targetY);
        });

        scrollView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                // Get the container (LinearLayout inside the scrollview)
                ViewGroup container = (ViewGroup) scrollView.getChildAt(0);

                // Each item has fixed width (58dp in your XML)
                int itemHeight = container.getChildAt(0).getHeight();
                int scrollY = scrollView.getScrollY();

                // Decide which item we are closer to: 0 (GitHub) or 1 (Theme container)
                int page = (scrollY + itemHeight / 2) / itemHeight;
                int targetY = page * itemHeight;

                scrollView.post(() -> scrollView.smoothScrollTo(0, targetY));
                return false;
            }
            return false;
        });

        // Inside onCreate in MainActivity.java
        exportLauncher = registerForActivityResult(new ActivityResultContracts.CreateDocument("application/json"), uri -> {
            if (uri != null) {
                StravaArchiveManager.exportData(this, uri);
            }
        });

        importLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri != null) {
                StravaArchiveManager.importData(this, uri);
            }
        });

        setupGuideRecyclerView();

        setupThemeButtons();
        setupButtons();
        setupWeekPager();
        setupOnBackPressed();
        handleWidgetIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleWidgetIntent(intent);
    }

    private void handleWidgetIntent(Intent intent) {
        if (intent != null && intent.hasExtra("EXTRA_DAY_INDEX")) {
            int dayIndex = intent.getIntExtra("EXTRA_DAY_INDEX", 0);
            ViewPager2 weekPager = findViewById(R.id.weekPager);
            if (weekPager != null) {
                // Find current middle position to align to Monday correctly, then add dayIndex
                int middle = Integer.MAX_VALUE / 2;
                int startPosition = middle - (middle % 7) + dayIndex;
                weekPager.setCurrentItem(startPosition, false);
            }
        }
    }

    private void setupGuideRecyclerView() {
        RecyclerView guideRv = findViewById(R.id.guide_container);
        guideRv.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));

        GuideAdapter adapter = new GuideAdapter(new GuideAdapter.OnArchiveInteractionListener() {
            @Override
            public void onExportClicked() {
                String fileName = "Strava_Archive_" + System.currentTimeMillis() + ".json";
                exportLauncher.launch(fileName);
            }

            @Override
            public void onImportClicked() {
                importLauncher.launch(new String[]{"application/json"});
            }
        });
        guideRv.setAdapter(adapter);

        // FIX: Pre-calculate the height of the 'Usage Info' page (the tallest one)
        // We inflate it effectively "off-screen" just to measure it.
        View usageView = getLayoutInflater().inflate(R.layout.settings_usageinfo, guideRv, false);

        // Measure it with the width of the screen (or parent) to see how tall it gets
        int widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(getResources().getDisplayMetrics().widthPixels,
                View.MeasureSpec.AT_MOST);
        int heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        usageView.measure(widthMeasureSpec, heightMeasureSpec);

        // Apply that measured height as the minimum height for the RecyclerView
        int usageHeight = usageView.getMeasuredHeight();
        guideRv.setMinimumHeight(usageHeight);

        PagerSnapHelper snapHelper = new PagerSnapHelper();
        snapHelper.attachToRecyclerView(guideRv);
    }

    private void setupButtons() {
        ImageButton githubicon = findViewById(R.id.githubIcon);
        ImageButton stravaaccess = findViewById(R.id.stravaAccess);

        githubicon.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://www.github.com/spewedprojects/WorkoutRepo"));
            v.getContext().startActivity(intent);
        });

        // Define the actions
        Runnable openUrlAction = () -> {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            String url = prefs.getString("CustomStravaUrl", "https://www.strava.com/athletes/32298220");
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(this, "Invalid URL saved", Toast.LENGTH_SHORT).show();
            }
        };

        Runnable openSheetAction = () -> {
            ViewPager2 weekPager = findViewById(R.id.weekPager);
            if (weekPager != null) {
                int currentItem = weekPager.getCurrentItem();
                int dayIndex = currentItem % 7;
                String dayName = getDayNameFromIndex(dayIndex);

                StravaBottomSheet bottomSheet = new StravaBottomSheet(dayName);
                bottomSheet.show(getSupportFragmentManager(), "StravaSheet");
            }
        };

        Runnable openArchiveAction = () -> {
            startActivity(new Intent(MainActivity.this, StravaArchiveActivity.class));
        };

        // SINGLE CLICK LISTENER
        stravaaccess.setOnClickListener(v -> {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            boolean longClickStravaAction = prefs.getBoolean("StravaButtonLongClickAction", true);

            if (longClickStravaAction) {
                openSheetAction.run();
            } else {
                openArchiveAction.run();
            }
        });

        // Set initial visibility based on preference
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean isStravaEnabled = prefs.getBoolean("EnableStravaFeature", false);
        stravaaccess.setVisibility(isStravaEnabled ? View.VISIBLE : View.GONE);

        // Listen for changes
        // --- UPDATED: Assign to the global variable ---
        prefListener = (sharedPreferences, key) -> {
            if ("EnableStravaFeature".equals(key)) {
                boolean enabled = sharedPreferences.getBoolean("EnableStravaFeature", false);
                stravaaccess.setVisibility(enabled ? View.VISIBLE : View.GONE);
            }
        };
        // Register the strong reference
        prefs.registerOnSharedPreferenceChangeListener(prefListener);

        // LONG CLICK LISTENER
        stravaaccess.setOnLongClickListener(v -> {
            boolean longClickStravaAction = prefs.getBoolean("StravaButtonLongClickAction", true);

            // If long click is assigned to Archive (true), open Archive.
            if (longClickStravaAction) {
                openArchiveAction.run();
            } else {
                openSheetAction.run();
            }
            return true; // Consume event
        });

        ImageButton guideBtn = findViewById(R.id.guide_btn);
        guideBtn.setOnClickListener(v -> {
            // Check current state by ID or Progress
            if (motionLayout.getCurrentState() == R.id.end_visible) {
                motionLayout.transitionToStart(); // Animates to guide_hidden
            } else {
                motionLayout.transitionToEnd(); // Animates to guide_visible
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

    private String getDayNameFromIndex(int index) {
        switch (index) {
            case 0: return "Monday";
            case 1: return "Tuesday";
            case 2: return "Wednesday";
            case 3: return "Thursday";
            case 4: return "Friday";
            case 5: return "Saturday";
            case 6: return "Sunday";
            default: return "Monday"; // Fallback
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // --- NEW: Re-check Strava button visibility when returning to the app ---
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean isStravaEnabled = prefs.getBoolean("EnableStravaFeature", false);
        ImageButton stravaaccess = findViewById(R.id.stravaAccess);
        if (stravaaccess != null) {
            stravaaccess.setVisibility(isStravaEnabled ? View.VISIBLE : View.GONE);
        }

        // This forces the adapter to reload data from the JSON file which might have changed if you clicked "Apply" in the Routines screen
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
                // First check if MotionLayout is expanded
                if (motionLayout.getCurrentState() == R.id.end_visible) {
                    // Collapse it instead of exiting
                    motionLayout.transitionToStart();
                    return; // consume back press here
                }

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