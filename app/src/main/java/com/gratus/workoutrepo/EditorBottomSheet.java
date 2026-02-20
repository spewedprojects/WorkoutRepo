package com.gratus.workoutrepo;

import android.app.Dialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import com.gratus.workoutrepo.R;

import com.google.android.material.bottomsheet.BottomSheetDialog;
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

        setStyle(DialogFragment.STYLE_NORMAL, R.style.TransparentBottomSheetDialogTheme);
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
        View view = inflater.inflate(R.layout.bottomsheet_editor, container, false);

        TextView title = view.findViewById(R.id.editorTitle);
        title.setText("Edit " + day + " " + fieldKey);

        TextInputEditText editText = view.findViewById(R.id.editorEditText);
        if (!TextUtils.isEmpty(editableText)) editText.setText(editableText);
        editText.setSelection(editText.getText().length());
        clearFocusOnKeyboardHide(editText, view);

        MaterialButton cancel = view.findViewById(R.id.cancelBtn);
        MaterialButton save = view.findViewById(R.id.saveBtn);

        cancel.setOnClickListener(v -> dismiss());

        save.setOnClickListener(v -> {
            String userText = editText.getText() == null ? "" : editText.getText().toString();
            // Return the raw editable text (no bullets) to caller via callback
            if (onSaveListener != null) {
                onSaveListener.onSave(userText, adapterPosition);
            }
            dismiss();
        });

        return view;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        dialog.setOnShowListener(dialogInterface -> {
            BottomSheetDialog bottomSheetDialog = (BottomSheetDialog) dialogInterface;
            View bottomSheetInternal = bottomSheetDialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);

            if (bottomSheetInternal != null) {
                // 1. Remove the default background color/drawable of the internal container
                bottomSheetInternal.setBackgroundResource(android.R.color.transparent);

                // 2. Disable clipping on the parent container to allow the shadow to show
                if (bottomSheetInternal.getParent() instanceof ViewGroup parent) {
                    parent.setClipChildren(false);
                    parent.setClipToPadding(false);
                };
            }
        });
        return dialog;
    }

    // 01/02/2026 - clear focus when keyboard not visible
    private static void clearFocusOnKeyboardHide(EditText editText, View rootView) {
        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {
            boolean imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime());
            if (!imeVisible) {
                editText.clearFocus();
            }
            return insets;
        });
    }


    // convenience to show
    public void show(FragmentManager fm, String tag, OnSaveListener listener) {
        setOnSaveListener(listener);
        super.show(fm, tag);
    }
}

