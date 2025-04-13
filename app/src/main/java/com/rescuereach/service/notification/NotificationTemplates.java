package com.rescuereach.service.notification;

import android.content.Context;

import com.rescuereach.R;
import com.rescuereach.data.model.SOSReport;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Templates for different notification types
 * Provides standardized notification content for different emergency scenarios
 */
public class NotificationTemplates {

    /**
     * Create a notification payload for a police emergency
     * @param context Application context
     * @param report SOS Report data
     * @return JSONObject with the notification payload
     */
    public static JSONObject createPoliceEmergencyPayload(Context context, SOSReport report) {
        String title = context.getString(R.string.police_emergency_title);
        String message = formatLocationMessage(context, report, R.string.police_emergency_message);
        return createEmergencyPayload("POLICE", title, message, report);
    }

    /**
     * Create a notification payload for a fire emergency
     * @param context Application context
     * @param report SOS Report data
     * @return JSONObject with the notification payload
     */
    public static JSONObject createFireEmergencyPayload(Context context, SOSReport report) {
        String title = context.getString(R.string.fire_emergency_title);
        String message = formatLocationMessage(context, report, R.string.fire_emergency_message);
        return createEmergencyPayload("FIRE", title, message, report);
    }

    /**
     * Create a notification payload for a medical emergency
     * @param context Application context
     * @param report SOS Report data
     * @return JSONObject with the notification payload
     */
    public static JSONObject createMedicalEmergencyPayload(Context context, SOSReport report) {
        String title = context.getString(R.string.medical_emergency_title);
        String message = formatLocationMessage(context, report, R.string.medical_emergency_message);
        return createEmergencyPayload("MEDICAL", title, message, report);
    }

    /**
     * Create a notification payload for a status update
     * @param context Application context
     * @param report SOS Report with updated status
     * @return JSONObject with the notification payload
     */
    public static JSONObject createStatusUpdatePayload(Context context, SOSReport report) {
        String title = context.getString(R.string.status_update_title);
        String messageFormat = context.getString(R.string.status_update_message);

        // Format message with status
        String status = convertStatusToDisplayText(context, report.getStatus());
        String message = String.format(messageFormat, status);

        try {
            JSONObject payload = new JSONObject();

            // Basic notification info
            payload.put("app_id", context.getString(R.string.onesignal_app_id));

            // Heading and content
            JSONObject headings = new JSONObject().put("en", title);
            JSONObject contents = new JSONObject().put("en", message);

            payload.put("headings", headings);
            payload.put("contents", contents);

            // Additional data
            JSONObject data = new JSONObject();
            data.put("type", "STATUS_UPDATE");
            data.put("reportId", report.getReportId());
            data.put("status", report.getStatus());

            payload.put("data", data);

            return payload;
        } catch (JSONException e) {
            e.printStackTrace();
            return new JSONObject();
        }
    }

    /**
     * Create a notification payload for SMS delivery status
     * @param context Application context
     * @param report SOS Report
     * @param statusSuccess Whether SMS was delivered successfully
     * @return JSONObject with the notification payload
     */
    public static JSONObject createSmsDeliveryPayload(Context context, SOSReport report,
                                                      boolean statusSuccess) {
        String title = statusSuccess ?
                context.getString(R.string.sms_delivered_title) :
                context.getString(R.string.sms_failed_title);

        String message = statusSuccess ?
                context.getString(R.string.sms_delivered_message) :
                context.getString(R.string.sms_failed_message);

        try {
            JSONObject payload = new JSONObject();

            // Basic notification info
            payload.put("app_id", context.getString(R.string.onesignal_app_id));

            // Heading and content
            JSONObject headings = new JSONObject().put("en", title);
            JSONObject contents = new JSONObject().put("en", message);

            payload.put("headings", headings);
            payload.put("contents", contents);

            // Additional data
            JSONObject data = new JSONObject();
            data.put("type", "SMS_STATUS");
            data.put("reportId", report.getReportId());
            data.put("successful", statusSuccess);

            payload.put("data", data);

            return payload;
        } catch (JSONException e) {
            e.printStackTrace();
            return new JSONObject();
        }
    }

    /**
     * Create base emergency payload with common fields
     */
    private static JSONObject createEmergencyPayload(String emergencyType, String title, String message,
                                                     SOSReport report) {
        try {
            JSONObject payload = new JSONObject();

            // Basic notification info
            payload.put("app_id", "d85004b4-aabf-48ad-8c12-a74b90bdf57c");

            // Heading and content
            JSONObject headings = new JSONObject().put("en", title);
            JSONObject contents = new JSONObject().put("en", message);

            payload.put("headings", headings);
            payload.put("contents", contents);

            // Set high priority and sound
            payload.put("priority", 10);
            payload.put("android_channel_id", "emergency_channel");
            payload.put("android_sound", "emergency_alert");

            // Additional data
            JSONObject data = new JSONObject();
            data.put("type", "EMERGENCY");
            data.put("emergencyType", emergencyType);

            if (report.getReportId() != null) {
                data.put("reportId", report.getReportId());
            }

            if (report.getLocation() != null) {
                data.put("latitude", report.getLocation().getLatitude());
                data.put("longitude", report.getLocation().getLongitude());
            }

            if (report.getAddress() != null) {
                data.put("address", report.getAddress());
            }

            if (report.getCity() != null) {
                data.put("city", report.getCity());
            }

            if (report.getState() != null) {
                data.put("state", report.getState());
            }

            payload.put("data", data);

            // Add filters for targeting
            JSONObject filters = new JSONObject();

            // User must be a volunteer or responder
            JSONObject volunteerFilter = new JSONObject()
                    .put("field", "tag")
                    .put("key", "volunteer")
                    .put("relation", "=")
                    .put("value", "true");

            JSONObject roleFilter = new JSONObject()
                    .put("field", "tag")
                    .put("key", "role")
                    .put("relation", "=")
                    .put("value", "responder");

            // Logic: (volunteer=true OR role=responder) AND region=userState
            JSONObject orOperator = new JSONObject()
                    .put("operator", "OR");

            // Add region filter if state is available
            JSONArray filterArray = new JSONArray()
                    .put(volunteerFilter)
                    .put(orOperator)
                    .put(roleFilter);

            if (report.getState() != null && !report.getState().isEmpty()) {
                JSONObject andOperator = new JSONObject()
                        .put("operator", "AND");

                JSONObject regionFilter = new JSONObject()
                        .put("field", "tag")
                        .put("key", "region")
                        .put("relation", "=")
                        .put("value", report.getState());

                filterArray.put(andOperator).put(regionFilter);
            }

            payload.put("filters", filterArray);

            return payload;
        } catch (JSONException e) {
            e.printStackTrace();
            return new JSONObject();
        }
    }

    /**
     * Format location message with address information
     */
    private static String formatLocationMessage(Context context, SOSReport report, int messageResId) {
        String messageFormat = context.getString(messageResId);

        // Determine location description
        String location;
        if (report.getAddress() != null && !report.getAddress().isEmpty()) {
            // Use address if available
            location = report.getAddress();

            // Add city and state if available
            if (report.getCity() != null && !report.getCity().equals("Unknown")) {
                location += ", " + report.getCity();

                if (report.getState() != null && !report.getState().equals("Unknown")) {
                    location += ", " + report.getState();
                }
            }
        } else if (report.getLocation() != null) {
            // Use coordinates if no address
            location = report.getLocation().getLatitude() + ", " + report.getLocation().getLongitude();
        } else {
            // No location info available
            location = context.getString(R.string.unknown_location);
        }

        return String.format(messageFormat, location);
    }

    /**
     * Convert SOS status to display text
     */
    private static String convertStatusToDisplayText(Context context, String status) {
        if (status == null) return context.getString(R.string.status_unknown);

        switch (status) {
            case SOSReport.STATUS_PENDING:
                return context.getString(R.string.status_pending);
            case SOSReport.STATUS_RECEIVED:
                return context.getString(R.string.status_received);
            case SOSReport.STATUS_RESPONDING:
                return context.getString(R.string.status_responding);
            case SOSReport.STATUS_RESOLVED:
                return context.getString(R.string.status_resolved);
            default:
                return context.getString(R.string.status_unknown);
        }
    }
}