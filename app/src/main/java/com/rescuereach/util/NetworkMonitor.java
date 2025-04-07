package com.rescuereach.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

/**
 * Network connectivity monitoring utility using LiveData
 */
public class NetworkMonitor {
    private static final String TAG = "NetworkMonitor";

    // Singleton instance
    private static NetworkMonitor instance;

    // Android system connectivity manager
    private final ConnectivityManager connectivityManager;

    // LiveData for connection status
    private final MutableLiveData<Boolean> connectionStatus = new MutableLiveData<>();

    // LiveData for connection type (more detailed status)
    private final MutableLiveData<ConnectionType> connectionType = new MutableLiveData<>();

    // Handler for posting to main thread
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Connection type enum
    public enum ConnectionType {
        NONE,
        WIFI,
        CELLULAR,
        ETHERNET,
        BLUETOOTH,
        OTHER
    }

    // ConnectivityManager callback for network changes
    private final ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(@NonNull Network network) {
            super.onAvailable(network);
            updateNetworkStatus();
        }

        @Override
        public void onLost(@NonNull Network network) {
            super.onLost(network);
            updateNetworkStatus();
        }

        @Override
        public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities capabilities) {
            super.onCapabilitiesChanged(network, capabilities);
            updateNetworkStatus();
        }
    };

    /**
     * Private constructor
     */
    private NetworkMonitor(Context context) {
        connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        // Register for network callbacks
        NetworkRequest.Builder builder = new NetworkRequest.Builder();
        connectivityManager.registerNetworkCallback(builder.build(), networkCallback);

        // Set initial status
        updateNetworkStatus();
    }

    /**
     * Get singleton instance
     */
    public static synchronized NetworkMonitor getInstance(Context context) {
        if (instance == null) {
            instance = new NetworkMonitor(context.getApplicationContext());
        }
        return instance;
    }

    /**
     * Check current network connection status
     */
    public boolean isConnected() {
        if (connectivityManager == null) return false;

        Network activeNetwork = connectivityManager.getActiveNetwork();
        if (activeNetwork == null) return false;

        NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(activeNetwork);
        return capabilities != null && (
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH));
    }

    /**
     * Determine the current connection type
     */
    private ConnectionType getCurrentConnectionType() {
        if (!isConnected()) return ConnectionType.NONE;

        Network activeNetwork = connectivityManager.getActiveNetwork();
        NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(activeNetwork);

        if (capabilities == null) return ConnectionType.NONE;

        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            return ConnectionType.WIFI;
        } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            return ConnectionType.CELLULAR;
        } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            return ConnectionType.ETHERNET;
        } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) {
            return ConnectionType.BLUETOOTH;
        } else {
            return ConnectionType.OTHER;
        }
    }

    /**
     * Update network status and post to LiveData
     */
    private void updateNetworkStatus() {
        mainHandler.post(() -> {
            boolean connected = isConnected();
            ConnectionType type = getCurrentConnectionType();

            // Update LiveData values
            connectionStatus.setValue(connected);
            connectionType.setValue(type);

            Log.d(TAG, "Network status updated: connected=" + connected + ", type=" + type);
        });
    }

    /**
     * Get LiveData for connection status (true/false)
     */
    public LiveData<Boolean> getConnectionStatus() {
        return connectionStatus;
    }

    /**
     * Get LiveData for connection type (WIFI, CELLULAR, etc.)
     */
    public LiveData<ConnectionType> getConnectionType() {
        return connectionType;
    }

    /**
     * Force a status update
     */
    public void refreshStatus() {
        updateNetworkStatus();
    }
}