package com.rescuereach.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings;

import java.util.UUID;

/**
 * Utility class for device-related functions
 */
public class DeviceUtils {

    private static final String PREFS_NAME = "device_info_prefs";
    private static final String DEVICE_ID_KEY = "device_installation_id";

    /**
     * Get a unique device ID that persists across app installs
     * @param context The application context
     * @return A unique device ID string
     */
    public static String getDeviceId(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        String deviceId = prefs.getString(DEVICE_ID_KEY, null);

        if (deviceId == null) {
            // Generate a new device ID
            deviceId = generateDeviceId(context);

            // Save it for future use
            prefs.edit().putString(DEVICE_ID_KEY, deviceId).apply();
        }

        return deviceId;
    }

    /**
     * Generate a device ID based on hardware and Android ID
     * @param context The application context
     * @return A unique device ID string
     */
    private static String generateDeviceId(Context context) {
        // Use a combination of hardware info and Android ID
        String androidId = Settings.Secure.getString(
                context.getContentResolver(), Settings.Secure.ANDROID_ID);

        if (androidId == null || androidId.equals("9774d56d682e549c") ||
                androidId.length() < 8) {
            // Android ID is not reliable on some devices, fallback to UUID
            androidId = UUID.randomUUID().toString();
        }

        // Combine with hardware info
        String hardwareInfo = Build.BOARD + Build.BRAND + Build.DEVICE + Build.MANUFACTURER +
                Build.MODEL + Build.PRODUCT + Build.HARDWARE;

        // Generate a UUID based on these values
        return UUID.nameUUIDFromBytes(
                (androidId + hardwareInfo).getBytes()).toString();
    }

    /**
     * Get device model name
     * @return The device model name
     */
    public static String getDeviceModel() {
        String manufacturer = Build.MANUFACTURER;
        String model = Build.MODEL;

        if (model.startsWith(manufacturer)) {
            return capitalize(model);
        } else {
            return capitalize(manufacturer) + " " + model;
        }
    }

    /**
     * Capitalize the first letter of a string
     */
    private static String capitalize(String s) {
        if (s == null || s.length() == 0) {
            return "";
        }
        char first = s.charAt(0);
        if (Character.isUpperCase(first)) {
            return s;
        } else {
            return Character.toUpperCase(first) + s.substring(1);
        }
    }
}