package com.gratus.workoutrepo.adapters;

import static com.gratus.workoutrepo.BaseActivity.PREFS_NAME;
import com.gratus.workoutrepo.BaseActivity;

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

            // --- SOURCE SELECTION & SYNC ---
            boolean isSyncEnabled = prefs.getBoolean(PREF_ENABLE_SYNC, false);
            String activeSource = prefs.getString(PREF_ACTIVE_SYNC_SOURCE, "");
            boolean isStravaActive = isSyncEnabled && SourceProvider.STRAVA.name().equals(activeSource);
            boolean isICUActive = isSyncEnabled && SourceProvider.INTERVALS_ICU.name().equals(activeSource);

            updateSourceButtons(sHolder, isSyncEnabled ? activeSource : "");
            updateSettingsVisibility(sHolder, isSyncEnabled ? activeSource : "");
            sHolder.globalSettingsContainer.setVisibility((isStravaActive || isICUActive) ? View.VISIBLE : View.GONE);

            sHolder.btnSelectStrava.setOnClickListener(v -> {
                boolean currentlyEnabled = prefs.getBoolean(PREF_ENABLE_SYNC, false);
                String currentSource = prefs.getString(PREF_ACTIVE_SYNC_SOURCE, "");
                boolean currentlyStrava = currentlyEnabled && SourceProvider.STRAVA.name().equals(currentSource);

                if (currentlyStrava) {
                    clearSyncSource(sHolder, prefs);
                } else {
                    setSyncSource(sHolder, prefs, SourceProvider.STRAVA.name());
                }
            });

            sHolder.btnSelectICU.setOnClickListener(v -> {
                boolean currentlyEnabled = prefs.getBoolean(PREF_ENABLE_SYNC, false);
                String currentSource = prefs.getString(PREF_ACTIVE_SYNC_SOURCE, "");
                boolean currentlyICU = currentlyEnabled && SourceProvider.INTERVALS_ICU.name().equals(currentSource);

                if (currentlyICU) {
                    clearSyncSource(sHolder, prefs);
                } else {
                    setSyncSource(sHolder, prefs, SourceProvider.INTERVALS_ICU.name());
                }
            });

            // --- STRAVA PROFILE URL ---
            String savedUrl = prefs.getString(STRAVA_URL_KEY, "https://www.strava.com/athletes/32298220");
            sHolder.etUrl.setText(savedUrl);
            sHolder.etUrl.setOnEditorActionListener((v, actionId, event) -> {
                saveUrl(sHolder.etUrl, prefs, v);
                return true;
            });
            sHolder.tilUrl.setEndIconOnClickListener(v -> saveUrl(sHolder.etUrl, prefs, sHolder.etUrl));

            // --- STRAVA CREDENTIALS ---
            if (sHolder.etClientId != null) {
                sHolder.etClientId.setText(TokenManager.getClientId(context));
            }
            if (sHolder.etClientSecret != null) {
                sHolder.etClientSecret.setText(TokenManager.getClientSecret(context));
            }
            if (sHolder.etRefreshToken != null) {
                sHolder.etRefreshToken.setText(TokenManager.getRefreshToken(context));
            }

            Runnable updateSaveClientBtnVisibility = () -> {
                boolean hasFocus = (sHolder.etClientId != null && sHolder.etClientId.hasFocus())
                        || (sHolder.etClientSecret != null && sHolder.etClientSecret.hasFocus())
                        || (sHolder.etRefreshToken != null && sHolder.etRefreshToken.hasFocus());
                WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(sHolder.itemView);
                boolean isImeVisible = insets != null && insets.isVisible(WindowInsetsCompat.Type.ime());
                if (sHolder.btnSaveClientDetails != null) {
                    sHolder.btnSaveClientDetails.setVisibility((hasFocus && isImeVisible) ? View.VISIBLE : View.GONE);
                }
            };

            View.OnFocusChangeListener clientFocusListener = (v, hasFocus) -> updateSaveClientBtnVisibility.run();
            if (sHolder.etClientId != null) sHolder.etClientId.setOnFocusChangeListener(clientFocusListener);
            if (sHolder.etClientSecret != null) sHolder.etClientSecret.setOnFocusChangeListener(clientFocusListener);
            if (sHolder.etRefreshToken != null) sHolder.etRefreshToken.setOnFocusChangeListener(clientFocusListener);

            if (sHolder.btnSaveClientDetails != null) {
                sHolder.btnSaveClientDetails.setOnClickListener(v -> saveStravaDetails(context, sHolder));
            }

            if (sHolder.etRefreshToken != null) {
                sHolder.etRefreshToken.setOnEditorActionListener((v, actionId, event) -> {
                    saveStravaDetails(context, sHolder);
                    return true;
                });
            }
            if (sHolder.tilRefreshToken != null) {
                sHolder.tilRefreshToken.setEndIconOnClickListener(v -> saveStravaDetails(context, sHolder));
            }

            if (sHolder.etClientId != null) {
                sHolder.etClientId.setOnEditorActionListener((v, actionId, event) -> {
                    saveStravaDetails(context, sHolder);
                    return true;
                });
            }
            if (sHolder.tilClientId != null) {
                sHolder.tilClientId.setEndIconOnClickListener(v -> saveStravaDetails(context, sHolder));
            }

            if (sHolder.etClientSecret != null) {
                sHolder.etClientSecret.setOnEditorActionListener((v, actionId, event) -> {
                    saveStravaDetails(context, sHolder);
                    return true;
                });
            }

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
                boolean hasIcuFocus = (sHolder.etAPIKeyIcu != null && sHolder.etAPIKeyIcu.hasFocus())
                        || (sHolder.etDurationIcu != null && sHolder.etDurationIcu.hasFocus());
                if (sHolder.btnSaveApiDetails != null) {
                    sHolder.btnSaveApiDetails.setVisibility((hasIcuFocus && isImeVisible) ? View.VISIBLE : View.GONE);
                }
                boolean hasClientFocus = (sHolder.etClientId != null && sHolder.etClientId.hasFocus())
                        || (sHolder.etClientSecret != null && sHolder.etClientSecret.hasFocus())
                        || (sHolder.etRefreshToken != null && sHolder.etRefreshToken.hasFocus());
                if (sHolder.btnSaveClientDetails != null) {
                    sHolder.btnSaveClientDetails.setVisibility((hasClientFocus && isImeVisible) ? View.VISIBLE : View.GONE);
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

            // --- DIALOG OVER BOTTOMSHEET TOGGLE ---
            if (sHolder.switchDialogBtmSheet != null) {
                boolean useDialog = prefs.getBoolean(BaseActivity.PREF_USE_DIALOG, false);
                sHolder.switchDialogBtmSheet.setOnCheckedChangeListener(null);
                sHolder.switchDialogBtmSheet.setChecked(useDialog);
                sHolder.switchDialogBtmSheet.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    prefs.edit().putBoolean(BaseActivity.PREF_USE_DIALOG, isChecked).apply();
                });
            }

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
        prefs.edit()
                .putBoolean(PREF_ENABLE_SYNC, true)
                .putString(PREF_ACTIVE_SYNC_SOURCE, source)
                .apply();
        sHolder.globalSettingsContainer.setVisibility(View.VISIBLE);
        updateSourceButtons(sHolder, source);
        updateSettingsVisibility(sHolder, source);
        updateSwitchText(sHolder.tvClickSub, prefs.getBoolean(PREF_LONG_CLICK_STRAVA, true), source);
    }

    private void clearSyncSource(SettingsViewHolder sHolder, SharedPreferences prefs) {
        prefs.edit()
                .putBoolean(PREF_ENABLE_SYNC, false)
                .putString(PREF_ACTIVE_SYNC_SOURCE, "")
                .apply();
        sHolder.btnSelectStrava.setSelected(false);
        sHolder.btnSelectICU.setSelected(false);
        sHolder.globalSettingsContainer.setVisibility(View.GONE);
    }

    private void updateSourceButtons(SettingsViewHolder sHolder, String source) {
        SharedPreferences prefs = sHolder.itemView.getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean isSyncEnabled = prefs.getBoolean(PREF_ENABLE_SYNC, false);
        boolean isStrava = isSyncEnabled && SourceProvider.STRAVA.name().equals(source);
        boolean isICU = isSyncEnabled && SourceProvider.INTERVALS_ICU.name().equals(source);
        
        sHolder.btnSelectStrava.setSelected(isStrava);
        sHolder.btnSelectICU.setSelected(isICU);
    }

    private void updateSettingsVisibility(SettingsViewHolder sHolder, String source) {
        SharedPreferences prefs = sHolder.itemView.getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean isSyncEnabled = prefs.getBoolean(PREF_ENABLE_SYNC, false);
        boolean isStrava = isSyncEnabled && SourceProvider.STRAVA.name().equals(source);
        boolean isICU = isSyncEnabled && SourceProvider.INTERVALS_ICU.name().equals(source);
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

    private void confirmCredentialChange(Context context, SettingsViewHolder sHolder, boolean isStravaSource, Runnable onConfirm) {
        if (!com.gratus.workoutrepo.archive.data.ActivityArchiveManager.INSTANCE.getActivities(context).isEmpty()) {
            Dialog dialog = new Dialog(context);
            dialog.setContentView(R.layout.dialog_athlete_warning);
            if (dialog.getWindow() != null) {
                dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            }

            MaterialButton btnExportFirst = dialog.findViewById(R.id.btnExportFirst);
            MaterialButton btnSameAthlete = dialog.findViewById(R.id.btnSameAthlete);
            MaterialButton btnCancel = dialog.findViewById(R.id.btnCancel);

            if (btnSameAthlete != null) {
                int colorRes = isStravaSource ? R.color.strava_color : R.color.intervals_icu_color;
                btnSameAthlete.setBackgroundTintList(androidx.core.content.ContextCompat.getColorStateList(context, colorRes));
                btnSameAthlete.setOnClickListener(v -> {
                    dialog.dismiss();
                    onConfirm.run();
                });
            }

            if (btnExportFirst != null) {
                btnExportFirst.setOnClickListener(v -> {
                    dialog.dismiss();
                    if (sHolder.btnExport != null) {
                        sHolder.btnExport.performClick();
                    }
                    onConfirm.run();
                });
            }

            if (btnCancel != null) {
                btnCancel.setOnClickListener(v -> dialog.dismiss());
            }

            dialog.show();
        } else {
            onConfirm.run();
        }
    }

    private void saveStravaDetails(Context context, SettingsViewHolder sHolder) {
        String clientId = sHolder.etClientId != null && sHolder.etClientId.getText() != null ? sHolder.etClientId.getText().toString().trim() : "";
        String clientSecret = sHolder.etClientSecret != null && sHolder.etClientSecret.getText() != null ? sHolder.etClientSecret.getText().toString().trim() : "";
        String refreshToken = sHolder.etRefreshToken != null && sHolder.etRefreshToken.getText() != null ? sHolder.etRefreshToken.getText().toString().trim() : "";

        String existingId = TokenManager.getClientId(context);
        String existingSecret = TokenManager.getClientSecret(context);
        String existingRefresh = TokenManager.getRefreshToken(context);

        boolean isChanged = !clientId.equals(existingId) || !clientSecret.equals(existingSecret) || !refreshToken.equals(existingRefresh);

        Runnable performSave = () -> {
            TokenManager.saveCredentials(context, clientId, clientSecret, refreshToken);
            if (sHolder.etClientId != null) sHolder.etClientId.clearFocus();
            if (sHolder.etClientSecret != null) sHolder.etClientSecret.clearFocus();
            if (sHolder.etRefreshToken != null) sHolder.etRefreshToken.clearFocus();
            hideKeyboard(sHolder.itemView);
            if (sHolder.btnSaveClientDetails != null) {
                sHolder.btnSaveClientDetails.setVisibility(View.GONE);
            }
        };

        if (isChanged) {
            confirmCredentialChange(context, sHolder, true, performSave);
        } else {
            performSave.run();
        }
    }

    private void saveIntervalsDetails(Context context, SharedPreferences prefs, SettingsViewHolder sHolder) {
        String newKey = sHolder.etAPIKeyIcu != null && sHolder.etAPIKeyIcu.getText() != null ? sHolder.etAPIKeyIcu.getText().toString().trim() : "";
        String existingKey = IntervalsRepository.INSTANCE.getApiKey(context);
        boolean isKeyChanged = !newKey.isEmpty() && (existingKey == null || !newKey.equals(existingKey));

        Runnable performSave = () -> {
            if (sHolder.etAPIKeyIcu != null) {
                if (newKey.length() >= 20) {
                    IntervalsRepository.INSTANCE.saveApiKey(context, newKey);
                } else if (!newKey.isEmpty()) {
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
        };

        if (isKeyChanged) {
            confirmCredentialChange(context, sHolder, false, performSave);
        } else {
            performSave.run();
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
        LinearLayout globalSettingsContainer;

        LinearLayout chooseBtns;
        View btnSelectStrava, btnSelectICU;
        
        LinearLayout stravaURLSetting;
        com.google.android.material.textfield.TextInputLayout tilUrl;
        TextInputEditText etUrl;

        com.google.android.material.textfield.TextInputLayout tilClientId;
        TextInputEditText etClientId;
        com.google.android.material.textfield.TextInputLayout tilClientSecret;
        TextInputEditText etClientSecret;
        com.google.android.material.textfield.TextInputLayout tilRefreshToken;
        TextInputEditText etRefreshToken;
        MaterialButton btnSaveClientDetails;

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

        MaterialSwitch switchDialogBtmSheet;

        ImageButton btnExport, btnImport;

        SettingsViewHolder(View v) {
            super(v);
            globalSettingsContainer = v.findViewById(R.id.global_settings_container);

            chooseBtns = v.findViewById(R.id.chooseBtns);
            btnSelectStrava = v.findViewById(R.id.selectStravaNew);
            if (btnSelectStrava != null) btnSelectStrava.setEnabled(true);
            btnSelectICU = v.findViewById(R.id.selectICUNew);
            if (btnSelectICU != null) btnSelectICU.setEnabled(true);

            stravaURLSetting = v.findViewById(R.id.stravaURL_setting);
            tilUrl = v.findViewById(R.id.tilProfileUrl);
            etUrl = v.findViewById(R.id.etProfileUrl);

            tilClientId = v.findViewById(R.id.tilClientId);
            etClientId = v.findViewById(R.id.etClientId);
            tilClientSecret = v.findViewById(R.id.tilClientSecret);
            etClientSecret = v.findViewById(R.id.etClientSecret);
            tilRefreshToken = v.findViewById(R.id.tilRefreshToken);
            etRefreshToken = v.findViewById(R.id.etRefreshToken);
            btnSaveClientDetails = v.findViewById(R.id.saveClientDetails);

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

            switchDialogBtmSheet = v.findViewById(R.id.switch_DialogBtmSheet);

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