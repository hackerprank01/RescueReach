package com.rescuereach.data.repository;

import com.rescuereach.data.model.Incident;

import java.util.List;

public interface IncidentRepository {
    interface OnIncidentFetchedListener {
        void onSuccess(Incident incident);
        void onError(Exception e);
    }

    interface OnIncidentListFetchedListener {
        void onSuccess(List<Incident> incidents);
        void onError(Exception e);
    }

    void getIncidentById(String incidentId, OnIncidentFetchedListener listener);
    void getIncidentsByReporter(String reporterId, OnIncidentListFetchedListener listener);
    void getIncidentsByResponder(String responderId, OnIncidentListFetchedListener listener);
    void getIncidentsByStatus(Incident.IncidentStatus status, OnIncidentListFetchedListener listener);
    void getAllIncidents(OnIncidentListFetchedListener listener);
    void saveIncident(Incident incident, OnCompleteListener listener);
    void updateIncident(Incident incident, OnCompleteListener listener);
    void updateIncidentStatus(String incidentId, Incident.IncidentStatus status,
                              String responderId, String notes, OnCompleteListener listener);
    void deleteIncident(String incidentId, OnCompleteListener listener);
}