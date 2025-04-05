package com.rescuereach.citizen.settings;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.rescuereach.R;
import com.rescuereach.base.BaseActivity;
import com.rescuereach.citizen.PhoneAuthActivity;
import com.rescuereach.data.repository.OnCompleteListener;
import com.rescuereach.service.auth.UserSessionManager;
import com.rescuereach.service.backup.LocalBackupManager;
import com.rescuereach.service.data.AccountDeletionManager;
import com.rescuereach.service.data.DataClearManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DataManagementActivity extends BaseActivity {
    private static final String TAG = "DataManagementActivity";

    private UserSessionManager sessionManager;
    private LocalBackupManager backupManager;
    private DataClearManager dataClearManager;
    private AccountDeletionManager accountDeletionManager;

    private SwitchMaterial switchAutoBackup;
    private TextView lastBackupDate;
    private LinearLayout layoutClearData;
    private LinearLayout layoutDeleteAccount;
    private CircularProgressIndicator progressIndicator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_data_management);

        // Initialize managers
        sessionManager = UserSessionManager.getInstance(this);
        backupManager = new LocalBackupManager(this);
        dataClearManager = new DataClearManager(this);
        accountDeletionManager = new AccountDeletionManager(this);

        // Set up toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.data_management);
        }

        // Initialize UI
        initUI();

        // Load saved preferences
        loadSavedPreferences();

        // Set up listeners
        setupListeners();
    }

    private void initUI() {
        switchAutoBackup = findViewById(R.id.switch_auto_backup);
        lastBackupDate = findViewById(R.id.text_last_backup_date);
        layoutClearData = findViewById(R.id.layout_clear_data);
        layoutDeleteAccount = findViewById(R.id.layout_delete_account);
        progressIndicator = findViewById(R.id.progress_indicator);
    }

    private void loadSavedPreferences() {
        try {
            // Load auto backup preference
            boolean autoBackupEnabled = sessionManager.getBackupPreference("auto_backup_enabled", false);
            switchAutoBackup.setChecked(autoBackupEnabled);

            // Load last backup date
            long lastBackupTimestamp = sessionManager.getLongPreference("last_backup_timestamp", 0);
            if (lastBackupTimestamp > 0) {
                SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault());
                String formattedDate = dateFormat.format(new Date(lastBackupTimestamp));
                lastBackupDate.setText(getString(R.string.last_backup, formattedDate));
                lastBackupDate.setVisibility(View.VISIBLE);
            } else {
                lastBackupDate.setText(R.string.no_backup_yet);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading preferences", e);
            Toast.makeText(this, "Error loading settings", Toast.LENGTH_SHORT).show();
        }
    }

    private void setupListeners() {
        // Auto backup toggle
        switchAutoBackup.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sessionManager.setBackupPreference("auto_backup_enabled", isChecked);

            if (isChecked) {
                // Schedule auto backup
                backupManager.scheduleAutoBackup();
                Toast.makeText(this, R.string.auto_backup_enabled, Toast.LENGTH_SHORT).show();

                // Create initial backup if none exists
                if (sessionManager.getLongPreference("last_backup_timestamp", 0) == 0) {
                    createBackupNow();
                }
            } else {
                // Cancel scheduled backups
                backupManager.cancelAutoBackup();
                Toast.makeText(this, R.string.auto_backup_disabled, Toast.LENGTH_SHORT).show();
            }
        });

        // Create backup now option
        findViewById(R.id.layout_backup_now).setOnClickListener(v -> createBackupNow());

        // Clear data option
        layoutClearData.setOnClickListener(v -> showClearDataConfirmation());

        // Delete account option
        layoutDeleteAccount.setOnClickListener(v -> showDeleteAccountConfirmation());
    }

    private void createBackupNow() {
        showProgress(true);

        backupManager.createBackup(new OnCompleteListener() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    showProgress(false);

                    // Update last backup date
                    long currentTime = System.currentTimeMillis();
                    sessionManager.setLongPreference("last_backup_timestamp", currentTime);

                    SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault());
                    String formattedDate = dateFormat.format(new Date(currentTime));
                    lastBackupDate.setText(getString(R.string.last_backup, formattedDate));
                    lastBackupDate.setVisibility(View.VISIBLE);

                    Toast.makeText(DataManagementActivity.this,
                            R.string.backup_created_successfully, Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> {
                    showProgress(false);
                    Log.e(TAG, "Backup failed", e);
                    Toast.makeText(DataManagementActivity.this,
                            getString(R.string.backup_failed, e.getMessage()),
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void showClearDataConfirmation() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.clear_data_title)
                .setMessage(R.string.clear_data_message)
                .setPositiveButton(R.string.clear_data, (dialog, which) -> clearUserData())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void clearUserData() {
        showProgress(true);

        dataClearManager.clearUserData(new OnCompleteListener() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    showProgress(false);
                    Toast.makeText(DataManagementActivity.this,
                            R.string.data_cleared_successfully, Toast.LENGTH_SHORT).show();

                    // Navigate to auth screen
                    navigateToAuthScreen();
                });
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> {
                    showProgress(false);
                    Log.e(TAG, "Data clear failed", e);
                    Toast.makeText(DataManagementActivity.this,
                            getString(R.string.data_clear_failed, e.getMessage()),
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void showDeleteAccountConfirmation() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete_account_title)
                .setMessage(R.string.delete_account_message)
                .setPositiveButton(R.string.delete_account, (dialog, which) -> {
                    // Double confirmation
                    showFinalDeleteConfirmation();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showFinalDeleteConfirmation() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.confirm_delete_account)
                .setMessage(R.string.final_delete_account_message)
                .setPositiveButton(R.string.confirm_delete, (dialog, which) -> deleteUserAccount())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void deleteUserAccount() {
        showProgress(true);

        accountDeletionManager.deleteUserAccount(new OnCompleteListener() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    showProgress(false);
                    Toast.makeText(DataManagementActivity.this,
                            R.string.account_deleted_successfully, Toast.LENGTH_SHORT).show();

                    // Navigate to auth screen
                    navigateToAuthScreen();
                });
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> {
                    showProgress(false);
                    Log.e(TAG, "Account deletion failed", e);
                    Toast.makeText(DataManagementActivity.this,
                            getString(R.string.account_deletion_failed, e.getMessage()),
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void navigateToAuthScreen() {
        Intent intent = new Intent(this, PhoneAuthActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void showProgress(boolean show) {
        progressIndicator.setVisibility(show ? View.VISIBLE : View.GONE);
        // Disable interaction during progress
        switchAutoBackup.setEnabled(!show);
        layoutClearData.setEnabled(!show);
        layoutDeleteAccount.setEnabled(!show);
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