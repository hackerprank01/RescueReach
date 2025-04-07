package com.rescuereach.util;

import android.content.Context;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

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

    /**
     * Get a descriptive network status string
     */
    public String getNetworkStatusString() {
        if (!isNetworkAvailable()) {
            return "Offline";
        }

        NetworkInfo activeNetwork = systemConnectivityManager.getActiveNetworkInfo();
        if (activeNetwork == null) {
            return "Unknown";
        }

        switch (activeNetwork.getType()) {
            case android.net.ConnectivityManager.TYPE_WIFI:
                return "WiFi";
            case android.net.ConnectivityManager.TYPE_MOBILE:
                return "Mobile Data";
            case android.net.ConnectivityManager.TYPE_ETHERNET:
                return "Ethernet";
            default:
                return "Connected";
        }
    }

    /**
     * Check if we have a high-speed connection
     */
    public boolean isHighSpeedConnection() {
        if (!isNetworkAvailable()) {
            return false;
        }

        NetworkInfo activeNetwork = systemConnectivityManager.getActiveNetworkInfo();
        if (activeNetwork == null) {
            return false;
        }

        switch (activeNetwork.getType()) {
            case android.net.ConnectivityManager.TYPE_WIFI:
            case android.net.ConnectivityManager.TYPE_ETHERNET:
                return true;
            case android.net.ConnectivityManager.TYPE_MOBILE:
                int subType = activeNetwork.getSubtype();
                switch (subType) {
                    case android.telephony.TelephonyManager.NETWORK_TYPE_LTE:
                    case android.telephony.TelephonyManager.NETWORK_TYPE_NR: // 5G
                    case android.telephony.TelephonyManager.NETWORK_TYPE_HSPAP:
                        return true;
                    default:
                        return false;
                }
            default:
                return false;
        }
    }
}