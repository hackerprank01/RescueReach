package com.rescuereach.data.repository.firebase;

import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.rescuereach.data.model.Incident;
import com.rescuereach.data.repository.IncidentRepository;
import com.rescuereach.data.repository.OnCompleteListener;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class FirebaseIncidentRepository implements IncidentRepository {
    private static final String COLLECTION_INCIDENTS = "incidents";
    private final CollectionReference incidentsCollection;

    public FirebaseIncidentRepository() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        incidentsCollection = db.collection(COLLECTION_INCIDENTS);
    }

    @Override
    public void getIncidentById(String incidentId, OnIncidentFetchedListener listener) {
        incidentsCollection.document(incidentId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Incident incident = documentSnapshot.toObject(Incident.class);
                        listener.onSuccess(incident);
                    } else {
                        listener.onError(new Exception("Incident not found"));
                    }
                })
                .addOnFailureListener(e -> listener.onError(e));
    }

    @Override
    public void getIncidentsByReporter(String reporterId, OnIncidentListFetchedListener listener) {
        incidentsCollection.whereEqualTo("reporterId", reporterId)
                .orderBy("reportedAt", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<Incident> incidents = new ArrayList<>();
                    for (DocumentSnapshot snapshot : queryDocumentSnapshots) {
                        Incident incident = snapshot.toObject(Incident.class);
                        incidents.add(incident);
                    }
                    listener.onSuccess(incidents);
                })
                .addOnFailureListener(e -> listener.onError(e));
    }

    @Override
    public void getIncidentsByResponder(String responderId, OnIncidentListFetchedListener listener) {
        incidentsCollection.whereEqualTo("assignedResponderId", responderId)
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<Incident> incidents = new ArrayList<>();
                    for (DocumentSnapshot snapshot : queryDocumentSnapshots) {
                        Incident incident = snapshot.toObject(Incident.class);
                        incidents.add(incident);
                    }
                    listener.onSuccess(incidents);
                })
                .addOnFailureListener(e -> listener.onError(e));
    }

    @Override
    public void getIncidentsByStatus(Incident.IncidentStatus status, OnIncidentListFetchedListener listener) {
        incidentsCollection.whereEqualTo("status", status)
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<Incident> incidents = new ArrayList<>();
                    for (DocumentSnapshot snapshot : queryDocumentSnapshots) {
                        Incident incident = snapshot.toObject(Incident.class);
                        incidents.add(incident);
                    }
                    listener.onSuccess(incidents);
                })
                .addOnFailureListener(e -> listener.onError(e));
    }

    @Override
    public void getAllIncidents(OnIncidentListFetchedListener listener) {
        incidentsCollection.orderBy("reportedAt", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<Incident> incidents = new ArrayList<>();
                    for (DocumentSnapshot snapshot : queryDocumentSnapshots) {
                        Incident incident = snapshot.toObject(Incident.class);
                        incidents.add(incident);
                    }
                    listener.onSuccess(incidents);
                })
                .addOnFailureListener(e -> listener.onError(e));
    }

    @Override
    public void saveIncident(Incident incident, OnCompleteListener listener) {
        if (incident.getIncidentId() == null) {
            // Create new document with auto-generated ID
            String incidentId = incidentsCollection.document().getId();
            incident.setIncidentId(incidentId);
        }

        incidentsCollection.document(incident.getIncidentId()).set(incident)
                .addOnSuccessListener(aVoid -> listener.onSuccess())
                .addOnFailureListener(e -> listener.onError(e));
    }

    @Override
    public void updateIncident(Incident incident, OnCompleteListener listener) {
        if (incident.getIncidentId() == null) {
            listener.onError(new Exception("Incident ID cannot be null"));
            return;
        }

        incident.setUpdatedAt(new Date());
        incidentsCollection.document(incident.getIncidentId()).set(incident)
                .addOnSuccessListener(aVoid -> listener.onSuccess())
                .addOnFailureListener(e -> listener.onError(e));
    }

    @Override
    public void updateIncidentStatus(String incidentId, Incident.IncidentStatus status,
                                     String responderId, String notes, OnCompleteListener listener) {
        getIncidentById(incidentId, new OnIncidentFetchedListener() {
            @Override
            public void onSuccess(Incident incident) {
                incident.addStatusUpdate(status, responderId, notes);
                updateIncident(incident, listener);
            }

            @Override
            public void onError(Exception e) {
                listener.onError(e);
            }
        });
    }

    @Override
    public void deleteIncident(String incidentId, OnCompleteListener listener) {
        incidentsCollection.document(incidentId).delete()
                .addOnSuccessListener(aVoid -> listener.onSuccess())
                .addOnFailureListener(e -> listener.onError(e));
    }
}