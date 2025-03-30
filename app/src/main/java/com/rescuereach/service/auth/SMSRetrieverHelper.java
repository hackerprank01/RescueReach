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
                    Status status = (Status) extras.get(SmsRetriever.EXTRA_STATUS);

                    switch (status.getStatusCode()) {
                        case CommonStatusCodes.SUCCESS:
                            // Get SMS message contents
                            String message = (String) extras.get(SmsRetriever.EXTRA_SMS_MESSAGE);
                            if (message != null) {
                                String otp = extractOTPFromMessage(message);
                                if (otp != null && listener != null) {
                                    listener.onSMSRetrieved(otp);
                                }
                            }
                            break;

                        case CommonStatusCodes.TIMEOUT:
                            // Timeout occurred
                            if (listener != null) {
                                listener.onSMSRetrievalFailed(new Exception("SMS retrieval timed out"));
                            }
                            break;
                    }
                }
            }
        };

        // Register the receiver with NOT_EXPORTED flag (for Android 13+)
        IntentFilter intentFilter = new IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            activity.registerReceiver(smsReceiver, intentFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            ContextCompat.registerReceiver(activity, smsReceiver, intentFilter, ContextCompat.RECEIVER_NOT_EXPORTED);
        }
    }

    private String extractOTPFromMessage(String message) {
        // This pattern looks for a 6-digit number in the message
        Pattern pattern = Pattern.compile("(\\d{6})");
        Matcher matcher = pattern.matcher(message);

        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    public void unregisterReceiver() {
        if (smsReceiver != null) {
            try {
                activity.unregisterReceiver(smsReceiver);
            } catch (Exception e) {
                Log.e(TAG, "Error unregistering SMS receiver", e);
            }
        }
    }
}