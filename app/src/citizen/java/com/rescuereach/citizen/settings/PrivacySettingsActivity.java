//package com.rescuereach.citizen.settings;
//
//import android.Manifest;
//import android.content.Intent;
//import android.content.pm.PackageManager;
//import android.net.Uri;
//import android.os.Bundle;
//import android.provider.Settings;
//import android.view.MenuItem;
//import android.widget.Toast;
//
//import androidx.activity.result.ActivityResultLauncher;
//import androidx.activity.result.contract.ActivityResultContracts;
//import androidx.annotation.NonNull;
//import androidx.appcompat.app.AppCompatActivity;
//import androidx.appcompat.widget.Toolbar;
//import androidx.core.app.ActivityCompat;
//import androidx.core.content.ContextCompat;
//
//import com.google.android.material.dialog.MaterialAlertDialogBuilder;
//import com.google.android.material.snackbar.Snackbar;
//import com.google.android.material.switchmaterial.SwitchMaterial;
//import com.rescuereach.R;
//import com.rescuereach.service.auth.UserSessionManager;
//import com.rescuereach.service.emergency.EmergencyAlertManager;
//import com.rescuereach.util.LocationManager;
//import com.rescuereach.util.MediaManager;
//
//public class PrivacySettingsActivity extends AppCompatActivity {
//
//    private static final String TAG = "PrivacySettingsActivity";
//    private static final int LOCATION_PERMISSION_CODE = 456;
//    private static final int CAMERA_PERMISSION_CODE = 457;
//    private static final int STORAGE_PERMISSION_CODE = 458;
//
//    private UserSessionManager sessionManager;
//    private LocationManager locationManager;
//    private MediaManager mediaManager;
//    private EmergencyAlertManager emergencyAlertManager;
//
//    private SwitchMaterial switchLocationSharing;
//    private SwitchMaterial switchProfileVisibility;
//    private SwitchMaterial switchMediaSharing;
//    private SwitchMaterial switchEmergencyAlerts;
//
//    // Permission request launcher for location
//    private final ActivityResultLauncher<String[]> requestLocationPermissionLauncher =
//            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
//                boolean allGranted = true;
//                for (Boolean granted : result.values()) {
//                    if (!granted) {
//                        allGranted = false;
//                        break;
//                    }
//                }
//
//                if (allGranted) {
//                    // All permissions granted
//                    Toast.makeText(this, R.string.location_permission_granted, Toast.LENGTH_SHORT).show();
//                    sessionManager.setPrivacyPreference("location_sharing", true);
//                    switchLocationSharing.setChecked(true);
//                } else {
//                    // Some permissions denied
//                    showLocationPermissionDeniedMessage();
//
//                    // Update switch to reflect actual state
//                    switchLocationSharing.setChecked(false);
//                    sessionManager.setPrivacyPreference("location_sharing", false);
//                }
//            });
//
//    // Permission request launcher for media (camera and storage)
//    private final ActivityResultLauncher<String[]> requestMediaPermissionLauncher =
//            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
//                boolean allGranted = true;
//                for (Boolean granted : result.values()) {
//                    if (!granted) {
//                        allGranted = false;
//                        break;
//                    }
//                }
//
//                if (allGranted) {
//                    // All permissions granted
//                    Toast.makeText(this, R.string.media_permission_granted, Toast.LENGTH_SHORT).show();
//                    sessionManager.setPrivacyPreference("media_sharing", true);
//                    switchMediaSharing.setChecked(true);
//                } else {
//                    // Some permissions denied
//                    showMediaPermissionDeniedMessage();
//
//                    // Update switch to reflect actual state
//                    switchMediaSharing.setChecked(false);
//                    sessionManager.setPrivacyPreference("media_sharing", false);
//                }
//            });
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_privacy_settings);
//
//        // Initialize services and managers
//        sessionManager = UserSessionManager.getInstance(this);
//        locationManager = new LocationManager(this);
//        mediaManager = new MediaManager(this);
//        emergencyAlertManager = new EmergencyAlertManager(this);
//
//        // Set up toolbar
//        Toolbar toolbar = findViewById(R.id.toolbar);
//        setSupportActionBar(toolbar);
//        if (getSupportActionBar() != null) {
//            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
//            getSupportActionBar().setTitle(R.string.privacy);
//        }
//
//        // Initialize UI elements
//        initUI();
//
//        // Load saved preferences
//        loadSavedPreferences();
//
//        // Set up listeners
//        setupListeners();
//    }
//
//    private void initUI() {
//        switchLocationSharing = findViewById(R.id.switch_location_sharing);
//        switchProfileVisibility = findViewById(R.id.switch_profile_visibility);
//        switchMediaSharing = findViewById(R.id.switch_media_sharing);
//        switchEmergencyAlerts = findViewById(R.id.switch_emergency_alerts);
//    }
//
//    private void loadSavedPreferences() {
//        // Load all privacy preferences from shared preferences
//        switchLocationSharing.setChecked(sessionManager.getPrivacyPreference("location_sharing", true));
//        switchProfileVisibility.setChecked(sessionManager.getPrivacyPreference("profile_visibility", true));
//        switchMediaSharing.setChecked(sessionManager.getPrivacyPreference("media_sharing", true));
//        switchEmergencyAlerts.setChecked(sessionManager.getEmergencyPreference("emergency_alerts_enabled", true));
//    }
//
//    private void setupListeners() {
//        // Location sharing toggle
//        switchLocationSharing.setOnCheckedChangeListener((buttonView, isChecked) -> {
//            if (isChecked) {
//                // Check if location permission is granted
//                if (checkLocationPermission()) {
//                    sessionManager.setPrivacyPreference("location_sharing", true);
//                    Toast.makeText(this, R.string.location_sharing_enabled, Toast.LENGTH_SHORT).show();
//                } else {
//                    // Don't update preference until permission is granted
//                    switchLocationSharing.setChecked(false);
//                }
//            } else {
//                // User is turning off location sharing
//                sessionManager.setPrivacyPreference("location_sharing", false);
//                Toast.makeText(this, R.string.location_sharing_disabled, Toast.LENGTH_SHORT).show();
//
//                // Stop any active location updates
//                locationManager.stopLocationUpdates();
//            }
//        });
//
//        // Profile visibility toggle
//        switchProfileVisibility.setOnCheckedChangeListener((buttonView, isChecked) -> {
//            sessionManager.setPrivacyPreference("profile_visibility", isChecked);
//            updateProfileVisibility(isChecked);
//
//            Toast.makeText(this, isChecked ?
//                    R.string.profile_visibility_enabled :
//                    R.string.profile_visibility_disabled, Toast.LENGTH_SHORT).show();
//        });
//
//        // Media sharing toggle
//        switchMediaSharing.setOnCheckedChangeListener((buttonView, isChecked) -> {
//            if (isChecked) {
//                // Check if camera and storage permissions are granted
//                if (checkMediaPermissions()) {
//                    sessionManager.setPrivacyPreference("media_sharing", true);
//                    Toast.makeText(this, R.string.media_sharing_enabled, Toast.LENGTH_SHORT).show();
//                } else {
//                    // Don't update preference until permissions are granted
//                    switchMediaSharing.setChecked(false);
//                }
//            } else {
//                // User is turning off media sharing
//                sessionManager.setPrivacyPreference("media_sharing", false);
//                Toast.makeText(this, R.string.media_sharing_disabled, Toast.LENGTH_SHORT).show();
//            }
//        });
//
//        // Emergency alerts toggle
//        switchEmergencyAlerts.setOnCheckedChangeListener((buttonView, isChecked) -> {
//            sessionManager.setEmergencyPreference("emergency_alerts_enabled", isChecked);
//            Toast.makeText(this, isChecked ?
//                    R.string.emergency_alerts_enabled :
//                    R.string.emergency_alerts_disabled, Toast.LENGTH_SHORT).show();
//        });
//    }
//
//    private boolean checkLocationPermission() {
//        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) !=
//                PackageManager.PERMISSION_GRANTED ||
//                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) !=
//                        PackageManager.PERMISSION_GRANTED) {
//
//            // Permission not granted, request it
//            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
//                    Manifest.permission.ACCESS_FINE_LOCATION)) {
//                // Show an explanation
//                showLocationPermissionRationale();
//            } else {
//                // No explanation needed, request the permission
//                requestLocationPermissionLauncher.launch(new String[] {
//                        Manifest.permission.ACCESS_FINE_LOCATION,
//                        Manifest.permission.ACCESS_COARSE_LOCATION
//                });
//            }
//            return false;
//        }
//        return true;
//    }
//
//    private boolean checkMediaPermissions() {
//        boolean cameraPermission = ContextCompat.checkSelfPermission(this,
//                Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
//        boolean storagePermission = ContextCompat.checkSelfPermission(this,
//                Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
//
//        if (!cameraPermission || !storagePermission) {
//            // At least one permission not granted, request them
//            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA) ||
//                    ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
//                // Show an explanation
//                showMediaPermissionRationale();
//            } else {
//                // No explanation needed, request the permissions
//                requestMediaPermissionLauncher.launch(new String[] {
//                        Manifest.permission.CAMERA,
//                        Manifest.permission.WRITE_EXTERNAL_STORAGE
//                });
//            }
//            return false;
//        }
//        return true;
//    }
//
//    private void showLocationPermissionRationale() {
//        new MaterialAlertDialogBuilder(this)
//                .setTitle(R.string.location_permission_title)
//                .setMessage(R.string.location_permission_message)
//                .setPositiveButton(R.string.grant_permission, (dialog, which) -> {
//                    requestLocationPermissionLauncher.launch(new String[] {
//                            Manifest.permission.ACCESS_FINE_LOCATION,
//                            Manifest.permission.ACCESS_COARSE_LOCATION
//                    });
//                })
//                .setNegativeButton(R.string.cancel, (dialog, which) -> {
//                    dialog.dismiss();
//                    showLocationPermissionDeniedMessage();
//
//                    // Update switch to reflect actual state
//                    switchLocationSharing.setChecked(false);
//                })
//                .show();
//    }
//
//    private void showMediaPermissionRationale() {
//        new MaterialAlertDialogBuilder(this)
//                .setTitle(R.string.media_permission_title)
//                .setMessage(R.string.media_permission_message)
//                .setPositiveButton(R.string.grant_permission, (dialog, which) -> {
//                    requestMediaPermissionLauncher.launch(new String[] {
//                            Manifest.permission.CAMERA,
//                            Manifest.permission.WRITE_EXTERNAL_STORAGE
//                    });
//                })
//                .setNegativeButton(R.string.cancel, (dialog, which) -> {
//                    dialog.dismiss();
//                    showMediaPermissionDeniedMessage();
//
//                    // Update switch to reflect actual state
//                    switchMediaSharing.setChecked(false);
//                })
//                .show();
//    }
//
//    private void showLocationPermissionDeniedMessage() {
//        Snackbar.make(findViewById(android.R.id.content),
//                        R.string.location_permission_denied, Snackbar.LENGTH_LONG)
//                .setAction(R.string.settings, view -> {
//                    // Open app settings
//                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
//                    Uri uri = Uri.fromParts("package", getPackageName(), null);
//                    intent.setData(uri);
//                    startActivity(intent);
//                })
//                .show();
//    }
//
//    private void showMediaPermissionDeniedMessage() {
//        Snackbar.make(findViewById(android.R.id.content),
//                        R.string.media_permission_denied, Snackbar.LENGTH_LONG)
//                .setAction(R.string.settings, view -> {
//                    // Open app settings
//                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
//                    Uri uri = Uri.fromParts("package", getPackageName(), null);
//                    intent.setData(uri);
//                    startActivity(intent);
//                })
//                .show();
//    }
//
//    private void updateProfileVisibility(boolean isVisible) {
//        // Get user ID
//        String userId = sessionManager.getUserId();
//        if (userId == null) {
//            Toast.makeText(this, R.string.error_user_not_logged_in, Toast.LENGTH_SHORT).show();
//            return;
//        }
//
//        // Update profile visibility in Firestore
//        // In a real app, this would update a field in the user's document
//        Toast.makeText(this, isVisible ?
//                R.string.profile_visibility_enabled :
//                R.string.profile_visibility_disabled, Toast.LENGTH_SHORT).show();
//    }
//
//    @Override
//    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
//        if (item.getItemId() == android.R.id.home) {
//            onBackPressed();
//            return true;
//        }
//        return super.onOptionsItemSelected(item);
//    }
//
//    @Override
//    public void onBackPressed() {
//        Toast.makeText(this, R.string.privacy_settings_saved, Toast.LENGTH_SHORT).show();
//        super.onBackPressed();
//    }
//}