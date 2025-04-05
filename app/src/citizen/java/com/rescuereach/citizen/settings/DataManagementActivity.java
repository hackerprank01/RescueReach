package com.rescuereach.citizen.settings;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.auth.FirebaseAuth;
import com.rescuereach.R;
import com.rescuereach.service.auth.UserSessionManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DataManagementActivity extends AppCompatActivity {

    private UserSessionManager sessionManager;
    private SwitchMaterial switchAutoBackup;
    private TextView textLastBackupDate;
    private MaterialButton btnBackupNow;
    private MaterialButton btnExportData;
    private MaterialButton btnClearData;
    private MaterialButton btnDeleteAccount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_data_management);

        sessionManager = UserSessionManager.getInstance(this);

        // Set up toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle(R.string.data_management);

        // Initialize UI elements
        initUI();

        // Load saved preferences
        loadSavedPreferences();

        // Set up listeners
        setupListeners();
    }

    private void initUI() {
        switchAutoBackup = findViewById(R.id.switch_auto_backup);
        textLastBackupDate = findViewById(R.id.text_last_backup_date);
        btnBackupNow = findViewById(R.id.btn_backup_now);
        btnExportData = findViewById(R.id.btn_export_data);
        btnClearData = findViewById(R.id.btn_clear_data);
        btnDeleteAccount = findViewById(R.id.btn_delete_account);
    }

    private void loadSavedPreferences() {
        // Auto backup preference
        switchAutoBackup.setChecked(sessionManager.getDataPreference("auto_backup", true));

        // Last backup date
        long lastBackupTimestamp = sessionManager.getLongPreference("last_backup_timestamp", 0);
        if (lastBackupTimestamp > 0) {
            SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault());
            String lastBackupDate = sdf.format(new Date(lastBackupTimestamp));
            textLastBackupDate.setText(getString(R.string.last_backup_date, lastBackupDate));
        } else {
            textLastBackupDate.setText(R.string.no_backup_yet);
        }
    }

    private void setupListeners() {
        switchAutoBackup.setOnCheckedChangeListener((buttonView, isChecked) ->
                sessionManager.setDataPreference("auto_backup", isChecked));

        btnBackupNow.setOnClickListener(v -> performBackup());

        btnExportData.setOnClickListener(v -> exportUserData());

        btnClearData.setOnClickListener(v -> confirmClearData());

        btnDeleteAccount.setOnClickListener(v -> confirmDeleteAccount());
    }

    private void performBackup() {
        // In a real app, this would perform an actual data backup
        // For now, we'll just update the last backup timestamp

        long currentTime = System.currentTimeMillis();
        sessionManager.setLongPreference("last_backup_timestamp", currentTime);

        // Update the UI
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault());
        String lastBackupDate = sdf.format(new Date(currentTime));
        textLastBackupDate.setText(getString(R.string.last_backup_date, lastBackupDate));

        Toast.makeText(this, R.string.backup_complete, Toast.LENGTH_SHORT).show();
    }

    private void exportUserData() {
        // In a real app, this would generate and share a file with the user's data
        Toast.makeText(this, R.string.export_started, Toast.LENGTH_SHORT).show();

        // Simulate a delay
        btnExportData.setEnabled(false);
        btnExportData.postDelayed(() -> {
            Toast.makeText(DataManagementActivity.this, R.string.export_complete, Toast.LENGTH_SHORT).show();
            btnExportData.setEnabled(true);
        }, 2000);
    }

    private void confirmClearData() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.clear_data_title)
                .setMessage(R.string.clear_data_message)
                .setPositiveButton(R.string.clear, (dialog, which) -> {
                    // Clear local data (in a real app, this would be more comprehensive)
                    sessionManager.clearLocalData();
                    Toast.makeText(DataManagementActivity.this, R.string.data_cleared, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.cancel, null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    private void confirmDeleteAccount() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_account_title)
                .setMessage(R.string.delete_account_message)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    // In a real app, this would delete the user's account from Firebase
                    deleteUserAccount();
                })
                .setNegativeButton(R.string.cancel, null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    private void deleteUserAccount() {
        // This is a simulation - in a real app you would delete the Firebase account
        Toast.makeText(this, R.string.deleting_account, Toast.LENGTH_SHORT).show();

        // Disable the button during the operation
        btnDeleteAccount.setEnabled(false);

        FirebaseAuth.getInstance().getCurrentUser().delete()
                .addOnSuccessListener(aVoid -> {
                    // Clear all local data
                    sessionManager.clearSession();

                    Toast.makeText(DataManagementActivity.this,
                            R.string.account_deleted, Toast.LENGTH_SHORT).show();

                    // Navigate back to login screen
                    finishAffinity();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(DataManagementActivity.this,
                            getString(R.string.delete_account_error, e.getMessage()),
                            Toast.LENGTH_LONG).show();
                    btnDeleteAccount.setEnabled(true);
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
}