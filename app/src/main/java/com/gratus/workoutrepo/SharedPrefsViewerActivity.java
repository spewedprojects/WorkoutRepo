package com.gratus.workoutrepo;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.TableLayout;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

public class SharedPrefsViewerActivity extends AppCompatActivity {

    private final String[] weekDays = {
            "MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY","SATURDAY","SUNDAY"
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_shared_prefs);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.scroll_root), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            return insets;
        });

        LinearLayout container = findViewById(R.id.prefs_container);
        LayoutInflater inflater = LayoutInflater.from(this);

        for (String day : weekDays) {
            // inflate item layout
            LinearLayout dayBlock = (LinearLayout) inflater.inflate(R.layout.item_day_prefs, container, false);

            TextView tvDay = dayBlock.findViewById(R.id.day_title);
            TextView tvWorkoutType = dayBlock.findViewById(R.id.day_workouttype);

            TableLayout table = dayBlock.findViewById(R.id.day_table);

            tvDay.setText(day);

            // read stored values via WorkoutStorage (same API used elsewhere)
            Context ctx = this;
            String workoutType = WorkoutStorage.getWorkout(ctx, day, "workoutType", "");
            String major = WorkoutStorage.getWorkout(ctx, day, "workoutsMajor", "");
            String minor = WorkoutStorage.getWorkout(ctx, day, "workoutsMinor", "");
            String notes = WorkoutStorage.getWorkout(ctx, day, "notes", "");

            tvWorkoutType.setText(workoutType);

            // helper to make a single-row table with 3 cells
            LinearLayout row = (LinearLayout) inflater.inflate(R.layout.row_3cols, table, false);
            TextView c1 = row.findViewById(R.id.col1);
            TextView c2 = row.findViewById(R.id.col2);
            TextView c3 = row.findViewById(R.id.col3);

            // show plaintext (preserve bullets/newlines). If you want single-line, you can strip newlines.
            c1.setText(prepareDisplay(major));
            c2.setText(prepareDisplay(minor));
            c3.setText(prepareDisplay(notes));

            table.addView(row);

            container.addView(dayBlock);
        }
    }

    private String prepareDisplay(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "-";
        // Because your stored majors/minors usually have bullets "• ..." we can keep them.
        // Replace bullet char with a newline-friendly representation if needed.
        return raw;
    }
}
