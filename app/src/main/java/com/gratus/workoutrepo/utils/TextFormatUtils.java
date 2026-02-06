package com.gratus.workoutrepo.utils;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.LeadingMarginSpan;
import android.text.style.StyleSpan;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TextFormatUtils {

    // Spacing constants (in pixels)
    private static final int BULLET_GAP_WIDTH = 15; // Space between bullet and text

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
     * Formatting for Notes.
     * Handles Main Bullets (\u2022) and Sub-Bullets (\u25E6).
     */
    public static CharSequence formatNotesForDisplay(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "";

        SpannableStringBuilder ssb = new SpannableStringBuilder();
        String[] lines = raw.split("\\r?\\n", -1);

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int start = ssb.length();
            boolean isLastLine = (i == lines.length - 1);

            if (line.startsWith("- ") || line.startsWith("-")) {
                // MAIN BULLET
                String content = line.substring(2).trim();
                ssb.append(content);
                // Apply Custom Span: "\u2022" (Filled Bullet)
                ssb.setSpan(new TextBulletSpan("\u2022", BULLET_GAP_WIDTH, 0),
                        start, ssb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

            } else if (line.startsWith("-- ") || line.startsWith("--") || line.startsWith(" - ") || line.startsWith(" -")) {
                // MAIN BULLET Indented
                int dashIndex = line.indexOf("-");
                String content = line.substring(dashIndex + 1).trim();
                ssb.append(content);
                // Apply Custom Span: "\u2022" (Filled Bullet)
                ssb.setSpan(new TextBulletSpan("\u2022", BULLET_GAP_WIDTH, MAIN_BULLET_INDENT),
                        start, ssb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

            } else if (line.startsWith("--- ") || line.startsWith("---") || line.startsWith("  - ") || line.startsWith("  -")) {
                // SUB BULLET
                int dashIndex = line.indexOf("-");
                String content = line.substring(dashIndex + 2).trim();
                ssb.append(content);

                // Apply Custom Span: "\u25E6" (Hollow Bullet) with EXTRA INDENT
                ssb.setSpan(new TextBulletSpan("\u25E6", BULLET_GAP_WIDTH, SUB_BULLET_INDENT),
                        start, ssb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

            } else {
                // REGULAR TEXT (No bullet, just text)
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

        // Take the first 5 lines
        StringBuilder collapsed = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            collapsed.append(lines[i]);
            if (i < 4) collapsed.append("\n");
        }
        collapsed.append("..."); // The visual indicator

        return formatNotesForDisplay(collapsed.toString());
    }
}