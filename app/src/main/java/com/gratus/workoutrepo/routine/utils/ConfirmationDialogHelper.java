package com.gratus.workoutrepo.routine.utils;

import android.app.Dialog;
import android.content.Context;
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
