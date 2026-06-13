package com.gratus.workoutrepo.utils;

import android.app.Dialog;
import android.content.Context;
import android.view.ViewGroup;
import android.widget.TextView;
import com.gratus.workoutrepo.R;

public class ConfirmationDialogHelper {

    public interface ConfirmationListener {
        void onYesClicked();
        default void onNoClicked() {}
    }

    public static void showConfirmationDialog(Context context, CharSequence messageText, ConfirmationListener listener) {
        Dialog confirmDialog = new Dialog(context);
        confirmDialog.setContentView(R.layout.dialog_confirmation);
        if (confirmDialog.getWindow() != null) {
            confirmDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            confirmDialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        TextView message = confirmDialog.findViewById(R.id.dialogMessage);
        if (message != null) {
            message.setText(messageText);
        }

        confirmDialog.findViewById(R.id.btnYes).setOnClickListener(v -> {
            listener.onYesClicked();
            confirmDialog.dismiss();
        });

        confirmDialog.findViewById(R.id.btnNo).setOnClickListener(v -> {
            listener.onNoClicked();
            confirmDialog.dismiss();
        });

        confirmDialog.show();
    }
}
