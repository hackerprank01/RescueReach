package com.rescuereach.data.repository.firebase;

import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.rescuereach.data.model.Volunteer;
import com.rescuereach.data.repository.OnCompleteListener;
import com.rescuereach.data.repository.VolunteerRepository;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class FirebaseVolunteerRepository implements VolunteerRepository {
    private static final String COLLECTION_VOLUNTEERS = "volunteers";
    private final CollectionReference volunteersCollection;

    public FirebaseVolunteerRepository() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        volunteersCollection = db.collection(COLLECTION_VOLUNTEERS);
    }

    @Override
    public void getVolunteerById(String volunteerId, OnVolunteerFetchedListener listener) {
        volunteersCollection.document(volunteerId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Volunteer volunteer = documentSnapshot.toObject(Volunteer.class);
                        listener.onSuccess(volunteer);
                    } else {
                        listener.onError(new Exception("Volunteer not found"));
                    }
                })
                .addOnFailureListener(e -> listener.onError(e));
    }

    @Override
    public void getVolunteerByUserId(String userId, OnVolunteerFetchedListener listener) {
        volunteersCollection.whereEqualTo("userId", userId).limit(1).get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        Volunteer volunteer = queryDocumentSnapshots.getDocuments().get(0).toObject(Volunteer.class);
                        listener.onSuccess(volunteer);
                    } else {
                        listener.onError(new Exception("Volunteer not found"));
                    }
                })
                .addOnFailureListener(e -> listener.onError(e));
    }

    @Override
    public void getVolunteersByZone(String zone, OnVolunteerListFetchedListener listener) {
        volunteersCollection.whereEqualTo("zone", zone).get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<Volunteer> volunteers = new ArrayList<>();
                    for (DocumentSnapshot snapshot : queryDocumentSnapshots) {
                        Volunteer volunteer = snapshot.toObject(Volunteer.class);
                        volunteers.add(volunteer);
                    }
                    listener.onSuccess(volunteers);
                })
                .addOnFailureListener(e -> listener.onError(e));
    }

    @Override
    public void getAvailableVolunteers(OnVolunteerListFetchedListener listener) {
        volunteersCollection.whereEqualTo("isAvailable", true)
                .whereEqualTo("isVerified", true).get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<Volunteer> volunteers = new ArrayList<>();
                    for (DocumentSnapshot snapshot : queryDocumentSnapshots) {
                        Volunteer volunteer = snapshot.toObject(Volunteer.class);
                        volunteers.add(volunteer);
                    }
                    listener.onSuccess(volunteers);
                })
                .addOnFailureListener(e -> listener.onError(e));
    }

    @Override
    public void getAllVolunteers(OnVolunteerListFetchedListener listener) {
        volunteersCollection.get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<Volunteer> volunteers = new ArrayList<>();
                    for (DocumentSnapshot snapshot : queryDocumentSnapshots) {
                        Volunteer volunteer = snapshot.toObject(Volunteer.class);
                        volunteers.add(volunteer);
                    }
                    listener.onSuccess(volunteers);
                })
                .addOnFailureListener(e -> listener.onError(e));
    }

    @Override
    public void saveVolunteer(Volunteer volunteer, OnCompleteListener listener) {
        if (volunteer.getVolunteerId() == null) {
            // Create new document with auto-generated ID
            String volunteerId = volunteersCollection.document().getId();
            volunteer.setVolunteerId(volunteerId);
        }

        volunteersCollection.document(volunteer.getVolunteerId()).set(volunteer)
                .addOnSuccessListener(aVoid -> listener.onSuccess())
                .addOnFailureListener(e -> listener.onError(e));
    }

    @Override
    public void updateVolunteer(Volunteer volunteer, OnCompleteListener listener) {
        if (volunteer.getVolunteerId() == null) {
            listener.onError(new Exception("Volunteer ID cannot be null"));
            return;
        }

        volunteersCollection.document(volunteer.getVolunteerId()).set(volunteer)
                .addOnSuccessListener(aVoid -> listener.onSuccess())
                .addOnFailureListener(e -> listener.onError(e));
    }

    @Override
    public void updateVolunteerAvailability(String volunteerId, boolean isAvailable, OnCompleteListener listener) {
        getVolunteerById(volunteerId, new OnVolunteerFetchedListener() {
            @Override
            public void onSuccess(Volunteer volunteer) {
                volunteer.setAvailable(isAvailable);
                volunteer.setLastActiveAt(new Date());
                updateVolunteer(volunteer, listener);
            }

            @Override
            public void onError(Exception e) {
                listener.onError(e);
            }
        });
    }

    @Override
    public void deleteVolunteer(String volunteerId, OnCompleteListener listener) {
        volunteersCollection.document(volunteerId).delete()
                .addOnSuccessListener(aVoid -> listener.onSuccess())
                .addOnFailureListener(e -> listener.onError(e));
    }
}