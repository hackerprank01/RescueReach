package com.rescuereach.interfaces;

public interface EmergencyNotificationHandler {
    /**
     * Handles emergency notifications received via FCM
     * @param reportId The ID of the emergency report
     */
    static void handleEmergencyNotification(String reportId) {
        // This is a static interface method with default implementation
        // Will be overridden in implementations
    }

    /**
     * Factory method to get the proper handler implementation
     */
    static EmergencyNotificationHandler getImplementation() {
        try {
            // Try to load citizen implementation if available
            Class<?> handlerClass = Class.forName("com.rescuereach.citizen.CitizenEmergencyHandler");
            return (EmergencyNotificationHandler) handlerClass.newInstance();
        } catch (Exception e) {
            // Fallback to empty implementation if citizen flavor is not available
            return new EmergencyNotificationHandler() {
                // Empty implementation
            };
        }
    }
}