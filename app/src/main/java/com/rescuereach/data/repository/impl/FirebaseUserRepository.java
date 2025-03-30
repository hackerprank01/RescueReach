package com.rescuereach.data.repository.impl;

import android.util.Log;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.rescuereach.data.model.User;
import com.rescuereach.data.repository.OnCompleteListener;
import com.rescuereach.data.repository.UserRepository;

public abstract class FirebaseUserRepository implements UserRepository {
    private static final String TAG = "FirebaseUserRepository";

    // Firestore references
    private final FirebaseFirestore firestore;
    private final CollectionReference usersCollection;

    // Realtime Database references
    private final FirebaseDatabase database;
    private final DatabaseReference usersRef;

    public FirebaseUserRepository() {
        // Initialize Firestore
        firestore = FirebaseFirestore.getInstance();
        usersCollection = firestore.collection("users");

        // Initialize Realtime Database
        database = FirebaseDatabase.getInstance();
        usersRef = database.getReference("users");
    }

    @Override
    public void getUserById(String userId, final OnUserFetchedListener listener) {
        if (userId == null || userId.isEmpty()) {
            listener.onError(new IllegalArgumentException("User ID cannot be null or empty"));
            return;
        }

        // Try to get from Firestore first
        usersCollection.document(userId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        User user = documentSnapshot.toObject(User.class);
                        listener.onSuccess(user);
                    } else {
                        // If not in Firestore, try Realtime Database
                        usersRef.child(userId).addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(DataSnapshot dataSnapshot) {
                                if (dataSnapshot.exists()) {
                                    User user = dataSnapshot.getValue(User.class);
                                    listener.onSuccess(user);
                                } else {
                                    listener.onError(new Exception("User not found"));
                                }
                            }

                            @Override
                            public void onCancelled(DatabaseError databaseError) {
                                listener.onError(databaseError.toException());
                            }
                        });
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error getting user from Firestore", e);

                    // Handle permission error gracefully - try Realtime Database as fallback
                    if (e.getMessage().contains("PERMISSION_DENIED")) {
                        Log.d(TAG, "Firestore permission denied, trying Realtime Database");
                        usersRef.child(userId).addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(DataSnapshot dataSnapshot) {
                                if (dataSnapshot.exists()) {
                                    User user = dataSnapshot.getValue(User.class);
                                    listener.onSuccess(user);
                                } else {
                                    listener.onError(new Exception("User not found"));
                                }
                            }

                            @Override
                            public void onCancelled(DatabaseError databaseError) {
                                listener.onError(databaseError.toException());
                            }
                        });
                    } else {
                        listener.onError(e);
                    }
                });
    }

    @Override
    public void getUserByPhoneNumber(String phoneNumber, final OnUserFetchedListener listener) {
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            listener.onError(new IllegalArgumentException("Phone number cannot be null or empty"));
            return;
        }

        // Ensure Firebase Auth is initialized before making queries
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            listener.onError(new Exception("User authentication required"));
            return;
        }

        // Try Firestore first
        usersCollection.whereEqualTo("phoneNumber", phoneNumber).get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        User user = querySnapshot.getDocuments().get(0).toObject(User.class);
                        listener.onSuccess(user);
                    } else {
                        // If not in Firestore, try Realtime Database
                        Query phoneQuery = usersRef.orderByChild("phoneNumber").equalTo(phoneNumber);
                        phoneQuery.addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(DataSnapshot dataSnapshot) {
                                if (dataSnapshot.exists()) {
                                    for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                                        User user = snapshot.getValue(User.class);
                                        listener.onSuccess(user);
                                        return;
                                    }
                                }
                                listener.onError(new Exception("User not found"));
                            }

                            @Override
                            public void onCancelled(DatabaseError databaseError) {
                                listener.onError(databaseError.toException());
                            }
                        });
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error querying users by phone", e);

                    // Handle permission error - try Realtime Database
                    if (e.getMessage().contains("PERMISSION_DENIED")) {
                        Log.d(TAG, "Firestore permission denied, trying Realtime Database");
                        Query phoneQuery = usersRef.orderByChild("phoneNumber").equalTo(phoneNumber);
                        phoneQuery.addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(DataSnapshot dataSnapshot) {
                                if (dataSnapshot.exists()) {
                                    for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                                        User user = snapshot.getValue(User.class);
                                        listener.onSuccess(user);
                                        return;
                                    }
                                }
                                listener.onError(new Exception("User not found"));
                            }

                            @Override
                            public void onCancelled(DatabaseError databaseError) {
                                listener.onError(databaseError.toException());
                            }
                        });
                    } else {
                        listener.onError(e);
                    }
                });
    }

    @Override
    public void saveUser(User user, final OnCompleteListener listener) {
        if (user == null || user.getUserId() == null || user.getUserId().isEmpty()) {
            listener.onError(new IllegalArgumentException("User and user ID cannot be null or empty"));
            return;
        }

        // Save to Firestore
        usersCollection.document(user.getUserId()).set(user)
                .addOnSuccessListener(aVoid -> {
                    // Also save to Realtime Database for redundancy and real-time features
                    usersRef.child(user.getUserId()).setValue(user)
                            .addOnSuccessListener(aVoid1 -> listener.onSuccess())
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Error saving user to Realtime Database", e);
                                // Still consider success if Firestore worked but RTDB failed
                                listener.onSuccess();
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error saving user to Firestore", e);

                    // If Firestore fails, try Realtime Database
                    if (e.getMessage().contains("PERMISSION_DENIED")) {
                        Log.d(TAG, "Firestore permission denied, trying Realtime Database only");
                        usersRef.child(user.getUserId()).setValue(user)
                                .addOnSuccessListener(aVoid -> listener.onSuccess())
                                .addOnFailureListener(e1 -> listener.onError(e1));
                    } else {
                        listener.onError(e);
                    }
                });
    }
}