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

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.rescuereach.R;
import com.rescuereach.service.auth.UserSessionManager;
import com.rescuereach.service.emergency.SOSManager;

public class EmergencySettingsActivity extends AppCompatActivity {

    private UserSessionManager sessionManager;
    private SOSManager sosManager;
    private SwitchMaterial switchQuickSOS;
    private SwitchMaterial switchShareLocationEmergency;
    private SwitchMaterial switchAlertContacts;
    private TextInputEditText editMessageTemplate;
    private MaterialButton btnSaveTemplate;
    private MaterialButton btnTestSOS;

    private static final int SMS_PERMISSION_CODE = 789;

    private final ActivityResultLauncher<String> requestSmsPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    // Permission granted
                    Toast.makeText(this, R.string.sms_permission_granted, Toast.LENGTH_SHORT).show();
                } else {
                    // Permission denied
                    switchAlertContacts.setChecked(false);
                    sessionManager.setEmergencyPreference("alert_contacts", false);
                    showSmsPermissionDeniedMessage();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_emergency_settings);

        sessionManager = UserSessionManager.getInstance(this);
        sosManager = SOSManager.getInstance(this);

        // Set up toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle(R.string.emergency_settings);

        // Initialize UI elements
        initUI();

        // Load saved preferences
        loadSavedPreferences();

        // Set up listeners
        setupListeners();
    }

    private void initUI() {
        switchQuickSOS = findViewById(R.id.switch_quick_sos);
        switchShareLocationEmergency = findViewById(R.id.switch_share_location_emergency);
        switchAlertContacts = findViewById(R.id.switch_alert_contacts);
        editMessageTemplate = findViewById(R.id.edit_message_template);
        btnSaveTemplate = findViewById(R.id.btn_save_template);
        btnTestSOS = findViewById(R.id.btn_test_sos);
    }

    private void loadSavedPreferences() {
        // Load all emergency preferences
        switchQuickSOS.setChecked(sessionManager.getEmergencyPreference("quick_sos", true));
        switchShareLocationEmergency.setChecked(sessionManager.getEmergencyPreference("share_location_emergency", true));
        switchAlertContacts.setChecked(sessionManager.getEmergencyPreference("alert_contacts", true));

        // Load message template
        String defaultTemplate = getString(R.string.default_emergency_message);
        editMessageTemplate.setText(sessionManager.getStringPreference("emergency_message_template", defaultTemplate));
    }

    private void setupListeners() {
        switchQuickSOS.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sessionManager.setEmergencyPreference("quick_sos", isChecked);
            Toast.makeText(this, isChecked ?
                    R.string.quick_sos_enabled :
                    R.string.quick_sos_disabled, Toast.LENGTH_SHORT).show();
        });

        switchShareLocationEmergency.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sessionManager.setEmergencyPreference("share_location_emergency", isChecked);
            Toast.makeText(this, isChecked ?
                    R.string.location_sharing_emergency_enabled :
                    R.string.location_sharing_emergency_disabled, Toast.LENGTH_SHORT).show();
        });

        switchAlertContacts.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                // Check if SMS permission is granted
                if (checkSmsPermission()) {
                    sessionManager.setEmergencyPreference("alert_contacts", true);
                    Toast.makeText(this, R.string.alert_contacts_enabled, Toast.LENGTH_SHORT).show();
                } else {
                    // Don't update preference until permission is granted
                    switchAlertContacts.setChecked(false);
                }
            } else {
                sessionManager.setEmergencyPreference("alert_contacts", false);
                Toast.makeText(this, R.string.alert_contacts_disabled, Toast.LENGTH_SHORT).show();
            }
        });

        btnSaveTemplate.setOnClickListener(v -> {
            String template = editMessageTemplate.getText().toString().trim();
            if (!template.isEmpty()) {
                sessionManager.setStringPreference("emergency_message_template", template);
                Toast.makeText(this, R.string.template_saved, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, R.string.template_empty_error, Toast.LENGTH_SHORT).show();
            }
        });

        btnTestSOS.setOnClickListener(v -> {
            testSOSFeature();
        });
    }

    private boolean checkSmsPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) !=
                PackageManager.PERMISSION_GRANTED) {

            // Permission not granted, request it
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.SEND_SMS)) {
                // Show an explanation
                showSmsPermissionRationale();
            } else {
                // No explanation needed, request the permission
                requestSmsPermissionLauncher.launch(Manifest.permission.SEND_SMS);
            }
            return false;
        }
        return true;
    }

    private void showSmsPermissionRationale() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.sms_permission_title)
                .setMessage(R.string.sms_permission_message)
                .setPositiveButton(R.string.grant_permission, (dialog, which) -> {
                    requestSmsPermissionLauncher.launch(Manifest.permission.SEND_SMS);
                })
                .setNegativeButton(R.string.cancel, (dialog, which) -> {
                    dialog.dismiss();
                    showSmsPermissionDeniedMessage();

                    // Update switch to reflect actual state
                    switchAlertContacts.setChecked(false);
                    sessionManager.setEmergencyPreference("alert_contacts", false);
                })
                .show();
    }

    private void showSmsPermissionDeniedMessage() {
        Snackbar.make(findViewById(android.R.id.content),
                        R.string.sms_permission_denied, Snackbar.LENGTH_LONG)
                .setAction(R.string.settings, view -> {
                    // Open app settings
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    Uri uri = Uri.fromParts("package", getPackageName(), null);
                    intent.setData(uri);
                    startActivity(intent);
                })
                .show();
    }

    private void testSOSFeature() {
        // Show a confirmation dialog
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.test_sos_title)
                .setMessage(R.string.test_sos_message)
                .setPositiveButton(R.string.proceed, (dialog, which) -> {
                    // Proceed with test
                    performTestSOS();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void performTestSOS() {
        // Check for required permissions
        boolean canProceed = true;

        // If location sharing is enabled, check location permission
        if (sessionManager.getEmergencyPreference("share_location_emergency", true)) {
            boolean hasLocationPermission = ContextCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;

            if (!hasLocationPermission) {
                Toast.makeText(this, R.string.location_permission_required, Toast.LENGTH_LONG).show();
                canProceed = false;
            }
        }

        // If alert contacts is enabled, check SMS permission
        if (sessionManager.getEmergencyPreference("alert_contacts", true)) {
            boolean hasSmsPermission = ContextCompat.checkSelfPermission(this,
                    Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED;

            if (!hasSmsPermission) {
                Toast.makeText(this, R.string.sms_permission_required, Toast.LENGTH_LONG).show();
                canProceed = false;
            }
        }

        if (!canProceed) {
            return;
        }

        // Test SOS feature (in a real implementation, this would be a "test mode" flag)
        Toast.makeText(this, R.string.sending_test_sos, Toast.LENGTH_SHORT).show();

        // Simulate an SOS alert
        sosManager.sendSOSAlert(new SOSManager.OnSOSResultListener() {
            @Override
            public void onSuccess(String emergencyId) {
                runOnUiThread(() -> {
                    Toast.makeText(EmergencySettingsActivity.this,
                            getString(R.string.test_sos_sent, emergencyId),
                            Toast.LENGTH_LONG).show();
                });
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(EmergencySettingsActivity.this,
                            getString(R.string.test_sos_failed, e.getMessage()),
                            Toast.LENGTH_LONG).show();
                });
            }
        });
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
        Toast.makeText(this, R.string.emergency_settings_saved, Toast.LENGTH_SHORT).show();
        super.onBackPressed();
    }
}