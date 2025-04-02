package com.rescuereach.data.repository.realtime;

import android.util.Log;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;
import com.rescuereach.data.repository.OnCompleteListener;
import com.rescuereach.data.repository.StatusRepository;

import java.util.HashMap;
import java.util.Map;

/**
 * Implementation of StatusRepository using Firebase Realtime Database.
 */
public class RealtimeStatusRepository implements StatusRepository {
    private static final String TAG = "RealtimeStatusRepo";

    private final FirebaseDatabase database;
    private final DatabaseReference statusRef;
    private final Map<String, ValueEventListener> userListeners = new HashMap<>();
    private final Map<String, ValueEventListener> incidentListeners = new HashMap<>();

    public RealtimeStatusRepository() {
        this.database = FirebaseDatabase.getInstance();
        this.statusRef = database.getReference("status");
    }

    @Override
    public void updateUserStatus(String userId, String status, OnCompleteListener listener) {
        if (userId == null || userId.isEmpty()) {
            listener.onError(new IllegalArgumentException("User ID cannot be null or empty"));
            return;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("status", status);
        updates.put("timestamp", ServerValue.TIMESTAMP);

        DatabaseReference userStatusRef = statusRef.child("users").child(userId);
        userStatusRef.updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "User status updated successfully");
                    listener.onSuccess();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error updating user status", e);
                    listener.onError(e);
                });
    }

    @Override
    public void updateIncidentStatus(String incidentId, String status, OnCompleteListener listener) {
        if (incidentId == null || incidentId.isEmpty()) {
            listener.onError(new IllegalArgumentException("Incident ID cannot be null or empty"));
            return;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("status", status);
        updates.put("timestamp", ServerValue.TIMESTAMP);

        DatabaseReference incidentStatusRef = statusRef.child("incidents").child(incidentId);
        incidentStatusRef.updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Incident status updated successfully");
                    listener.onSuccess();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error updating incident status", e);
                    listener.onError(e);
                });
    }

    @Override
    public void listenToUserStatus(String userId, StatusListener listener) {
        if (userId == null || userId.isEmpty()) {
            listener.onError(new IllegalArgumentException("User ID cannot be null or empty"));
            return;
        }

        DatabaseReference userStatusRef = statusRef.child("users").child(userId);
        ValueEventListener valueEventListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    String status = dataSnapshot.child("status").getValue(String.class);
                    if (status != null) {
                        listener.onStatusChanged(userId, status);
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e(TAG, "User status listener cancelled", databaseError.toException());
                listener.onError(databaseError.toException());
            }
        };

        userStatusRef.addValueEventListener(valueEventListener);
        userListeners.put(userId, valueEventListener);
    }

    @Override
    public void listenToIncidentStatus(String incidentId, StatusListener listener) {
        if (incidentId == null || incidentId.isEmpty()) {
            listener.onError(new IllegalArgumentException("Incident ID cannot be null or empty"));
            return;
        }

        DatabaseReference incidentStatusRef = statusRef.child("incidents").child(incidentId);
        ValueEventListener valueEventListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    String status = dataSnapshot.child("status").getValue(String.class);
                    if (status != null) {
                        listener.onStatusChanged(incidentId, status);
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e(TAG, "Incident status listener cancelled", databaseError.toException());
                listener.onError(databaseError.toException());
            }
        };

        incidentStatusRef.addValueEventListener(valueEventListener);
        incidentListeners.put(incidentId, valueEventListener);
    }

    @Override
    public void removeUserStatusListener(String userId) {
        if (userId != null && !userId.isEmpty() && userListeners.containsKey(userId)) {
            ValueEventListener listener = userListeners.get(userId);
            statusRef.child("users").child(userId).removeEventListener(listener);
            userListeners.remove(userId);
        }
    }

    @Override
    public void removeIncidentStatusListener(String incidentId) {
        if (incidentId != null && !incidentId.isEmpty() && incidentListeners.containsKey(incidentId)) {
            ValueEventListener listener = incidentListeners.get(incidentId);
            statusRef.child("incidents").child(incidentId).removeEventListener(listener);
            incidentListeners.remove(incidentId);
        }
    }
}