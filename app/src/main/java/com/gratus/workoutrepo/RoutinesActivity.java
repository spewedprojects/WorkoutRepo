package com.gratus.workoutrepo;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.google.gson.Gson;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import com.google.gson.GsonBuilder;
import com.gratus.workoutrepo.routine.adapters.RoutinesPagerAdapter;
import com.gratus.workoutrepo.routine.data.RoutineRepository;
import com.gratus.workoutrepo.routine.model.Routine;
import com.gratus.workoutrepo.routine.model.DayWorkout;
import com.gratus.workoutrepo.routine.utils.ConfirmationDialogHelper;
import com.gratus.workoutrepo.routine.utils.TextFormatUtils;

public class RoutinesActivity extends BaseActivity {

    private RecyclerView routinesRecycler; // Changed from ViewPager2
    private RoutinesPagerAdapter adapter;
    private List<Routine> loadedRoutines;
    private Routine routineToExport; // Temp holder for export

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_routines); // Ensure this XML has ViewPager2 with id routinesPager
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.routines_activity), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        routinesRecycler = findViewById(R.id.routinesPager);
        // 1. Setup Horizontal Layout
        routinesRecycler.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));

        // 2. Add "Magnet" Snapping (This makes it feel like ViewPager)
        PagerSnapHelper snapHelper = new PagerSnapHelper();
        snapHelper.attachToRecyclerView(routinesRecycler);

        if (savedInstanceState != null) {
            String json = savedInstanceState.getString("loaded_routines_json");
            String editingId = savedInstanceState.getString("editing_routine_id");
            int position = savedInstanceState.getInt("current_scroll_position", -1);
            loadData(json, editingId, position);
        } else {
            loadData();
        }

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (adapter != null && adapter.getEditingRoutineId() != null) {
                    ConfirmationDialogHelper.showConfirmationDialog(
                            RoutinesActivity.this,
                            "Are you sure you want to discard changes made in current routine?",
                            new ConfirmationDialogHelper.ConfirmationListener() {
                                @Override
                                public void onYesClicked() {
                                    loadData(); // Discard memory changes
                                    adapter.exitEditMode();
                                }
                            }
                    );
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                    setEnabled(true);
                }
            }
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (adapter != null) {
            outState.putString("editing_routine_id", adapter.getEditingRoutineId());
        }
        if (routinesRecycler != null && routinesRecycler.getLayoutManager() instanceof LinearLayoutManager) {
            LinearLayoutManager lm = (LinearLayoutManager) routinesRecycler.getLayoutManager();
            int currentPos = lm.findFirstVisibleItemPosition();
            if (currentPos != RecyclerView.NO_POSITION) {
                outState.putInt("current_scroll_position", currentPos);
            }
        }
        if (loadedRoutines != null) {
            String json = new Gson().toJson(loadedRoutines);
            outState.putString("loaded_routines_json", json);
        }
    }

    private void loadData() {
        loadData(null, null, -1);
    }

    private void loadData(String restoredRoutinesJson, String restoredEditingId, int restoredPosition) {
        Routine activeRoutine = RoutineRepository.getActiveRoutine(this);

        if (restoredRoutinesJson != null) {
            try {
                java.lang.reflect.Type listType = new com.google.gson.reflect.TypeToken<List<Routine>>(){}.getType();
                loadedRoutines = new Gson().fromJson(restoredRoutinesJson, listType);
            } catch (Exception e) {
                loadedRoutines = RoutineRepository.getAllSavedRoutines(this);
            }
        } else {
            loadedRoutines = RoutineRepository.getAllSavedRoutines(this);
        }

        // Pass the Active ID to the adapter so it can hide the delete button
        adapter = new RoutinesPagerAdapter(loadedRoutines, activeRoutine.id, actionListener);
        if (restoredEditingId != null) {
            adapter.setEditingRoutineId(restoredEditingId);
        }
        routinesRecycler.setAdapter(adapter);

        int targetIndex = -1;
        if (restoredPosition != -1) {
            targetIndex = restoredPosition;
        } else {
            // Logic to Jump to the Active Routine
            for (int i = 0; i < loadedRoutines.size(); i++) {
                if (loadedRoutines.get(i).id.equals(activeRoutine.id)) {
                    targetIndex = i;
                    break;
                }
            }
        }

        if (targetIndex != -1 && targetIndex < loadedRoutines.size()) {
            routinesRecycler.scrollToPosition(targetIndex);
        }
    }

    // --- Action Listener Implementation ---
    private final RoutineActionListener actionListener = new RoutineActionListener() {
        @Override
        public void onApply(Routine routine) {
            // Overwrite active routine
            RoutineRepository.saveActiveRoutine(RoutinesActivity.this, routine);
            Toast.makeText(RoutinesActivity.this, "Routine Applied!", Toast.LENGTH_SHORT).show();
            // Trigger widget refresh
            com.gratus.workoutrepo.widgets.WorkoutsWidgetProvider.Companion.sendRefreshBroadcast(RoutinesActivity.this);
            // Optional: Close activity to return to main
            loadData();
        }

        @Override
        public void onExport(Routine routine) {
            routineToExport = routine;
            // Launch SAF Create Document
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_TITLE, routine.title + ".json");
            //exportLauncher.launch(intent)
            autoExportRoutine(RoutinesActivity.this, routine);
        }

        @Override
        public void onDelete(Routine routine) {
            String fullText = "Are you sure you want to delete '" + routine.title + "' routine ?";
            SpannableString spannable = new SpannableString(fullText);
            int start = fullText.indexOf(routine.title);
            int end = start + routine.title.length();
            spannable.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

            ConfirmationDialogHelper.showConfirmationDialog(
                    RoutinesActivity.this,
                    spannable,
                    new ConfirmationDialogHelper.ConfirmationListener() {
                        @Override
                        public void onYesClicked() {
                            RoutineRepository.deleteRoutine(RoutinesActivity.this, routine.id);
                            loadData(); // Refresh UI
                        }
                    }
            );
        }

        @Override
        public void onEditRoutineMeta(Routine routine, String field) {
            // Reuse your existing EditorBottomSheet here
            // When saved, call RoutineRepository.saveRoutineToLibrary(...) then notify adapter
            // NEW: Pass "Routine" as the first arg.
            // Result Title: "Edit Routine Title" or "Edit Routine Notes"
            String label = field.substring(0, 1).toUpperCase() + field.substring(1); // Capitalize "title" -> "Title"

            EditorBottomSheet sheet = EditorBottomSheet.newInstance("Routine", label,
                    field.equals("title") ? routine.title : routine.notes, -1);

            sheet.setOnSaveListener((text, pos) -> {
                if(field.equals("title")) routine.title = text;
                else routine.notes = text;

                // 2. Save to Library (Standard behavior) (02/02/26)
                RoutineRepository.saveRoutineToLibrary(RoutinesActivity.this, routine);

                // 3. FIX: Check if this is the ACTIVE routine. If so, update the buffer too! (18/01/26)
                Routine active = RoutineRepository.getActiveRoutine(RoutinesActivity.this);
                if (active != null && active.id.equals(routine.id)) {
                    // Update the active buffer so the Main Page knows about the new Title/Notes
                    RoutineRepository.saveActiveRoutine(RoutinesActivity.this, routine);
                }
                adapter.notifyDataSetChanged();
            });
            sheet.show(getSupportFragmentManager(), "meta_edit");
        }

        @Override
        public void onAddBlank() {
            Routine newRoutine = new Routine();
            RoutineRepository.saveRoutineToLibrary(RoutinesActivity.this, newRoutine);
            loadData();
            // Scroll to the new item (2nd to last)
            routinesRecycler.scrollToPosition(loadedRoutines.size() - 1);
        }

        @Override
        public void onImport() {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            importLauncher.launch(intent);
        }

        @Override
        public void onEditRoutineField(Routine routine, String day, String fieldKey, String currentValue) {
            String label = fieldKey;
            if(fieldKey.equals("workoutsMajor")) label = "Major Workouts";
            if(fieldKey.equals("workoutsMinor")) label = "Minor Workouts";
            if(fieldKey.equals("majorLabel")) label = "Major Label";
            if(fieldKey.equals("minorLabel")) label = "Minor Label";
            if(fieldKey.equals("workoutType")) label = "Type";
            if(fieldKey.equals("notes")) label = "Notes";

            EditorBottomSheet sheet = EditorBottomSheet.newInstance(day, label, currentValue, -1);
            sheet.setOnSaveListener((text, pos) -> {
                String finalText = text == null ? "" : text.trim();
                if (fieldKey.equals("workoutsMajor") || fieldKey.equals("workoutsMinor")) {
                    finalText = TextFormatUtils.cleanTextForStorage(finalText);
                }

                // Update the routine IN MEMORY ONLY
                DayWorkout dWorkout = null;
                for (DayWorkout d : routine.days) {
                    if (d.dayName.equalsIgnoreCase(day)) {
                        dWorkout = d;
                        break;
                    }
                }
                if (dWorkout != null) {
                    switch (fieldKey) {
                        case "workoutType": dWorkout.workoutType = finalText; break;
                        case "workoutsMajor": dWorkout.majorWorkouts = finalText; break;
                        case "workoutsMinor": dWorkout.minorWorkouts = finalText; break;
                        case "majorLabel": dWorkout.majorLabel = finalText; break;
                        case "minorLabel": dWorkout.minorLabel = finalText; break;
                        case "notes": dWorkout.notes = finalText; break;
                    }
                    adapter.notifyDataSetChanged();
                }
            });
            sheet.show(getSupportFragmentManager(), "field_edit");
        }

        @Override
        public void onConfirmEdit(Routine routine) {
            RoutineRepository.saveRoutineToLibrary(RoutinesActivity.this, routine);
            Routine active = RoutineRepository.getActiveRoutine(RoutinesActivity.this);
            if (active != null && active.id.equals(routine.id)) {
                RoutineRepository.saveActiveRoutine(RoutinesActivity.this, routine);
                com.gratus.workoutrepo.widgets.WorkoutsWidgetProvider.Companion.sendRefreshBroadcast(RoutinesActivity.this);
            }
            Toast.makeText(RoutinesActivity.this, "Routine changes saved!", Toast.LENGTH_SHORT).show();
            adapter.notifyDataSetChanged(); // Just refresh view
        }
    };

    // --- SAF Launchers ---

    private final ActivityResultLauncher<Intent> exportLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    writeRoutineToFile(uri);
                }
            });

    private final ActivityResultLauncher<Intent> importLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    readRoutineFromFile(uri);
                }
            });

    private void writeRoutineToFile(Uri uri) {
        try (OutputStream os = getContentResolver().openOutputStream(uri)) {
            // FIX: Use setPrettyPrinting() so the file is human-readable with indentation
            String json = new com.google.gson.GsonBuilder()
                    .setPrettyPrinting()
                    .create()
                    .toJson(routineToExport);
            os.write(json.getBytes());
            Toast.makeText(this, "Export Successful", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Export Failed", Toast.LENGTH_SHORT).show();
        }
    }

    public static void autoExportRoutine(Context context, Routine routine) {
        // Build filename with timestamp
        String timeStamp = new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date());
        String fileName = "routine_" + routine.title + "_" + timeStamp + ".json";

        OutputStream outputStream = null;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ : use MediaStore
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                values.put(MediaStore.MediaColumns.MIME_TYPE, "application/json");
                values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                        Environment.DIRECTORY_DOCUMENTS + "/RoutineExports");

                Uri fileUri = context.getContentResolver()
                        .insert(MediaStore.Files.getContentUri("external"), values);
                if (fileUri == null) {
                    throw new FileNotFoundException("Failed to create file in Documents/RoutineExports");
                }
                outputStream = context.getContentResolver().openOutputStream(fileUri);
            } else {
                // Legacy storage for Android 9 and below
                File exportDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
                File routineDir = new File(exportDir, "RoutineExports");
                if (!routineDir.exists() && !routineDir.mkdirs()) {
                    throw new IOException("Failed to create RoutineExports directory.");
                }
                File exportFile = new File(routineDir, fileName);
                outputStream = new FileOutputStream(exportFile);
            }

            // Write JSON with pretty printing
            String json = new GsonBuilder()
                    .setPrettyPrinting()
                    .create()
                    .toJson(routine);
            try (Writer writer = new OutputStreamWriter(outputStream)) {
                writer.write(json);
            }

            Toast.makeText(context, "Saved to Documents/RoutineExports!", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(context, "Export Failed.", Toast.LENGTH_SHORT).show();
        }
    }

    private void readRoutineFromFile(Uri uri) {
        try (InputStream is = getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {

            Routine imported = new Gson().fromJson(reader, Routine.class);

            // 1. Validate Structure
            if (imported.days == null || imported.days.size() != 7) {
                throw new Exception("Invalid Format");
            }

            // 2. FORCE NEW UUID
            // This handles missing UUIDs (sets one) and duplicates (avoids overwrite)
            imported.id = java.util.UUID.randomUUID().toString();

            // 3. HANDLE TITLE COLLISIONS (Suffix Logic)
            List<Routine> existingRoutines = RoutineRepository.getAllSavedRoutines(this);
            String originalTitle = imported.title;
            String uniqueTitle = originalTitle;
            int counter = 2;

            while (titleExists(existingRoutines, uniqueTitle)) {
                uniqueTitle = originalTitle + "_" + counter;
                counter++;
            }
            imported.title = uniqueTitle;

            // 4. Save to internal library
            RoutineRepository.saveRoutineToLibrary(this, imported);

            // 5. Refresh UI and scroll to the new item
            loadData();
            // Scroll to the newly added item (it will be at the end due to timestamp sort)
            // Or find it by ID if you want to be precise
            int newIndex = findRoutineIndex(imported.id);
            if (newIndex != -1) routinesRecycler.scrollToPosition(newIndex);

            Toast.makeText(this, "Imported: " + uniqueTitle, Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Import Failed: Invalid JSON format", Toast.LENGTH_LONG).show();
        }
    }

    // Helper to check for duplicates
    private boolean titleExists(List<Routine> list, String title) {
        for (Routine r : list) {
            if (r.title.equalsIgnoreCase(title)) return true;
        }
        return false;
    }

    // Helper to find index of specific routine ID
    private int findRoutineIndex(String id) {
        if (loadedRoutines == null) return -1;
        for (int i = 0; i < loadedRoutines.size(); i++) {
            if (loadedRoutines.get(i).id.equals(id)) return i;
        }
        return -1;
    }

    public interface RoutineActionListener {
        void onApply(Routine routine);
        void onExport(Routine routine);
        void onDelete(Routine routine);
        void onEditRoutineMeta(Routine routine, String field);
        void onAddBlank();
        void onImport();
        void onEditRoutineField(Routine routine, String day, String fieldKey, String currentValue);
        void onConfirmEdit(Routine routine);
    }
}