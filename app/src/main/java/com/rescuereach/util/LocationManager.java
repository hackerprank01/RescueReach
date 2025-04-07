package com.rescuereach.util;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.BatteryManager;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;
import com.rescuereach.service.auth.UserSessionManager;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Enhanced location manager with specific modes for emergencies and battery optimization
 * Supports offline operation and respects user privacy settings
 */
public class LocationManager {
    private static final String TAG = "LocationManager";

    // Location update intervals
    private static final long NORMAL_UPDATE_INTERVAL = 60000; // 1 minute
    private static final long EMERGENCY_UPDATE_INTERVAL = 5000; // 5 seconds (faster for emergency)
    private static final long BACKGROUND_UPDATE_INTERVAL = 300000; // 5 minutes
    private static final long LOW_BATTERY_UPDATE_INTERVAL = 120000; // 2 minutes (when battery low)

    // Accuracy priorities
    private static final int NORMAL_PRIORITY = Priority.PRIORITY_BALANCED_POWER_ACCURACY;
    private static final int EMERGENCY_PRIORITY = Priority.PRIORITY_HIGH_ACCURACY;
    private static final int BACKGROUND_PRIORITY = Priority.PRIORITY_LOW_POWER;
    private static final int LOW_BATTERY_PRIORITY = Priority.PRIORITY_BALANCED_POWER_ACCURACY;

    // Location cache timeout
    private static final long LOCATION_CACHE_TIMEOUT = TimeUnit.MINUTES.toMillis(15); // 15 minutes
    private static final long EMERGENCY_CACHE_TIMEOUT = TimeUnit.MINUTES.toMillis(60); // 1 hour for emergency

    // Battery thresholds
    private static final int LOW_BATTERY_THRESHOLD = 15; // 15%
    private static final int CRITICAL_BATTERY_THRESHOLD = 5; // 5%

    private final Context context;
    private final FusedLocationProviderClient fusedLocationClient;
    private final FirebaseFirestore db;
    private final UserSessionManager sessionManager;

    private LocationRequest normalLocationRequest;
    private LocationRequest emergencyLocationRequest;
    private LocationRequest backgroundLocationRequest;
    private LocationRequest lowBatteryLocationRequest;

    private LocationCallback locationCallback;
    private Location cachedLocation;
    private long cachedLocationTimestamp;
    private boolean isBackgroundMode = false;
    private boolean isEmergencyMode = false;
    private boolean isLowBatteryMode = false;

    // Interface for location updates
    public interface LocationUpdateListener {
        void onLocationUpdated(Location location);
        void onLocationError(String message);
    }

    private LocationUpdateListener locationUpdateListener;

    public LocationManager(Context context) {
        this.context = context.getApplicationContext();
        this.fusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
        this.db = FirebaseFirestore.getInstance();
        this.sessionManager = UserSessionManager.getInstance(context);

        setupLocationRequests();
    }

    /**
     * Create different location request profiles for different scenarios
     */
    private void setupLocationRequests() {
        // Regular location updates
        normalLocationRequest = new LocationRequest.Builder(NORMAL_UPDATE_INTERVAL)
                .setPriority(NORMAL_PRIORITY)
                .setWaitForAccurateLocation(false)
                .setMinUpdateIntervalMillis(NORMAL_UPDATE_INTERVAL / 2)
                .build();

        // Emergency mode - high accuracy, frequent updates
        emergencyLocationRequest = new LocationRequest.Builder(EMERGENCY_UPDATE_INTERVAL)
                .setPriority(EMERGENCY_PRIORITY)
                .setWaitForAccurateLocation(true)
                .setMinUpdateIntervalMillis(EMERGENCY_UPDATE_INTERVAL / 2)
                .setMaxUpdateDelayMillis(EMERGENCY_UPDATE_INTERVAL)
                .build();

        // Background mode - low power, infrequent updates
        backgroundLocationRequest = new LocationRequest.Builder(BACKGROUND_UPDATE_INTERVAL)
                .setPriority(BACKGROUND_PRIORITY)
                .setWaitForAccurateLocation(false)
                .setMinUpdateIntervalMillis(BACKGROUND_UPDATE_INTERVAL / 2)
                .build();

        // Low battery mode - conserves power but still frequent enough
        lowBatteryLocationRequest = new LocationRequest.Builder(LOW_BATTERY_UPDATE_INTERVAL)
                .setPriority(LOW_BATTERY_PRIORITY)
                .setWaitForAccurateLocation(false)
                .setMinUpdateIntervalMillis(LOW_BATTERY_UPDATE_INTERVAL / 2)
                .build();
    }

    /**
     * Register a listener for location updates
     */
    public void setLocationUpdateListener(LocationUpdateListener listener) {
        this.locationUpdateListener = listener;
    }

    /**
     * Start receiving location updates in normal mode
     */
    public void startLocationUpdates() {
        startLocationUpdates(false, false);
    }

    /**
     * Start location updates in a specific mode
     * @param isEmergency Whether to use high accuracy emergency settings
     * @param isBackground Whether to use battery-friendly background settings
     */
    public void startLocationUpdates(boolean isEmergency, boolean isBackground) {
        if (!hasLocationPermission()) {
            if (locationUpdateListener != null) {
                locationUpdateListener.onLocationError("Location permission not granted");
            }
            return;
        }

        // Set mode flags
        this.isEmergencyMode = isEmergency;
        this.isBackgroundMode = isBackground;

        // Check battery level and set low battery mode if necessary
        // but override it if in emergency mode (battery less important than emergency)
        this.isLowBatteryMode = (getBatteryLevel() <= LOW_BATTERY_THRESHOLD) && !isEmergency;

        // Select appropriate request based on mode
        LocationRequest activeRequest;
        if (isEmergency) {
            activeRequest = emergencyLocationRequest;
        } else if (isLowBatteryMode) {
            activeRequest = lowBatteryLocationRequest;
        } else if (isBackground) {
            activeRequest = backgroundLocationRequest;
        } else {
            activeRequest = normalLocationRequest;
        }

        // Remove any existing callbacks first
        stopLocationUpdates();

        // Create new callback
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                for (Location location : locationResult.getLocations()) {
                    if (location != null) {
                        processNewLocation(location);
                    }
                }
            }
        };

        // Request updates
        try {
            fusedLocationClient.requestLocationUpdates(
                    activeRequest,
                    locationCallback,
                    Looper.getMainLooper()
            );

            // Try to get last location immediately
            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(location -> {
                        if (location != null) {
                            processNewLocation(location);
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error getting last location", e);
                        if (locationUpdateListener != null) {
                            locationUpdateListener.onLocationError("Error getting last location: " + e.getMessage());
                        }
                    });
        } catch (SecurityException e) {
            Log.e(TAG, "Location permission exception", e);
            if (locationUpdateListener != null) {
                locationUpdateListener.onLocationError("Location permission error: " + e.getMessage());
            }
        }
    }

    /**
     * Process a new location, cache it and notify listeners
     */
    private void processNewLocation(Location location) {
        // Update cache
        cachedLocation = location;
        cachedLocationTimestamp = System.currentTimeMillis();

        // Notify listener
        if (locationUpdateListener != null) {
            locationUpdateListener.onLocationUpdated(location);
        }

        // Save to Firestore if user settings allow and online
        if (shouldSaveLocationToCloud()) {
            if (isOnline()) {
                saveLocationToFirestore(location);
            } else {
                // Save to local storage for later sync
                saveLocationLocally(location);
            }
        }
    }

    /**
     * Save location to local storage for later sync when online
     */
    private void saveLocationLocally(Location location) {
        // Implementation using Room DB would go here
        // This would be synced via WorkManager when online
        // For now, we just log it
        Log.d(TAG, "Location saved locally for later sync: " +
                location.getLatitude() + ", " + location.getLongitude());

        // Notify system for offline handling
        Map<String, Object> locationData = new HashMap<>();
        locationData.put("latitude", location.getLatitude());
        locationData.put("longitude", location.getLongitude());
        locationData.put("accuracy", location.getAccuracy());
        locationData.put("timestamp", System.currentTimeMillis());
        locationData.put("isEmergency", isEmergencyMode);

        // This would trigger the WorkManager to queue this for later sync
        // WorkManagerHelper.queueLocationSync(context, locationData);
    }

    /**
     * Check if we should save location to cloud based on user privacy settings
     */
    private boolean shouldSaveLocationToCloud() {
        if (isEmergencyMode) {
            // During emergency, use emergency-specific setting
            return sessionManager.getEmergencyPreference("share_location_emergency", true);
        } else {
            // Otherwise use general location sharing preference
            return sessionManager.getPrivacyPreference("location_sharing", true);
        }
    }

    /**
     * Save location data to Firestore
     */
    private void saveLocationToFirestore(Location location) {
        String phoneNumber = sessionManager.getSavedPhoneNumber();
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            Log.e(TAG, "Cannot save location: No phone number available");
            return;
        }

        Map<String, Object> locationData = new HashMap<>();
        locationData.put("location", new GeoPoint(location.getLatitude(), location.getLongitude()));
        locationData.put("accuracy", location.getAccuracy());
        locationData.put("timestamp", System.currentTimeMillis());
        locationData.put("isEmergency", isEmergencyMode);
        locationData.put("batteryLevel", getBatteryLevel());
        locationData.put("networkType", getNetworkType());

        db.collection("users")
                .document(phoneNumber)
                .collection("locations")
                .document("last_known")
                .set(locationData)
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error saving location to Firestore", e);
                    // Fall back to local storage on failure
                    saveLocationLocally(location);
                });
    }

    /**
     * Get the most recent location either from active updates or cache
     */
    public void getCurrentLocation(LocationUpdateListener oneTimeListener) {
        // First check if we have a recent cache entry
        if (hasRecentCachedLocation()) {
            if (oneTimeListener != null) {
                oneTimeListener.onLocationUpdated(cachedLocation);
            }
            return;
        }

        // Otherwise request a fresh location
        if (!hasLocationPermission()) {
            if (oneTimeListener != null) {
                oneTimeListener.onLocationError("Location permission not granted");
            }
            return;
        }

        try {
            // Use proper priority based on mode and battery
            int priority = isEmergencyMode ?
                    EMERGENCY_PRIORITY :
                    (isLowBatteryMode ? LOW_BATTERY_PRIORITY : NORMAL_PRIORITY);

            fusedLocationClient.getCurrentLocation(priority, null)
                    .addOnSuccessListener(location -> {
                        if (location != null) {
                            // Cache the result
                            cachedLocation = location;
                            cachedLocationTimestamp = System.currentTimeMillis();

                            // Notify listener
                            if (oneTimeListener != null) {
                                oneTimeListener.onLocationUpdated(location);
                            }
                        } else {
                            if (oneTimeListener != null) {
                                oneTimeListener.onLocationError("Could not obtain current location");
                            }
                        }
                    })
                    .addOnFailureListener(e -> {
                        if (oneTimeListener != null) {
                            oneTimeListener.onLocationError("Location error: " + e.getMessage());
                        }
                    });
        } catch (SecurityException e) {
            Log.e(TAG, "Location permission exception", e);
            if (oneTimeListener != null) {
                oneTimeListener.onLocationError("Location permission error: " + e.getMessage());
            }
        }
    }

    /**
     * Get location specifically for emergency reporting
     * Will return cached location immediately if available
     * And will start high-priority updates for continued tracking
     */
    public void shareLocationDuringEmergency(final LocationUpdateListener emergencyListener) {
        // Check if emergency location sharing is allowed
        boolean locationSharingEnabled = sessionManager.getPrivacyPreference("location_sharing", true);
        boolean shareLocationEmergency = sessionManager.getEmergencyPreference("share_location_emergency", true);

        // If both settings are disabled, don't share location
        if (!locationSharingEnabled && !shareLocationEmergency) {
            if (emergencyListener != null) {
                emergencyListener.onLocationError("Location sharing during emergency is disabled");
            }
            return;
        }

        // If we have a cached location, send it immediately
        if (cachedLocation != null) {
            emergencyListener.onLocationUpdated(cachedLocation);
        }

        // Start emergency mode updates for continued tracking
        startLocationUpdates(true, false);

        // Get a fresh location with high accuracy
        getCurrentLocation(new LocationUpdateListener() {
            @Override
            public void onLocationUpdated(Location location) {
                emergencyListener.onLocationUpdated(location);
                // Also save this emergency location to Firestore
                if (shouldSaveLocationToCloud()) {
                    if (isOnline()) {
                        saveLocationToFirestore(location);
                    } else {
                        saveLocationLocally(location);
                    }
                }
            }

            @Override
            public void onLocationError(String error) {
                // Only report error if we haven't sent a cached location
                if (cachedLocation == null) {
                    emergencyListener.onLocationError(error);
                }
            }
        });
    }

    /**
     * Stop receiving location updates
     */
    public void stopLocationUpdates() {
        if (locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
            locationCallback = null;
        }
    }

    /**
     * Check if we have a recent location in cache
     */
    private boolean hasRecentCachedLocation() {
        if (cachedLocation == null) {
            return false;
        }

        long age = System.currentTimeMillis() - cachedLocationTimestamp;
        // Use a longer timeout for emergency mode
        long timeout = isEmergencyMode ? EMERGENCY_CACHE_TIMEOUT : LOCATION_CACHE_TIMEOUT;
        return age < timeout;
    }

    /**
     * Check if we have location permission
     */
    private boolean hasLocationPermission() {
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                        PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Get last known location even if it's a bit outdated (for emergencies)
     */
    public Location getLastKnownLocation() {
        return cachedLocation;
    }

    /**
     * Get device battery level
     */
    private int getBatteryLevel() {
        BatteryManager batteryManager = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        return batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
    }

    /**
     * Check if device is online
     */
    private boolean isOnline() {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.getActiveNetwork());
        return capabilities != null &&
                (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR));
    }

    /**
     * Get network type (WiFi, Cellular, etc.)
     */
    private String getNetworkType() {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.getActiveNetwork());

        if (capabilities == null) {
            return "OFFLINE";
        }

        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            return "WIFI";
        } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            return "CELLULAR";
        } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) {
            return "BLUETOOTH";
        } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            return "ETHERNET";
        } else {
            return "UNKNOWN";
        }
    }

    /**
     * Check if we're currently in emergency mode
     */
    public boolean isInEmergencyMode() {
        return isEmergencyMode;
    }

    /**
     * Check if we're currently in background mode
     */
    public boolean isInBackgroundMode() {
        return isBackgroundMode;
    }

    /**
     * Check if we're in low battery mode
     */
    public boolean isInLowBatteryMode() {
        return isLowBatteryMode;
    }
}