package com.rescuereach.service.auth;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.google.android.gms.auth.api.phone.SmsRetriever;
import com.google.android.gms.auth.api.phone.SmsRetrieverClient;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SMSRetrieverHelper {
    private static final String TAG = "SMSRetrieverHelper";
    private final Activity activity;
    private SMSRetrievedListener listener;
    private BroadcastReceiver smsReceiver;

    public interface SMSRetrievedListener {
        void onSMSRetrieved(String otp);
        void onSMSRetrievalFailed(Exception e);
    }

    public SMSRetrieverHelper(Activity activity) {
        this.activity = activity;
    }

    public void setSMSRetrievedListener(SMSRetrievedListener listener) {
        this.listener = listener;
    }

    public void startSMSRetriever() {
        SmsRetrieverClient client = SmsRetriever.getClient(activity);
        Task<Void> task = client.startSmsRetriever();

        task.addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void aVoid) {
                Log.d(TAG, "SMS Retriever started successfully");
                registerBroadcastReceiver();
            }
        });

        task.addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Log.e(TAG, "Failed to start SMS Retriever", e);
                if (listener != null) {
                    listener.onSMSRetrievalFailed(e);
                }
            }
        });
    }

    private void registerBroadcastReceiver() {
        smsReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (SmsRetriever.SMS_RETRIEVED_ACTION.equals(intent.getAction())) {
                    Bundle extras = intent.getExtras();
                    if (extras == null) {
                        Log.e(TAG, "SMS retrieved extras were null");
                        return;
                    }

                    Status status = (Status) extras.get(SmsRetriever.EXTRA_STATUS);
                    if (status == null) {
                        Log.e(TAG, "SMS retrieved status was null");
                        return;
                    }

                    switch (status.getStatusCode()) {
                        case CommonStatusCodes.SUCCESS:
                            // Get SMS message contents
                            String message = (String) extras.get(SmsRetriever.EXTRA_SMS_MESSAGE);
                            Log.d(TAG, "SMS retrieved: " + message);

                            if (message != null) {
                                String otp = extractOTPFromMessage(message);
                                Log.d(TAG, "Extracted OTP: " + otp);

                                if (otp != null && listener != null) {
                                    listener.onSMSRetrieved(otp);
                                } else if (listener != null) {
                                    listener.onSMSRetrievalFailed(
                                            new Exception("Could not extract OTP from message"));
                                }
                            } else if (listener != null) {
                                listener.onSMSRetrievalFailed(new Exception("Retrieved SMS was empty"));
                            }
                            break;

                        case CommonStatusCodes.TIMEOUT:
                            // Timeout occurred
                            Log.e(TAG, "SMS retrieval timed out");
                            if (listener != null) {
                                listener.onSMSRetrievalFailed(new Exception("SMS retrieval timed out"));
                            }
                            break;

                        default:
                            Log.e(TAG, "Unexpected status code: " + status.getStatusCode());
                            if (listener != null) {
                                listener.onSMSRetrievalFailed(
                                        new Exception("SMS retrieval failed with code: " + status.getStatusCode()));
                            }
                            break;
                    }
                }
            }
        };

        // Register the receiver with NOT_EXPORTED flag (for Android 13+)
        IntentFilter intentFilter = new IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION);
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                activity.registerReceiver(smsReceiver, intentFilter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                ContextCompat.registerReceiver(activity, smsReceiver, intentFilter, ContextCompat.RECEIVER_NOT_EXPORTED);
            }
            Log.d(TAG, "SMS broadcast receiver registered successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error registering SMS receiver", e);
        }
    }

    private String extractOTPFromMessage(String message) {
        if (message == null) return null;

        // Log the message for debugging
        Log.d(TAG, "Attempting to extract OTP from: " + message);

        // Try different patterns to extract the OTP
        // Pattern 1: Look for a 6-digit number
        Pattern pattern = Pattern.compile("(\\d{6})");
        Matcher matcher = pattern.matcher(message);

        if (matcher.find()) {
            return matcher.group(1);
        }

        // Pattern 2: Look for "code", "verification", or "OTP" followed by digits
        pattern = Pattern.compile("(?:code|verification code|OTP)[^\\d]*(\\d{4,6})", Pattern.CASE_INSENSITIVE);
        matcher = pattern.matcher(message);

        if (matcher.find()) {
            return matcher.group(1);
        }

        // Pattern 3: Last resort - any sequence of 4-6 digits
        pattern = Pattern.compile("\\b(\\d{4,6})\\b");
        matcher = pattern.matcher(message);

        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }

    public void unregisterReceiver() {
        if (smsReceiver != null) {
            try {
                activity.unregisterReceiver(smsReceiver);
                Log.d(TAG, "SMS broadcast receiver unregistered successfully");
            } catch (Exception e) {
                Log.e(TAG, "Error unregistering SMS receiver", e);
            }
        }
    }
}