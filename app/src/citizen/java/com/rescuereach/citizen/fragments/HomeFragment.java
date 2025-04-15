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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
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
import com.google.firebase.firestore.GeoPoint;
import com.rescuereach.R;
import com.rescuereach.RescueReachApplication;
import com.rescuereach.citizen.dialogs.SOSConfirmationDialog;
import com.rescuereach.citizen.dialogs.SOSStatusDialog;
import com.rescuereach.data.model.SOSReport;
import com.rescuereach.service.auth.UserSessionManager;
import com.rescuereach.service.notification.NotificationService;
import com.rescuereach.service.sos.SOSDataCollectionService;
import com.rescuereach.service.sos.SOSProcessingService;
import com.rescuereach.util.LocationManager;
import com.rescuereach.util.NetworkUtils;
import com.rescuereach.util.PermissionManager;
import com.rescuereach.util.SafetyTipProvider;
import com.rescuereach.util.SharedPreferencesManager;
import com.rescuereach.util.ToastUtil;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Home screen fragment with emergency buttons and status information
 */
public class HomeFragment extends Fragment implements OnMapReadyCallback,
        NotificationService.NotificationActionListener {
    private static final String TAG = "HomeFragment";

    private final Executor backgroundExecutor = Executors.newSingleThreadExecutor();


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
    private NotificationService notificationService;
    private SOSDataCollectionService sosDataCollectionService;
    private SOSProcessingService sosProcessingService;
    private UserSessionManager sessionManager;
    private SharedPreferencesManager prefsManager;
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

        // Initialize managers and services
        initializeServices();

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

        // Check for active SOS reports
        checkForActiveSOS();

        return rootView;
    }

    private void initializeServices() {
        locationManager = new LocationManager(requireContext());
        permissionManager = PermissionManager.getInstance(requireContext());
        connectivityManager = (ConnectivityManager) requireContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);

        // Initialize shared preferences manager
        prefsManager = new SharedPreferencesManager(requireContext());

        // Initialize notification service
        notificationService = ((RescueReachApplication) requireActivity().getApplication())
                .getNotificationService();
        notificationService.setNotificationActionListener(this);

        // Initialize SOS data collection service
        sosDataCollectionService = new SOSDataCollectionService(requireContext());

        // Initialize SOS processing service
        sosProcessingService = new SOSProcessingService(requireContext());

        // Initialize session manager
        sessionManager = UserSessionManager.getInstance(requireContext());

        // Set notification tags based on user profile if available
        if (notificationService != null) {
            // Set role as citizen
            notificationService.setUserRole("citizen");

            // Set volunteer status
            notificationService.setUserAsVolunteer(sessionManager.isVolunteer());

            // Set user identifier
            String phoneNumber = sessionManager.getSavedPhoneNumber();
            if (phoneNumber != null && !phoneNumber.isEmpty()) {
                notificationService.setUserIdentifier(phoneNumber);
            }

            // Set region if available
            String region = sessionManager.getState();
            if (region != null && !region.isEmpty()) {
                notificationService.setUserRegion(region);
            }

            // Update last active timestamp
            notificationService.updateLastActive();
        }
    }

    /**
     * Check for any active SOS reports and show status dialog if needed
     */
    private void checkForActiveSOS() {
        if (!isAdded() || getContext() == null) return;

        String activeReportId = prefsManager.getString("active_sos_report_id", null);
        boolean isMinimized = prefsManager.getBoolean("sos_dialog_minimized", false);

        if (activeReportId != null && !activeReportId.isEmpty() && !isMinimized) {
            // Only show dialog if it's not minimized
            showSOSStatusForReport(activeReportId);
        }
    }

    /**
     * Show SOS status dialog for a specific report ID
     */
    private void showSOSStatusForReport(String reportId) {
        if (!isAdded() || getContext() == null) return;

        // Create and show dialog for existing report
        SOSStatusDialog dialog = new SOSStatusDialog(requireContext(), reportId);
        dialog.setSOSStatusDialogListener(new SOSStatusDialog.SOSStatusDialogListener() {
            @Override
            public void onStatusChanged(String reportId, String newStatus) {
                // Handle status change
                if (SOSReport.STATUS_RESOLVED.equals(newStatus) ||
                        SOSReport.STATUS_CANCELED.equals(newStatus)) {
                    clearSOSState();
                }
            }

            @Override
            public void onDismissed(String reportId, String currentStatus) {
                // Handle dialog dismissal
                if (SOSReport.STATUS_RESOLVED.equals(currentStatus) ||
                        SOSReport.STATUS_CANCELED.equals(currentStatus)) {
                    clearSOSState();
                } else {
                    // Dialog was minimized
                    prefsManager.putBoolean("sos_dialog_minimized", true);
                }
            }

            @Override
            public void onError(String errorMessage) {
                Toast.makeText(requireContext(),
                        "Error loading SOS status: " + errorMessage,
                        Toast.LENGTH_SHORT).show();
                clearSOSState();
            }
        });

        // Reset minimized state when showing dialog
        prefsManager.putBoolean("sos_dialog_minimized", false);
        dialog.show();
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
                // Avoid immediate UI updates by using background processing
                backgroundExecutor.execute(() -> {
                    // Check network and prepare state
                    final boolean isOnline = NetworkUtils.isOnline(requireContext());
                    final boolean hasLocationPermission = permissionManager.hasLocationPermissions(false);

                    // Return to UI thread for actual updates
                    uiUpdateHandler.post(() -> {
                        if (!isAdded()) return;

                        updateNetworkStatus();
                        updateLocationStatus();
                        updateLastUpdatedTime();
                    });
                });

                // Schedule next update with longer interval to reduce CPU usage
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

        // Check if there's already an active SOS report
        String activeReportId = prefsManager.getString("active_sos_report_id", null);
        boolean isMinimized = prefsManager.getBoolean("sos_dialog_minimized", false);

        if (activeReportId != null && !activeReportId.isEmpty()) {
            // Show the existing SOS status dialog
            prefsManager.putBoolean("sos_dialog_minimized", false); // No longer minimized
            showSOSStatusForReport(activeReportId);
            return;
        }

        // Simplified permission flow to avoid double-click issue
        if (!permissionManager.hasLocationPermissions(false)) {
            permissionManager.requestLocationPermissions(getActivity(), false,
                    (isGranted, granted, denied) -> {
                        if (isGranted) {
                            // Show dialog immediately
                            displaySOSConfirmationDialog(emergencyType);
                        } else {
                            Toast.makeText(requireContext(),
                                    "Location permission needed for emergency services",
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
        } else {
            // Already have location permission, show dialog immediately
            displaySOSConfirmationDialog(emergencyType);
        }
    }

    private void displaySOSConfirmationDialog(String emergencyType) {
        SOSConfirmationDialog dialog = new SOSConfirmationDialog(
                requireContext(), emergencyType, new SOSConfirmationDialog.SOSDialogListener() {
            @Override
            public void onSOSConfirmed(String type) {
                // Move to background thread
                backgroundExecutor.execute(() -> handleSOSConfirmation(type));
            }

            @Override
            public void onSOSCancelled() {
                // No work needed for cancellation
                Log.d(TAG, "SOS cancelled by user");
            }
        });
        dialog.show();
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
        if (!isAdded() || getContext() == null) return;

        Log.d(TAG, "SOS confirmed for: " + emergencyType);

        // Show toast to indicate processing
        ToastUtil.showShort(requireContext(),
                getString(R.string.collecting_sos_data, emergencyType));

        // Collect all emergency data
        sosDataCollectionService.collectSOSData(emergencyType, new SOSDataCollectionService.SOSDataCollectionListener() {
            @Override
            public void onDataCollectionComplete(SOSReport report) {
                // Set online status based on current network
                report.setOnline(NetworkUtils.isOnline(requireContext()));

                // Process the collected data using SOSProcessingService
                processSOSReport(report);
            }

            @Override
            public void onDataCollectionFailed(String errorMessage) {
                // Handle error
                Toast.makeText(requireContext(),
                        getString(R.string.sos_data_collection_failed, errorMessage),
                        Toast.LENGTH_LONG).show();

                // Try with basic location as fallback
                createFallbackSOSReport(emergencyType);
            }
        });
    }

    private void processSOSReport(SOSReport report) {
        if (!isAdded() || getContext() == null) return;

        // Use proper background handling and avoid ANR
        backgroundExecutor.execute(() -> {
            // Process the SOS report using the processing service
            sosProcessingService.processSOSReport(report, new SOSProcessingService.SOSProcessingListener() {
                @Override
                public void onProcessingComplete(final SOSReport processedReport) {
                    if (!isAdded() || getContext() == null) return;

                    // Return to UI thread for UI operations
                    uiUpdateHandler.post(() -> {
                        if (!isAdded() || getContext() == null) return;

                        // Update notification tags
                        updateNotificationTags(processedReport);

                        // Save the report ID to preferences to track active SOS
                        if (processedReport.getReportId() != null) {
                            prefsManager.putString("active_sos_report_id", processedReport.getReportId());
                        }

                        // Show the status dialog
                        showSOSStatusDialog(processedReport);
                    });
                }

                @Override
                public void onProcessingFailed(final String errorMessage) {
                    if (!isAdded()) return;

                    // Return to UI thread for UI operations
                    uiUpdateHandler.post(() -> {
                        if (!isAdded() || getContext() == null) return;

                        Toast.makeText(requireContext(),
                                getString(R.string.sos_processing_error) + ": " + errorMessage,
                                Toast.LENGTH_LONG).show();
                    });
                }
            });
        });
    }




    /**
     * Show the SOS status dialog for a new report
     */
    private void showSOSStatusDialog(SOSReport report) {
        if (!isAdded() || getContext() == null) return;

        SOSStatusDialog dialog = new SOSStatusDialog(requireContext(), report);
        dialog.setSOSStatusDialogListener(new SOSStatusDialog.SOSStatusDialogListener() {
            @Override
            public void onStatusChanged(String reportId, String newStatus) {
                // Update UI if needed
                if (SOSReport.STATUS_RESOLVED.equals(newStatus) ||
                        SOSReport.STATUS_CANCELED.equals(newStatus)) {
                    clearSOSState();
                }
            }

            @Override
            public void onDismissed(String reportId, String currentStatus) {
                // Handle dialog dismissal - either resolved/canceled or minimized
                if (SOSReport.STATUS_RESOLVED.equals(currentStatus) ||
                        SOSReport.STATUS_CANCELED.equals(currentStatus)) {
                    clearSOSState();
                } else {
                    // Dialog was minimized
                    prefsManager.putBoolean("sos_dialog_minimized", true);
                }
            }

            @Override
            public void onError(String errorMessage) {
                Toast.makeText(requireContext(),
                        "Error updating SOS status: " + errorMessage,
                        Toast.LENGTH_SHORT).show();
            }
        });

        // Reset minimized state when showing dialog
        prefsManager.putBoolean("sos_dialog_minimized", false);
        dialog.show();
    }

    private void clearSOSState() {
        prefsManager.remove("active_sos_report_id");
        prefsManager.putBoolean("sos_dialog_minimized", false);
    }

    private void updateNotificationTags(SOSReport report) {
        if (notificationService != null) {
            // Set emergency preference tag
            notificationService.setEmergencyPreference(report.getEmergencyType());

            // Update last active timestamp
            notificationService.updateLastActive();

            // Set region if available
            if (report.getState() != null && !report.getState().isEmpty() &&
                    !report.getState().equalsIgnoreCase("Unknown")) {
                notificationService.setUserRegion(report.getState());
            }
        }
    }

    private void createFallbackSOSReport(String emergencyType) {
        if (!isAdded() || getContext() == null) return;

        // Create a basic emergency report with just location
        Location lastLocation = locationManager.getLastKnownLocation();
        if (lastLocation != null) {
            SOSReport report = new SOSReport();
            report.setEmergencyType(emergencyType);
            report.setLocation(new GeoPoint(lastLocation.getLatitude(), lastLocation.getLongitude()));
            report.setOnline(NetworkUtils.isOnline(requireContext()));
            report.setUserId(sessionManager.getSavedPhoneNumber());
            report.setTimestamp(new Date());

            // Process this basic report
            processSOSReport(report);
        } else {
            // If no location is available, show an error
            Toast.makeText(requireContext(),
                    getString(R.string.no_location_available),
                    Toast.LENGTH_LONG).show();
        }
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

    @Override
    public void onNotificationOpened(String type, JSONObject data) {
        try {
            Log.d(TAG, "Notification opened: " + type);

            switch (type) {
                case "EMERGENCY":
                    handleEmergencyNotification(data);
                    break;
                case "STATUS_UPDATE":
                    handleStatusUpdateNotification(data);
                    break;
                case "SMS_STATUS":
                    handleSmsStatusNotification(data);
                    break;
                default:
                    Log.d(TAG, "Unknown notification type: " + type);
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing notification", e);
        }
    }

    @Override
    public void onForegroundNotification(String type, JSONObject data) {
        // Similar to onNotificationOpened, but for notifications received while app is in foreground
        // For now, just log it - we'll handle it in a future implementation
        Log.d(TAG, "Foreground notification: " + type);
    }

    @Override
    public void onInAppMessageAction(String actionId, JSONObject data) {
        // Handle in-app message actions
        Log.d(TAG, "In-app message action: " + actionId);
    }

    private void handleEmergencyNotification(JSONObject data) {
        try {
            String emergencyType = data.optString("emergencyType", "UNKNOWN");
            String reportId = data.optString("reportId", "");

            Log.d(TAG, "Emergency notification: " + emergencyType + " (Report ID: " + reportId + ")");

            // If there's a report ID, show the status dialog
            if (!reportId.isEmpty()) {
                showSOSStatusForReport(reportId);
            } else {
                Toast.makeText(requireContext(),
                        "Received " + emergencyType + " emergency alert",
                        Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling emergency notification", e);
        }
    }

    private void handleStatusUpdateNotification(JSONObject data) {
        try {
            String reportId = data.optString("reportId", "");
            String status = data.optString("status", "");

            Log.d(TAG, "Status update notification: " + status + " (Report ID: " + reportId + ")");

            // If there's a report ID and it matches our active report, show the status dialog
            String activeReportId = prefsManager.getString("active_sos_report_id", null);
            if (reportId.equals(activeReportId)) {
                showSOSStatusForReport(reportId);
            } else {
                Toast.makeText(requireContext(),
                        "Emergency status updated to: " + status,
                        Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling status update notification", e);
        }
    }

    private void handleSmsStatusNotification(JSONObject data) {
        try {
            String reportId = data.optString("reportId", "");
            boolean successful = data.optBoolean("successful", false);

            Log.d(TAG, "SMS status notification: " + (successful ? "SUCCESS" : "FAILURE") +
                    " (Report ID: " + reportId + ")");

            // Display appropriate message
            String message = successful ?
                    "Emergency contact notified via SMS" :
                    "Could not reach emergency contact via SMS";

            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "Error handling SMS status notification", e);
        }
    }

    private void updateNetworkStatus() {
        if (!isAdded() || getContext() == null) return;

        boolean isOnline = NetworkUtils.isOnline(requireContext());

        // Avoid unnecessary view updates
        if (networkStatus != null) {
            networkStatus.setText(isOnline ? R.string.status_online : R.string.status_offline);
            networkStatus.setBackground(ContextCompat.getDrawable(requireContext(),
                    isOnline ? R.drawable.badge_green : R.drawable.badge_red));
        }

        // Update overall status
        updateOverallStatus(isOnline);
    }

    private void updateLocationStatus() {
        if (!isAdded() || getContext() == null) return;

        boolean hasPermission = permissionManager.hasLocationPermissions(false);

        if (locationAccuracy != null) {
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

                    // Only update current location if it's significantly different
                    if (currentLocation == null ||
                            currentLocation.distanceTo(lastKnown) > 5) { // 5 meters threshold
                        currentLocation = lastKnown;
                        updateMapWithCurrentLocation();
                    }
                } else {
                    locationAccuracy.setText(getString(R.string.location_unavailable));
                }
            } else {
                locationAccuracy.setText(getString(R.string.location_permission_required));
            }
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
        // Check if we're on the UI thread
        if (Looper.myLooper() != Looper.getMainLooper()) {
            uiUpdateHandler.post(this::doUpdateMapWithCurrentLocation);
        } else {
            doUpdateMapWithCurrentLocation();
        }
    }

    private void doUpdateMapWithCurrentLocation() {
        if (alertsGoogleMap != null && currentLocation != null && isMapReady) {
            LatLng latLng = new LatLng(currentLocation.getLatitude(), currentLocation.getLongitude());

            // Clear previous markers
            alertsGoogleMap.clear();

            // Add marker for current location
            alertsGoogleMap.addMarker(new MarkerOptions()
                    .position(latLng)
                    .title(getString(R.string.your_location))
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)));

            // Move camera to show current location - avoid animations to prevent jank
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

        // Update last active timestamp in OneSignal
        if (notificationService != null) {
            notificationService.updateLastActive();
        }

        // Check for active SOS reports
        checkForActiveSOS();
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