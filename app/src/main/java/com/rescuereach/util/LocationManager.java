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

public class LocationManager {
    private static final String TAG = "LocationManager";
    private static final long UPDATE_INTERVAL = 10000; // 10 seconds
    private static final long FASTEST_INTERVAL = 5000; // 5 seconds

    private final Context context;
    private final FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private LocationRequest locationRequest;
    private LocationUpdateListener locationUpdateListener;

    public interface LocationUpdateListener {
        void onLocationUpdated(Location location);
        void onLocationError(String message);
    }

    public LocationManager(Context context) {
        this.context = context;
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
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
        // We'll implement this in a future step with Firebase
        // This is a placeholder for now
        Log.d(TAG, "Location saved to backend: " + location.getLatitude() + ", " + location.getLongitude());
    }

    public void getCurrentLocation(LocationUpdateListener oneTimeListener) {
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
}