package com.gratus.workoutrepo.adapters;

import static com.gratus.workoutrepo.BaseActivity.PREFS_NAME;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.gratus.workoutrepo.R;

public class GuideAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_SETTINGS = 0;
    private static final int TYPE_USAGE = 1;
    // Keys from BaseActivity
    private static final String STRAVA_URL_KEY = "CustomStravaUrl";
    private static final String PREF_LONG_CLICK_STRAVA = "StravaButtonLongClickAction";
    // New Keys
    private static final String PREF_ENABLE_STRAVA = "EnableStravaFeature";
    private static final String PREF_ENABLE_AUTO_REFRESH = "EnableAutoRefresh";
    private static final String PREF_CACHE_DURATION_HOURS = "CacheDurationHours";

    @Override
    public int getItemViewType(int position) {
        return (position == 1) ? TYPE_SETTINGS : TYPE_USAGE;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_SETTINGS) {
            View v = inflater.inflate(R.layout.settings_app, parent, false);
            return new SettingsViewHolder(v);
        } else {
            View v = inflater.inflate(R.layout.settings_usageinfo, parent, false);
            return new UsageViewHolder(v);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof SettingsViewHolder sHolder) {
            SharedPreferences prefs = sHolder.itemView.getContext()
                    .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

            // --- STRAVA TOGGLE ---
            boolean isStravaEnabled = prefs.getBoolean(PREF_ENABLE_STRAVA, false);
            sHolder.switchEnableStrava.setOnCheckedChangeListener(null);
            sHolder.switchEnableStrava.setChecked(isStravaEnabled);
            updateStravaSubText(sHolder.tvEnableStravaSub, isStravaEnabled);
            sHolder.stravaSettingsContainer.setVisibility(isStravaEnabled ? View.VISIBLE : View.GONE);

            sHolder.switchEnableStrava.setOnCheckedChangeListener((buttonView, isChecked) -> {
                prefs.edit().putBoolean(PREF_ENABLE_STRAVA, isChecked).apply();
                updateStravaSubText(sHolder.tvEnableStravaSub, isChecked);
                sHolder.stravaSettingsContainer.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            });

            // --- PROFILE URL ---
            String savedUrl = prefs.getString(STRAVA_URL_KEY, "https://www.strava.com/athletes/32298220");
            sHolder.etUrl.setText(savedUrl);

            // Save on Done
            sHolder.etUrl.setOnEditorActionListener((v, actionId, event) -> {
                saveUrl(sHolder.etUrl, prefs, v);
                return true;
            });

            // Save on End Icon Click
            sHolder.tilUrl.setEndIconOnClickListener(v -> {
                saveUrl(sHolder.etUrl, prefs, sHolder.etUrl);
            });

            // --- LONG/SHORT CLICK TOGGLE ---
            boolean longClickStravaAction = prefs.getBoolean(PREF_LONG_CLICK_STRAVA, true);
            sHolder.switchClick.setOnCheckedChangeListener(null);
            sHolder.switchClick.setChecked(longClickStravaAction);
            updateSwitchText(sHolder.tvClickSub, longClickStravaAction);

            sHolder.switchClick.setOnCheckedChangeListener((buttonView, isChecked) -> {
                prefs.edit().putBoolean(PREF_LONG_CLICK_STRAVA, isChecked).apply();
                updateSwitchText(sHolder.tvClickSub, isChecked);
            });

            // --- AUTO REFRESH TOGGLE ---
            boolean isAutoRefresh = prefs.getBoolean(PREF_ENABLE_AUTO_REFRESH, true);
            sHolder.switchAutoRefresh.setOnCheckedChangeListener(null);
            sHolder.switchAutoRefresh.setChecked(isAutoRefresh);
            sHolder.tilAutoRefresh.setVisibility(isAutoRefresh ? View.VISIBLE : View.GONE);

            sHolder.switchAutoRefresh.setOnCheckedChangeListener((buttonView, isChecked) -> {
                prefs.edit().putBoolean(PREF_ENABLE_AUTO_REFRESH, isChecked).apply();
                sHolder.tilAutoRefresh.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            });

            // --- AUTO REFRESH DURATION ---
            long savedDuration = prefs.getLong(PREF_CACHE_DURATION_HOURS, 48);
            sHolder.etAutoRefresh.setText(String.valueOf(savedDuration));

            sHolder.etAutoRefresh.setOnEditorActionListener((v, actionId, event) -> {
                String input = sHolder.etAutoRefresh.getText().toString().trim();
                if (!input.isEmpty()) {
                    try {
                        long hours = Long.parseLong(input);
                        prefs.edit().putLong(PREF_CACHE_DURATION_HOURS, hours).apply();
                    } catch (NumberFormatException e) {
                        // ignore
                    }
                }
                sHolder.etAutoRefresh.clearFocus();
                hideKeyboard(v);
                return true;
            });

        } else {
            UsageViewHolder uHolder = (UsageViewHolder) holder;

            ImageView imageView = uHolder.itemView.findViewById(R.id.ivSwipeLeft);
            // Start fade after 3 seconds
            imageView.postDelayed(() -> {
                // Create a fade-out animation lasting 2 seconds
                AlphaAnimation fadeOut = new AlphaAnimation(1.0f, 0.0f);
                fadeOut.setDuration(2000); // 2 seconds
                fadeOut.setFillAfter(true); // keep alpha at 0 after animation
                imageView.startAnimation(fadeOut);
            }, 4000);
            imageView.postDelayed(() -> imageView.setVisibility(View.GONE), 6000);
        }
    }

    private void saveUrl(TextInputEditText et, SharedPreferences prefs, View v) {
        String newUrl = et.getText().toString().trim();
        if (!newUrl.isEmpty()) {
            prefs.edit().putString(STRAVA_URL_KEY, newUrl).apply();
            et.clearFocus();
        }
        hideKeyboard(v);
    }

    private void hideKeyboard(View v) {
        InputMethodManager imm = (InputMethodManager) v.getContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
        }
    }

    private void updateStravaSubText(TextView tv, boolean isEnabled) {
        tv.setText(isEnabled ? "Strava feature enabled" : "Strava feature disabled");
    }

    // Helper method to update text
    private void updateSwitchText(TextView tv, boolean isStrava) {
        if (isStrava) {
            tv.setText("Long click opens Strava Profile");
        } else {
            tv.setText("Long click opens Strava Activities");
        }
    }

    @Override
    public int getItemCount() {
        return 2;
    }

    static class SettingsViewHolder extends RecyclerView.ViewHolder {
        // Strava Feature
        MaterialSwitch switchEnableStrava;
        TextView tvEnableStravaSub;
        LinearLayout stravaSettingsContainer;

        // Profile URL
        com.google.android.material.textfield.TextInputLayout tilUrl;
        TextInputEditText etUrl;

        // Click Config
        MaterialSwitch switchClick;
        TextView tvClickSub;

        // Auto Refresh
        MaterialSwitch switchAutoRefresh;
        com.google.android.material.textfield.TextInputLayout tilAutoRefresh;
        TextInputEditText etAutoRefresh;

        SettingsViewHolder(View v) {
            super(v);
            // Strava Feature
            switchEnableStrava = v.findViewById(R.id.switch_enablestrava);
            tvEnableStravaSub = v.findViewById(R.id.tvenablestrava_sub);
            stravaSettingsContainer = v.findViewById(R.id.strava_settings_container);

            // Profile URL
            tilUrl = v.findViewById(R.id.tilProfileUrl);
            etUrl = v.findViewById(R.id.etProfileUrl);

            // Click Config
            switchClick = v.findViewById(R.id.switch_longshortClick);
            tvClickSub = v.findViewById(R.id.tvClick_sub);

            // Auto Refresh
            switchAutoRefresh = v.findViewById(R.id.switch_enableautorefresh);
            tilAutoRefresh = v.findViewById(R.id.tilAutoRefresh);
            etAutoRefresh = v.findViewById(R.id.etAutoRefresh);
        }
    }

    static class UsageViewHolder extends RecyclerView.ViewHolder {
        UsageViewHolder(View itemView) {
            super(itemView);
        }
    }
}