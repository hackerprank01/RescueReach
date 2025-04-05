package com.rescuereach.service.notification;

import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.rescuereach.R;
import com.rescuereach.citizen.CitizenMainActivity;
import com.rescuereach.service.auth.UserSessionManager;

public class NotificationManager {
    private static final String CHANNEL_GROUP_EMERGENCY = "group_emergency";
    private static final String CHANNEL_GROUP_COMMUNITY = "group_community";
    private static final String CHANNEL_GROUP_NEWS = "group_news";

    private static final String CHANNEL_ID_EMERGENCY = "channel_emergency";
    private static final String CHANNEL_ID_COMMUNITY = "channel_community";
    private static final String CHANNEL_ID_NEWS = "channel_news";

    private static NotificationManager instance;
    private final Context context;
    private final UserSessionManager sessionManager;
    private final NotificationManagerCompat notificationManagerCompat;

    private NotificationManager(Context context) {
        this.context = context.getApplicationContext();
        this.sessionManager = UserSessionManager.getInstance(context);
        this.notificationManagerCompat = NotificationManagerCompat.from(context);
        createNotificationChannels();
    }

    public static synchronized NotificationManager getInstance(Context context) {
        if (instance == null) {
            instance = new NotificationManager(context);
        }
        return instance;
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.NotificationManager notificationManager =
                    (android.app.NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

            // Create channel groups
            NotificationChannelGroup emergencyGroup = new NotificationChannelGroup(
                    CHANNEL_GROUP_EMERGENCY, context.getString(R.string.emergency_alerts));

            NotificationChannelGroup communityGroup = new NotificationChannelGroup(
                    CHANNEL_GROUP_COMMUNITY, context.getString(R.string.community_alerts));

            NotificationChannelGroup newsGroup = new NotificationChannelGroup(
                    CHANNEL_GROUP_NEWS, context.getString(R.string.news_updates));

            notificationManager.createNotificationChannelGroups(
                    java.util.Arrays.asList(emergencyGroup, communityGroup, newsGroup));

            // Create channels with default settings
            // We'll update them based on user preferences

            // Emergency channel
            NotificationChannel emergencyChannel = new NotificationChannel(
                    CHANNEL_ID_EMERGENCY,
                    context.getString(R.string.emergency_alerts),
                    android.app.NotificationManager.IMPORTANCE_HIGH);
            emergencyChannel.setDescription(context.getString(R.string.emergency_alerts_desc));
            emergencyChannel.setGroup(CHANNEL_GROUP_EMERGENCY);
            emergencyChannel.enableVibration(true);
            emergencyChannel.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build());

            // Community channel
            NotificationChannel communityChannel = new NotificationChannel(
                    CHANNEL_ID_COMMUNITY,
                    context.getString(R.string.community_alerts),
                    android.app.NotificationManager.IMPORTANCE_DEFAULT);
            communityChannel.setDescription(context.getString(R.string.community_alerts_desc));
            communityChannel.setGroup(CHANNEL_GROUP_COMMUNITY);
            communityChannel.enableVibration(true);

            // News channel
            NotificationChannel newsChannel = new NotificationChannel(
                    CHANNEL_ID_NEWS,
                    context.getString(R.string.news_updates),
                    android.app.NotificationManager.IMPORTANCE_LOW);
            newsChannel.setDescription(context.getString(R.string.news_updates_desc));
            newsChannel.setGroup(CHANNEL_GROUP_NEWS);
            newsChannel.enableVibration(false);

            // Register the channels
            notificationManager.createNotificationChannels(
                    java.util.Arrays.asList(emergencyChannel, communityChannel, newsChannel));
        }
    }

    private void updateChannelsFromSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.NotificationManager notificationManager =
                    (android.app.NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

            // Get user preferences
            boolean soundEnabled = sessionManager.getNotificationPreference("sound", true);
            boolean vibrationEnabled = sessionManager.getNotificationPreference("vibration", true);

            // Update emergency channel
            NotificationChannel emergencyChannel = notificationManager.getNotificationChannel(CHANNEL_ID_EMERGENCY);
            if (emergencyChannel != null) {
                emergencyChannel.enableVibration(vibrationEnabled);

                if (soundEnabled) {
                    emergencyChannel.setSound(
                            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                            new AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                    .build());
                } else {
                    emergencyChannel.setSound(null, null);
                }

                notificationManager.createNotificationChannel(emergencyChannel);
            }

            // Update community channel
            NotificationChannel communityChannel = notificationManager.getNotificationChannel(CHANNEL_ID_COMMUNITY);
            if (communityChannel != null) {
                communityChannel.enableVibration(vibrationEnabled);

                if (soundEnabled) {
                    communityChannel.setSound(
                            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                            new AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                    .build());
                } else {
                    communityChannel.setSound(null, null);
                }

                notificationManager.createNotificationChannel(communityChannel);
            }

            // Update news channel
            NotificationChannel newsChannel = notificationManager.getNotificationChannel(CHANNEL_ID_NEWS);
            if (newsChannel != null) {
                newsChannel.enableVibration(vibrationEnabled);

                if (soundEnabled) {
                    newsChannel.setSound(
                            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                            new AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                    .build());
                } else {
                    newsChannel.setSound(null, null);
                }

                notificationManager.createNotificationChannel(newsChannel);
            }
        }
    }

    /**
     * Show an emergency notification if enabled in settings
     */
    public void showEmergencyNotification(String title, String message, String emergencyId) {
        if (!sessionManager.getNotificationPreference("emergency_alerts", true)) {
            return; // Emergency notifications disabled
        }

        // Update channels from current settings
        updateChannelsFromSettings();

        Intent intent = new Intent(context, CitizenMainActivity.class);
        intent.putExtra("emergency_id", emergencyId);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        Uri soundUri = sessionManager.getNotificationPreference("sound", true) ?
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) : null;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID_EMERGENCY)
                .setSmallIcon(R.drawable.ic_sos)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        if (soundUri != null) {
            builder.setSound(soundUri);
        }

        if (sessionManager.getNotificationPreference("vibration", true)) {
            builder.setVibrate(new long[]{0, 500, 200, 500});
        }

        // Show the notification
        try {
            notificationManagerCompat.notify(emergencyId.hashCode(), builder.build());
        } catch (SecurityException e) {
            // Handle missing notification permission
            e.printStackTrace();
        }
    }

    /**
     * Show a community notification if enabled in settings
     */
    public void showCommunityNotification(String title, String message, String communityId) {
        if (!sessionManager.getNotificationPreference("community_alerts", true)) {
            return; // Community notifications disabled
        }

        // Update channels from current settings
        updateChannelsFromSettings();

        Intent intent = new Intent(context, CitizenMainActivity.class);
        intent.putExtra("community_id", communityId);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        Uri soundUri = sessionManager.getNotificationPreference("sound", true) ?
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) : null;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID_COMMUNITY)
                .setSmallIcon(R.drawable.ic_notifications)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        if (soundUri != null) {
            builder.setSound(soundUri);
        }

        if (sessionManager.getNotificationPreference("vibration", true)) {
            builder.setVibrate(new long[]{0, 300, 200, 300});
        }

        // Show the notification
        try {
            notificationManagerCompat.notify(communityId.hashCode(), builder.build());
        } catch (SecurityException e) {
            // Handle missing notification permission
            e.printStackTrace();
        }
    }

    /**
     * Show a news notification if enabled in settings
     */
    public void showNewsNotification(String title, String message, String newsId) {
        if (!sessionManager.getNotificationPreference("news_updates", false)) {
            return; // News notifications disabled (default off)
        }

        // Update channels from current settings
        updateChannelsFromSettings();

        Intent intent = new Intent(context, CitizenMainActivity.class);
        intent.putExtra("news_id", newsId);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        Uri soundUri = sessionManager.getNotificationPreference("sound", true) ?
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) : null;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID_NEWS)
                .setSmallIcon(R.drawable.ic_data)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        if (soundUri != null) {
            builder.setSound(soundUri);
        }

        if (sessionManager.getNotificationPreference("vibration", true)) {
            builder.setVibrate(new long[]{0, 200, 200, 200});
        }

        // Show the notification
        try {
            notificationManagerCompat.notify(newsId.hashCode(), builder.build());
        } catch (SecurityException e) {
            // Handle missing notification permission
            e.printStackTrace();
        }
    }

    /**
     * Apply changes to notification settings
     */
    public void applyNotificationSettings() {
        updateChannelsFromSettings();
    }
}