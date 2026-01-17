package com.gratus.workoutrepo.utils; // Create this package or put in main

public class TextFormatUtils {

    // DISPLAY: Converts raw text "Squats\nLunges" -> "• Squats\n• Lunges"
    public static String formatBulletsForDisplay(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "";

        String[] lines = raw.replace("\r\n", "\n").split("\n");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            // Prevent double bullets if data is messy
            if (line.startsWith("•") || line.startsWith("- ")) {
                line = line.substring(1).trim();
            }
            sb.append("\u2022 ").append(line); // Add Bullet + Space
            if (i < lines.length - 1) sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * Convert raw stored notes (which may contain lines that start with "- ")
     * into a display-friendly string where lines beginning with "- " become
     * bullet lines showing "• <text>". Other lines are left as plain text.
     */
    // DISPLAY: Converts raw notes "Para 1\n- Point A" -> "Para 1\n• Point A"
    public static String formatNotesForDisplay(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "";

        String[] lines = raw.split("\\r?\\n", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.startsWith("- ")) {
                String after = line.substring(2).trim();
                sb.append("\u2022"); // Bullet char
                if (!after.isEmpty()) sb.append(" ").append(after);
            } else {
                sb.append(line);
            }
            if (i < lines.length - 1) sb.append("\n");
        }
        return sb.toString();
    }

    // SAVE: Cleans up input for storage.
    // Removes bullets if the user copy-pasted them, ensures clean newlines.
    public static String cleanTextForStorage(String raw) {
        if (raw == null) return "";
        String[] lines = raw.replace("\r\n", "\n").replace("\r", "\n").split("\n");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;

            // Strip existing bullets so we don't save them
            // regex removes leading "•", "-", "*", or whitespace
            line = line.replaceAll("^[\\u2022\\*\\-\\s]+", "");

            sb.append(line);
            if (i < lines.length - 1) sb.append("\n");
        }
        return sb.toString().trim();
    }
}