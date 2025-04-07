package com.rescuereach.util;

import android.content.Context;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.Looper;

/**
 * Utility class to monitor network connectivity
 */
public class ConnectivityManager {
    private static final String TAG = "ConnectivityManager";

    private final Context context;
    private final android.net.ConnectivityManager systemConnectivityManager;
    private ConnectivityListener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface ConnectivityListener {
        void onConnectivityChanged(boolean isConnected);
    }

    public ConnectivityManager(Context context) {
        this.context = context.getApplicationContext();
        this.systemConnectivityManager =
                (android.net.ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    /**
     * Check if network is currently available
     */
    public boolean isNetworkAvailable() {
        if (systemConnectivityManager == null) {
            return false;
        }

        NetworkInfo activeNetwork = systemConnectivityManager.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
    }

    /**
     * Set a listener to be notified of connectivity changes
     */
    public void setConnectivityListener(ConnectivityListener listener) {
        this.listener = listener;
    }

    /**
     * Update connectivity status to listeners
     */
    public void updateConnectivityStatus() {
        final boolean isConnected = isNetworkAvailable();

        if (listener != null) {
            mainHandler.post(() -> listener.onConnectivityChanged(isConnected));
        }
    }
}