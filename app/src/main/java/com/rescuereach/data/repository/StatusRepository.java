package com.rescuereach.data.repository;

/**
 * Repository for handling real-time status data.
 */
public interface StatusRepository {

    interface StatusListener {
        void onStatusChanged(String entityId, String status);
        void onError(Exception e);
    }

    void updateUserStatus(String userId, String status, OnCompleteListener listener);

    void updateIncidentStatus(String incidentId, String status, OnCompleteListener listener);

    void listenToUserStatus(String userId, StatusListener listener);

    void listenToIncidentStatus(String incidentId, StatusListener listener);

    void removeUserStatusListener(String userId);

    void removeIncidentStatusListener(String incidentId);
}