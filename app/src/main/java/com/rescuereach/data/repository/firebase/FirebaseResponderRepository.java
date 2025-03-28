package com.rescuereach.data.repository.firebase;

import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.rescuereach.data.model.Responder;
import com.rescuereach.data.repository.OnCompleteListener;
import com.rescuereach.data.repository.ResponderRepository;

import java.util.ArrayList;
import java.util.List;

public class FirebaseResponderRepository implements ResponderRepository {
    private static final String COLLECTION_RESPONDERS = "responders";
    private final CollectionReference respondersCollection;

    public FirebaseResponderRepository() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        respondersCollection = db.collection(COLLECTION_RESPONDERS);
    }

    @Override
    public void getResponderById(String responderId, OnResponderFetchedListener listener) {
        respondersCollection.document(responderId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Responder responder = documentSnapshot.toObject(Responder.class);
                        listener.onSuccess(responder);
                    } else {
                        listener.onError(new Exception("Responder not found"));
                    }
                })
                .addOnFailureListener(e -> listener.onError(e));
    }

    @Override
    public void getResponderByUsername(String username, OnResponderFetchedListener listener) {
        respondersCollection.whereEqualTo("username", username).limit(1).get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        Responder responder = queryDocumentSnapshots.getDocuments().get(0).toObject(Responder.class);
                        listener.onSuccess(responder);
                    } else {
                        listener.onError(new Exception("Responder not found"));
                    }
                })
                .addOnFailureListener(e -> listener.onError(e));
    }

    @Override
    public void getRespondersByRole(Responder.ResponderRole role, OnResponderListFetchedListener listener) {
        respondersCollection.whereEqualTo("role", role).get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<Responder> responders = new ArrayList<>();
                    for (DocumentSnapshot snapshot : queryDocumentSnapshots) {
                        Responder responder = snapshot.toObject(Responder.class);
                        responders.add(responder);
                    }
                    listener.onSuccess(responders);
                })
                .addOnFailureListener(e -> listener.onError(e));
    }

    @Override
    public void getAllResponders(OnResponderListFetchedListener listener) {
        respondersCollection.get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<Responder> responders = new ArrayList<>();
                    for (DocumentSnapshot snapshot : queryDocumentSnapshots) {
                        Responder responder = snapshot.toObject(Responder.class);
                        responders.add(responder);
                    }
                    listener.onSuccess(responders);
                })
                .addOnFailureListener(e -> listener.onError(e));
    }

    @Override
    public void saveResponder(Responder responder, OnCompleteListener listener) {
        if (responder.getResponderId() == null) {
            // Create new document with auto-generated ID
            String responderId = respondersCollection.document().getId();
            responder.setResponderId(responderId);
        }

        respondersCollection.document(responder.getResponderId()).set(responder)
                .addOnSuccessListener(aVoid -> listener.onSuccess())
                .addOnFailureListener(e -> listener.onError(e));
    }

    @Override
    public void updateResponder(Responder responder, OnCompleteListener listener) {
        if (responder.getResponderId() == null) {
            listener.onError(new Exception("Responder ID cannot be null"));
            return;
        }

        respondersCollection.document(responder.getResponderId()).set(responder)
                .addOnSuccessListener(aVoid -> listener.onSuccess())
                .addOnFailureListener(e -> listener.onError(e));
    }

    @Override
    public void deleteResponder(String responderId, OnCompleteListener listener) {
        respondersCollection.document(responderId).delete()
                .addOnSuccessListener(aVoid -> listener.onSuccess())
                .addOnFailureListener(e -> listener.onError(e));
    }
}