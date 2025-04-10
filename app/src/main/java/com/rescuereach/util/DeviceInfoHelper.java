package com.rescuereach.util;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.BatteryManager;
import android.os.Build;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * Helper class to collect device information for emergency reporting
 */
public class DeviceInfoHelper {
    private static final String TAG = "DeviceInfoHelper";

    private final Context context;

    public DeviceInfoHelper(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * Get battery information
     * @return Map containing battery level, charging status
     */
    public Map<String, Object> getBatteryInfo() {
        Map<String, Object> batteryInfo = new HashMap<>();

        try {
            IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus = context.registerReceiver(null, ifilter);

            if (batteryStatus != null) {
                int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                int batteryPct = (int) ((level / (float) scale) * 100);

                int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL;

                batteryInfo.put("level", batteryPct);
                batteryInfo.put("isCharging", isCharging);

                // Add charging type
                int chargePlug = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
                boolean usbCharge = chargePlug == BatteryManager.BATTERY_PLUGGED_USB;
                boolean acCharge = chargePlug == BatteryManager.BATTERY_PLUGGED_AC;
                boolean wirelessCharge = false;

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    wirelessCharge = chargePlug == BatteryManager.BATTERY_PLUGGED_WIRELESS;
                }

                if (usbCharge) {
                    batteryInfo.put("chargeType", "USB");
                } else if (acCharge) {
                    batteryInfo.put("chargeType", "AC");
                } else if (wirelessCharge) {
                    batteryInfo.put("chargeType", "WIRELESS");
                } else {
                    batteryInfo.put("chargeType", "UNKNOWN");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting battery info", e);
        }

        return batteryInfo;
    }

    /**
     * Get basic device information
     * @return Map containing device model, manufacturer, OS version
     */
    public Map<String, Object> getDeviceInfo() {
        Map<String, Object> deviceInfo = new HashMap<>();

        try {
            deviceInfo.put("model", Build.MODEL);
            deviceInfo.put("manufacturer", Build.MANUFACTURER);
            deviceInfo.put("device", Build.DEVICE);
            deviceInfo.put("product", Build.PRODUCT);
            deviceInfo.put("osVersion", Build.VERSION.RELEASE);
            deviceInfo.put("sdkVersion", Build.VERSION.SDK_INT);
            deviceInfo.put("hardware", Build.HARDWARE);

            String deviceId = Settings.Secure.getString(
                    context.getContentResolver(), Settings.Secure.ANDROID_ID);
            deviceInfo.put("deviceId", deviceId);

            // Get language and time zone
            deviceInfo.put("language", Locale.getDefault().getLanguage());
            deviceInfo.put("country", Locale.getDefault().getCountry());
            deviceInfo.put("timezone", TimeZone.getDefault().getID());
            deviceInfo.put("timezoneOffset", TimeZone.getDefault().getRawOffset() / 3600000);
        } catch (Exception e) {
            Log.e(TAG, "Error getting device info", e);
        }

        return deviceInfo;
    }

    /**
     * Get memory information
     * @return Map containing available memory, total memory
     */
    public Map<String, Object> getMemoryInfo() {
        Map<String, Object> memoryInfo = new HashMap<>();

        try {
            ActivityManager actManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
            actManager.getMemoryInfo(memInfo);

            long totalMemory = memInfo.totalMem;
            long availMemory = memInfo.availMem;
            boolean lowMemory = memInfo.lowMemory;

            memoryInfo.put("totalMemory", totalMemory / (1024 * 1024)); // MB
            memoryInfo.put("availableMemory", availMemory / (1024 * 1024)); // MB
            memoryInfo.put("lowMemory", lowMemory);

            // Get memory usage percentage
            double memoryUsage = (totalMemory - availMemory) * 100.0 / totalMemory;
            memoryInfo.put("memoryUsagePercent", (int) memoryUsage);
        } catch (Exception e) {
            Log.e(TAG, "Error getting memory info", e);
        }

        return memoryInfo;
    }

    /**
     * Get network-related information
     * @return Map containing network type, provider
     */
    public Map<String, Object> getNetworkInfo() {
        Map<String, Object> networkInfo = new HashMap<>();

        try {
            TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);

            // Get network operator name (carrier)
            String carrierName = tm.getNetworkOperatorName();
            if (carrierName != null && !carrierName.isEmpty()) {
                networkInfo.put("carrier", carrierName);
            } else {
                networkInfo.put("carrier", "Unknown");
            }

            // Get connection type
            ConnectivityManager connectivityManager = (ConnectivityManager)
                    context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkCapabilities capabilities = connectivityManager
                    .getNetworkCapabilities(connectivityManager.getActiveNetwork());

            boolean isConnected = capabilities != null;
            networkInfo.put("isConnected", isConnected);

            if (isConnected) {
                if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    networkInfo.put("connectionType", "WIFI");
                } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                    networkInfo.put("connectionType", "CELLULAR");
                } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                    networkInfo.put("connectionType", "ETHERNET");
                } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) {
                    networkInfo.put("connectionType", "BLUETOOTH");
                } else {
                    networkInfo.put("connectionType", "OTHER");
                }

                networkInfo.put("hasInternetCapability",
                        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET));
                networkInfo.put("hasNotMeteredCapability",
                        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED));
            } else {
                networkInfo.put("connectionType", "NONE");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting network info", e);
            networkInfo.put("error", e.getMessage());
        }

        return networkInfo;
    }

    /**
     * Get display information
     * @return Map containing screen resolution, density
     */
    public Map<String, Object> getDisplayInfo() {
        Map<String, Object> displayInfo = new HashMap<>();

        try {
            WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            DisplayMetrics metrics = new DisplayMetrics();
            wm.getDefaultDisplay().getMetrics(metrics);

            int width = metrics.widthPixels;
            int height = metrics.heightPixels;
            float density = metrics.density;

            displayInfo.put("screenWidth", width);
            displayInfo.put("screenHeight", height);
            displayInfo.put("screenDensity", density);
            displayInfo.put("screenDensityDpi", metrics.densityDpi);
        } catch (Exception e) {
            Log.e(TAG, "Error getting display info", e);
        }

        return displayInfo;
    }

    /**
     * Get all device information for emergency reporting
     * @return Map containing all device information
     */
    public Map<String, Object> getAllDeviceInfo() {
        Map<String, Object> allInfo = new HashMap<>();

        // Basic device info
        allInfo.putAll(getDeviceInfo());

        // Battery info
        Map<String, Object> batteryInfo = getBatteryInfo();
        allInfo.put("battery", batteryInfo);

        // Memory info
        Map<String, Object> memoryInfo = getMemoryInfo();
        allInfo.put("memory", memoryInfo);

        // Network info
        Map<String, Object> networkInfo = getNetworkInfo();
        allInfo.put("network", networkInfo);

        // Display info
        Map<String, Object> displayInfo = getDisplayInfo();
        allInfo.put("display", displayInfo);

        return allInfo;
    }
}