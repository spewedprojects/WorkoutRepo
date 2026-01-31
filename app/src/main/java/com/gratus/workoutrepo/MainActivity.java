package com.gratus.workoutrepo;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.viewpager2.widget.ViewPager2;

import com.gratus.workoutrepo.adapters.WeekPagerAdapter;

import java.util.Calendar;

public class MainActivity extends AppCompatActivity {

    private long backPressedTime;
    private Toast backToast;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        findViewById(R.id.browseWorkouts).setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, RoutinesActivity.class))
        );

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

        // inside onCreate(...)
        ViewPager2 weekPager = findViewById(R.id.weekPager);
        WeekPagerAdapter adapter = new WeekPagerAdapter(this);
        weekPager.setAdapter(adapter);

        // Calculate current day index (0 = Monday, 6 = Sunday)
        Calendar calendar = Calendar.getInstance();
        int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
        int dayIndex;
        switch (dayOfWeek) {
            case Calendar.MONDAY: dayIndex = 0; break;
            case Calendar.TUESDAY: dayIndex = 1; break;
            case Calendar.WEDNESDAY: dayIndex = 2; break;
            case Calendar.THURSDAY: dayIndex = 3; break;
            case Calendar.FRIDAY: dayIndex = 4; break;
            case Calendar.SATURDAY: dayIndex = 5; break;
            case Calendar.SUNDAY: dayIndex = 6; break;
            default: dayIndex = 0;
        }

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

}