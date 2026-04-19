package com.gratus.workoutrepo.utils;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.gratus.workoutrepo.repository.StravaRepository;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StravaArchiveManager {

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static void exportData(Context context, Uri uri) {
        executor.execute(() -> {
            try {
                // Fetch the pretty-printed JSON from the Repository
                String jsonData = StravaRepository.INSTANCE.getExportData(context);

                try (OutputStream os = context.getContentResolver().openOutputStream(uri)) {
                    if (os != null) {
                        os.write(jsonData.getBytes());
                        showToast(context, "Archive exported successfully!");
                    }
                }
            } catch (Exception e) {
                showToast(context, "Export failed: " + e.getLocalizedMessage());
            }
        });
    }

    public static void importData(Context context, Uri uri) {
        executor.execute(() -> {
            try (InputStream is = context.getContentResolver().openInputStream(uri);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {

                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }

                // Pass the string to your Kotlin Repository for merging
                boolean success = StravaRepository.INSTANCE.importArchive(context, sb.toString());

                mainHandler.post(() -> {
                    if (success) {
                        Toast.makeText(context, "Archive imported and merged!", Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(context, "Invalid archive file.", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                showToast(context, "Import failed: " + e.getLocalizedMessage());
            }
        });
    }

    private static void showToast(Context context, String message) {
        mainHandler.post(() -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show());
    }
}