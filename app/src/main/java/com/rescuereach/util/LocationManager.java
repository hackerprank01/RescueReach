package com.rescuereach.util;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.BatteryManager;
import android.os.Handler;
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
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;
import com.rescuereach.service.auth.UserSessionManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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
    private final Handler mainHandler;

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
    private List<Location> pendingLocations = new ArrayList<>();

    public String getAddressFromLocation(Location location) throws IOException {
        Geocoder geocoder = new Geocoder(context, Locale.getDefault());
        List<Address> addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
        if (addresses != null && !addresses.isEmpty()) {
            Address address = addresses.get(0);
            StringBuilder addressString = new StringBuilder();
            addressString.append(address.getAddressLine(0)); // Full address
            if (address.getLocality() != null) {
                addressString.append(", ").append(address.getLocality()); // City
            }
            if (address.getAdminArea() != null) {
                addressString.append(", ").append(address.getAdminArea()); // State
            }
            if (address.getPostalCode() != null) {
                addressString.append(", ").append(address.getPostalCode()); // Postal code
            }
            return addressString.toString();
        } else {
            return "Unknown address";
        }
    }

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
        this.mainHandler = new Handler(Looper.getMainLooper());

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
                mainHandler.post(() -> locationUpdateListener.onLocationError("Location permission not granted"));
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
                            mainHandler.post(() -> locationUpdateListener.onLocationError("Error getting last location: " + e.getMessage()));
                        }
                    });
        } catch (SecurityException e) {
            Log.e(TAG, "Location permission exception", e);
            if (locationUpdateListener != null) {
                mainHandler.post(() -> locationUpdateListener.onLocationError("Location permission error: " + e.getMessage()));
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
            mainHandler.post(() -> locationUpdateListener.onLocationUpdated(location));
        }

        // Save to Firestore if user settings allow and online
        if (shouldSaveLocationToCloud()) {
            if (isOnline()) {
                saveLocationToFirestore(location);
            } else {
                // Save to local storage for later sync
                pendingLocations.add(location);
                saveLocationLocally(location);
            }
        }
    }

    /**
     * Check if any pending locations need to be synced and sync them
     */
    public void syncPendingLocations() {
        if (!isOnline() || pendingLocations.isEmpty()) {
            return;
        }

        // Copy to avoid concurrent modification
        List<Location> locationsToSync = new ArrayList<>(pendingLocations);
        pendingLocations.clear();

        // Save all pending locations
        for (Location location : locationsToSync) {
            saveLocationToFirestore(location);
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
        if (sessionManager == null) return false; // Safety check

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
        // Check if Firebase Auth is available to satisfy security rules
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        String userIdentifier;

        if (currentUser != null) {
            // Use Firebase Auth UID which satisfies security rules
            userIdentifier = currentUser.getUid();
        } else {
            // Fallback to phone number from session manager
            if (sessionManager == null) {
                Log.e(TAG, "SessionManager is null, cannot get user identifier");
                return;
            }

            userIdentifier = sessionManager.getSavedPhoneNumber();

            // If no identifier is available, we can't save the location
            if (userIdentifier == null || userIdentifier.isEmpty()) {
                Log.e(TAG, "Cannot save location: No user identifier available");

                // Add to pending locations to try again later
                if (!pendingLocations.contains(location)) {
                    pendingLocations.add(location);
                }
                return;
            }

            // Try to authenticate anonymously to satisfy Firestore rules
            tryAnonymousAuth();
        }

        // CRITICAL: Make sure we have the right collection/document structure
        // that matches our Firebase security rules

        // Create the location data
        Map<String, Object> locationData = new HashMap<>();
        locationData.put("location", new GeoPoint(location.getLatitude(), location.getLongitude()));
        locationData.put("accuracy", location.getAccuracy());
        locationData.put("timestamp", System.currentTimeMillis());
        locationData.put("isEmergency", isEmergencyMode);
        locationData.put("batteryLevel", getBatteryLevel());
        locationData.put("networkType", getNetworkType());

        // MOST IMPORTANT: Add the userId field to match security rule requirements
        // This is the field the security rules check
        locationData.put("userId", userIdentifier);

        // Store in system_status collection which should have less restrictive rules
        db.collection("system_status")
                .document(userIdentifier)
                .collection("locations")
                .document("last_known")
                .set(locationData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Location saved to Firestore system_status collection");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error saving location to Firestore system_status collection", e);
                    saveLocationLocally(location);

                    if (!pendingLocations.contains(location)) {
                        pendingLocations.add(location);
                    }
                });

        // Save a separate safety copy for emergencies in the sos_data collection
        // which should have even more permissive rules
        if (isEmergencyMode) {
            Map<String, Object> sosLocationData = new HashMap<>(locationData);
            sosLocationData.put("emergencyTimestamp", System.currentTimeMillis());

            db.collection("sos_data")
                    .document(userIdentifier)
                    .collection("emergency_locations")
                    .document(String.valueOf(System.currentTimeMillis()))
                    .set(sosLocationData)
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "Emergency location saved to sos_data collection");
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error saving emergency location to sos_data", e);
                    });
        }
    }

    /**
     * Update user's online status document which has more permissive rules
     */
    private void updateUserStatus(String userIdentifier, Location location) {
        if (userIdentifier == null || userIdentifier.isEmpty()) return;

        Map<String, Object> statusUpdate = new HashMap<>();
        statusUpdate.put("lastSeen", System.currentTimeMillis());
        statusUpdate.put("isOnline", true);
        statusUpdate.put("lastLocation", new GeoPoint(location.getLatitude(), location.getLongitude()));
        statusUpdate.put("userId", userIdentifier); // CRITICAL: Include userId for security rules

        db.collection("user_status")
                .document(userIdentifier)
                .set(statusUpdate)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "User status updated with location");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error updating user status with location", e);
                });
    }

    /**
     * Try to authenticate anonymously to satisfy Firestore rules
     */
    private void tryAnonymousAuth() {
        // Only try to authenticate if there's no current user
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            FirebaseAuth.getInstance().signInAnonymously()
                    .addOnSuccessListener(authResult -> {
                        Log.d(TAG, "Anonymous auth success, can now save location");
                        // Sync any pending locations
                        syncPendingLocations();
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Anonymous auth failed", e);
                    });
        }
    }

    /**
     * Get the most recent location either from active updates or cache
     */
    public void getCurrentLocation(LocationUpdateListener oneTimeListener) {
        // First check if we have a recent cache entry
        if (hasRecentCachedLocation()) {
            if (oneTimeListener != null) {
                mainHandler.post(() -> oneTimeListener.onLocationUpdated(cachedLocation));
            }
            return;
        }

        // Otherwise request a fresh location
        if (!hasLocationPermission()) {
            if (oneTimeListener != null) {
                mainHandler.post(() -> oneTimeListener.onLocationError("Location permission not granted"));
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
                                mainHandler.post(() -> oneTimeListener.onLocationUpdated(location));
                            }
                        } else {
                            if (oneTimeListener != null) {
                                mainHandler.post(() -> oneTimeListener.onLocationError("Could not obtain current location"));
                            }
                        }
                    })
                    .addOnFailureListener(e -> {
                        if (oneTimeListener != null) {
                            mainHandler.post(() -> oneTimeListener.onLocationError("Location error: " + e.getMessage()));
                        }
                    });
        } catch (SecurityException e) {
            Log.e(TAG, "Location permission exception", e);
            if (oneTimeListener != null) {
                mainHandler.post(() -> oneTimeListener.onLocationError("Location permission error: " + e.getMessage()));
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
        if (sessionManager == null) {
            if (emergencyListener != null) {
                mainHandler.post(() -> emergencyListener.onLocationError("Session manager not available"));
            }
            return;
        }

        boolean locationSharingEnabled = sessionManager.getPrivacyPreference("location_sharing", true);
        boolean shareLocationEmergency = sessionManager.getEmergencyPreference("share_location_emergency", true);

        // If both settings are disabled, don't share location
        if (!locationSharingEnabled && !shareLocationEmergency) {
            if (emergencyListener != null) {
                mainHandler.post(() -> emergencyListener.onLocationError("Location sharing during emergency is disabled"));
            }
            return;
        }

        // If we have a cached location, send it immediately
        if (cachedLocation != null) {
            final Location cachedLocationCopy = cachedLocation;
            mainHandler.post(() -> emergencyListener.onLocationUpdated(cachedLocationCopy));
        }

        // Start emergency mode updates for continued tracking
        startLocationUpdates(true, false);

        // Get a fresh location with high accuracy
        getCurrentLocation(new LocationUpdateListener() {
            @Override
            public void onLocationUpdated(Location location) {
                mainHandler.post(() -> emergencyListener.onLocationUpdated(location));

                // Also save this emergency location to Firestore
                if (shouldSaveLocationToCloud()) {
                    if (isOnline()) {
                        saveLocationToFirestore(location);

                        // Also update user status for emergency
                        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                        if (user != null) {
                            updateUserStatus(user.getUid(), location);
                        } else if (sessionManager != null) {
                            updateUserStatus(sessionManager.getSavedPhoneNumber(), location);
                        }
                    } else {
                        saveLocationLocally(location);
                    }
                }
            }

            @Override
            public void onLocationError(String error) {
                // Only report error if we haven't sent a cached location
                if (cachedLocation == null) {
                    mainHandler.post(() -> emergencyListener.onLocationError(error));
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
        if (batteryManager != null) {
            return batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        }
        return 100; // Default to 100% if can't access battery manager
    }

    /**
     * Check if device is online
     */
    private boolean isOnline() {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            return false;
        }

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
        if (connectivityManager == null) {
            return "OFFLINE";
        }

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