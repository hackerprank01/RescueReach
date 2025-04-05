package com.rescuereach.citizen.settings;

import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.rescuereach.R;
import com.rescuereach.service.auth.UserSessionManager;

public class EmergencySettingsActivity extends AppCompatActivity {

    private UserSessionManager sessionManager;
    private SwitchMaterial switchQuickSOS;
    private SwitchMaterial switchShareLocationEmergency;
    private SwitchMaterial switchAlertContacts;
    private TextInputEditText editMessageTemplate;
    private MaterialButton btnSaveTemplate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_emergency_settings);

        sessionManager = UserSessionManager.getInstance(this);

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
        switchQuickSOS.setOnCheckedChangeListener((buttonView, isChecked) ->
                sessionManager.setEmergencyPreference("quick_sos", isChecked));

        switchShareLocationEmergency.setOnCheckedChangeListener((buttonView, isChecked) ->
                sessionManager.setEmergencyPreference("share_location_emergency", isChecked));

        switchAlertContacts.setOnCheckedChangeListener((buttonView, isChecked) ->
                sessionManager.setEmergencyPreference("alert_contacts", isChecked));

        btnSaveTemplate.setOnClickListener(v -> {
            String template = editMessageTemplate.getText().toString().trim();
            if (!template.isEmpty()) {
                sessionManager.setStringPreference("emergency_message_template", template);
                Toast.makeText(this, R.string.template_saved, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, R.string.template_empty_error, Toast.LENGTH_SHORT).show();
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