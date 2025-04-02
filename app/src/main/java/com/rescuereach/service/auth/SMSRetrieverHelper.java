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
        // Get an instance of SmsRetrieverClient
        SmsRetrieverClient client = SmsRetriever.getClient(activity);

        // Start SMS Retriever
        Task<Void> task = client.startSmsRetriever();

        // Add success/failure listeners
        task.addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void aVoid) {
                Log.d(TAG, "SMS Retriever started successfully");

                // When successfully started, register the broadcast receiver
                registerBroadcastReceiver();

                // Only listen for SMS for 5 minutes as per API limits
                // After that, the SMS retriever will automatically stop
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

    public void generateAppSignature() {
        /*
         * This method is commented out because it's typically used during development
         * to get your app's hash. In production, you should know your app's hash already.
         *
         * AppSignatureHelper appSignatureHelper = new AppSignatureHelper(activity);
         * String appSignature = appSignatureHelper.getAppSignature();
         * Log.d(TAG, "App Signature: " + appSignature);
         */
    }

    private void registerBroadcastReceiver() {
        // Create a new broadcast receiver if not already created
        if (smsReceiver != null) {
            try {
                activity.unregisterReceiver(smsReceiver);
            } catch (Exception e) {
                Log.e(TAG, "Error unregistering existing receiver", e);
            }
        }

        smsReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d(TAG, "SMS Broadcast received: " + intent.getAction());

                if (SmsRetriever.SMS_RETRIEVED_ACTION.equals(intent.getAction())) {
                    Bundle extras = intent.getExtras();

                    if (extras == null) {
                        Log.e(TAG, "SMS retrieved extras were null");
                        if (listener != null) {
                            listener.onSMSRetrievalFailed(new Exception("SMS extras were null"));
                        }
                        return;
                    }

                    Status status = (Status) extras.get(SmsRetriever.EXTRA_STATUS);

                    if (status == null) {
                        Log.e(TAG, "SMS retrieved status was null");
                        if (listener != null) {
                            listener.onSMSRetrievalFailed(new Exception("SMS status was null"));
                        }
                        return;
                    }

                    switch (status.getStatusCode()) {
                        case CommonStatusCodes.SUCCESS:
                            // Get SMS message contents
                            String message = (String) extras.get(SmsRetriever.EXTRA_SMS_MESSAGE);
                            Log.d(TAG, "SMS retrieved successfully: " + message);

                            if (message != null) {
                                // Extract the OTP from the SMS message
                                String otp = extractOTPFromMessage(message);

                                if (otp != null && listener != null) {
                                    listener.onSMSRetrieved(otp);
                                    Log.d(TAG, "OTP extracted: " + otp);
                                } else if (listener != null) {
                                    listener.onSMSRetrievalFailed(
                                            new Exception("Could not extract OTP from message: " + message));
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

        try {
            // Register the receiver with appropriate intent filter
            IntentFilter intentFilter = new IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION);

            // Use the appropriate method for the Android version
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                activity.registerReceiver(smsReceiver, intentFilter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                ContextCompat.registerReceiver(activity, smsReceiver, intentFilter, ContextCompat.RECEIVER_NOT_EXPORTED);
            }

            Log.d(TAG, "SMS broadcast receiver registered successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error registering SMS receiver", e);
            if (listener != null) {
                listener.onSMSRetrievalFailed(e);
            }
        }
    }

    private String extractOTPFromMessage(String message) {
        if (message == null || message.isEmpty()) {
            return null;
        }

        Log.d(TAG, "Attempting to extract OTP from message: " + message);

        // Try multiple patterns to extract the OTP - from most specific to least specific

        // Pattern 1: Look for OTP followed by 6 digits
        Pattern pattern = Pattern.compile("(?i)\\b(?:OTP|code|verification code|verify code|verification)[^0-9]*([0-9]{6})");
        Matcher matcher = pattern.matcher(message);
        if (matcher.find()) {
            return matcher.group(1);
        }

        // Pattern 2: Look for a standalone 6-digit number with word boundaries
        pattern = Pattern.compile("\\b([0-9]{6})\\b");
        matcher = pattern.matcher(message);
        if (matcher.find()) {
            return matcher.group(1);
        }

        // Pattern 3: Try to find a 6-digit number anywhere
        pattern = Pattern.compile("([0-9]{6})");
        matcher = pattern.matcher(message);
        if (matcher.find()) {
            return matcher.group(1);
        }

        // Pattern 4: Try to find any sequence of digits (at least 6)
        pattern = Pattern.compile("([0-9]{6,})");
        matcher = pattern.matcher(message);
        if (matcher.find()) {
            // If we find a longer sequence, truncate to 6 digits
            String digits = matcher.group(1);
            return digits.substring(0, Math.min(6, digits.length()));
        }

        Log.d(TAG, "No OTP pattern found in message");
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
            smsReceiver = null;
        }
    }
}