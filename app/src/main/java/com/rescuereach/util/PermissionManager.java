package com.rescuereach.util;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import android.util.SparseArray;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Centralized permission management system for handling runtime permissions
 * Handles requesting, tracking, and explaining permissions
 */
public class PermissionManager {
    private static final String TAG = "PermissionManager";

    // Permission types
    public static final String PERMISSION_LOCATION = Manifest.permission.ACCESS_FINE_LOCATION;
    public static final String PERMISSION_COARSE_LOCATION = Manifest.permission.ACCESS_COARSE_LOCATION;
    public static final String PERMISSION_BACKGROUND_LOCATION = "android.permission.ACCESS_BACKGROUND_LOCATION"; // API 29+
    public static final String PERMISSION_SEND_SMS = Manifest.permission.SEND_SMS;
    public static final String PERMISSION_RECEIVE_SMS = Manifest.permission.RECEIVE_SMS;
    public static final String PERMISSION_CAMERA = Manifest.permission.CAMERA;
    public static final String PERMISSION_READ_STORAGE = Manifest.permission.READ_EXTERNAL_STORAGE;
    public static final String PERMISSION_WRITE_STORAGE = Manifest.permission.WRITE_EXTERNAL_STORAGE;
    public static final String PERMISSION_RECORD_AUDIO = Manifest.permission.RECORD_AUDIO;
    public static final String PERMISSION_READ_CONTACTS = Manifest.permission.READ_CONTACTS;

    // Permission groups for requesting multiple permissions
    public static final String[] PERMISSIONS_LOCATION = {
            PERMISSION_LOCATION,
            PERMISSION_COARSE_LOCATION
    };

    public static final String[] PERMISSIONS_LOCATION_BACKGROUND = {
            PERMISSION_LOCATION,
            PERMISSION_COARSE_LOCATION,
            PERMISSION_BACKGROUND_LOCATION
    };

    public static final String[] PERMISSIONS_SMS = {
            PERMISSION_SEND_SMS,
            PERMISSION_RECEIVE_SMS
    };

    public static final String[] PERMISSIONS_MEDIA = {
            PERMISSION_READ_STORAGE,
            PERMISSION_WRITE_STORAGE,
            PERMISSION_CAMERA
    };

    // Request codes
    public static final int RC_LOCATION = 1001;
    public static final int RC_BACKGROUND_LOCATION = 1002;
    public static final int RC_SMS = 1003;
    public static final int RC_CAMERA = 1004;
    public static final int RC_STORAGE = 1005;
    public static final int RC_MICROPHONE = 1006;
    public static final int RC_CONTACTS = 1007;
    public static final int RC_MULTIPLE = 1008;

    // Shared preferences
    private static final String PREF_PERMISSIONS = "rescue_reach_permissions";
    private static final String KEY_DENIED_COUNT = "denied_count_";

    // Singleton instance
    private static PermissionManager instance;

    // Context
    private final Context context;
    private final SharedPreferences preferences;

    // Maps to track pending permission requests
    private final Map<Integer, PermissionCallback> callbacks = new HashMap<>();
    private final Map<Integer, String[]> pendingPermissions = new HashMap<>();
    private final SparseArray<String> pendingRationales = new SparseArray<>();

    /**
     * Private constructor for singleton pattern
     */
    private PermissionManager(Context context) {
        this.context = context.getApplicationContext();
        this.preferences = context.getSharedPreferences(PREF_PERMISSIONS, Context.MODE_PRIVATE);
    }

    /**
     * Get the singleton instance of PermissionManager
     */
    public static synchronized PermissionManager getInstance(Context context) {
        if (instance == null) {
            instance = new PermissionManager(context);
        }
        return instance;
    }

    /**
     * Check if a single permission is granted
     * @param permission The permission to check (e.g., Manifest.permission.CAMERA)
     * @return True if permission is granted, false otherwise
     */
    public boolean hasPermission(String permission) {
        return ContextCompat.checkSelfPermission(context, permission)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Check if all permissions in a group are granted
     * @param permissions Array of permissions to check
     * @return True if all permissions are granted, false otherwise
     */
    public boolean hasPermissions(String[] permissions) {
        for (String permission : permissions) {
            if (!hasPermission(permission)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Request a single permission with a callback and optional rationale
     * @param activity The activity to request permission from
     * @param permission The permission to request
     * @param requestCode A unique request code
     * @param rationale An optional explanation to show if permission was previously denied
     * @param callback Callback to handle permission result
     */
    public void requestPermission(Activity activity, String permission, int requestCode,
                                  String rationale, PermissionCallback callback) {
        requestPermissions(activity, new String[]{permission}, requestCode, rationale, callback);
    }

    /**
     * Request multiple permissions with a callback and optional rationale
     * @param activity The activity to request permissions from
     * @param permissions The permissions to request
     * @param requestCode A unique request code
     * @param rationale An optional explanation to show if permissions were previously denied
     * @param callback Callback to handle permission results
     */
    public void requestPermissions(Activity activity, String[] permissions, int requestCode,
                                   String rationale, PermissionCallback callback) {
        // Skip if activity is null
        if (activity == null) {
            Log.e(TAG, "Activity is null, cannot request permissions");
            if (callback != null) {
                callback.onPermissionResult(false, Arrays.asList(permissions), new ArrayList<>());
            }
            return;
        }

        // Check if we already have permissions
        List<String> missingPermissions = new ArrayList<>();
        for (String permission : permissions) {
            if (!hasPermission(permission)) {
                missingPermissions.add(permission);
            }
        }

        // If no missing permissions, return success
        if (missingPermissions.isEmpty()) {
            if (callback != null) {
                callback.onPermissionResult(true, Arrays.asList(permissions), missingPermissions);
            }
            return;
        }

        // Store callback and pending permissions for later use
        if (callback != null) {
            callbacks.put(requestCode, callback);
            pendingPermissions.put(requestCode, permissions);
        }

        // Check if we need to show a rationale for any permission
        boolean shouldShowRationale = false;
        for (String permission : missingPermissions) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)) {
                shouldShowRationale = true;
                break;
            }
        }

        // Show rationale if needed
        if (shouldShowRationale && rationale != null && !rationale.isEmpty()) {
            pendingRationales.put(requestCode, rationale);
            showRationale(activity, rationale, requestCode, missingPermissions.toArray(new String[0]));
        } else {
            // Request permissions directly
            ActivityCompat.requestPermissions(activity,
                    missingPermissions.toArray(new String[0]),
                    requestCode);
        }
    }

    /**
     * Request permissions from a fragment
     * @param fragment The fragment to request permissions from
     * @param permissions The permissions to request
     * @param requestCode A unique request code
     * @param rationale An optional explanation to show if permissions were previously denied
     * @param callback Callback to handle permission results
     */
    public void requestPermissionsFromFragment(Fragment fragment, String[] permissions, int requestCode,
                                               String rationale, PermissionCallback callback) {
        if (fragment == null || !fragment.isAdded() || fragment.getActivity() == null) {
            Log.e(TAG, "Fragment is null or not attached, cannot request permissions");
            if (callback != null) {
                callback.onPermissionResult(false, Arrays.asList(permissions), new ArrayList<>());
            }
            return;
        }

        // Store callback and pending permissions
        if (callback != null) {
            callbacks.put(requestCode, callback);
            pendingPermissions.put(requestCode, permissions);
        }

        // Check for missing permissions
        List<String> missingPermissions = new ArrayList<>();
        for (String permission : permissions) {
            if (!hasPermission(permission)) {
                missingPermissions.add(permission);
            }
        }

        // If no missing permissions, return success
        if (missingPermissions.isEmpty()) {
            if (callback != null) {
                callback.onPermissionResult(true, Arrays.asList(permissions), missingPermissions);
            }
            return;
        }

        // Check if we need to show rationale
        boolean shouldShowRationale = false;
        for (String permission : missingPermissions) {
            if (fragment.shouldShowRequestPermissionRationale(permission)) {
                shouldShowRationale = true;
                break;
            }
        }

        if (shouldShowRationale && rationale != null && !rationale.isEmpty()) {
            pendingRationales.put(requestCode, rationale);
            showRationaleFromFragment(fragment, rationale, requestCode,
                    missingPermissions.toArray(new String[0]));
        } else {
            // Request permissions directly
            fragment.requestPermissions(missingPermissions.toArray(new String[0]), requestCode);
        }
    }

    /**
     * Handle permission result from Activity.onRequestPermissionsResult()
     * @param requestCode The request code passed to requestPermissions()
     * @param permissions The requested permissions
     * @param grantResults The grant results
     */
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        PermissionCallback callback = callbacks.get(requestCode);
        String[] requestedPermissions = pendingPermissions.get(requestCode);

        // Check if we have a callback and requested permissions
        if (callback != null && requestedPermissions != null) {
            List<String> grantedPermissions = new ArrayList<>();
            List<String> deniedPermissions = new ArrayList<>();

            // Process results
            for (int i = 0; i < permissions.length; i++) {
                String permission = permissions[i];
                if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    grantedPermissions.add(permission);
                } else {
                    deniedPermissions.add(permission);
                    incrementDenialCount(permission);
                }
            }

            // Invoke callback
            boolean allGranted = deniedPermissions.isEmpty();
            callback.onPermissionResult(allGranted, grantedPermissions, deniedPermissions);

            // Clean up
            callbacks.remove(requestCode);
            pendingPermissions.remove(requestCode);
            pendingRationales.remove(requestCode);
        }
    }

    /**
     * Show permission rationale dialog
     */
    private void showRationale(Activity activity, String rationale, int requestCode, String[] permissions) {
        new AlertDialog.Builder(activity)
                .setTitle("Permission Required")
                .setMessage(rationale)
                .setPositiveButton("Grant", (dialog, which) -> {
                    dialog.dismiss();
                    ActivityCompat.requestPermissions(activity, permissions, requestCode);
                })
                .setNegativeButton("Deny", (dialog, which) -> {
                    dialog.dismiss();
                    PermissionCallback callback = callbacks.get(requestCode);
                    if (callback != null) {
                        callback.onPermissionResult(false, new ArrayList<>(), Arrays.asList(permissions));
                        callbacks.remove(requestCode);
                        pendingPermissions.remove(requestCode);
                        pendingRationales.remove(requestCode);
                    }
                })
                .setCancelable(false)
                .show();
    }

    /**
     * Show permission rationale dialog from fragment
     */
    private void showRationaleFromFragment(Fragment fragment, String rationale, int requestCode,
                                           String[] permissions) {
        if (fragment.getContext() == null) return;

        new AlertDialog.Builder(fragment.getContext())
                .setTitle("Permission Required")
                .setMessage(rationale)
                .setPositiveButton("Grant", (dialog, which) -> {
                    dialog.dismiss();
                    fragment.requestPermissions(permissions, requestCode);
                })
                .setNegativeButton("Deny", (dialog, which) -> {
                    dialog.dismiss();
                    PermissionCallback callback = callbacks.get(requestCode);
                    if (callback != null) {
                        callback.onPermissionResult(false, new ArrayList<>(), Arrays.asList(permissions));
                        callbacks.remove(requestCode);
                        pendingPermissions.remove(requestCode);
                        pendingRationales.remove(requestCode);
                    }
                })
                .setCancelable(false)
                .show();
    }

    /**
     * Show a dialog to direct user to app settings when permission has been permanently denied
     * @param activity The activity
     * @param message Message explaining why permission is needed
     */
    public void showSettingsDialog(Activity activity, String message) {
        if (activity == null || activity.isFinishing()) return;

        new AlertDialog.Builder(activity)
                .setTitle("Permission Required")
                .setMessage(message + "\n\nPlease enable it in app settings.")
                .setPositiveButton("Settings", (dialog, which) -> {
                    dialog.dismiss();
                    openAppSettings(activity);
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .setCancelable(true)
                .show();
    }

    /**
     * Open app settings screen
     */
    public void openAppSettings(Activity activity) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", activity.getPackageName(), null);
        intent.setData(uri);
        activity.startActivity(intent);
    }

    /**
     * Track permission denial count to determine when to show settings dialog
     * @param permission The permission that was denied
     */
    private void incrementDenialCount(String permission) {
        String key = KEY_DENIED_COUNT + permission;
        int count = preferences.getInt(key, 0);
        preferences.edit().putInt(key, count + 1).apply();
    }

    /**
     * Check if a permission has been denied multiple times
     * @param permission The permission to check
     * @return True if the permission has been denied multiple times
     */
    public boolean isPermissionPermanentlyDenied(String permission) {
        String key = KEY_DENIED_COUNT + permission;
        int count = preferences.getInt(key, 0);
        return count >= 2;
    }

    /**
     * Get a map of multiple permissions status
     * @param permissions Array of permissions to check
     * @return Map of permission to boolean (granted/denied)
     */
    public Map<String, Boolean> getPermissionsStatus(String[] permissions) {
        Map<String, Boolean> status = new HashMap<>();
        for (String permission : permissions) {
            status.put(permission, hasPermission(permission));
        }
        return status;
    }

    /**
     * Check if SMS permissions are granted for direct SMS capability
     * @return True if all SMS permissions are granted
     */
    public boolean hasSmsPermissions() {
        return hasPermissions(PERMISSIONS_SMS);
    }

    /**
     * Check if location permissions are granted
     * @param includeBackground Whether to check for background location permission
     * @return True if all required location permissions are granted
     */
    public boolean hasLocationPermissions(boolean includeBackground) {
        boolean hasBasicLocation = hasPermissions(PERMISSIONS_LOCATION);

        if (!includeBackground) {
            return hasBasicLocation;
        }

        // Check for background location on Android 10+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return hasBasicLocation && hasPermission(PERMISSION_BACKGROUND_LOCATION);
        } else {
            return hasBasicLocation;
        }
    }

    /**
     * Request location permissions
     * @param activity The activity
     * @param includeBackground Whether to include background location permission
     * @param callback Callback for permission result
     */
    public void requestLocationPermissions(Activity activity, boolean includeBackground,
                                           PermissionCallback callback) {
        String rationale = "Location permission is needed to send your coordinates " +
                "during emergencies and to find nearby emergency services.";

        if (includeBackground && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // For Android 10+, request in two steps
            requestPermissions(activity, PERMISSIONS_LOCATION, RC_LOCATION, rationale,
                    new PermissionCallback() {
                        @Override
                        public void onPermissionResult(boolean isGranted, List<String> granted,
                                                       List<String> denied) {
                            if (isGranted) {
                                // Now request background location
                                String backgroundRationale = "Background location access is needed " +
                                        "to continuously update your location during emergencies, " +
                                        "even when the app is not in use.";

                                requestPermission(activity, PERMISSION_BACKGROUND_LOCATION,
                                        RC_BACKGROUND_LOCATION, backgroundRationale, callback);
                            } else {
                                if (callback != null) {
                                    callback.onPermissionResult(false, granted, denied);
                                }
                            }
                        }
                    });
        } else {
            // For older Android versions, request all at once
            requestPermissions(activity, PERMISSIONS_LOCATION, RC_LOCATION, rationale, callback);
        }
    }

    /**
     * Request SMS permissions
     * @param activity The activity
     * @param callback Callback for permission result
     */
    public void requestSmsPermissions(Activity activity, PermissionCallback callback) {
        String rationale = "SMS permission is needed to send emergency alerts when " +
                "there is no internet connection available.";
        requestPermissions(activity, PERMISSIONS_SMS, RC_SMS, rationale, callback);
    }

    /**
     * Request camera permission
     * @param activity The activity
     * @param callback Callback for permission result
     */
    public void requestCameraPermission(Activity activity, PermissionCallback callback) {
        String rationale = "Camera permission is needed to capture photos of incidents.";
        requestPermission(activity, PERMISSION_CAMERA, RC_CAMERA, rationale, callback);
    }

    /**
     * Request storage permissions
     * @param activity The activity
     * @param callback Callback for permission result
     */
    public void requestStoragePermissions(Activity activity, PermissionCallback callback) {
        String rationale = "Storage permission is needed to save and access photos and files " +
                "related to emergency reports.";
        String[] permissions = {PERMISSION_READ_STORAGE, PERMISSION_WRITE_STORAGE};
        requestPermissions(activity, permissions, RC_STORAGE, rationale, callback);
    }

    /**
     * Request microphone permission
     * @param activity The activity
     * @param callback Callback for permission result
     */
    public void requestMicrophonePermission(Activity activity, PermissionCallback callback) {
        String rationale = "Microphone permission is needed to record audio during " +
                "incident reporting.";
        requestPermission(activity, PERMISSION_RECORD_AUDIO, RC_MICROPHONE, rationale, callback);
    }

    /**
     * Request contacts permission
     * @param activity The activity
     * @param callback Callback for permission result
     */
    public void requestContactsPermission(Activity activity, PermissionCallback callback) {
        String rationale = "Contacts permission is needed to select emergency contacts " +
                "from your address book.";
        requestPermission(activity, PERMISSION_READ_CONTACTS, RC_CONTACTS, rationale, callback);
    }

    /**
     * Clear all permission caches
     */
    public void clearPermissionCache() {
        callbacks.clear();
        pendingPermissions.clear();
        pendingRationales.clear();
    }

    /**
     * Interface for permission callbacks
     */
    public interface PermissionCallback {
        void onPermissionResult(boolean isGranted, List<String> granted, List<String> denied);
    }
}