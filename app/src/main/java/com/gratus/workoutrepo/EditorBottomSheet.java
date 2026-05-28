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
import androidx.core.view.WindowInsetsAnimationCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import java.util.List;
import android.view.KeyEvent;

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
    private static final String ARG_UNSAVED = "arg_unsaved";
    private static final String ARG_POSITION = "arg_position";

    private OnSaveListener onSaveListener;
    private String day, fieldKey, editableText, unsavedText;
    private int adapterPosition;
    private boolean intentionalDismiss = false;
    private TextInputEditText editText;

    public static EditorBottomSheet newInstance(String day, String fieldKey, String editableText, int adapterPosition) {
        return newInstance(day, fieldKey, editableText, editableText, adapterPosition);
    }

    public static EditorBottomSheet newInstance(String day, String fieldKey, String editableText, String unsavedText, int adapterPosition) {
        EditorBottomSheet bs = new EditorBottomSheet();
        Bundle b = new Bundle();
        b.putString(ARG_DAY, day);
        b.putString(ARG_FIELD, fieldKey);
        b.putString(ARG_EDITABLE, editableText);
        b.putString(ARG_UNSAVED, unsavedText);
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

        //setStyle(DialogFragment.STYLE_NORMAL, R.style.TransparentBottomSheetDialogTheme);
        Bundle a = getArguments();
        if (a != null) {
            day = a.getString(ARG_DAY);
            fieldKey = a.getString(ARG_FIELD);
            editableText = a.getString(ARG_EDITABLE);
            unsavedText = a.getString(ARG_UNSAVED);
            if (unsavedText == null) unsavedText = editableText;
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

        editText = view.findViewById(R.id.editorEditText);
        if (!TextUtils.isEmpty(unsavedText)) editText.setText(unsavedText);
        editText.setSelection(editText.getText().length());
        clearFocusOnKeyboardHide(editText, view);

        MaterialButton cancel = view.findViewById(R.id.cancelBtn);
        MaterialButton save = view.findViewById(R.id.saveBtn);

        cancel.setOnClickListener(v -> attemptExit());

        save.setOnClickListener(v -> {
            intentionalDismiss = true;
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
        
        dialog.setOnKeyListener((dialogInterface, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                attemptExit();
                return true;
            }
            return false;
        });

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
                }
            }
        });
        return dialog;
    }

    private void attemptExit() {
        String currentText = editText.getText() == null ? "" : editText.getText().toString();
        String originalText = editableText == null ? "" : editableText;
        if (!currentText.equals(originalText)) {
            Dialog confirmDialog = new Dialog(requireContext());
            confirmDialog.setContentView(R.layout.dialog_confirmation);
            confirmDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            TextView message = confirmDialog.findViewById(R.id.dialogMessage);
            message.setText("Exit without saving?");
            
            confirmDialog.findViewById(R.id.btnYes).setOnClickListener(v1 -> {
                intentionalDismiss = true;
                confirmDialog.dismiss();
                dismissAllowingStateLoss();
            });
            
            confirmDialog.findViewById(R.id.btnNo).setOnClickListener(v1 -> confirmDialog.dismiss());
            
            confirmDialog.show();
        } else {
            intentionalDismiss = true;
            dismissAllowingStateLoss();
        }
    }

    @Override
    public void onDismiss(@NonNull android.content.DialogInterface dialogInterface) {
        final androidx.fragment.app.FragmentActivity hostActivity = getActivity();
        final String fragmentTag = getTag();

        super.onDismiss(dialogInterface);

        if (!intentionalDismiss && hostActivity != null) {
            String currentText = editText.getText() == null ? "" : editText.getText().toString();
            String originalText = editableText == null ? "" : editableText;
            if (!currentText.equals(originalText)) {
                Dialog confirmDialog = new Dialog(hostActivity);
                confirmDialog.setContentView(R.layout.dialog_confirmation);
                confirmDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
                TextView message = confirmDialog.findViewById(R.id.dialogMessage);
                message.setText("Exit without saving?");
                
                confirmDialog.findViewById(R.id.btnYes).setOnClickListener(v1 -> confirmDialog.dismiss());
                
                confirmDialog.findViewById(R.id.btnNo).setOnClickListener(v1 -> {
                    confirmDialog.dismiss();
                    if (!hostActivity.isFinishing() && !hostActivity.isDestroyed()) {
                        EditorBottomSheet sheet = EditorBottomSheet.newInstance(day, fieldKey, editableText, currentText, adapterPosition);
                        sheet.setOnSaveListener(onSaveListener);
                        sheet.show(hostActivity.getSupportFragmentManager(), fragmentTag);
                    }
                });
                
                confirmDialog.show();
            }
        }
    }

    // 01/02/2026 - clear focus when keyboard not visible
    public static void clearFocusOnKeyboardHide(EditText editText, View rootView) {
        ViewCompat.setWindowInsetsAnimationCallback(editText, new WindowInsetsAnimationCompat.Callback(WindowInsetsAnimationCompat.Callback.DISPATCH_MODE_STOP) {
            @NonNull
            @Override
            public WindowInsetsCompat onProgress(@NonNull WindowInsetsCompat insets, @NonNull List<WindowInsetsAnimationCompat> runningAnimations) {
                return insets;
            }

            @Override
            public void onEnd(@NonNull WindowInsetsAnimationCompat animation) {
                super.onEnd(animation);
                WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(editText);
                if (insets != null && !insets.isVisible(WindowInsetsCompat.Type.ime())) {
                    editText.clearFocus();
                }
            }
        });
    }


    // convenience to show
    public void show(FragmentManager fm, String tag, OnSaveListener listener) {
        setOnSaveListener(listener);
        super.show(fm, tag);
    }
}

