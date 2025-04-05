package com.rescuereach.citizen.settings;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
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
import com.rescuereach.R;
import com.rescuereach.service.auth.UserSessionManager;
import com.rescuereach.service.notification.NotificationManager;

public class NotificationSettingsActivity extends AppCompatActivity {

    private UserSessionManager sessionManager;
    private NotificationManager notificationManager;
    private SwitchMaterial switchEmergencyAlerts;
    private SwitchMaterial switchCommunityAlerts;
    private SwitchMaterial switchSound;
    private SwitchMaterial switchVibration;
    private SwitchMaterial switchNewsUpdates;

    private static final int NOTIFICATION_PERMISSION_CODE = 123;

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    // Permission granted
                    applyNotificationSettings();
                } else {
                    // Permission denied
                    showPermissionDeniedMessage();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notification_settings);

        sessionManager = UserSessionManager.getInstance(this);
        notificationManager = NotificationManager.getInstance(this);

        // Set up toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle(R.string.notifications);

        // Initialize UI elements
        initUI();

        // Load saved preferences
        loadSavedPreferences();

        // Set up listeners
        setupListeners();

        // Check notification permission
        checkNotificationPermission();
    }

    private void initUI() {
        switchEmergencyAlerts = findViewById(R.id.switch_emergency_alerts);
        switchCommunityAlerts = findViewById(R.id.switch_community_alerts);
        switchSound = findViewById(R.id.switch_sound);
        switchVibration = findViewById(R.id.switch_vibration);
        switchNewsUpdates = findViewById(R.id.switch_news_updates);
    }

    private void loadSavedPreferences() {
        // Load all notification preferences from shared preferences
        switchEmergencyAlerts.setChecked(sessionManager.getNotificationPreference("emergency_alerts", true));
        switchCommunityAlerts.setChecked(sessionManager.getNotificationPreference("community_alerts", true));
        switchSound.setChecked(sessionManager.getNotificationPreference("sound", true));
        switchVibration.setChecked(sessionManager.getNotificationPreference("vibration", true));
        switchNewsUpdates.setChecked(sessionManager.getNotificationPreference("news_updates", false));
    }

    private void setupListeners() {
        switchEmergencyAlerts.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sessionManager.setNotificationPreference("emergency_alerts", isChecked);
            applyNotificationSettings();
        });

        switchCommunityAlerts.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sessionManager.setNotificationPreference("community_alerts", isChecked);
            applyNotificationSettings();
        });

        switchSound.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sessionManager.setNotificationPreference("sound", isChecked);
            applyNotificationSettings();
        });

        switchVibration.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sessionManager.setNotificationPreference("vibration", isChecked);
            applyNotificationSettings();
        });

        switchNewsUpdates.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sessionManager.setNotificationPreference("news_updates", isChecked);
            applyNotificationSettings();
        });
    }

    private void checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                // Permission not granted, request it
                if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                        Manifest.permission.POST_NOTIFICATIONS)) {
                    // Show an explanation
                    showNotificationPermissionRationale();
                } else {
                    // No explanation needed, request the permission
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
                }
            }
        }
    }

    private void showNotificationPermissionRationale() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.notification_permission_title)
                .setMessage(R.string.notification_permission_message)
                .setPositiveButton(R.string.grant_permission, (dialog, which) -> {
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
                })
                .setNegativeButton(R.string.cancel, (dialog, which) -> {
                    dialog.dismiss();
                    showPermissionDeniedMessage();
                })
                .show();
    }

    private void showPermissionDeniedMessage() {
        Snackbar.make(findViewById(android.R.id.content),
                        R.string.notification_permission_denied, Snackbar.LENGTH_LONG)
                .setAction(R.string.settings, view -> {
                    // Open app settings
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    Uri uri = Uri.fromParts("package", getPackageName(), null);
                    intent.setData(uri);
                    startActivity(intent);
                })
                .show();
    }

    private void applyNotificationSettings() {
        notificationManager.applyNotificationSettings();
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
        Toast.makeText(this, R.string.notification_settings_saved, Toast.LENGTH_SHORT).show();
        super.onBackPressed();
    }
}