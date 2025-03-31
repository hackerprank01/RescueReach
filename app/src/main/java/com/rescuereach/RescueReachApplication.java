package com.rescuereach;

import android.app.Application;
import android.util.Log;

import com.google.firebase.FirebaseApp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.rescuereach.data.repository.RepositoryProvider;
import com.rescuereach.service.auth.AuthServiceProvider;

public class RescueReachApplication extends Application {
    private static final String TAG = "RescueReachApp";

    @Override
    public void onCreate() {
        super.onCreate();

        // Initialize Firebase
        initializeFirebase();

        // Initialize service providers
        initializeServiceProviders();

        Log.d(TAG, "Application initialized");
    }

    private void initializeFirebase() {
        // Initialize Firebase
        FirebaseApp.initializeApp(this);

        // Configure Firestore settings for offline persistence
        FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(true)
                .build();
        FirebaseFirestore.getInstance().setFirestoreSettings(settings);
    }

    private void initializeServiceProviders() {
        // Initialize the AuthServiceProvider
        AuthServiceProvider.getInstance();

        // Initialize the RepositoryProvider
        RepositoryProvider.getInstance();
    }
}