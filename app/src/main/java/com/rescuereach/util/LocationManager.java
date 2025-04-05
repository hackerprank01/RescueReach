package com.rescuereach.util;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
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
import com.rescuereach.service.auth.UserSessionManager;

public class LocationManager {
    private static final String TAG = "LocationManager";
    private static final long UPDATE_INTERVAL = 10000; // 10 seconds
    private static final long FASTEST_INTERVAL = 5000; // 5 seconds

    private final Context context;
    private final FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private LocationRequest locationRequest;
    private LocationUpdateListener locationUpdateListener;

    // Add UserSessionManager
    private final UserSessionManager sessionManager;

    public interface LocationUpdateListener {
        void onLocationUpdated(Location location);
        void onLocationError(String message);
    }

    public LocationManager(Context context) {
        this.context = context;
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
        // Initialize UserSessionManager
        sessionManager = UserSessionManager.getInstance(context);
        setupLocationRequest();
    }

    private void setupLocationRequest() {
        locationRequest = new LocationRequest.Builder(UPDATE_INTERVAL)
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setMinUpdateIntervalMillis(FASTEST_INTERVAL)
                .build();
    }

    public void setLocationUpdateListener(LocationUpdateListener listener) {
        this.locationUpdateListener = listener;
    }

    public void startLocationUpdates() {
        // Check privacy settings first
        if (!isLocationEnabled()) {
            if (locationUpdateListener != null) {
                locationUpdateListener.onLocationError("Location sharing is disabled in privacy settings");
            }
            return;
        }

        if (ActivityCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(context,
                        Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if (locationUpdateListener != null) {
                locationUpdateListener.onLocationError("Location permission not granted");
            }
            return;
        }

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                // Check privacy settings on each update
                if (!isLocationEnabled()) {
                    stopLocationUpdates();
                    return;
                }

                for (Location location : locationResult.getLocations()) {
                    if (location != null && locationUpdateListener != null) {
                        locationUpdateListener.onLocationUpdated(location);

                        // Save location to backend
                        saveLocationToBackend(location);
                    }
                }
            }
        };

        fusedLocationClient.requestLocationUpdates(
                locationRequest, locationCallback, Looper.getMainLooper());

        // Get last known location immediately
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(location -> {
                    if (location != null && locationUpdateListener != null) {
                        locationUpdateListener.onLocationUpdated(location);

                        // Save location to backend
                        saveLocationToBackend(location);
                    }
                })
                .addOnFailureListener(e -> {
                    if (locationUpdateListener != null) {
                        locationUpdateListener.onLocationError("Failed to get last location: " + e.getMessage());
                    }
                });
    }

    public void stopLocationUpdates() {
        if (locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
    }

    private void saveLocationToBackend(Location location) {
        // Only save location if privacy settings allow it
        if (!isLocationEnabled()) {
            Log.d(TAG, "Location not saved: location sharing is disabled");
            return;
        }

        // We'll implement this in a future step with Firebase
        // This is a placeholder for now
        Log.d(TAG, "Location saved to backend: " + location.getLatitude() + ", " + location.getLongitude());
    }

    public void getCurrentLocation(LocationUpdateListener oneTimeListener) {
        // Check privacy settings first
        if (!isLocationEnabled()) {
            if (oneTimeListener != null) {
                oneTimeListener.onLocationError("Location sharing is disabled in privacy settings");
            }
            return;
        }

        if (ActivityCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(context,
                        Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if (oneTimeListener != null) {
                oneTimeListener.onLocationError("Location permission not granted");
            }
            return;
        }

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(location -> {
                    if (location != null && oneTimeListener != null) {
                        oneTimeListener.onLocationUpdated(location);
                    } else if (oneTimeListener != null) {
                        oneTimeListener.onLocationError("Location is null");
                    }
                })
                .addOnFailureListener(e -> {
                    if (oneTimeListener != null) {
                        oneTimeListener.onLocationError("Failed to get location: " + e.getMessage());
                    }
                });
    }

    // NEW METHOD: Check if location is enabled based on privacy setting
    public boolean isLocationEnabled() {
        return sessionManager.getPrivacyPreference("location_sharing", true);
    }

    // NEW METHOD: Share location during emergency regardless of general location setting
    public void shareLocationDuringEmergency(final LocationUpdateListener emergencyListener) {
        boolean locationSharingEnabled = sessionManager.getPrivacyPreference("location_sharing", true);
        boolean shareLocationEmergency = sessionManager.getEmergencyPreference("share_location_emergency", true);

        // If both settings are disabled, don't share location
        if (!locationSharingEnabled && !shareLocationEmergency) {
            if (emergencyListener != null) {
                emergencyListener.onLocationError("Location sharing during emergency is disabled");
            }
            return;
        }

        // Check permissions
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) !=
                        PackageManager.PERMISSION_GRANTED) {

            if (emergencyListener != null) {
                emergencyListener.onLocationError("Location permission not granted");
            }
            return;
        }

        // Get high-accuracy location for emergency
        LocationRequest emergencyRequest = new LocationRequest.Builder(0)
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setMaxUpdateDelayMillis(0)
                .setWaitForAccurateLocation(true)
                .build();

        try {
            // Try to get the current location with high accuracy
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener(location -> {
                        if (location != null && emergencyListener != null) {
                            emergencyListener.onLocationUpdated(location);

                            // Log the emergency location share
                            Log.d(TAG, "Emergency location shared: " + location.getLatitude() + ", " + location.getLongitude());
                        } else if (emergencyListener != null) {
                            emergencyListener.onLocationError("Emergency location is null");
                        }
                    })
                    .addOnFailureListener(e -> {
                        if (emergencyListener != null) {
                            emergencyListener.onLocationError("Failed to get emergency location: " + e.getMessage());
                        }
                    });
        } catch (Exception e) {
            Log.e(TAG, "Error getting emergency location", e);
            if (emergencyListener != null) {
                emergencyListener.onLocationError("Error getting emergency location: " + e.getMessage());
            }
        }
    }
}