package com.rescuereach;

import android.app.Application;
import android.content.Context;

import com.google.firebase.FirebaseApp;
import com.rescuereach.service.auth.UserSessionManager;

public class RescueReachApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // Initialize Firebase
        FirebaseApp.initializeApp(this);
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
    }

    @Override
    public void onTerminate() {
        // Unregister callbacks
        try {
            UserSessionManager.getInstance(this).unregisterNetworkCallback();
        } catch (Exception e) {
            // Ignore
        }
        super.onTerminate();
    }
}