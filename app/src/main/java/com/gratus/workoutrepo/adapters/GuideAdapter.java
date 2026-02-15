package com.gratus.workoutrepo.adapters;

import static com.gratus.workoutrepo.BaseActivity.PREFS_NAME;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.gratus.workoutrepo.R;

public class GuideAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_SETTINGS = 0;
    private static final int TYPE_USAGE = 1;
    // Keys from BaseActivity
    private static final String STRAVA_URL_KEY = "CustomStravaUrl";

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
        if (holder instanceof SettingsViewHolder) {
            SettingsViewHolder sHolder = (SettingsViewHolder) holder;
            SharedPreferences prefs = sHolder.itemView.getContext()
                    .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

            // Load existing URL
            String savedUrl = prefs.getString(STRAVA_URL_KEY, "https://www.strava.com/athletes/32298220");
            sHolder.etUrl.setText(savedUrl);

            sHolder.etUrl.setOnFocusChangeListener((v, hasFocus) -> {
                // FIX: Before changing visibility, set a minHeight to the root
                // so the RecyclerView doesn't collapse
                if (sHolder.itemView.getHeight() > 0) {
                    sHolder.itemView.setMinimumHeight(sHolder.itemView.getHeight());
                }

                sHolder.btnContainer.setVisibility(hasFocus ? View.VISIBLE : View.GONE);
            });

            sHolder.btnSave.setOnClickListener(v -> {
                String newUrl = sHolder.etUrl.getText().toString().trim();
                if (!newUrl.isEmpty()) {
                    prefs.edit().putString(STRAVA_URL_KEY, newUrl).apply();
                    sHolder.etUrl.clearFocus();
                }
            });

            sHolder.btnCancel.setOnClickListener(v -> {
                sHolder.etUrl.clearFocus();
                // hide the IME
                InputMethodManager imm = (InputMethodManager) v.getContext()
                        .getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                }

            });
        }
    }

    @Override
    public int getItemCount() { return 2; }

    static class SettingsViewHolder extends RecyclerView.ViewHolder {
        TextInputEditText etUrl;
        LinearLayout btnContainer;
        MaterialButton btnSave, btnCancel;

        SettingsViewHolder(View v) {
            super(v);
            etUrl = v.findViewById(R.id.etProfileUrl);
            // Ensure you add this ID to the LinearLayout in settings_app.xml
            btnContainer = v.findViewById(R.id.actionBtn_container);
            btnContainer.setVisibility(View.GONE);
            btnSave = v.findViewById(R.id.btnSave);
            btnCancel = v.findViewById(R.id.btnCancel);
        }
    }

    static class UsageViewHolder extends RecyclerView.ViewHolder {
        UsageViewHolder(View itemView) { super(itemView); }
    }
}