package com.rescuereach.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Build;

/**
 * Utility class for network-related operations
 */
public class NetworkUtils {

    /**
     * Check if the device has an active network connection
     * @param context Application context
     * @return true if network is available, false otherwise
     */
    public static boolean isNetworkAvailable(Context context) {
        if (context == null) return false;

        ConnectivityManager connectivityManager = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);

        if (connectivityManager == null) return false;

        NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(
                connectivityManager.getActiveNetwork());

        return capabilities != null && (
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
    }

    /**
     * Get the current network type
     * @param context Application context
     * @return String representing the network type ("WIFI", "CELLULAR", "ETHERNET", "OTHER", or "NONE")
     */
    public static String getNetworkType(Context context) {
        if (context == null) return "NONE";

        ConnectivityManager connectivityManager = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);

        if (connectivityManager == null) return "NONE";

        NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(
                connectivityManager.getActiveNetwork());

        if (capabilities == null) return "NONE";

        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            return "WIFI";
        } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            return "CELLULAR";
        } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            return "ETHERNET";
        } else {
            return "OTHER";
        }
    }

    /**
     * Check if the device has a high-bandwidth connection
     * @param context Application context
     * @return true if high-bandwidth connection is available
     */
    public static boolean isHighBandwidthConnection(Context context) {
        if (context == null) return false;

        ConnectivityManager connectivityManager = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);

        if (connectivityManager == null) return false;

        NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(
                connectivityManager.getActiveNetwork());

        if (capabilities == null) return false;

        // Check if the connection is unmetered (typically WiFi) or has high bandwidth
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) ||
                capabilities.getLinkDownstreamBandwidthKbps() >= 1000;
    }
}