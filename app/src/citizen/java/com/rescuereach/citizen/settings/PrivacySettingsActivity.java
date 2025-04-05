package com.rescuereach.citizen.settings;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.rescuereach.R;
import com.rescuereach.service.auth.UserSessionManager;
import com.rescuereach.util.LocationManager;

public class PrivacySettingsActivity extends AppCompatActivity {

    private UserSessionManager sessionManager;
    private com.rescuereach.util.LocationManager locationManager;
    private FirebaseAnalytics firebaseAnalytics;

    private SwitchMaterial switchLocationSharing;
    private SwitchMaterial switchAnonymousReporting;
    private SwitchMaterial switchDataCollection;
    private SwitchMaterial switchProfileVisibility;

    private static final int LOCATION_PERMISSION_CODE = 456;

    private final ActivityResultLauncher<String[]> requestLocationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean allGranted = true;
                for (Boolean granted : result.values()) {
                    if (!granted) {
                        allGranted = false;
                        break;
                    }
                }

                if (allGranted) {
                    // All permissions granted
                    Toast.makeText(this, R.string.location_permission_granted, Toast.LENGTH_SHORT).show();
                } else {
                    // Some permissions denied
                    showLocationPermissionDeniedMessage();

                    // Update switch to reflect actual state
                    switchLocationSharing.setChecked(false);
                    sessionManager.setPrivacyPreference("location_sharing", false);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_privacy_settings);

        sessionManager = UserSessionManager.getInstance(this);
        locationManager = new com.rescuereach.util.LocationManager(this);
        firebaseAnalytics = FirebaseAnalytics.getInstance(this);

        // Set up toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle(R.string.privacy);

        // Initialize UI elements
        initUI();

        // Load saved preferences
        loadSavedPreferences();

        // Set up listeners
        setupListeners();
    }

    private void initUI() {
        switchLocationSharing = findViewById(R.id.switch_location_sharing);
        switchAnonymousReporting = findViewById(R.id.switch_anonymous_reporting);
        switchDataCollection = findViewById(R.id.switch_data_collection);
        switchProfileVisibility = findViewById(R.id.switch_profile_visibility);
    }

    private void loadSavedPreferences() {
        // Load all privacy preferences from shared preferences
        switchLocationSharing.setChecked(sessionManager.getPrivacyPreference("location_sharing", true));
        switchAnonymousReporting.setChecked(sessionManager.getPrivacyPreference("anonymous_reporting", false));
        switchDataCollection.setChecked(sessionManager.getPrivacyPreference("data_collection", true));
        switchProfileVisibility.setChecked(sessionManager.getPrivacyPreference("profile_visibility", true));

        // Apply data collection setting to Firebase Analytics
        firebaseAnalytics.setAnalyticsCollectionEnabled(
                sessionManager.getPrivacyPreference("data_collection", true));
    }

    private void setupListeners() {
        switchLocationSharing.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                // Check if location permission is granted
                if (checkLocationPermission()) {
                    sessionManager.setPrivacyPreference("location_sharing", true);
                } else {
                    // Don't update preference until permission is granted
                    switchLocationSharing.setChecked(false);
                }
            } else {
                // User is turning off location sharing
                sessionManager.setPrivacyPreference("location_sharing", false);

                // Stop any active location updates
                locationManager.stopLocationUpdates();
            }
        });

        switchAnonymousReporting.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sessionManager.setPrivacyPreference("anonymous_reporting", isChecked);
            Toast.makeText(this, isChecked ?
                    R.string.anonymous_reporting_enabled :
                    R.string.anonymous_reporting_disabled, Toast.LENGTH_SHORT).show();
        });

        switchDataCollection.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sessionManager.setPrivacyPreference("data_collection", isChecked);

            // Apply data collection setting to Firebase Analytics
            firebaseAnalytics.setAnalyticsCollectionEnabled(isChecked);

            Toast.makeText(this, isChecked ?
                    R.string.data_collection_enabled :
                    R.string.data_collection_disabled, Toast.LENGTH_SHORT).show();
        });

        switchProfileVisibility.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sessionManager.setPrivacyPreference("profile_visibility", isChecked);

            // Update profile visibility in Firestore
            updateProfileVisibility(isChecked);
        });
    }

    private boolean checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) !=
                        PackageManager.PERMISSION_GRANTED) {

            // Permission not granted, request it
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.ACCESS_FINE_LOCATION)) {
                // Show an explanation
                showLocationPermissionRationale();
            } else {
                // No explanation needed, request the permission
                requestLocationPermissionLauncher.launch(new String[] {
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                });
            }
            return false;
        }
        return true;
    }

    private void showLocationPermissionRationale() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.location_permission_title)
                .setMessage(R.string.location_permission_message)
                .setPositiveButton(R.string.grant_permission, (dialog, which) -> {
                    requestLocationPermissionLauncher.launch(new String[] {
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    });
                })
                .setNegativeButton(R.string.cancel, (dialog, which) -> {
                    dialog.dismiss();
                    showLocationPermissionDeniedMessage();

                    // Update switch to reflect actual state
                    switchLocationSharing.setChecked(false);
                })
                .show();
    }

    private void showLocationPermissionDeniedMessage() {
        Snackbar.make(findViewById(android.R.id.content),
                        R.string.location_permission_denied, Snackbar.LENGTH_LONG)
                .setAction(R.string.settings, view -> {
                    // Open app settings
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    Uri uri = Uri.fromParts("package", getPackageName(), null);
                    intent.setData(uri);
                    startActivity(intent);
                })
                .show();
    }

    private void updateProfileVisibility(boolean isVisible) {
        // Get user ID
        String userId = sessionManager.getUserId();
        if (userId == null) {
            Toast.makeText(this, R.string.error_user_not_logged_in, Toast.LENGTH_SHORT).show();
            return;
        }

        // Update profile visibility in Firestore
        // In a real app, this would update a field in the user's document
        Toast.makeText(this, isVisible ?
                R.string.profile_visibility_enabled :
                R.string.profile_visibility_disabled, Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        Toast.makeText(this, R.string.privacy_settings_saved, Toast.LENGTH_SHORT).show();
        super.onBackPressed();
    }
}