package com.gratus.workoutrepo;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.viewpager2.widget.ViewPager2;
import com.google.gson.Gson;
import com.gratus.workoutrepo.adapters.RoutinesPagerAdapter;
import com.gratus.workoutrepo.data.RoutineRepository;
import com.gratus.workoutrepo.model.Routine;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.List;

public class RoutinesActivity extends AppCompatActivity {

    private ViewPager2 viewPager;
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

        viewPager = findViewById(R.id.routinesPager);
        loadData();
    }

    private void loadData() {
        loadedRoutines = RoutineRepository.getAllSavedRoutines(this);
        Routine activeRoutine = RoutineRepository.getActiveRoutine(this);

        // Pass the Active ID to the adapter so it can hide the delete button
        adapter = new RoutinesPagerAdapter(loadedRoutines, activeRoutine.id, actionListener);
        viewPager.setAdapter(adapter);

        // Logic to Jump to the Active Routine
        int activeIndex = -1;
        for (int i = 0; i < loadedRoutines.size(); i++) {
            if (loadedRoutines.get(i).id.equals(activeRoutine.id)) {
                activeIndex = i;
                break;
            }
        }

        if (activeIndex != -1) {
            // Jump instantly (false = no animation)
            viewPager.setCurrentItem(activeIndex, false);
        } else {
            // If active routine isn't in the list (e.g. freshly migrated), go to start
            viewPager.setCurrentItem(0, false);
        }
    }

    // --- Action Listener Implementation ---
    private final RoutineActionListener actionListener = new RoutineActionListener() {
        @Override
        public void onApply(Routine routine) {
            // Overwrite active routine
            RoutineRepository.saveActiveRoutine(RoutinesActivity.this, routine);
            Toast.makeText(RoutinesActivity.this, "Routine Applied!", Toast.LENGTH_SHORT).show();
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
            exportLauncher.launch(intent);
        }

        @Override
        public void onDelete(Routine routine) {
            RoutineRepository.deleteRoutine(RoutinesActivity.this, routine.id);
            loadData(); // Refresh UI
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
            viewPager.setCurrentItem(loadedRoutines.size() - 1);
        }

        @Override
        public void onImport() {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            importLauncher.launch(intent);
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
            if (newIndex != -1) viewPager.setCurrentItem(newIndex, true);

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
    }
}