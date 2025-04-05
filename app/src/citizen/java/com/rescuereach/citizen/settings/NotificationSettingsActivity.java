package com.rescuereach.citizen.settings;

import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.rescuereach.R;
import com.rescuereach.service.auth.UserSessionManager;

public class NotificationSettingsActivity extends AppCompatActivity {

    private UserSessionManager sessionManager;
    private SwitchMaterial switchEmergencyAlerts;
    private SwitchMaterial switchCommunityAlerts;
    private SwitchMaterial switchSound;
    private SwitchMaterial switchVibration;
    private SwitchMaterial switchNewsUpdates;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notification_settings);

        sessionManager = UserSessionManager.getInstance(this);

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
        switchEmergencyAlerts.setOnCheckedChangeListener((buttonView, isChecked) ->
                sessionManager.setNotificationPreference("emergency_alerts", isChecked));

        switchCommunityAlerts.setOnCheckedChangeListener((buttonView, isChecked) ->
                sessionManager.setNotificationPreference("community_alerts", isChecked));

        switchSound.setOnCheckedChangeListener((buttonView, isChecked) ->
                sessionManager.setNotificationPreference("sound", isChecked));

        switchVibration.setOnCheckedChangeListener((buttonView, isChecked) ->
                sessionManager.setNotificationPreference("vibration", isChecked));

        switchNewsUpdates.setOnCheckedChangeListener((buttonView, isChecked) ->
                sessionManager.setNotificationPreference("news_updates", isChecked));
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