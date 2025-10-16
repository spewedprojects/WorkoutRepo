package com.gratus.workoutrepo;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

public class EditorBottomSheet extends BottomSheetDialogFragment {

    public interface OnSaveListener {
        void onSave(String editedText, int adapterPosition);
    }

    private static final String ARG_DAY = "arg_day";
    private static final String ARG_FIELD = "arg_field";
    private static final String ARG_EDITABLE = "arg_editable";
    private static final String ARG_POSITION = "arg_position";

    private OnSaveListener onSaveListener;
    private String day, fieldKey, editableText;
    private int adapterPosition;

    public static EditorBottomSheet newInstance(String day, String fieldKey, String editableText, int adapterPosition) {
        EditorBottomSheet bs = new EditorBottomSheet();
        Bundle b = new Bundle();
        b.putString(ARG_DAY, day);
        b.putString(ARG_FIELD, fieldKey);
        b.putString(ARG_EDITABLE, editableText);
        b.putInt(ARG_POSITION, adapterPosition);
        bs.setArguments(b);
        return bs;
    }

    public void setOnSaveListener(OnSaveListener listener) {
        this.onSaveListener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle a = getArguments();
        if (a != null) {
            day = a.getString(ARG_DAY);
            fieldKey = a.getString(ARG_FIELD);
            editableText = a.getString(ARG_EDITABLE);
            adapterPosition = a.getInt(ARG_POSITION, 0);
        }
        setCancelable(true);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.bottom_sheet_editor, container, false);

        TextView title = v.findViewById(R.id.editorTitle);
        title.setText("Edit " + day + " " + fieldKey);

        TextInputEditText editText = v.findViewById(R.id.editorEditText);
        if (!TextUtils.isEmpty(editableText)) editText.setText(editableText);
        editText.setSelection(editText.getText().length());

        MaterialButton cancel = v.findViewById(R.id.cancelBtn);
        MaterialButton save = v.findViewById(R.id.saveBtn);

        cancel.setOnClickListener(view -> dismiss());

        save.setOnClickListener(view -> {
            String userText = editText.getText() == null ? "" : editText.getText().toString();
            // Return the raw editable text (no bullets) to caller via callback
            if (onSaveListener != null) {
                onSaveListener.onSave(userText, adapterPosition);
            }
            dismiss();
        });

        return v;
    }

    // convenience to show
    public void show(FragmentManager fm, String tag, OnSaveListener listener) {
        setOnSaveListener(listener);
        super.show(fm, tag);
    }
}

