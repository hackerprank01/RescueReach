package com.rescuereach.data.repository.firebase;

import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.rescuereach.data.model.User;
import com.rescuereach.data.repository.OnCompleteListener;
import com.rescuereach.data.repository.UserRepository;

import java.util.ArrayList;
import java.util.List;

public class FirebaseUserRepository implements UserRepository {
    private static final String COLLECTION_USERS = "users";
    private final CollectionReference usersCollection;

    public FirebaseUserRepository() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        usersCollection = db.collection(COLLECTION_USERS);
    }

    @Override
    public void getUserById(String userId, OnUserFetchedListener listener) {
        usersCollection.document(userId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        User user = documentSnapshot.toObject(User.class);
                        listener.onSuccess(user);
                    } else {
                        listener.onError(new Exception("User not found"));
                    }
                })
                .addOnFailureListener(e -> listener.onError(e));
    }

    @Override
    public void getUserByPhoneNumber(String phoneNumber, OnUserFetchedListener listener) {
        usersCollection.whereEqualTo("phoneNumber", phoneNumber).limit(1).get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        User user = queryDocumentSnapshots.getDocuments().get(0).toObject(User.class);
                        listener.onSuccess(user);
                    } else {
                        listener.onError(new Exception("User not found"));
                    }
                })
                .addOnFailureListener(e -> listener.onError(e));
    }

    @Override
    public void saveUser(User user, OnCompleteListener listener) {
        if (user.getUserId() == null) {
            // Create new document with auto-generated ID
            String userId = usersCollection.document().getId();
            user.setUserId(userId);
        }

        usersCollection.document(user.getUserId()).set(user)
                .addOnSuccessListener(aVoid -> listener.onSuccess())
                .addOnFailureListener(e -> listener.onError(e));
    }

    @Override
    public void updateUser(User user, OnCompleteListener listener) {
        if (user.getUserId() == null) {
            listener.onError(new Exception("User ID cannot be null"));
            return;
        }

        usersCollection.document(user.getUserId()).set(user)
                .addOnSuccessListener(aVoid -> listener.onSuccess())
                .addOnFailureListener(e -> listener.onError(e));
    }

    @Override
    public void deleteUser(String userId, OnCompleteListener listener) {
        usersCollection.document(userId).delete()
                .addOnSuccessListener(aVoid -> listener.onSuccess())
                .addOnFailureListener(e -> listener.onError(e));
    }

    @Override
    public void getAllUsers(OnUserListFetchedListener listener) {
        usersCollection.get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<User> users = new ArrayList<>();
                    for (DocumentSnapshot snapshot : queryDocumentSnapshots) {
                        User user = snapshot.toObject(User.class);
                        users.add(user);
                    }
                    listener.onSuccess(users);
                })
                .addOnFailureListener(e -> listener.onError(e));
    }
}