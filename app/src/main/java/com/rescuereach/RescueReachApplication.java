package com.rescuereach;

import android.app.Application;

import com.google.firebase.FirebaseApp;

public class RescueReachApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // Initialize Firebase
        FirebaseApp.initializeApp(this);
    }
}