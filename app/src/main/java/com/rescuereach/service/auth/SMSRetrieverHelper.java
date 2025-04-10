package com.rescuereach.service.auth;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;

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

    private final Context context;
    private BroadcastReceiver smsRetrieverBroadcastReceiver;
    private SMSRetrievedListener smsRetrievedListener;
    private final SmsRetrieverClient smsRetrieverClient;

    public interface SMSRetrievedListener {
        void onSMSRetrieved(String otp);
        void onSMSRetrievalFailed(Exception e);
    }

    public SMSRetrieverHelper(Context context) {
        this.context = context;
        this.smsRetrieverClient = SmsRetriever.getClient(context);
    }

    public void setSMSRetrievedListener(SMSRetrievedListener listener) {
        this.smsRetrievedListener = listener;
    }

    public void startSMSRetriever() {
        // Start SMS retriever
        Task<Void> task = smsRetrieverClient.startSmsRetriever();

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
                if (smsRetrievedListener != null) {
                    smsRetrievedListener.onSMSRetrievalFailed(e);
                }
            }
        });
    }

    private void registerBroadcastReceiver() {
        // Unregister any existing receiver
        unregisterReceiver();

        // Create new receiver
        smsRetrieverBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (SmsRetriever.SMS_RETRIEVED_ACTION.equals(intent.getAction())) {
                    Bundle extras = intent.getExtras();
                    if (extras != null) {
                        Status status = (Status) extras.get(SmsRetriever.EXTRA_STATUS);

                        if (status != null) {
                            switch (status.getStatusCode()) {
                                case CommonStatusCodes.SUCCESS:
                                    // Get SMS message content
                                    String message = (String) extras.get(SmsRetriever.EXTRA_SMS_MESSAGE);
                                    Log.d(TAG, "SMS retrieved: " + message);

                                    // Extract OTP from message
                                    String otp = extractOTPFromMessage(message);

                                    if (otp != null && smsRetrievedListener != null) {
                                        smsRetrievedListener.onSMSRetrieved(otp);
                                    } else if (smsRetrievedListener != null) {
                                        smsRetrievedListener.onSMSRetrievalFailed(
                                                new Exception("Could not extract OTP from SMS"));
                                    }
                                    break;
                                case CommonStatusCodes.TIMEOUT:
                                    // Timeout occurred, handle accordingly
                                    if (smsRetrievedListener != null) {
                                        smsRetrievedListener.onSMSRetrievalFailed(
                                                new Exception("SMS retrieval timed out"));
                                    }
                                    break;
                                default:
                                    // Other error, handle accordingly
                                    if (smsRetrievedListener != null) {
                                        smsRetrievedListener.onSMSRetrievalFailed(
                                                new Exception("SMS retrieval failed with code: " +
                                                        status.getStatusCode()));
                                    }
                            }
                        }
                    }
                }
            }
        };

        // Register the receiver
        context. registerReceiver(smsRetrieverBroadcastReceiver, new IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION),Context.RECEIVER_NOT_EXPORTED);

        Log.d(TAG, "SMS retriever broadcast receiver registered");
    }

    private String extractOTPFromMessage(String message) {
        if (message == null) return null;

        // Try pattern for 6 digit OTP
        Pattern pattern = Pattern.compile("\\b(\\d{6})\\b");
        Matcher matcher = pattern.matcher(message);

        if (matcher.find()) {
            return matcher.group(1);
        }

        // Try looking for OTP prefixed with common identifiers
        pattern = Pattern.compile("(?i)(?:verification|code|otp)[^0-9]*([0-9]{6})");
        matcher = pattern.matcher(message);

        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }

    public void unregisterReceiver() {
        if (smsRetrieverBroadcastReceiver != null) {
            try {
                context.unregisterReceiver(smsRetrieverBroadcastReceiver);
                Log.d(TAG, "SMS retriever broadcast receiver unregistered");
            } catch (Exception e) {
                Log.e(TAG, "Error unregistering SMS retriever receiver", e);
            }
            smsRetrieverBroadcastReceiver = null;
        }
    }
}