package com.rescuereach.citizen;

import com.rescuereach.interfaces.EmergencyNotificationHandler;
import android.content.Intent;
import android.content.Context;

public class CitizenEmergencyHandler implements EmergencyNotificationHandler {

    /**
     * Implementation specific to the Citizen app
     */
    public static void handleEmergencyNotification(String reportId) {
        // Your existing implementation from CitizenMainActivity
        // For example:
        CitizenMainActivity.navigateToEmergencyDetails(reportId);
    }
}