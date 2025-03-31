package com.rescuereach.ui.common;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.rescuereach.R;

import java.util.ArrayList;
import java.util.List;

public class OTPInputView extends LinearLayout {
    private List<EditText> otpBoxes = new ArrayList<>(6);
    private String otp = "";
    private OTPCompletionListener completionListener;

    public interface OTPCompletionListener {
        void onOTPCompleted(String otp);
    }

    public OTPInputView(Context context) {
        super(context);
        init(context);
    }

    public OTPInputView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public OTPInputView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        LayoutInflater.from(context).inflate(R.layout.view_otp_input, this, true);

        // Find all EditText boxes
        otpBoxes.add(findViewById(R.id.otp_box_1));
        otpBoxes.add(findViewById(R.id.otp_box_2));
        otpBoxes.add(findViewById(R.id.otp_box_3));
        otpBoxes.add(findViewById(R.id.otp_box_4));
        otpBoxes.add(findViewById(R.id.otp_box_5));
        otpBoxes.add(findViewById(R.id.otp_box_6));

        // Set up text change listeners for each box
        for (int i = 0; i < otpBoxes.size(); i++) {
            final int currentIndex = i;
            EditText currentBox = otpBoxes.get(currentIndex);

            currentBox.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override
                public void afterTextChanged(Editable s) {
                    if (s.length() > 0) {
                        // Move to next box if available
                        if (currentIndex < otpBoxes.size() - 1) {
                            otpBoxes.get(currentIndex + 1).requestFocus();
                        } else {
                            // Hide keyboard if this is the last box
                            InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                            imm.hideSoftInputFromWindow(currentBox.getWindowToken(), 0);

                            // Check if OTP is complete
                            checkOTPCompletion();
                        }
                    }
                }
            });

            // Handle backspace for navigation
            currentBox.setOnKeyListener((v, keyCode, event) -> {
                if (keyCode == KeyEvent.KEYCODE_DEL && event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (currentBox.getText().toString().isEmpty() && currentIndex > 0) {
                        // If current box is empty and backspace is pressed, go to previous box
                        otpBoxes.get(currentIndex - 1).requestFocus();
                        otpBoxes.get(currentIndex - 1).setText("");
                        return true;
                    }
                }
                return false;
            });
        }
    }

    private void checkOTPCompletion() {
        StringBuilder otpBuilder = new StringBuilder();
        boolean isComplete = true;

        for (EditText box : otpBoxes) {
            String digit = box.getText().toString();
            if (digit.isEmpty()) {
                isComplete = false;
                break;
            }
            otpBuilder.append(digit);
        }

        if (isComplete) {
            otp = otpBuilder.toString();
            if (completionListener != null) {
                completionListener.onOTPCompleted(otp);
            }
        }
    }

    public void setOTPCompletionListener(OTPCompletionListener listener) {
        this.completionListener = listener;
    }

    public String getOTP() {
        StringBuilder otpBuilder = new StringBuilder();
        for (EditText box : otpBoxes) {
            otpBuilder.append(box.getText().toString());
        }
        return otpBuilder.toString();
    }

    public void setOTP(String otp) {
        if (otp == null || otp.length() != 6) {
            return;
        }

        for (int i = 0; i < 6; i++) {
            otpBoxes.get(i).setText(String.valueOf(otp.charAt(i)));
        }
    }

    public void clearOTP() {
        for (EditText box : otpBoxes) {
            box.setText("");
        }
        otpBoxes.get(0).requestFocus();
    }
}