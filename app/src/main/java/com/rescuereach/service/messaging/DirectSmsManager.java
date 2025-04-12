package com.rescuereach.service.messaging;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.telephony.SmsManager;
import android.util.Log;
import android.Manifest;
import android.content.pm.PackageManager;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;

/**
 * Manager for sending SMS directly from the device using Android's SmsManager
 */
public class DirectSmsManager {
    private static final String TAG = "DirectSmsManager";

    private final Context context;
    private final SmsManager smsManager;

    public DirectSmsManager(Context context) {
        this.context = context.getApplicationContext();
        this.smsManager = SmsManager.getDefault();
    }

    /**
     * Send SMS to a single number
     */
    public void sendSms(String phoneNumber, String message) {
        if (!areSmsSendingPermissionsGranted()) {
            Log.e(TAG, "SMS permissions not granted");
            return;
        }

        try {
            Log.d(TAG, "Sending SMS to " + phoneNumber);

            // Create pending intents for delivery reports
            PendingIntent sentPI = createPendingIntent("SMS_SENT", 0);
            PendingIntent deliveredPI = createPendingIntent("SMS_DELIVERED", 1);

            // Send the SMS
            smsManager.sendTextMessage(
                    phoneNumber,
                    null,
                    message,
                    sentPI,
                    deliveredPI
            );

        } catch (Exception e) {
            Log.e(TAG, "Error sending SMS: " + e.getMessage(), e);
        }
    }

    /**
     * Send multipart SMS for longer messages
     */
    public void sendMultipartSms(String phoneNumber, String message) {
        if (!areSmsSendingPermissionsGranted()) {
            Log.e(TAG, "SMS permissions not granted");
            return;
        }

        try {
            Log.d(TAG, "Sending multipart SMS to " + phoneNumber);

            // Divide the message into parts if it's too long
            ArrayList<String> messageParts = smsManager.divideMessage(message);

            // Create arrays of PendingIntents for delivery reports
            ArrayList<PendingIntent> sentIntents = new ArrayList<>();
            ArrayList<PendingIntent> deliveredIntents = new ArrayList<>();

            // Create a pending intent for each message part
            for (int i = 0; i < messageParts.size(); i++) {
                sentIntents.add(createPendingIntent("SMS_SENT_PART_" + i, i * 2));
                deliveredIntents.add(createPendingIntent("SMS_DELIVERED_PART_" + i, i * 2 + 1));
            }

            // Send the multipart SMS
            smsManager.sendMultipartTextMessage(
                    phoneNumber,
                    null,
                    messageParts,
                    sentIntents,
                    deliveredIntents
            );

        } catch (Exception e) {
            Log.e(TAG, "Error sending multipart SMS: " + e.getMessage(), e);
        }
    }

    /**
     * Check if SMS sending permissions are granted
     */
    public boolean areSmsSendingPermissionsGranted() {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Create a PendingIntent for delivery reports
     */
    private PendingIntent createPendingIntent(String action, int requestCode) {
        Intent intent = new Intent(action);
        return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_IMMUTABLE
        );
    }
}