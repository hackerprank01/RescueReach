package com.rescuereach.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;

import androidx.annotation.NonNull;

/**
 * Utility class to monitor network connectivity status
 */
public class NetworkManager {
    private final ConnectivityManager connectivityManager;
    private NetworkCallback networkCallback;
    private boolean isNetworkConnected = false;
    private NetworkStateListener networkStateListener;

    /**
     * Interface to receive network state changes
     */
    public interface NetworkStateListener {
        void onNetworkStateChanged(boolean isConnected);
    }

    public NetworkManager(Context context) {
        connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        checkInitialConnectionState();
    }

    /**
     * Check the initial connection state
     */
    private void checkInitialConnectionState() {
        if (connectivityManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Network network = connectivityManager.getActiveNetwork();
                NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
                isNetworkConnected = capabilities != null &&
                        (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
            } else {
                @SuppressWarnings("deprecation")
                android.net.NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
                isNetworkConnected = activeNetworkInfo != null && activeNetworkInfo.isConnected();
            }
        }
    }

    /**
     * Start monitoring network state
     * @param listener Callback for network state changes
     */
    public void startNetworkMonitoring(NetworkStateListener listener) {
        this.networkStateListener = listener;

        if (networkCallback == null) {
            networkCallback = new NetworkCallback();

            NetworkRequest networkRequest = new NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build();

            connectivityManager.registerNetworkCallback(networkRequest, networkCallback);

            // Immediately notify with current state
            if (networkStateListener != null) {
                networkStateListener.onNetworkStateChanged(isNetworkConnected);
            }
        }
    }

    /**
     * Stop monitoring network state
     */
    public void stopNetworkMonitoring() {
        if (networkCallback != null) {
            connectivityManager.unregisterNetworkCallback(networkCallback);
            networkCallback = null;
        }
        networkStateListener = null;
    }

    /**
     * Get current network connection state
     * @return true if connected, false otherwise
     */
    public boolean isNetworkConnected() {
        return isNetworkConnected;
    }

    /**
     * Network callback to monitor connection changes
     */
    private class NetworkCallback extends ConnectivityManager.NetworkCallback {
        @Override
        public void onAvailable(@NonNull Network network) {
            isNetworkConnected = true;
            if (networkStateListener != null) {
                networkStateListener.onNetworkStateChanged(true);
            }
        }

        @Override
        public void onLost(@NonNull Network network) {
            isNetworkConnected = false;
            if (networkStateListener != null) {
                networkStateListener.onNetworkStateChanged(false);
            }
        }
    }
}