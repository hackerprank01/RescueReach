package com.rescuereach.service.messaging;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.telephony.SmsManager;
import android.util.Log;
import android.Manifest;
import android.content.pm.PackageManager;

import androidx.core.content.ContextCompat;

import com.rescuereach.util.PermissionManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manager for sending SMS directly from device using Android's SmsManager
 * Handles SMS sending, delivery tracking, and retry logic
 */
public class DirectSmsManager {
    private static final String TAG = "DirectSmsManager";

    // SMS Action Constants
    private static final String ACTION_SMS_SENT = "com.rescuereach.ACTION_SMS_SENT";
    private static final String ACTION_SMS_DELIVERED = "com.rescuereach.ACTION_SMS_DELIVERED";

    // Max retry constants
    private static final int MAX_SMS_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 5000; // 5 seconds

    // Manager and context
    private final Context context;
    private final SmsManager smsManager;

    // Broadcast receivers
    private BroadcastReceiver sentReceiver;
    private BroadcastReceiver deliveredReceiver;

    // Track sent messages for retry logic
    private final Map<String, Integer> retryCountMap = new HashMap<>();
    private final Map<String, String> messageMap = new HashMap<>();
    private final Handler retryHandler = new Handler(Looper.getMainLooper());

    /**
     * Constructor initializes the SMS manager
     * @param context Application context
     */
    public DirectSmsManager(Context context) {
        this.context = context.getApplicationContext();
        this.smsManager = SmsManager.getDefault();
        registerReceivers();
    }

    /**
     * Register broadcast receivers for tracking SMS sending and delivery
     */
    private void registerReceivers() {
        Log.d(TAG, "Registering SMS broadcast receivers");

        try {
            // SMS sent receiver
            sentReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String phoneNumber = intent.getStringExtra("phone_number");
                    String messageId = intent.getStringExtra("message_id");

                    switch (getResultCode()) {
                        case Activity.RESULT_OK:
                            Log.d(TAG, "SMS sent successfully to " + phoneNumber);
                            retryCountMap.remove(messageId);
                            messageMap.remove(messageId);
                            break;

                        case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                            Log.e(TAG, "Generic failure sending SMS to " + phoneNumber);
                            handleSendFailure(phoneNumber, messageId);
                            break;

                        case SmsManager.RESULT_ERROR_NO_SERVICE:
                            Log.e(TAG, "No service for sending SMS to " + phoneNumber);
                            handleSendFailure(phoneNumber, messageId);
                            break;

                        case SmsManager.RESULT_ERROR_NULL_PDU:
                            Log.e(TAG, "Null PDU error sending SMS to " + phoneNumber);
                            handleSendFailure(phoneNumber, messageId);
                            break;

                        case SmsManager.RESULT_ERROR_RADIO_OFF:
                            Log.e(TAG, "Radio off error sending SMS to " + phoneNumber);
                            handleSendFailure(phoneNumber, messageId);
                            break;

                        default:
                            Log.e(TAG, "Unknown error sending SMS to " + phoneNumber +
                                    " with code: " + getResultCode());
                            handleSendFailure(phoneNumber, messageId);
                            break;
                    }
                }
            };

            // SMS delivered receiver
            deliveredReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String phoneNumber = intent.getStringExtra("phone_number");

                    switch (getResultCode()) {
                        case Activity.RESULT_OK:
                            Log.d(TAG, "SMS delivered successfully to " + phoneNumber);
                            break;

                        case Activity.RESULT_CANCELED:
                            Log.w(TAG, "SMS delivery failed to " + phoneNumber);
                            break;

                        default:
                            Log.w(TAG, "SMS delivery status unknown for " + phoneNumber +
                                    " with code: " + getResultCode());
                            break;
                    }
                }
            };

            // Register receivers with the appropriate flags for Android 13+
            int flags = 0;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                flags = Context.RECEIVER_NOT_EXPORTED;
            }

            context.registerReceiver(sentReceiver, new IntentFilter(ACTION_SMS_SENT), flags);
            context.registerReceiver(deliveredReceiver, new IntentFilter(ACTION_SMS_DELIVERED), flags);

            Log.d(TAG, "SMS broadcast receivers registered successfully");

        } catch (Exception e) {
            Log.e(TAG, "Error registering SMS receivers", e);
        }
    }

    /**
     * Unregister broadcast receivers
     * Call this method when the app is shutting down
     */
    public void unregisterReceivers() {
        try {
            if (sentReceiver != null) {
                context.unregisterReceiver(sentReceiver);
                sentReceiver = null;
            }

            if (deliveredReceiver != null) {
                context.unregisterReceiver(deliveredReceiver);
                deliveredReceiver = null;
            }

            Log.d(TAG, "SMS broadcast receivers unregistered");

        } catch (Exception e) {
            Log.e(TAG, "Error unregistering SMS receivers", e);
        }
    }

    /**
     * Handle SMS send failure with retry logic
     */
    private void handleSendFailure(String phoneNumber, String messageId) {
        // Get retry count
        int retryCount = retryCountMap.containsKey(messageId) ?
                retryCountMap.get(messageId) : 0;

        if (retryCount < MAX_SMS_RETRIES) {
            // Increment retry count
            retryCountMap.put(messageId, retryCount + 1);

            // Get the message
            String message = messageMap.get(messageId);
            if (message != null) {
                // Schedule retry
                Log.d(TAG, "Scheduling SMS retry " + (retryCount + 1) + "/" + MAX_SMS_RETRIES
                        + " to " + phoneNumber);

                retryHandler.postDelayed(() -> {
                    sendSms(phoneNumber, message, messageId);
                }, RETRY_DELAY_MS);
            }
        } else {
            // Max retries reached
            Log.e(TAG, "Max SMS retries (" + MAX_SMS_RETRIES +
                    ") reached for " + phoneNumber);
            retryCountMap.remove(messageId);
            messageMap.remove(messageId);
        }
    }

    /**
     * Send SMS to a single number
     * @param phoneNumber Recipient phone number
     * @param message SMS message
     */
    public void sendSms(String phoneNumber, String message) {
        String messageId = generateMessageId(phoneNumber, message);
        sendSms(phoneNumber, message, messageId);
    }

    /**
     * Send SMS with specific message ID for tracking
     */
    private void sendSms(String phoneNumber, String message, String messageId) {
        if (!areSmsSendingPermissionsGranted()) {
            Log.e(TAG, "SMS permissions not granted");
            return;
        }

        try {
            Log.d(TAG, "Sending SMS to " + phoneNumber);

            // Save message for potential retry
            messageMap.put(messageId, message);

            // Create pending intents with phone number
            PendingIntent sentPI = createPendingIntent(ACTION_SMS_SENT, phoneNumber, messageId, 0);
            PendingIntent deliveredPI = createPendingIntent(ACTION_SMS_DELIVERED, phoneNumber, messageId, 1);

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
     * @param phoneNumber Recipient phone number
     * @param message SMS message
     */
    public void sendMultipartSms(String phoneNumber, String message) {
        if (!areSmsSendingPermissionsGranted()) {
            Log.e(TAG, "SMS permissions not granted");
            return;
        }

        try {
            String messageId = generateMessageId(phoneNumber, message);
            Log.d(TAG, "Sending multipart SMS to " + phoneNumber);

            // Save message for potential retry
            messageMap.put(messageId, message);

            // Divide the message into parts if it's too long
            ArrayList<String> messageParts = smsManager.divideMessage(message);

            Log.d(TAG, "SMS divided into " + messageParts.size() + " parts");

            // Create arrays of PendingIntents for delivery reports
            ArrayList<PendingIntent> sentIntents = new ArrayList<>();
            ArrayList<PendingIntent> deliveredIntents = new ArrayList<>();

            // Create a pending intent for each message part
            for (int i = 0; i < messageParts.size(); i++) {
                sentIntents.add(createPendingIntent(
                        ACTION_SMS_SENT, phoneNumber, messageId + "_part_" + i, i * 2));
                deliveredIntents.add(createPendingIntent(
                        ACTION_SMS_DELIVERED, phoneNumber, messageId + "_part_" + i, i * 2 + 1));
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
     * Generate a unique message ID for tracking
     */
    private String generateMessageId(String phoneNumber, String message) {
        return phoneNumber + "_" + System.currentTimeMillis() + "_" + Math.abs(message.hashCode());
    }

    /**
     * Check if SMS sending permissions are granted
     * @return True if permissions are granted
     */
    public boolean areSmsSendingPermissionsGranted() {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Check and request SMS permissions using the PermissionManager
     * @param activity The activity requesting permissions
     * @param callback Callback for permission result
     */
    public void checkAndRequestSmsPermissions(Activity activity, PermissionManager.PermissionCallback callback) {
        try {
            PermissionManager permissionManager = PermissionManager.getInstance(context);

            if (permissionManager.hasPermission(Manifest.permission.SEND_SMS)) {
                // Permission already granted
                if (callback != null) {
                    List<String> granted = new ArrayList<>();
                    granted.add(Manifest.permission.SEND_SMS);
                    callback.onPermissionResult(true, granted, new ArrayList<>());
                }
            } else {
                // Request permission
                String rationale = "SMS permission is needed to send emergency alerts when " +
                        "there is no internet connection available.";
                permissionManager.requestPermission(activity, Manifest.permission.SEND_SMS,
                        PermissionManager.RC_SMS, rationale, callback);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking or requesting SMS permissions", e);
            if (callback != null) {
                callback.onPermissionResult(false, new ArrayList<>(), new ArrayList<>());
            }
        }
    }

    /**
     * Create a PendingIntent for delivery reports
     */
    private PendingIntent createPendingIntent(String action, String phoneNumber, String messageId, int requestCode) {
        Intent intent = new Intent(action);
        intent.putExtra("phone_number", phoneNumber);
        intent.putExtra("message_id", messageId);

        // Use FLAG_IMMUTABLE for Android 12+ compatibility
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                flags
        );
    }

    /**
     * For testing: get current retry count for a message
     */
    public int getRetryCount(String messageId) {
        return retryCountMap.containsKey(messageId) ? retryCountMap.get(messageId) : 0;
    }

    /**
     * Check if the device has SMS capabilities
     */
    public boolean deviceHasSmsCapability() {
        PackageManager packageManager = context.getPackageManager();
        return packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY);
    }
}