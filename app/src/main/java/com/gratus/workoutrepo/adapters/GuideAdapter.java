package com.gratus.workoutrepo.adapters;

import static com.gratus.workoutrepo.BaseActivity.PREFS_NAME;

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.gratus.workoutrepo.R;
import com.gratus.workoutrepo.archive.model.SourceProvider;
import com.gratus.workoutrepo.intervalsicu.repository.IntervalsRepository;
import com.gratus.workoutrepo.strava.repository.TokenManager;

public class GuideAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_SETTINGS = 0;
    private static final int TYPE_USAGE = 1;
    // Keys from BaseActivity
    private static final String STRAVA_URL_KEY = "CustomStravaUrl";
    private static final String PREF_LONG_CLICK_STRAVA = "StravaButtonLongClickAction";
    // New Keys
    private static final String PREF_ENABLE_SYNC = "EnableStravaFeature"; // Reusing key for backward compat
    public static final String PREF_ACTIVE_SYNC_SOURCE = "ActiveSyncSource"; // "STRAVA" or "INTERVALS_ICU"
    private static final String PREF_ENABLE_AUTO_REFRESH = "EnableAutoRefresh";
    private static final String PREF_CACHE_DURATION_HOURS = "CacheDurationHours";

    public interface OnArchiveInteractionListener {
        void onExportClicked();
        void onImportClicked();
    }

    private final OnArchiveInteractionListener listener;

    public GuideAdapter(OnArchiveInteractionListener listener) {
        this.listener = listener;
    }

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
            Context context = sHolder.itemView.getContext();
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

            // --- SYNC TOGGLE ---
            boolean isSyncEnabled = prefs.getBoolean(PREF_ENABLE_SYNC, false);
            String activeSource = prefs.getString(PREF_ACTIVE_SYNC_SOURCE, "");
            
            sHolder.switchEnableSync.setOnCheckedChangeListener(null);
            sHolder.switchEnableSync.setChecked(isSyncEnabled);
            updateSyncSubText(sHolder.tvEnableSyncSub, isSyncEnabled);
            sHolder.chooseBtns.setVisibility(isSyncEnabled ? View.VISIBLE : View.GONE);
            sHolder.globalSettingsContainer.setVisibility((isSyncEnabled && !activeSource.isEmpty()) ? View.VISIBLE : View.GONE);

            sHolder.switchEnableSync.setOnCheckedChangeListener((buttonView, isChecked) -> {
                prefs.edit().putBoolean(PREF_ENABLE_SYNC, isChecked).apply();
                updateSyncSubText(sHolder.tvEnableSyncSub, isChecked);
                sHolder.chooseBtns.setVisibility(isChecked ? View.VISIBLE : View.GONE);
                String currentSource = prefs.getString(PREF_ACTIVE_SYNC_SOURCE, "");
                sHolder.globalSettingsContainer.setVisibility((isChecked && !currentSource.isEmpty()) ? View.VISIBLE : View.GONE);
            });

            // --- SOURCE SELECTION ---
            updateSourceButtons(sHolder, activeSource);
            updateSettingsVisibility(sHolder, activeSource);

            sHolder.btnSelectStrava.setOnClickListener(v -> {
                showKeywordDialog(sHolder, prefs);
            });

            sHolder.btnSelectICU.setOnClickListener(v -> {
                setSyncSource(sHolder, prefs, SourceProvider.INTERVALS_ICU.name());
            });

            // --- STRAVA PROFILE URL ---
            String savedUrl = prefs.getString(STRAVA_URL_KEY, "https://www.strava.com/athletes/32298220");
            sHolder.etUrl.setText(savedUrl);
            sHolder.etUrl.setOnEditorActionListener((v, actionId, event) -> {
                saveUrl(sHolder.etUrl, prefs, v);
                return true;
            });
            sHolder.tilUrl.setEndIconOnClickListener(v -> saveUrl(sHolder.etUrl, prefs, sHolder.etUrl));

            // --- INTERVALS.ICU API KEY & DURATION ---
            String savedApiKey = IntervalsRepository.INSTANCE.getApiKey(context);
            if (savedApiKey != null) {
                sHolder.etAPIKeyIcu.setText(savedApiKey);
            }

            int savedDurationYears = prefs.getInt("IntervalsDurationYears", 1);
            if (sHolder.etDurationIcu != null) {
                sHolder.etDurationIcu.setText(String.valueOf(savedDurationYears));
            }

            Runnable updateSaveBtnVisibility = () -> {
                boolean hasFocus = (sHolder.etAPIKeyIcu != null && sHolder.etAPIKeyIcu.hasFocus())
                        || (sHolder.etDurationIcu != null && sHolder.etDurationIcu.hasFocus());
                WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(sHolder.itemView);
                boolean isImeVisible = insets != null && insets.isVisible(WindowInsetsCompat.Type.ime());
                if (sHolder.btnSaveApiDetails != null) {
                    sHolder.btnSaveApiDetails.setVisibility((hasFocus && isImeVisible) ? View.VISIBLE : View.GONE);
                }
            };

            ViewCompat.setOnApplyWindowInsetsListener(sHolder.itemView, (v, insets) -> {
                boolean isImeVisible = insets.isVisible(WindowInsetsCompat.Type.ime());
                boolean hasFocus = (sHolder.etAPIKeyIcu != null && sHolder.etAPIKeyIcu.hasFocus())
                        || (sHolder.etDurationIcu != null && sHolder.etDurationIcu.hasFocus());
                if (sHolder.btnSaveApiDetails != null) {
                    sHolder.btnSaveApiDetails.setVisibility((hasFocus && isImeVisible) ? View.VISIBLE : View.GONE);
                }
                return insets;
            });

            View.OnFocusChangeListener focusListener = (v, hasFocus) -> updateSaveBtnVisibility.run();
            if (sHolder.etAPIKeyIcu != null) sHolder.etAPIKeyIcu.setOnFocusChangeListener(focusListener);
            if (sHolder.etDurationIcu != null) sHolder.etDurationIcu.setOnFocusChangeListener(focusListener);

            if (sHolder.btnSaveApiDetails != null) {
                sHolder.btnSaveApiDetails.setOnClickListener(v -> saveIntervalsDetails(context, prefs, sHolder));
            }

            if (sHolder.etAPIKeyIcu != null) {
                sHolder.etAPIKeyIcu.setOnEditorActionListener((v, actionId, event) -> {
                    saveIntervalsDetails(context, prefs, sHolder);
                    return true;
                });
            }
            if (sHolder.tilAPIKeyIcu != null) {
                sHolder.tilAPIKeyIcu.setEndIconOnClickListener(v -> saveIntervalsDetails(context, prefs, sHolder));
            }

            if (sHolder.etDurationIcu != null) {
                sHolder.etDurationIcu.setOnEditorActionListener((v, actionId, event) -> {
                    saveIntervalsDetails(context, prefs, sHolder);
                    return true;
                });
            }
            if (sHolder.tilDurationIcu != null) {
                sHolder.tilDurationIcu.setEndIconOnClickListener(v -> saveIntervalsDetails(context, prefs, sHolder));
            }

            // --- LONG/SHORT CLICK TOGGLE ---
            boolean longClickAction = prefs.getBoolean(PREF_LONG_CLICK_STRAVA, true);
            sHolder.switchClick.setOnCheckedChangeListener(null);
            sHolder.switchClick.setChecked(longClickAction);
            updateSwitchText(sHolder.tvClickSub, longClickAction, activeSource);

            sHolder.switchClick.setOnCheckedChangeListener((buttonView, isChecked) -> {
                prefs.edit().putBoolean(PREF_LONG_CLICK_STRAVA, isChecked).apply();
                updateSwitchText(sHolder.tvClickSub, isChecked, prefs.getString(PREF_ACTIVE_SYNC_SOURCE, SourceProvider.STRAVA.name()));
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
                    } catch (NumberFormatException e) { }
                }
                sHolder.etAutoRefresh.clearFocus();
                hideKeyboard(v);
                return true;
            });

            sHolder.btnExport.setOnClickListener(v -> { if (listener != null) listener.onExportClicked(); });
            sHolder.btnImport.setOnClickListener(v -> { if (listener != null) listener.onImportClicked(); });

        } else {
            UsageViewHolder uHolder = (UsageViewHolder) holder;
            ImageView imageView = uHolder.itemView.findViewById(R.id.ivSwipeLeft);
            imageView.postDelayed(() -> {
                AlphaAnimation fadeOut = new AlphaAnimation(1.0f, 0.0f);
                fadeOut.setDuration(2000);
                fadeOut.setFillAfter(true);
                imageView.startAnimation(fadeOut);
            }, 8000);
            imageView.postDelayed(() -> imageView.setVisibility(View.GONE), 10000);
        }
    }

    private void setSyncSource(SettingsViewHolder sHolder, SharedPreferences prefs, String source) {
        prefs.edit().putString(PREF_ACTIVE_SYNC_SOURCE, source).apply();
        sHolder.globalSettingsContainer.setVisibility(View.VISIBLE);
        updateSourceButtons(sHolder, source);
        updateSettingsVisibility(sHolder, source);
        updateSwitchText(sHolder.tvClickSub, prefs.getBoolean(PREF_LONG_CLICK_STRAVA, true), source);
    }

    private void updateSourceButtons(SettingsViewHolder sHolder, String source) {
        boolean isStrava = SourceProvider.STRAVA.name().equals(source);
        boolean isICU = SourceProvider.INTERVALS_ICU.name().equals(source);
        
        sHolder.btnSelectStrava.setSelected(isStrava);
        sHolder.btnSelectICU.setSelected(isICU);
    }

    private void updateSettingsVisibility(SettingsViewHolder sHolder, String source) {
        boolean isStrava = SourceProvider.STRAVA.name().equals(source);
        boolean isICU = SourceProvider.INTERVALS_ICU.name().equals(source);
        sHolder.stravaURLSetting.setVisibility(isStrava ? View.VISIBLE : View.GONE);
        sHolder.intervalsICUSetting.setVisibility(isICU ? View.VISIBLE : View.GONE);
        
        if (isStrava) {
            sHolder.tvConfigBtn.setText("Configure Strava button");
        } else if (isICU) {
            sHolder.tvConfigBtn.setText("Configure Intervals.icu button");
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

    private void saveApiKey(Context context, TextInputEditText et, View v) {
        String key = et.getText().toString().trim();
        if (key.length() >= 20) {
            IntervalsRepository.INSTANCE.saveApiKey(context, key);
            et.clearFocus();
        } else {
            et.setError("Invalid API Key");
        }
        hideKeyboard(v);
    }

    private void saveIntervalsDetails(Context context, SharedPreferences prefs, SettingsViewHolder sHolder) {
        if (sHolder.etAPIKeyIcu != null) {
            String key = sHolder.etAPIKeyIcu.getText() != null ? sHolder.etAPIKeyIcu.getText().toString().trim() : "";
            if (key.length() >= 20) {
                IntervalsRepository.INSTANCE.saveApiKey(context, key);
            } else if (!key.isEmpty()) {
                sHolder.etAPIKeyIcu.setError("Invalid API Key");
            }
        }
        if (sHolder.etDurationIcu != null) {
            String durationStr = sHolder.etDurationIcu.getText() != null ? sHolder.etDurationIcu.getText().toString().trim() : "";
            if (!durationStr.isEmpty()) {
                try {
                    int years = Integer.parseInt(durationStr);
                    if (years > 0) {
                        prefs.edit().putInt("IntervalsDurationYears", years).apply();
                    }
                } catch (NumberFormatException ignored) {}
            }
        }
        if (sHolder.etAPIKeyIcu != null) sHolder.etAPIKeyIcu.clearFocus();
        if (sHolder.etDurationIcu != null) sHolder.etDurationIcu.clearFocus();
        hideKeyboard(sHolder.itemView);
        if (sHolder.btnSaveApiDetails != null) {
            sHolder.btnSaveApiDetails.setVisibility(View.GONE);
        }
    }

    private void hideKeyboard(View v) {
        InputMethodManager imm = (InputMethodManager) v.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
        }
    }

    private void updateSyncSubText(TextView tv, boolean isEnabled) {
        tv.setText(isEnabled ? "Sync enabled" : "Sync disabled");
    }

    private void showKeywordDialog(SettingsViewHolder sHolder, SharedPreferences prefs) {
        Context context = sHolder.itemView.getContext();
        Dialog dialog = new Dialog(context);
        dialog.setContentView(R.layout.dialog_stravakeyword);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        MaterialButton btnCancel = dialog.findViewById(R.id.btnCancel);
        MaterialButton btnProceed = dialog.findViewById(R.id.btnProceed);
        com.google.android.material.textfield.TextInputLayout tilKeyword = dialog.findViewById(R.id.tilKeyword);
        TextInputEditText etKeyword = dialog.findViewById(R.id.etKeyword);

        if (etKeyword != null && tilKeyword != null) {
            etKeyword.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) { tilKeyword.setError(null); }
                @Override public void afterTextChanged(android.text.Editable s) {}
            });
        }

        if (btnProceed != null) {
            btnProceed.setOnClickListener(v -> {
                String input = (etKeyword != null && etKeyword.getText() != null) ? etKeyword.getText().toString().trim() : "";
                if (TokenManager.INSTANCE.getAccessKeyword().equals(input)) {
                    setSyncSource(sHolder, prefs, SourceProvider.STRAVA.name());
                    if (etKeyword != null) hideKeyboard(etKeyword);
                    dialog.dismiss();
                } else {
                    if (tilKeyword != null) tilKeyword.setError("Incorrect keyword");
                }
            });
        }

        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> {
                if (etKeyword != null) hideKeyboard(etKeyword);
                dialog.dismiss();
            });
        }

        dialog.show();
    }

    private void updateSwitchText(TextView tv, boolean isLongClickArchive, String activeSource) {
        String sourceName = SourceProvider.STRAVA.name().equals(activeSource) ? "Strava" : "Intervals.icu";
        if (isLongClickArchive) {
            tv.setText("Long click opens " + sourceName + " Archive");
        } else {
            tv.setText("Long click opens " + sourceName + " Activities");
        }
    }

    @Override
    public int getItemCount() {
        return 2;
    }

    static class SettingsViewHolder extends RecyclerView.ViewHolder {
        MaterialSwitch switchEnableSync;
        TextView tvEnableSyncSub;
        LinearLayout globalSettingsContainer;

        LinearLayout chooseBtns;
        MaterialButton btnSelectStrava, btnSelectICU;
        
        LinearLayout stravaURLSetting;
        com.google.android.material.textfield.TextInputLayout tilUrl;
        TextInputEditText etUrl;

        LinearLayout intervalsICUSetting;
        com.google.android.material.textfield.TextInputLayout tilAPIKeyIcu;
        TextInputEditText etAPIKeyIcu;
        com.google.android.material.textfield.TextInputLayout tilDurationIcu;
        TextInputEditText etDurationIcu;
        MaterialButton btnSaveApiDetails;

        TextView tvConfigBtn;
        MaterialSwitch switchClick;
        TextView tvClickSub;

        MaterialSwitch switchAutoRefresh;
        com.google.android.material.textfield.TextInputLayout tilAutoRefresh;
        TextInputEditText etAutoRefresh;

        ImageButton btnExport, btnImport;

        SettingsViewHolder(View v) {
            super(v);
            switchEnableSync = v.findViewById(R.id.switch_enablestrava);
            tvEnableSyncSub = v.findViewById(R.id.tvenablestrava_sub);
            globalSettingsContainer = v.findViewById(R.id.global_settings_container);

            chooseBtns = v.findViewById(R.id.chooseBtns);
            btnSelectStrava = v.findViewById(R.id.selectStrava);
            btnSelectStrava.setEnabled(true); // Must be enabled now
            btnSelectICU = v.findViewById(R.id.selectICU);

            stravaURLSetting = v.findViewById(R.id.stravaURL_setting);
            tilUrl = v.findViewById(R.id.tilProfileUrl);
            etUrl = v.findViewById(R.id.etProfileUrl);

            intervalsICUSetting = v.findViewById(R.id.intervalsICU_setting);
            tilAPIKeyIcu = v.findViewById(R.id.tilAPIKey_icu);
            etAPIKeyIcu = v.findViewById(R.id.etAPIKey_icu);
            tilDurationIcu = v.findViewById(R.id.tilDuration_icu);
            etDurationIcu = v.findViewById(R.id.etDuration_icu);
            btnSaveApiDetails = v.findViewById(R.id.saveAPIDetails);

            tvConfigBtn = v.findViewById(R.id.tvConfigBtn);
            switchClick = v.findViewById(R.id.switch_longshortClick);
            tvClickSub = v.findViewById(R.id.tvClick_sub);

            switchAutoRefresh = v.findViewById(R.id.switch_enableautorefresh);
            tilAutoRefresh = v.findViewById(R.id.tilAutoRefresh);
            etAutoRefresh = v.findViewById(R.id.etAutoRefresh);

            btnExport = v.findViewById(R.id.Exp_S_Data);
            btnImport = v.findViewById(R.id.Imp_S_Data);
        }
    }

    static class UsageViewHolder extends RecyclerView.ViewHolder {
        UsageViewHolder(View itemView) {
            super(itemView);
        }
    }
}