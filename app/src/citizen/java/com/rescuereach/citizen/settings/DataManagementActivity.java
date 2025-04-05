package com.rescuereach.citizen.settings;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
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
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.auth.FirebaseAuth;

import com.rescuereach.R;
import com.rescuereach.data.repository.firebase.FirebaseUserRepository;
import com.rescuereach.data.repository.OnCompleteListener;
import com.rescuereach.data.repository.UserRepository;
import com.rescuereach.service.auth.UserSessionManager;
import com.rescuereach.service.data.DataManager;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DataManagementActivity extends AppCompatActivity {

    private UserSessionManager sessionManager;
    private DataManager dataManager;
    private UserRepository userRepository;

    private SwitchMaterial switchAutoBackup;
    private TextView textLastBackupDate;
    private MaterialButton btnBackupNow;
    private MaterialButton btnExportData;
    private MaterialButton btnClearData;
    private MaterialButton btnDeleteAccount;
    private CircularProgressIndicator progressIndicator;

    private static final int WRITE_STORAGE_PERMISSION_CODE = 101;

    private final ActivityResultLauncher<String> requestStoragePermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    // Permission granted, proceed with export
                    performDataExport();
                } else {
                    // Permission denied
                    showStoragePermissionDeniedMessage();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_data_management);

        // Initialize repositories and managers
        sessionManager = UserSessionManager.getInstance(this);
        userRepository = new FirebaseUserRepository();
        dataManager = DataManager.getInstance(this, userRepository);

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
        progressIndicator = findViewById(R.id.progress_indicator);

        // Set progress indicator visibility
        progressIndicator.setVisibility(android.view.View.GONE);
    }

    private void loadSavedPreferences() {
        // Auto backup preference
        switchAutoBackup.setChecked(dataManager.isAutoBackupEnabled());

        // Last backup date
        long lastBackupTimestamp = dataManager.getLastBackupTimestamp();
        updateLastBackupText(lastBackupTimestamp);
    }

    private void updateLastBackupText(long timestamp) {
        if (timestamp > 0) {
            SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault());
            String lastBackupDate = sdf.format(new Date(timestamp));
            textLastBackupDate.setText(getString(R.string.last_backup_date, lastBackupDate));
        } else {
            textLastBackupDate.setText(R.string.no_backup_yet);
        }
    }

    private void setupListeners() {
        switchAutoBackup.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sessionManager.setDataPreference("auto_backup", isChecked);
            Toast.makeText(this, isChecked ?
                    R.string.auto_backup_enabled :
                    R.string.auto_backup_disabled, Toast.LENGTH_SHORT).show();
        });

        btnBackupNow.setOnClickListener(v -> performBackup());

        btnExportData.setOnClickListener(v -> checkStoragePermissionAndExport());

        btnClearData.setOnClickListener(v -> confirmClearData());

        btnDeleteAccount.setOnClickListener(v -> confirmDeleteAccount());
    }

    private void showLoading(boolean show) {
        progressIndicator.setVisibility(show ? android.view.View.VISIBLE : android.view.View.GONE);
        btnBackupNow.setEnabled(!show);
        btnExportData.setEnabled(!show);
        btnClearData.setEnabled(!show);
        btnDeleteAccount.setEnabled(!show);
    }

    private void performBackup() {
        showLoading(true);

        dataManager.backupData(new DataManager.OnBackupListener() {
            @Override
            public void onSuccess(long timestamp) {
                runOnUiThread(() -> {
                    updateLastBackupText(timestamp);
                    showLoading(false);
                    Toast.makeText(DataManagementActivity.this,
                            R.string.backup_complete, Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> {
                    showLoading(false);
                    Toast.makeText(DataManagementActivity.this,
                            getString(R.string.backup_failed, e.getMessage()),
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private boolean checkStoragePermission() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                PackageManager.PERMISSION_GRANTED) {

            // Permission not granted, request it
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                // Show an explanation
                showStoragePermissionRationale();
            } else {
                // No explanation needed, request the permission
                requestStoragePermissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
            return false;
        }
        return true;
    }

    private void showStoragePermissionRationale() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.storage_permission_title)
                .setMessage(R.string.storage_permission_message)
                .setPositiveButton(R.string.grant_permission, (dialog, which) -> {
                    requestStoragePermissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
                })
                .setNegativeButton(R.string.cancel, (dialog, which) -> {
                    dialog.dismiss();
                    showStoragePermissionDeniedMessage();
                })
                .show();
    }

    private void showStoragePermissionDeniedMessage() {
        Snackbar.make(findViewById(android.R.id.content),
                        R.string.storage_permission_denied, Snackbar.LENGTH_LONG)
                .setAction(R.string.settings, view -> {
                    // Open app settings
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    android.net.Uri uri = android.net.Uri.fromParts("package", getPackageName(), null);
                    intent.setData(uri);
                    startActivity(intent);
                })
                .show();
    }

    private void checkStoragePermissionAndExport() {
        if (checkStoragePermission()) {
            performDataExport();
        }
    }

    private void performDataExport() {
        showLoading(true);

        // Create exports directory if it doesn't exist
        File exportDir = new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "exports");
        if (!exportDir.exists()) {
            exportDir.mkdirs();
        }

        dataManager.exportUserData(new DataManager.OnExportListener() {
            @Override
            public void onSuccess(String filePath) {
                runOnUiThread(() -> {
                    showLoading(false);
                    Toast.makeText(DataManagementActivity.this,
                            getString(R.string.export_complete_path, filePath),
                            Toast.LENGTH_LONG).show();
                });
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> {
                    showLoading(false);
                    Toast.makeText(DataManagementActivity.this,
                            getString(R.string.export_failed, e.getMessage()),
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void confirmClearData() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.clear_data_title)
                .setMessage(R.string.clear_data_message)
                .setPositiveButton(R.string.clear, (dialog, which) -> {
                    clearLocalData();
                })
                .setNegativeButton(R.string.cancel, null)
                .setIcon(R.drawable.ic_emergency)
                .show();
    }

    private void clearLocalData() {
        showLoading(true);

        dataManager.clearLocalData(new OnCompleteListener() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    showLoading(false);
                    Toast.makeText(DataManagementActivity.this,
                            R.string.data_cleared, Toast.LENGTH_SHORT).show();

                    // Reload preferences after clearing
                    loadSavedPreferences();
                });
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> {
                    showLoading(false);
                    Toast.makeText(DataManagementActivity.this,
                            getString(R.string.clear_data_failed, e.getMessage()),
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void confirmDeleteAccount() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete_account_title)
                .setMessage(R.string.delete_account_message)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    // Add extra confirmation for deletion
                    confirmFinalAccountDeletion();
                })
                .setNegativeButton(R.string.cancel, null)
                .setIcon(R.drawable.ic_emergency)
                .show();
    }

    private void confirmFinalAccountDeletion() {
        // Second confirmation with password
        final View passwordView = getLayoutInflater().inflate(R.layout.dialog_confirm_deletion, null);
        final TextView appVersionText = passwordView.findViewById(R.id.text_app_version);

        // Show app version for verification
        appVersionText.setText(getString(R.string.app_version_confirmation, "1.0"));

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.final_confirmation)
                .setView(passwordView)
                .setPositiveButton(R.string.confirm_delete, (dialog, which) -> {
                    // Final confirmation text
                    TextView confirmText = passwordView.findViewById(R.id.edit_confirm_text);
                    if (confirmText.getText().toString().equals(getString(R.string.delete_confirmation_text))) {
                        deleteUserAccount();
                    } else {
                        Toast.makeText(this, R.string.confirmation_text_mismatch, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .setIcon(R.drawable.ic_emergency)
                .show();
    }

    private void deleteUserAccount() {
        showLoading(true);
        Toast.makeText(this, R.string.deleting_account, Toast.LENGTH_SHORT).show();

        dataManager.deleteUserAccount(new OnCompleteListener() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    showLoading(false);
                    Toast.makeText(DataManagementActivity.this,
                            R.string.account_deleted, Toast.LENGTH_SHORT).show();

                    // Navigate back to login screen
                    finishAffinity();

                    // Start login activity
                    Intent intent = new Intent(DataManagementActivity.this,
                            com.rescuereach.citizen.PhoneAuthActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                });
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> {
                    showLoading(false);
                    Toast.makeText(DataManagementActivity.this,
                            getString(R.string.delete_account_error, e.getMessage()),
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
}