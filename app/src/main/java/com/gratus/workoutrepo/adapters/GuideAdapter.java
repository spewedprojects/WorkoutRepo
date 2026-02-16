package com.gratus.workoutrepo.adapters;

import static com.gratus.workoutrepo.BaseActivity.PREFS_NAME;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
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

            // Default is true: Long click opens Strava URL, Short click opens Sheet
            boolean longClickStravaAction = prefs.getBoolean(PREF_LONG_CLICK_STRAVA, true);

            sHolder.switchClick.setOnCheckedChangeListener(null); // Avoid triggering listener during bind
            sHolder.switchClick.setChecked(longClickStravaAction);
            updateSwitchText(sHolder.tvClickSub, longClickStravaAction);

            sHolder.switchClick.setOnCheckedChangeListener((buttonView, isChecked) -> {
                prefs.edit().putBoolean(PREF_LONG_CLICK_STRAVA, isChecked).apply();
                updateSwitchText(sHolder.tvClickSub, isChecked);
            });

            sHolder.btnSave.setOnClickListener(v -> {
                String newUrl = sHolder.etUrl.getText().toString().trim();
                if (!newUrl.isEmpty()) {
                    prefs.edit().putString(STRAVA_URL_KEY, newUrl).apply();
                    sHolder.etUrl.clearFocus();
                }

                // hide the IME after saving
                InputMethodManager imm = (InputMethodManager) v.getContext()
                        .getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                }
            });

            sHolder.btnCancel.setOnClickListener(v -> {
                sHolder.etUrl.clearFocus();

                // hide the IME after cancelling
                InputMethodManager imm = (InputMethodManager) v.getContext()
                        .getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                }
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

    // Helper method to update text
    private void updateSwitchText(TextView tv, boolean isStrava) {
        if (isStrava) {
            tv.setText("Long click opens Strava Profile");
        } else {
            tv.setText("Long click opens Strava Activities");
        }
    }

    @Override
    public int getItemCount() { return 2; }

    static class SettingsViewHolder extends RecyclerView.ViewHolder {
        TextInputEditText etUrl;
        LinearLayout btnContainer;
        MaterialButton btnSave, btnCancel;
        MaterialSwitch switchClick; // Add this
        TextView tvClickSub;

        SettingsViewHolder(View v) {
            super(v);
            etUrl = v.findViewById(R.id.etProfileUrl);
            // Ensure you add this ID to the LinearLayout in settings_app.xml
            btnContainer = v.findViewById(R.id.actionBtn_container);
            btnContainer.setVisibility(View.GONE);
            btnSave = v.findViewById(R.id.btnSave);
            btnCancel = v.findViewById(R.id.btnCancel);
            switchClick = v.findViewById(R.id.switch_longshortClick);
            tvClickSub = v.findViewById(R.id.tvClick_sub);
        }
    }

    static class UsageViewHolder extends RecyclerView.ViewHolder {
        UsageViewHolder(View itemView) { super(itemView); }
    }
}