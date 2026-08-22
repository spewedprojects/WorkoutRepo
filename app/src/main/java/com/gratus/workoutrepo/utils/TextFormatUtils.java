package com.gratus.workoutrepo.utils;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.LeadingMarginSpan;
import android.text.style.StyleSpan;
import android.widget.EditText;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TextFormatUtils {

    // Spacing constants (in pixels)
    private static final int BULLET_GAP_WIDTH = 15; // Space between bullet and text
    private static final int NUMBERED_BULLET_GAP_WIDTH = 25; // Space between bullet and text

    private static final int MAIN_BULLET_INDENT = 30;
    private static final int SUB_BULLET_INDENT = 60; // Indentation for sub-points

    /**
     * formatting for Major/Minor sections.
     * Uses the Main Bullet char (\u2022) with a custom span.
     */
    public static CharSequence formatBulletsForDisplay(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "";

        SpannableStringBuilder ssb = new SpannableStringBuilder();
        String[] lines = raw.replace("\r\n", "\n").split("\n");

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;

            // Strip existing text bullets/hyphens
            if (line.startsWith("•") || line.startsWith("- ")) {
                line = line.substring(line.startsWith("•") ? 1 : 2).trim();
            }

            int start = ssb.length();
            ssb.append(line);
            int end = ssb.length();

            // Apply Custom Span: "\u2022" with 0 extra indent
            ssb.setSpan(new TextBulletSpan("\u2022", BULLET_GAP_WIDTH, 0),
                    start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

            if (i < lines.length - 1) ssb.append("\n");
        }

        return applyBoldFormatting(ssb);
    }

    /**
     * Special formatting for Widgets using standard parcelable spans.
     * RemoteViews cannot handle custom span classes like TextBulletSpan.
     */
    public static CharSequence formatBulletsForWidget(String line) {
        if (line == null || line.trim().isEmpty()) return "";

        line = line.trim();
        // Strip existing text bullets/hyphens
        if (line.startsWith("•") || line.startsWith("- ")) {
            line = line.substring(line.startsWith("•") ? 1 : 2).trim();
        }

        SpannableStringBuilder ssb = new SpannableStringBuilder("• " + line);
        
        // Apply hanging indent using Standard LeadingMarginSpan
        // first = 0, rest = 32 (aprox width of "• ")
        ssb.setSpan(new LeadingMarginSpan.Standard(0, 32),
                0, ssb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        return applyBoldFormatting(ssb);
    }

    /**
     * Formatting for Notes.
     * Handles Main Bullets (\u2022) and Sub-Bullets (\u25E6 | \u09F9).
     */
    public static CharSequence formatNotesForDisplay(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "";

        SpannableStringBuilder ssb = new SpannableStringBuilder();
        String[] lines = raw.split("\\r?\\n", -1);
        // 1. Define the pattern for numbers (e.g., "1.", "12.", "1)")
        // ^\s* matches start of line with optional spaces
        // (\d+) captures the digits
        // [.)] matches either a dot or a closing parenthesis
        // \s* matches optional space after the punctuation
        Pattern numberPattern = Pattern.compile("^\\s*(\\d+)[.)]\\s*");

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int start = ssb.length();
            boolean isLastLine = (i == lines.length - 1);

            Matcher matcher = numberPattern.matcher(line);

            if (matcher.find()) {
                // NUMBERED LIST MATCHED
                String numberStr = matcher.group(1); // The actual number (e.g., "1")
                String fullMatch = matcher.group(0); // The whole "1. " part

                String content = line.substring(fullMatch.length()).trim();
                ssb.append(content);

                // Use the number + "." as the bullet character instead of \u2022
                ssb.setSpan(new TextBulletSpan(numberStr + ".", NUMBERED_BULLET_GAP_WIDTH, 0),
                        start, ssb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

            } else if (line.trim().startsWith("-")) {
                // Count consecutive dashes at the start
                int dashCount = 0;
                while (dashCount < line.length() && line.charAt(dashCount) == '-') {
                    dashCount++;
                }
                // Require a space after the dashes to qualify as a bullet
                if (dashCount > 0 && dashCount < line.length() && line.charAt(dashCount) == ' ') {
                    String content = line.substring(dashCount + 1).trim();
                    ssb.append(content);
                    // Decide bullet style and indent based on dash count
                    String bulletChar;
                    int indent = switch (dashCount) {
                        case 1 -> { // First dash (- )
                            bulletChar = "\u2022"; // filled bullet
                            yield 0;
                        }
                        case 2 -> { // Second dash (-- )
                            bulletChar = "•"; // filled bullet
                            yield MAIN_BULLET_INDENT;
                        }
                        case 3 -> { // Third dash (--- )
                            bulletChar = "৹"; // hollow bullet
                            yield SUB_BULLET_INDENT;
                        }
                        default -> { // More than 3 dashes (----... )
                            bulletChar = "\u09F9";
                            yield SUB_BULLET_INDENT + (dashCount - 3) * 20;
                        }
                    };
                    ssb.setSpan(new TextBulletSpan(bulletChar, BULLET_GAP_WIDTH, indent),
                            start, ssb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                } else {
                    // Not a valid bullet, just append text
                    ssb.append(line);
                }
            } else {
                // Regular text
                ssb.append(line);
            }


            if (!isLastLine) ssb.append("\n");
        }

        return applyBoldFormatting(ssb);
    }

    // --- Helper: Clean up **bold** markers ---
    private static SpannableStringBuilder applyBoldFormatting(SpannableStringBuilder ssb) {
        Pattern boldPattern = Pattern.compile("\\*\\*(.*?)\\*\\*");
        Matcher matcher = boldPattern.matcher(ssb);

        List<int[]> matches = new ArrayList<>();
        while (matcher.find()) {
            matches.add(new int[]{matcher.start(), matcher.end()});
        }

        for (int i = matches.size() - 1; i >= 0; i--) {
            int start = matches.get(i)[0];
            int end = matches.get(i)[1];
            ssb.setSpan(new StyleSpan(Typeface.BOLD), start + 2, end - 2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            ssb.delete(end - 2, end);
            ssb.delete(start, start + 2);
        }
        return ssb;
    }

    public static String cleanTextForStorage(String raw) {
        if (raw == null) return "";
        String[] lines = raw.replace("\r\n", "\n").replace("\r", "\n").split("\n");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            line = line.replaceAll("^[\\u2022\\*\\-\\s]+", "");
            sb.append(line);
            if (i < lines.length - 1) sb.append("\n");
        }
        return sb.toString().trim();
    }

    // =========================================================================================
    // CUSTOM SPAN CLASS: Draws specific text characters with Thickness and Indentation
    // =========================================================================================
    private static class TextBulletSpan implements LeadingMarginSpan {
        private final String bulletChar;
        private final int gapWidth;
        private final int indent; // Extra indent (for sub-bullets)

        public TextBulletSpan(String bulletChar, int gapWidth, int indent) {
            this.bulletChar = bulletChar;
            this.gapWidth = gapWidth;
            this.indent = indent;
        }

        @Override
        public int getLeadingMargin(boolean first) {
            // Reserve space for: Indent + Bullet + Gap
            return indent + gapWidth + 20; // +20 adds a little breathing room for the character itself
        }

        @Override
        public void drawLeadingMargin(Canvas c, Paint p, int x, int dir, int top, int baseline, int bottom,
                                      CharSequence text, int start, int end, boolean first, Layout layout) {
            // Only draw the bullet on the first line of the paragraph
            if (first) {
                Paint.Style originalStyle = p.getStyle();
                float originalStroke = p.getStrokeWidth();
                boolean originalFakeBold = p.isFakeBoldText();

                // 1. Make it THICK
                p.setFakeBoldText(true);
                // Optional: p.setStrokeWidth(originalStroke + 2);

                // 2. Draw the specific character
                // x + (dir * indent) puts it at the start of the indentation block
                c.drawText(bulletChar, x + (dir * indent), baseline, p);

                // 3. Restore original paint settings so text doesn't get messed up
                p.setFakeBoldText(originalFakeBold);
                p.setStyle(originalStyle);
                p.setStrokeWidth(originalStroke);
            }
        }
    }

    public static CharSequence getCollapsedNotes(String raw, int maxLines) {
        if (raw == null || raw.trim().isEmpty()) return "";

        String[] lines = raw.split("\\r?\\n");
        if (lines.length <= maxLines) {
            return formatNotesForDisplay(raw);
        }

        int count = Math.min(lines.length, Math.max(1, maxLines));
        StringBuilder collapsed = new StringBuilder();
        for (int i = 0; i < count; i++) {
            collapsed.append(lines[i]);
            if (i < count - 1) collapsed.append("\n");
        }
        collapsed.append("..."); // The visual indicator

        return formatNotesForDisplay(collapsed.toString());
    }

    // =========================================================================================
    // INTERACTIVE EDITOR FORMATTING HELPERS
    // =========================================================================================

    public static void applyBold(EditText editText) {
        if (editText == null) return;
        android.text.Editable editable = editText.getText();
        if (editable == null) return;

        int selStart = editText.getSelectionStart();
        int selEnd = editText.getSelectionEnd();
        int start = Math.min(selStart, selEnd);
        int end = Math.max(selStart, selEnd);

        if (start < 0) start = 0;
        if (end < 0) end = 0;

        if (start == end) {
            // No selection: insert **** and position cursor between asterisks
            editable.insert(start, "****");
            editText.setSelection(start + 2);
        } else {
            String selected = editable.subSequence(start, end).toString();
            // Check if selected text already starts and ends with **
            if (selected.startsWith("**") && selected.endsWith("**") && selected.length() >= 4) {
                String unbolded = selected.substring(2, selected.length() - 2);
                editable.replace(start, end, unbolded);
                editText.setSelection(start, start + unbolded.length());
            } else if (start >= 2 && end <= editable.length() - 2 &&
                    editable.subSequence(start - 2, start).toString().equals("**") &&
                    editable.subSequence(end, end + 2).toString().equals("**")) {
                editable.delete(end, end + 2);
                editable.delete(start - 2, start);
                editText.setSelection(start - 2, end - 2);
            } else {
                String bolded = "**" + selected + "**";
                editable.replace(start, end, bolded);
                editText.setSelection(start, start + bolded.length());
            }
        }
    }

    public static void applyBulletList(EditText editText) {
        if (editText == null) return;
        android.text.Editable editable = editText.getText();
        if (editable == null) return;

        int selStart = editText.getSelectionStart();
        int selEnd = editText.getSelectionEnd();
        int start = Math.min(selStart, selEnd);
        int end = Math.max(selStart, selEnd);

        if (start < 0) start = 0;
        if (end < 0) end = 0;

        String text = editable.toString();
        int lineStart = (start > 0) ? text.lastIndexOf('\n', start - 1) + 1 : 0;
        int lineEnd = text.indexOf('\n', end);
        if (lineEnd == -1) lineEnd = text.length();

        String block = text.substring(lineStart, lineEnd);
        String[] lines = block.split("\n", -1);
        StringBuilder sb = new StringBuilder();

        Pattern dashPattern = Pattern.compile("^(-+ )");
        Pattern numPattern = Pattern.compile("^(\\s*\\d+[.)]\\s*)");

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            Matcher dashMatcher = dashPattern.matcher(line);
            Matcher numMatcher = numPattern.matcher(line);

            if (dashMatcher.find()) {
                if (line.startsWith("- ")) {
                    // Toggle off
                    sb.append(line.substring(2));
                } else {
                    // Reset multi-dash to single dash bullet
                    sb.append("- ").append(line.substring(dashMatcher.end()));
                }
            } else if (numMatcher.find()) {
                sb.append("- ").append(line.substring(numMatcher.end()));
            } else {
                sb.append("- ").append(line);
            }

            if (i < lines.length - 1) sb.append("\n");
        }

        String result = sb.toString();
        editable.replace(lineStart, lineEnd, result);
        editText.setSelection(Math.min(lineStart + result.length(), editable.length()));
    }

    public static void applyIndentIncrease(EditText editText) {
        if (editText == null) return;
        android.text.Editable editable = editText.getText();
        if (editable == null) return;

        int selStart = editText.getSelectionStart();
        int selEnd = editText.getSelectionEnd();
        int start = Math.min(selStart, selEnd);
        int end = Math.max(selStart, selEnd);

        if (start < 0) start = 0;
        if (end < 0) end = 0;

        String text = editable.toString();
        int lineStart = (start > 0) ? text.lastIndexOf('\n', start - 1) + 1 : 0;
        int lineEnd = text.indexOf('\n', end);
        if (lineEnd == -1) lineEnd = text.length();

        String block = text.substring(lineStart, lineEnd);
        String[] lines = block.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        boolean modified = false;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int dashCount = 0;
            while (dashCount < line.length() && line.charAt(dashCount) == '-') {
                dashCount++;
            }

            // Must have >= 1 dash and followed by space (e.g., "- ", "-- ")
            if (dashCount >= 1 && dashCount < line.length() && line.charAt(dashCount) == ' ') {
                sb.append("-").append(line);
                modified = true;
            } else {
                sb.append(line);
            }

            if (i < lines.length - 1) sb.append("\n");
        }

        if (modified) {
            String result = sb.toString();
            editable.replace(lineStart, lineEnd, result);
            editText.setSelection(Math.min(lineStart + result.length(), editable.length()));
        }
    }

    public static void applyNumberedList(EditText editText) {
        if (editText == null) return;
        android.text.Editable editable = editText.getText();
        if (editable == null) return;

        int selStart = editText.getSelectionStart();
        int selEnd = editText.getSelectionEnd();
        int start = Math.min(selStart, selEnd);
        int end = Math.max(selStart, selEnd);

        if (start < 0) start = 0;
        if (end < 0) end = 0;

        String text = editable.toString();
        int lineStart = (start > 0) ? text.lastIndexOf('\n', start - 1) + 1 : 0;
        int lineEnd = text.indexOf('\n', end);
        if (lineEnd == -1) lineEnd = text.length();

        // Check preceding line to determine current starting number and delimiter style (1. vs 1))
        int nextNumber = 1;
        char delimiter = '.';

        if (lineStart > 0) {
            int prevLineEnd = lineStart - 1;
            int prevLineStart = text.lastIndexOf('\n', prevLineEnd - 1) + 1;
            String prevLine = text.substring(prevLineStart, prevLineEnd).trim();
            Pattern prevNumPattern = Pattern.compile("^(\\d+)([.)])\\s*");
            Matcher prevMatcher = prevNumPattern.matcher(prevLine);
            if (prevMatcher.find()) {
                try {
                    nextNumber = Integer.parseInt(prevMatcher.group(1)) + 1;
                    delimiter = prevMatcher.group(2).charAt(0);
                } catch (NumberFormatException ignored) {}
            }
        }

        String block = text.substring(lineStart, lineEnd);
        String[] lines = block.split("\n", -1);
        StringBuilder sb = new StringBuilder();

        Pattern dashPattern = Pattern.compile("^(-+ )");
        Pattern numPattern = Pattern.compile("^(\\s*\\d+[.)]\\s*)");

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            Matcher dashMatcher = dashPattern.matcher(line);
            Matcher numMatcher = numPattern.matcher(line);

            String prefix = nextNumber + "" + delimiter + " ";

            if (numMatcher.find()) {
                // If it already has exact prefix, toggle off
                if (line.startsWith(prefix)) {
                    sb.append(line.substring(numMatcher.end()));
                } else {
                    sb.append(prefix).append(line.substring(numMatcher.end()));
                    nextNumber++;
                }
            } else if (dashMatcher.find()) {
                sb.append(prefix).append(line.substring(dashMatcher.end()));
                nextNumber++;
            } else {
                sb.append(prefix).append(line);
                nextNumber++;
            }

            if (i < lines.length - 1) sb.append("\n");
        }

        String result = sb.toString();
        editable.replace(lineStart, lineEnd, result);
        editText.setSelection(Math.min(lineStart + result.length(), editable.length()));
    }
}