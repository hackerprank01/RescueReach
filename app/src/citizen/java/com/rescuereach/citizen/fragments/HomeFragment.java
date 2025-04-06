package com.rescuereach.citizen.fragments;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import android.app.AlertDialog;
import android.os.CountDownTimer;
import android.view.LayoutInflater;
import android.widget.Button;
import android.widget.TextView;
import com.rescuereach.data.model.SOSReport;
import com.rescuereach.service.emergency.SOSManager;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.MarkerOptions;
import com.rescuereach.R;
import com.rescuereach.service.auth.UserSessionManager;
import com.rescuereach.util.LocationManager;

public class HomeFragment extends Fragment implements OnMapReadyCallback {

    private static final String TAG = "HomeFragment";
    private static final String ALERTS_MAPVIEW_BUNDLE_KEY = "AlertsMapViewBundleKey";

    // UI Components
    private View btnSosPolice;
    private View btnSosFire;
    private View btnSosMedical;
    private View btnReportIncident;
    private TextView networkStatus;
    private TextView locationAccuracy;
    private TextView lastUpdated;
    private RecyclerView recyclerAlerts;
    private TextView textNoAlerts;
    private TextView textViewAllAlerts;
    private TextView textSafetyTip;

    // Map components
    private MapView alertsMapView;
    private GoogleMap alertsGoogleMap;
    private ImageView btnAlertsCenterLocation;

    // Services
    private UserSessionManager sessionManager;
    private LocationManager locationManager;
    private Location currentLocation;
    private ImageView btnAlertsZoomIn;
    private ImageView btnAlertsZoomOut;
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Initialize services
        sessionManager = UserSessionManager.getInstance(requireContext());
        locationManager = new LocationManager(requireContext());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize UI components
        initUI(view);

        // Initialize MapView
        initMapView(savedInstanceState);

        // Set up click listeners
        setupClickListeners();

        // Update UI with status information
        updateStatusInfo();

        // Load alerts (none for now, just UI setup)
        setupAlertsSection();
    }

    private void initUI(View view) {
        // SOS buttons
        btnSosPolice = view.findViewById(R.id.btn_sos_police);
        btnSosFire = view.findViewById(R.id.btn_sos_fire);
        btnSosMedical = view.findViewById(R.id.btn_sos_medical);

        // Report incident button
        btnReportIncident = view.findViewById(R.id.btn_report_incident);

        // Status indicators
        networkStatus = view.findViewById(R.id.network_status);
        locationAccuracy = view.findViewById(R.id.location_accuracy);
        lastUpdated = view.findViewById(R.id.last_updated);

        // Alerts section
        recyclerAlerts = view.findViewById(R.id.recycler_alerts);
        textNoAlerts = view.findViewById(R.id.text_no_alerts);
        textViewAllAlerts = view.findViewById(R.id.text_view_all_alerts);

        // Map components
        alertsMapView = view.findViewById(R.id.alerts_map_view);
        btnAlertsCenterLocation = view.findViewById(R.id.btn_alerts_center_location);
        btnAlertsZoomIn = view.findViewById(R.id.btn_alerts_zoom_in);
        btnAlertsZoomOut = view.findViewById(R.id.btn_alerts_zoom_out);

        // Safety tip
        textSafetyTip = view.findViewById(R.id.text_safety_tip);
    }

    private void initMapView(Bundle savedInstanceState) {
        // Extract MapView bundle if saved
        Bundle mapViewBundle = null;
        if (savedInstanceState != null) {
            mapViewBundle = savedInstanceState.getBundle(ALERTS_MAPVIEW_BUNDLE_KEY);
        }

        // Initialize MapView
        alertsMapView.onCreate(mapViewBundle);
        alertsMapView.getMapAsync(this);
    }

    private void setupClickListeners() {
        // For now, just add toast messages for the buttons
        // These will be replaced with actual functionality later

        // SOS - Police
        btnSosPolice.setOnClickListener(v -> {
            showSOSConfirmationDialog(SOSReport.CATEGORY_POLICE);
        });

        // SOS - Fire
        btnSosFire.setOnClickListener(v -> {
            showSOSConfirmationDialog(SOSReport.CATEGORY_FIRE);
        });

        // SOS - Medical
        btnSosMedical.setOnClickListener(v -> {
            showSOSConfirmationDialog(SOSReport.CATEGORY_MEDICAL);
        });

        // Report Incident
        btnReportIncident.setOnClickListener(v -> {
            Toast.makeText(requireContext(),
                    "Report Incident button pressed - Will be implemented",
                    Toast.LENGTH_SHORT).show();
        });

        // View all alerts
        textViewAllAlerts.setOnClickListener(v -> {
            Toast.makeText(requireContext(),
                    "View All Alerts - Will be implemented",
                    Toast.LENGTH_SHORT).show();
        });

        // Center location on alerts map
        btnAlertsCenterLocation.setOnClickListener(v -> {
            centerMapOnCurrentLocation();
        });
        // Zoom in on alerts map
        btnAlertsZoomIn.setOnClickListener(v -> {
            if (alertsGoogleMap != null) {
                alertsGoogleMap.animateCamera(CameraUpdateFactory.zoomIn());
            }
        });

        // Zoom out on alerts map
        btnAlertsZoomOut.setOnClickListener(v -> {
            if (alertsGoogleMap != null) {
                alertsGoogleMap.animateCamera(CameraUpdateFactory.zoomOut());
            }
        });
    }

    private void showSOSConfirmationDialog(String category) {
        // Inflate custom dialog layout
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_sos_confirmation, null);

        // Get dialog views
        TextView titleText = dialogView.findViewById(R.id.text_sos_title);
        TextView descriptionText = dialogView.findViewById(R.id.text_sos_description);
        TextView countdownText = dialogView.findViewById(R.id.text_countdown);
        Button cancelButton = dialogView.findViewById(R.id.btn_sos_cancel);
        Button confirmButton = dialogView.findViewById(R.id.btn_sos_confirm);

        // Customize dialog based on emergency category
        customizeSOSDialog(category, titleText, descriptionText);

        // Create and configure dialog
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .setCancelable(false)
                .create();

        // Create countdown timer - 5 seconds
        CountDownTimer countDownTimer = new CountDownTimer(5000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                // Update countdown text
                int secondsRemaining = (int) (millisUntilFinished / 1000);
                countdownText.setText(String.valueOf(secondsRemaining));
            }

            @Override
            public void onFinish() {
                // Auto-send when countdown completes
                dialog.dismiss();
                sendSOSAlert(category);
            }
        };

        // Start countdown
        countDownTimer.start();

        // Set button click listeners
        cancelButton.setOnClickListener(v -> {
            // Cancel timer and dismiss dialog
            countDownTimer.cancel();
            dialog.dismiss();
        });

        confirmButton.setOnClickListener(v -> {
            // Cancel timer, dismiss dialog, and send alert immediately
            countDownTimer.cancel();
            dialog.dismiss();
            sendSOSAlert(category);
        });

        // Show dialog
        dialog.show();
    }

    private void customizeSOSDialog(String category, TextView titleText, TextView descriptionText) {
        switch (category) {
            case SOSReport.CATEGORY_POLICE:
                titleText.setText(R.string.confirm_police_emergency);
                titleText.setTextColor(requireContext().getColor(R.color.police_blue));
                descriptionText.setText(R.string.police_sos_description);
                break;
            case SOSReport.CATEGORY_FIRE:
                titleText.setText(R.string.confirm_fire_emergency);
                titleText.setTextColor(requireContext().getColor(R.color.fire_orange));
                descriptionText.setText(R.string.fire_sos_description);
                break;
            case SOSReport.CATEGORY_MEDICAL:
                titleText.setText(R.string.confirm_medical_emergency);
                titleText.setTextColor(requireContext().getColor(R.color.medical_red));
                descriptionText.setText(R.string.medical_sos_description);
                break;
        }
    }

    private void sendSOSAlert(String category) {
        // Show loading dialog
        AlertDialog loadingDialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.sending_emergency_alert)
                .setMessage(R.string.please_wait)
                .setCancelable(false)
                .create();
        loadingDialog.show();

        // Get SOS manager instance
        SOSManager sosManager = SOSManager.getInstance(requireContext());

        // Initiate SOS alert
        sosManager.initiateEmergencySOS(category, new SOSManager.SOSListener() {
            @Override
            public void onSOSInitiated() {
                // Log initiation
                Log.d(TAG, "SOS initiated: " + category);
            }

            @Override
            public void onSOSSuccess() {
                // Dismiss loading dialog
                if (loadingDialog.isShowing()) {
                    loadingDialog.dismiss();
                }

                // Show success message
                new AlertDialog.Builder(requireContext())
                        .setTitle(R.string.emergency_alert_sent)
                        .setMessage(R.string.emergency_alert_success_message)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
            }

            @Override
            public void onSOSFailure(String errorMessage) {
                // Dismiss loading dialog
                if (loadingDialog.isShowing()) {
                    loadingDialog.dismiss();
                }

                // Show error message
                new AlertDialog.Builder(requireContext())
                        .setTitle(R.string.emergency_alert_failed)
                        .setMessage(getString(R.string.emergency_alert_error_message, errorMessage))
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
            }
        });
    }

    private void updateStatusInfo() {
        // For now, just show static information
        // This will be updated with real-time data later
        networkStatus.setText("Online");
        locationAccuracy.setText("Location: High Accuracy");
        lastUpdated.setText("Just now");

        // Sample safety tip - would be randomly selected or relevant to conditions
        textSafetyTip.setText("In emergency situations, remain calm and provide clear information to help responders reach you quickly.");
    }

    private void setupAlertsSection() {
        // Set up RecyclerView with empty adapter for now
        recyclerAlerts.setLayoutManager(new LinearLayoutManager(requireContext()));

        // For the initial implementation, just show the "No alerts" message
        recyclerAlerts.setVisibility(View.GONE);
        textNoAlerts.setVisibility(View.VISIBLE);
        textViewAllAlerts.setVisibility(View.GONE);

        // Later, we'll add actual alert data from Firebase
    }

    // Handle location updates from LocationManager
    private void startLocationUpdates() {
        // Only request if permission is granted
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) ==
                        PackageManager.PERMISSION_GRANTED) {

            locationManager.setLocationUpdateListener(new LocationManager.LocationUpdateListener() {
                @Override
                public void onLocationUpdated(Location location) {
                    currentLocation = location;
                    updateMapWithLocation();
                }

                @Override
                public void onLocationError(String message) {
                    Log.e(TAG, "Location error: " + message);
                }
            });

            locationManager.startLocationUpdates();
        }
    }

    // Center the map on the current location
    private void centerMapOnCurrentLocation() {
        if (alertsGoogleMap != null && currentLocation != null) {
            LatLng latLng = new LatLng(currentLocation.getLatitude(), currentLocation.getLongitude());
            alertsGoogleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 13f));
        } else {
            Toast.makeText(requireContext(), R.string.waiting_for_location, Toast.LENGTH_SHORT).show();
        }
    }

    // Update map with current location and sample alerts
    private void updateMapWithLocation() {
        if (alertsGoogleMap != null && currentLocation != null) {
            LatLng latLng = new LatLng(currentLocation.getLatitude(), currentLocation.getLongitude());

            // Clear existing markers
            alertsGoogleMap.clear();

            // Add marker for current location
            alertsGoogleMap.addMarker(new MarkerOptions()
                    .position(latLng)
                    .title(getString(R.string.current_location))
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)));

            // Add sample alert markers (for UI demo only)
            addSampleAlertMarkers(latLng);

            // Center map on current location
            alertsGoogleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 13f));
        }
    }

    // Add sample alert markers for UI demonstration
    private void addSampleAlertMarkers(LatLng currentLocation) {
        // Sample alert 1: nearby fire alert
        LatLng alertLocation1 = new LatLng(
                currentLocation.latitude + 0.01,
                currentLocation.longitude + 0.01
        );
        alertsGoogleMap.addMarker(new MarkerOptions()
                .position(alertLocation1)
                .title("Fire Alert")
                .snippet("Reported 15 minutes ago")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE)));

        // Sample alert 2: traffic accident
        LatLng alertLocation2 = new LatLng(
                currentLocation.latitude - 0.008,
                currentLocation.longitude + 0.003
        );
        alertsGoogleMap.addMarker(new MarkerOptions()
                .position(alertLocation2)
                .title("Traffic Accident")
                .snippet("Reported 5 minutes ago")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        alertsGoogleMap = googleMap;

        // Configure map UI settings
        alertsGoogleMap.getUiSettings().setAllGesturesEnabled(true);
        alertsGoogleMap.getUiSettings().setMapToolbarEnabled(false);
        alertsGoogleMap.getUiSettings().setMyLocationButtonEnabled(false);
        alertsGoogleMap.getUiSettings().setZoomControlsEnabled(false);

        // Apply map style based on night mode
        boolean isNightMode = requireContext().getResources().getBoolean(R.bool.is_night_mode);
        if (isNightMode) {
            alertsGoogleMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(requireContext(), R.raw.map_style_night));
        } else {
            alertsGoogleMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(requireContext(), R.raw.map_style_day));
        }

        // Enable my location layer if permission is granted
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) ==
                        PackageManager.PERMISSION_GRANTED) {
            alertsGoogleMap.setMyLocationEnabled(true);
        }

        // Start location updates
        startLocationUpdates();

        // If we already have a location (from activity), use it
        if (currentLocation != null) {
            updateMapWithLocation();
        }
    }

    // MapView lifecycle methods
    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        Bundle mapViewBundle = outState.getBundle(ALERTS_MAPVIEW_BUNDLE_KEY);
        if (mapViewBundle == null) {
            mapViewBundle = new Bundle();
            outState.putBundle(ALERTS_MAPVIEW_BUNDLE_KEY, mapViewBundle);
        }

        if (alertsMapView != null) {
            alertsMapView.onSaveInstanceState(mapViewBundle);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (alertsMapView != null) {
            alertsMapView.onStart();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (alertsMapView != null) {
            alertsMapView.onResume();
        }
        if (locationManager != null) {
            startLocationUpdates();
        }
    }

    @Override
    public void onPause() {
        if (alertsMapView != null) {
            alertsMapView.onPause();
        }
        if (locationManager != null) {
            locationManager.stopLocationUpdates();
        }
        super.onPause();
    }

    @Override
    public void onStop() {
        if (alertsMapView != null) {
            alertsMapView.onStop();
        }
        super.onStop();
    }

    @Override
    public void onDestroy() {
        if (alertsMapView != null) {
            alertsMapView.onDestroy();
        }
        super.onDestroy();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (alertsMapView != null) {
            alertsMapView.onLowMemory();
        }
    }
}