package com.rescuereach.ui.common;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Window;
import android.widget.TextView;

import com.rescuereach.R;

public class LoadingDialog extends Dialog {
    private String message;
    private TextView textViewMessage;

    public LoadingDialog(Context context, String message) {
        super(context);
        this.message = message;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_loading);
        setCancelable(false);

        if (getWindow() != null) {
            getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        textViewMessage = findViewById(R.id.text_loading_message);
        updateMessage(message);
    }

    public void updateMessage(String message) {
        this.message = message;
        if (textViewMessage != null) {
            textViewMessage.setText(message);
        }
    }
}