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

public class PrivacySettingsActivity extends AppCompatActivity {

    private UserSessionManager sessionManager;
    private SwitchMaterial switchLocationSharing;
    private SwitchMaterial switchAnonymousReporting;
    private SwitchMaterial switchDataCollection;
    private SwitchMaterial switchProfileVisibility;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_privacy_settings);

        sessionManager = UserSessionManager.getInstance(this);

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
    }

    private void setupListeners() {
        switchLocationSharing.setOnCheckedChangeListener((buttonView, isChecked) ->
                sessionManager.setPrivacyPreference("location_sharing", isChecked));

        switchAnonymousReporting.setOnCheckedChangeListener((buttonView, isChecked) ->
                sessionManager.setPrivacyPreference("anonymous_reporting", isChecked));

        switchDataCollection.setOnCheckedChangeListener((buttonView, isChecked) ->
                sessionManager.setPrivacyPreference("data_collection", isChecked));

        switchProfileVisibility.setOnCheckedChangeListener((buttonView, isChecked) ->
                sessionManager.setPrivacyPreference("profile_visibility", isChecked));
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