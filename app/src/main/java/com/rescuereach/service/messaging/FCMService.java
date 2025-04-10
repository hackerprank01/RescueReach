package com.rescuereach.service.messaging;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.rescuereach.R;
//import com.rescuereach.citizen.CitizenMainActivity;
import com.rescuereach.interfaces.EmergencyNotificationHandler;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * Service for handling FCM messages including emergency alerts
 */
public class FCMService extends FirebaseMessagingService {
    private static final String TAG = "FCMService";

    private static final String EMERGENCY_CHANNEL_ID = "emergency_channel";
    private static final String STATUS_CHANNEL_ID = "status_channel";

    @Override
    public void onNewToken(@NonNull String token) {
        Log.d(TAG, "FCM token refreshed");
        // Save the new token
        FCMManager manager = new FCMManager(this);
        manager.initialize();
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        Log.d(TAG, "FCM message received from: " + remoteMessage.getFrom());

        // Handle data payload
        Map<String, String> data = remoteMessage.getData();
        if (data != null && !data.isEmpty()) {
            Log.d(TAG, "Message data: " + data);

            // Check if this is an emergency status update
            String reportId = data.get("reportId");
            String status = data.get("status");

            if (reportId != null && status != null) {
                // This is a status update notification
                showStatusNotification(reportId, status, data);
                return;
            }
        }

        // Handle notification payload
        RemoteMessage.Notification notification = remoteMessage.getNotification();
        if (notification != null) {
            Log.d(TAG, "Message notification: " + notification.getBody());
            showNotification(notification.getTitle(), notification.getBody(), data);
        }
    }

    /**
     * Show a status notification for SOS updates
     */
    private void showStatusNotification(String reportId, String status, Map<String, String> data) {
        String title = "SOS Status Update";
        String message;

        // Customize message based on status
        switch (status) {
            case "RECEIVED":
                message = "Your emergency has been received";
                break;
            case "RESPONDING":
                message = "Help is on the way to your location";
                break;
            case "RESOLVED":
                message = "Your emergency has been marked as resolved";
                break;
            default:
                message = "Your SOS report status has changed to " + status;
        }

        Intent intent = new Intent(this, CitizenMainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra("reportId", reportId);
        intent.putExtra("status", status);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, STATUS_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(message)
                .setAutoCancel(true)
                .setSound(defaultSoundUri)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent);

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        createNotificationChannels(notificationManager);

        notificationManager.notify(reportId.hashCode(), builder.build());
    }

    /**
     * Show a general notification
     */
    private void showNotification(String title, String message, Map<String, String> data) {
        Intent intent = new Intent(this, CitizenMainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        if (data != null) {
            for (String key : data.keySet()) {
                intent.putExtra(key, data.get(key));
            }
        }


        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, EMERGENCY_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(message)
                .setAutoCancel(true)
                .setSound(defaultSoundUri)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent);

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        createNotificationChannels(notificationManager);

        int notificationId = (int) System.currentTimeMillis();
        notificationManager.notify(notificationId, builder.build());
    }

    /**
     * Create notification channels for Android O and above
     */
    private void createNotificationChannels(NotificationManager notificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Emergency channel (high priority)
            NotificationChannel emergencyChannel = new NotificationChannel(
                    EMERGENCY_CHANNEL_ID,
                    "Emergency Notifications",
                    NotificationManager.IMPORTANCE_HIGH);
            emergencyChannel.setDescription("Critical emergency alerts");
            emergencyChannel.enableVibration(true);
            emergencyChannel.setShowBadge(true);
            notificationManager.createNotificationChannel(emergencyChannel);

            // Status updates channel
            NotificationChannel statusChannel = new NotificationChannel(
                    STATUS_CHANNEL_ID,
                    "Status Updates",
                    NotificationManager.IMPORTANCE_DEFAULT);
            statusChannel.setDescription("Emergency status updates");
            statusChannel.setShowBadge(true);
            notificationManager.createNotificationChannel(statusChannel);
        }
    }
}