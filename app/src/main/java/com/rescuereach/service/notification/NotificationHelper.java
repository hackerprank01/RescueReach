package com.rescuereach.service.notification;

import android.content.Context;
import com.rescuereach.data.model.SOSReport;
import com.rescuereach.data.model.EmergencyContact;
import java.util.List;

/**
 * Helper interface for sending notifications
 * Provides a common interface for different notification methods
 */
public class NotificationHelper {
    private final Context context;
    private final OneSignalManager oneSignalManager;

    public NotificationHelper(Context context) {
        this.context = context.getApplicationContext();
        this.oneSignalManager = null; // To be implemented
    }

    // Method stubs to be implemented
    public void notifyResponders(SOSReport report) { }
    public void sendEmergencySMS(SOSReport report, List<EmergencyContact> contacts) { }
    public void notifyUserAboutSOSUpdate(String userId, SOSReport report) { }
}