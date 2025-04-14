package com.rescuereach.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Build;

/**
 * Utility class for network operations
 */
public class NetworkUtils {

    /**
     * Check if the device is currently online
     * @param context The application context
     * @return true if online, false otherwise
     */
    public static boolean isOnline(Context context) {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        if (connectivityManager == null) {
            return false;
        }

        NetworkCapabilities capabilities = connectivityManager
                .getNetworkCapabilities(connectivityManager.getActiveNetwork());

        return capabilities != null &&
                (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
    }

    /**
     * Get the current network type (WIFI, CELLULAR, etc)
     * @param context The application context
     * @return String describing the network type or "NONE" if offline
     */
    public static String getNetworkType(Context context) {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        if (connectivityManager == null) {
            return "NONE";
        }

        NetworkCapabilities capabilities = connectivityManager
                .getNetworkCapabilities(connectivityManager.getActiveNetwork());

        if (capabilities == null) {
            return "NONE";
        }

        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            return "WIFI";
        } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            return "CELLULAR";
        } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            return "ETHERNET";
        } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) {
            return "BLUETOOTH";
        } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
            return "VPN";
        } else {
            return "OTHER";
        }
    }

    /**
     * Check if the device has a high-bandwidth connection
     * @param context The application context
     * @return true if high-bandwidth (good for sending media), false otherwise
     */
    public static boolean isHighBandwidthConnection(Context context) {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        if (connectivityManager == null) {
            return false;
        }

        NetworkCapabilities capabilities = connectivityManager
                .getNetworkCapabilities(connectivityManager.getActiveNetwork());

        if (capabilities == null) {
            return false;
        }

        // Either high bandwidth or unmetered is suitable for media uploads
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) ||
                capabilities.getLinkDownstreamBandwidthKbps() >= 1000; // At least 1Mbps
    }
}