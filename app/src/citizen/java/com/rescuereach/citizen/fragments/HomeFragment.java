package com.rescuereach.citizen.fragments;

import android.content.Context;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.rescuereach.R;
import com.rescuereach.citizen.dialogs.SOSConfirmationDialog;
import com.rescuereach.util.LocationManager;
import com.rescuereach.util.PermissionManager;
import com.rescuereach.util.SafetyTipProvider;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Home screen fragment with emergency buttons and status information
 */
public class HomeFragment extends Fragment implements OnMapReadyCallback {
    private static final String TAG = "HomeFragment";

    // UI components
    private View rootView;
    private TextView statusText;
    private ImageView statusIcon;
    private TextView networkStatus;
    private TextView locationAccuracy;
    private TextView lastUpdated;
    private LinearLayout btnSosPolice, btnSosFire, btnSosMedical;
    private MaterialButton btnReportIncident;
    private TextView textNoAlerts;
    private RecyclerView recyclerAlerts;
    private TextView textViewAllAlerts;
    private TextView textSafetyTip;
    private MapView alertsMapView;
    private GoogleMap alertsGoogleMap;

    // Services
    private LocationManager locationManager;
    private PermissionManager permissionManager;
    private ConnectivityManager connectivityManager;
    private Handler uiUpdateHandler;
    private Runnable statusUpdateRunnable;
    private boolean isMapReady = false;
    private Location currentLocation;

    // Time formatter
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

    // Status update interval (30 seconds)
    private static final long STATUS_UPDATE_INTERVAL = 30000;

    public HomeFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout
        rootView = inflater.inflate(R.layout.fragment_home, container, false);

        // Initialize managers
        locationManager = new LocationManager(requireContext());
        permissionManager = PermissionManager.getInstance(requireContext());
        connectivityManager = (ConnectivityManager) requireContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);

        // Initialize UI components
        initializeUIComponents();

        // Set up the map with savedInstanceState
        setupMapView(savedInstanceState);

        // Set up handlers for status updates
        setupStatusUpdates();

        // Set up click listeners
        setupClickListeners();

        // Set safety tip
        updateSafetyTip();

        return rootView;
    }

    private void initializeUIComponents() {
        // Status card
        statusText = rootView.findViewById(R.id.status_text);
        statusIcon = rootView.findViewById(R.id.status_icon);
        networkStatus = rootView.findViewById(R.id.network_status);
        locationAccuracy = rootView.findViewById(R.id.location_accuracy);
        lastUpdated = rootView.findViewById(R.id.last_updated);

        // SOS buttons
        btnSosPolice = rootView.findViewById(R.id.btn_sos_police);
        btnSosFire = rootView.findViewById(R.id.btn_sos_fire);
        btnSosMedical = rootView.findViewById(R.id.btn_sos_medical);

        // Report incident button
        btnReportIncident = rootView.findViewById(R.id.btn_report_incident);

        // Alerts section
        alertsMapView = rootView.findViewById(R.id.alerts_map_view);
        textNoAlerts = rootView.findViewById(R.id.text_no_alerts);
        recyclerAlerts = rootView.findViewById(R.id.recycler_alerts);
        textViewAllAlerts = rootView.findViewById(R.id.text_view_all_alerts);

        // Safety tips
        textSafetyTip = rootView.findViewById(R.id.text_safety_tip);
    }

    private void setupMapView(Bundle savedInstanceState) {
        if (alertsMapView != null) {
            alertsMapView.onCreate(savedInstanceState);
            alertsMapView.getMapAsync(this);

            // Set up map control buttons
            ImageView btnAlertsZoomIn = rootView.findViewById(R.id.btn_alerts_zoom_in);
            ImageView btnAlertsZoomOut = rootView.findViewById(R.id.btn_alerts_zoom_out);
            ImageView btnAlertsCenterLocation = rootView.findViewById(R.id.btn_alerts_center_location);

            btnAlertsZoomIn.setOnClickListener(v -> {
                if (alertsGoogleMap != null) {
                    alertsGoogleMap.animateCamera(CameraUpdateFactory.zoomIn());
                }
            });

            btnAlertsZoomOut.setOnClickListener(v -> {
                if (alertsGoogleMap != null) {
                    alertsGoogleMap.animateCamera(CameraUpdateFactory.zoomOut());
                }
            });

            btnAlertsCenterLocation.setOnClickListener(v -> centerMapOnCurrentLocation());
        }
    }

    private void setupStatusUpdates() {
        uiUpdateHandler = new Handler(Looper.getMainLooper());
        statusUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                updateNetworkStatus();
                updateLocationStatus();
                updateLastUpdatedTime();

                // Schedule next update
                uiUpdateHandler.postDelayed(this, STATUS_UPDATE_INTERVAL);
            }
        };
    }

    private void setupClickListeners() {
        // SOS button click listeners
        btnSosPolice.setOnClickListener(v -> showSOSConfirmation("POLICE"));
        btnSosFire.setOnClickListener(v -> showSOSConfirmation("FIRE"));
        btnSosMedical.setOnClickListener(v -> showSOSConfirmation("MEDICAL"));

        // Report incident button click listener
        btnReportIncident.setOnClickListener(v -> startIncidentReporting());

        // View all alerts click listener
        textViewAllAlerts.setOnClickListener(v -> viewAllAlerts());
    }

    private void showSOSConfirmation(String emergencyType) {
        Log.d(TAG, "SOS button clicked for: " + emergencyType);

        // Check permissions before showing confirmation
        checkPermissionsForSOS(() -> {
            // Create and show SOS confirmation dialog
            SOSConfirmationDialog dialog = new SOSConfirmationDialog(
                    requireContext(), emergencyType, new SOSConfirmationDialog.SOSDialogListener() {
                @Override
                public void onSOSConfirmed(String type) {
                    // Handle SOS confirmation
                    handleSOSConfirmation(type);
                }

                @Override
                public void onSOSCancelled() {
                    // Handle SOS cancellation
                    Log.d(TAG, "SOS cancelled by user");
                }
            });
            dialog.show();
        });
    }

    private void checkPermissionsForSOS(Runnable onPermissionsGranted) {
        // First check location permission
        if (!permissionManager.hasLocationPermissions(false)) {
            permissionManager.requestLocationPermissions(getActivity(), false,
                    (isGranted, granted, denied) -> {
                        if (isGranted) {
                            // Then check SMS permission
                            checkSMSPermission(onPermissionsGranted);
                        } else {
                            Toast.makeText(requireContext(),
                                    "Location permission needed for emergency services",
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
        } else {
            // Already have location permission, check SMS
            checkSMSPermission(onPermissionsGranted);
        }
    }

    private void checkSMSPermission(Runnable onPermissionsGranted) {
        if (!permissionManager.hasSmsPermissions()) {
            permissionManager.requestSmsPermissions(getActivity(),
                    (isGranted, granted, denied) -> {
                        // Proceed even if SMS permission is denied (will use internet only)
                        onPermissionsGranted.run();

                        if (!isGranted) {
                            Toast.makeText(requireContext(),
                                    "SMS permission would be needed if internet is unavailable",
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
        } else {
            // Already have SMS permission
            onPermissionsGranted.run();
        }
    }

    private void handleSOSConfirmation(String emergencyType) {
        Log.d(TAG, "SOS confirmed for: " + emergencyType);

        // Get precise location for emergency
        locationManager.shareLocationDuringEmergency(new LocationManager.LocationUpdateListener() {
            @Override
            public void onLocationUpdated(Location location) {
                // TODO: This will be implemented in the next phase with the SOS data collection service
                Toast.makeText(requireContext(),
                        emergencyType + " emergency alert initiated!",
                        Toast.LENGTH_LONG).show();
            }

            @Override
            public void onLocationError(String message) {
                Toast.makeText(requireContext(),
                        "Location error: " + message + "\nUsing last known location",
                        Toast.LENGTH_LONG).show();

                // Use last known location if available
                Location lastLocation = locationManager.getLastKnownLocation();
                if (lastLocation != null) {
                    // TODO: Use last location for emergency
                } else {
                    Toast.makeText(requireContext(),
                            "Could not determine location. Please try again.",
                            Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void startIncidentReporting() {
        Toast.makeText(requireContext(),
                "Incident reporting will be implemented in the next phase",
                Toast.LENGTH_SHORT).show();
        // TODO: Start incident reporting activity in the next phase
    }

    private void viewAllAlerts() {
        Toast.makeText(requireContext(),
                "View all alerts will be implemented in future phases",
                Toast.LENGTH_SHORT).show();
        // TODO: Navigate to alerts list in future phases
    }

    private void updateNetworkStatus() {
        if (!isAdded() || getContext() == null) return;

        boolean isOnline = isNetworkAvailable();

        if (isOnline) {
            networkStatus.setText(R.string.status_online);
            networkStatus.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.badge_green));
        } else {
            networkStatus.setText(R.string.status_offline);
            networkStatus.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.badge_red));
        }

        // Update overall status
        updateOverallStatus(isOnline);
    }

    private boolean isNetworkAvailable() {
        if (connectivityManager == null) return false;

        NetworkCapabilities capabilities = connectivityManager
                .getNetworkCapabilities(connectivityManager.getActiveNetwork());

        return capabilities != null &&
                (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
    }

    private void updateLocationStatus() {
        if (!isAdded() || getContext() == null) return;

        boolean hasPermission = permissionManager.hasLocationPermissions(false);

        if (hasPermission) {
            Location lastKnown = locationManager.getLastKnownLocation();
            if (lastKnown != null) {
                float accuracy = lastKnown.getAccuracy();
                String accuracyText;

                if (accuracy < 20) {
                    accuracyText = getString(R.string.location_high_accuracy);
                } else if (accuracy < 100) {
                    accuracyText = getString(R.string.location_medium_accuracy);
                } else {
                    accuracyText = getString(R.string.location_low_accuracy);
                }

                locationAccuracy.setText(getString(R.string.location_status, accuracyText));

                // Update current location and map if available
                currentLocation = lastKnown;
                updateMapWithCurrentLocation();
            } else {
                locationAccuracy.setText(getString(R.string.location_unavailable));
            }
        } else {
            locationAccuracy.setText(getString(R.string.location_permission_required));
        }
    }

    private void updateLastUpdatedTime() {
        if (!isAdded()) return;

        String currentTime = timeFormat.format(new Date());
        lastUpdated.setText(currentTime);
    }

    private void updateOverallStatus(boolean isOnline) {
        if (!isAdded() || getContext() == null) return;

        boolean hasLocationPermission = permissionManager.hasLocationPermissions(false);
        boolean hasSmsPermission = permissionManager.hasSmsPermissions();

        if (isOnline && hasLocationPermission) {
            // All systems go
            statusText.setText(R.string.status_ready);
            statusIcon.setImageResource(R.drawable.ic_check_circle);
            statusIcon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.green_success));
        } else if (!isOnline && hasSmsPermission && hasLocationPermission) {
            // Offline but SMS available
            statusText.setText(R.string.status_offline_sms_ready);
            statusIcon.setImageResource(R.drawable.ic_warning);
            statusIcon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.warning_yellow));
        } else if (!isOnline && !hasSmsPermission && hasLocationPermission) {
            // Offline, no SMS, but location available
            statusText.setText(R.string.status_limited_functionality);
            statusIcon.setImageResource(R.drawable.ic_warning);
            statusIcon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.warning_yellow));
        } else {
            // Critical services missing
            statusText.setText(R.string.status_critical_permissions_missing);
            statusIcon.setImageResource(R.drawable.ic_error);
            statusIcon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.error_red));
        }
    }

    private void updateSafetyTip() {
        if (textSafetyTip != null) {
            String tip = SafetyTipProvider.getRandomSafetyTip(requireContext());
            textSafetyTip.setText(tip);
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        alertsGoogleMap = googleMap;
        isMapReady = true;

        // Configure map settings
        alertsGoogleMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
        alertsGoogleMap.getUiSettings().setAllGesturesEnabled(true);
        alertsGoogleMap.getUiSettings().setMapToolbarEnabled(false);
        alertsGoogleMap.getUiSettings().setZoomControlsEnabled(false);
        alertsGoogleMap.getUiSettings().setMyLocationButtonEnabled(false);

        // Apply custom style based on night mode
        boolean isNightMode = requireContext().getResources().getBoolean(R.bool.is_night_mode);
        if (isNightMode) {
            alertsGoogleMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(
                    requireContext(), R.raw.map_style_night));
        } else {
            alertsGoogleMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(
                    requireContext(), R.raw.map_style_day));
        }

        // Update with current location if available
        updateMapWithCurrentLocation();

        // For now, show "no alerts" message
        showNoAlertsState();
    }

    private void updateMapWithCurrentLocation() {
        if (alertsGoogleMap != null && currentLocation != null) {
            LatLng latLng = new LatLng(currentLocation.getLatitude(), currentLocation.getLongitude());

            // Clear previous markers
            alertsGoogleMap.clear();

            // Add marker for current location
            alertsGoogleMap.addMarker(new MarkerOptions()
                    .position(latLng)
                    .title(getString(R.string.your_location))
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)));

            // Move camera to show current location
            alertsGoogleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 14f));
        }
    }

    private void centerMapOnCurrentLocation() {
        if (alertsGoogleMap != null && currentLocation != null) {
            LatLng latLng = new LatLng(currentLocation.getLatitude(), currentLocation.getLongitude());
            alertsGoogleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 14f));
        } else {
            Toast.makeText(requireContext(), R.string.waiting_for_location, Toast.LENGTH_SHORT).show();
        }
    }

    private void showNoAlertsState() {
        if (textNoAlerts != null && recyclerAlerts != null && textViewAllAlerts != null) {
            textNoAlerts.setVisibility(View.VISIBLE);
            recyclerAlerts.setVisibility(View.GONE);
            textViewAllAlerts.setVisibility(View.GONE);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (alertsMapView != null) {
            alertsMapView.onStart();
        }

        // Start location updates
        if (permissionManager.hasLocationPermissions(false)) {
            locationManager.startLocationUpdates(false, false);
        }

        // Start periodic status updates
        uiUpdateHandler.post(statusUpdateRunnable);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (alertsMapView != null) {
            alertsMapView.onResume();
        }

        // Immediate status update
        updateNetworkStatus();
        updateLocationStatus();
        updateLastUpdatedTime();
    }

    @Override
    public void onPause() {
        if (alertsMapView != null) {
            alertsMapView.onPause();
        }
        super.onPause();
    }

    @Override
    public void onStop() {
        // Remove status update callbacks
        uiUpdateHandler.removeCallbacks(statusUpdateRunnable);

        // Stop location updates
        locationManager.stopLocationUpdates();

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
        if (alertsMapView != null) {
            alertsMapView.onLowMemory();
        }
        super.onLowMemory();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (alertsMapView != null) {
            alertsMapView.onSaveInstanceState(outState);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        permissionManager.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
}