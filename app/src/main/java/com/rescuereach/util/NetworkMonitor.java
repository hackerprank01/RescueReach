package com.rescuereach.util;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

/**
 * Monitors network connectivity status and changes
 */
public class NetworkMonitor {
    private static final String TAG = "NetworkMonitor";

    public enum ConnectionType {
        WIFI,
        CELLULAR,
        ETHERNET,
        VPN,
        OTHER,
        NONE
    }

    private final Context context;
    private final ConnectivityManager connectivityManager;
    private final MutableLiveData<Boolean> isConnectedLiveData = new MutableLiveData<>();
    private final MutableLiveData<ConnectionType> connectionTypeLiveData = new MutableLiveData<>();

    // For API 21+ (Lollipop and higher)
    private ConnectivityManager.NetworkCallback networkCallback;

    // For older APIs
    private BroadcastReceiver networkReceiver;

    // Singleton instance
    private static NetworkMonitor instance;

    /**
     * Get singleton instance
     */
    public static synchronized NetworkMonitor getInstance(Context context) {
        if (instance == null) {
            instance = new NetworkMonitor(context.getApplicationContext());
        }
        return instance;
    }

    private NetworkMonitor(Context context) {
        this.context = context;
        connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        // Initialize with current state
        boolean isConnected = checkCurrentConnection();
        isConnectedLiveData.setValue(isConnected);
        connectionTypeLiveData.setValue(getCurrentConnectionType());

        // Set up monitoring
        registerNetworkCallbacks();
    }

    /**
     * Register for network connectivity changes
     */
    private void registerNetworkCallbacks() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // API 24+ (Nougat and higher) - use NetworkCallback
            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(@NonNull Network network) {
                    super.onAvailable(network);
                    handleConnectionChange(true);
                }

                @Override
                public void onLost(@NonNull Network network) {
                    super.onLost(network);
                    handleConnectionChange(false);
                }

                @Override
                public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities capabilities) {
                    super.onCapabilitiesChanged(network, capabilities);
                    updateConnectionType(capabilities);
                }
            };

            NetworkRequest request = new NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build();

            connectivityManager.registerNetworkCallback(request, networkCallback);
        } else {
            // Older APIs - use BroadcastReceiver
            networkReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (ConnectivityManager.CONNECTIVITY_ACTION.equals(intent.getAction())) {
                        // Update connection status
                        boolean isConnected = checkCurrentConnection();
                        handleConnectionChange(isConnected);

                        // Update connection type
                        connectionTypeLiveData.postValue(getCurrentConnectionType());
                    }
                }
            };

            IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
            context.registerReceiver(networkReceiver, filter);
        }
    }

    /**
     * Unregister network callbacks to prevent leaks
     */
    public void unregisterNetworkCallbacks() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && networkCallback != null) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Error unregistering network callback", e);
            }
        } else if (networkReceiver != null) {
            try {
                context.unregisterReceiver(networkReceiver);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Error unregistering network receiver", e);
            }
        }
    }

    /**
     * Handle changes in connectivity
     */
    private void handleConnectionChange(boolean isConnected) {
        isConnectedLiveData.postValue(isConnected);

        if (isConnected) {
            connectionTypeLiveData.postValue(getCurrentConnectionType());
        } else {
            connectionTypeLiveData.postValue(ConnectionType.NONE);
        }

        Log.d(TAG, "Network connection changed: " + (isConnected ? "CONNECTED" : "DISCONNECTED"));
    }

    /**
     * Update the connection type based on NetworkCapabilities
     */
    private void updateConnectionType(NetworkCapabilities capabilities) {
        ConnectionType type = ConnectionType.OTHER;

        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            type = ConnectionType.WIFI;
        } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            type = ConnectionType.CELLULAR;
        } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            type = ConnectionType.ETHERNET;
        } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
            type = ConnectionType.VPN;
        }

        connectionTypeLiveData.postValue(type);
    }

    /**
     * Check current connection state
     */
    private boolean checkCurrentConnection() {
        if (connectivityManager == null) return false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network network = connectivityManager.getActiveNetwork();
            if (network == null) return false;

            NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
            return capabilities != null &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        } else {
            NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
            return networkInfo != null && networkInfo.isConnected();
        }
    }

    /**
     * Get current connection type
     */
    private ConnectionType getCurrentConnectionType() {
        if (connectivityManager == null) return ConnectionType.NONE;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network network = connectivityManager.getActiveNetwork();
            if (network == null) return ConnectionType.NONE;

            NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
            if (capabilities == null) return ConnectionType.NONE;

            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                return ConnectionType.WIFI;
            } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                return ConnectionType.CELLULAR;
            } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                return ConnectionType.ETHERNET;
            } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                return ConnectionType.VPN;
            } else {
                return ConnectionType.OTHER;
            }
        } else {
            NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
            if (networkInfo == null || !networkInfo.isConnected()) {
                return ConnectionType.NONE;
            }

            int type = networkInfo.getType();
            switch (type) {
                case ConnectivityManager.TYPE_WIFI:
                    return ConnectionType.WIFI;
                case ConnectivityManager.TYPE_MOBILE:
                    return ConnectionType.CELLULAR;
                case ConnectivityManager.TYPE_ETHERNET:
                    return ConnectionType.ETHERNET;
                case ConnectivityManager.TYPE_VPN:
                    return ConnectionType.VPN;
                default:
                    return ConnectionType.OTHER;
            }
        }
    }

    /**
     * Get LiveData for observing connection status
     */
    public LiveData<Boolean> getConnectionStatus() {
        return isConnectedLiveData;
    }

    /**
     * Get LiveData for observing connection type
     */
    public LiveData<ConnectionType> getConnectionType() {
        return connectionTypeLiveData;
    }

    /**
     * Get current connection state
     */
    public boolean isConnected() {
        Boolean value = isConnectedLiveData.getValue();
        return value != null && value;
    }

    /**
     * Get current connection type
     */
    public ConnectionType getActiveConnectionType() {
        ConnectionType value = connectionTypeLiveData.getValue();
        return value != null ? value : ConnectionType.NONE;
    }

    /**
     * Check if currently on WiFi
     */
    public boolean isOnWifi() {
        return getActiveConnectionType() == ConnectionType.WIFI;
    }

    /**
     * Check if on metered connection (usually cellular)
     */
    public boolean isOnMeteredConnection() {
        return getActiveConnectionType() == ConnectionType.CELLULAR;
    }

    /**
     * Interface for simpler callbacks
     */
    public interface NetworkStateListener {
        void onNetworkStateChanged(boolean isConnected);
        void onConnectionTypeChanged(ConnectionType connectionType);
    }

    /**
     * Register a listener for connection state changes
     */
    public void addNetworkStateListener(NetworkStateListener listener) {
        // Initial state
        listener.onNetworkStateChanged(isConnected());
        listener.onConnectionTypeChanged(getActiveConnectionType());

        // Future updates
        isConnectedLiveData.observeForever(isConnected ->
                listener.onNetworkStateChanged(isConnected)
        );

        connectionTypeLiveData.observeForever(connectionType ->
                listener.onConnectionTypeChanged(connectionType)
        );
    }
}