package com.rescuereach.citizen.settings;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.MenuItem;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.rescuereach.R;
import com.rescuereach.service.appearance.AppearanceManager;
import com.rescuereach.service.auth.UserSessionManager;

public class AppearanceSettingsActivity extends AppCompatActivity {

    private UserSessionManager sessionManager;
    private AppearanceManager appearanceManager;
    private RadioGroup themeGroup;
    private RadioButton radioLight, radioDark, radioSystem;
    private SeekBar fontSizeSeekbar;
    private TextView fontSizeValue;
    private TextView sampleText;
    private SwitchMaterial switchHighContrast;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize managers first since they may affect theme
        sessionManager = UserSessionManager.getInstance(this);
        appearanceManager = AppearanceManager.getInstance(this);

        // Apply theme before setting content view
        appearanceManager.applyCurrentTheme();

        setContentView(R.layout.activity_appearance_settings);

        // Set up toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle(R.string.appearance);

        // Initialize UI elements
        initUI();

        // Load saved preferences
        loadSavedPreferences();

        // Set up listeners
        setupListeners();
    }

    private void initUI() {
        themeGroup = findViewById(R.id.radio_group_theme);
        radioLight = findViewById(R.id.radio_light);
        radioDark = findViewById(R.id.radio_dark);
        radioSystem = findViewById(R.id.radio_system);

        fontSizeSeekbar = findViewById(R.id.seekbar_font_size);
        fontSizeValue = findViewById(R.id.text_font_size_value);
        sampleText = findViewById(R.id.sample_text);

        switchHighContrast = findViewById(R.id.switch_high_contrast);

        // Configure font size seekbar
        fontSizeSeekbar.setMax(4); // 0 = Small, 1 = Default, 2 = Large, 3 = X-Large, 4 = XX-Large
    }

    private void loadSavedPreferences() {
        // Theme setting
        String themeSetting = sessionManager.getAppearancePreference("theme", "system");
        switch (themeSetting) {
            case "light":
                radioLight.setChecked(true);
                break;
            case "dark":
                radioDark.setChecked(true);
                break;
            default:
                radioSystem.setChecked(true);
                break;
        }

        // Font size
        int fontSizeProgress = sessionManager.getIntPreference("font_size", 1);
        fontSizeSeekbar.setProgress(fontSizeProgress);
        updateFontSizeText(fontSizeProgress);

        // Update sample text to show the current font size
        updateSampleTextSize(fontSizeProgress);

        // High contrast
        boolean highContrast = sessionManager.getAppearancePreference("high_contrast", "false").equals("true");
        switchHighContrast.setChecked(highContrast);

        // If high contrast is enabled, apply it to the sample text
        if (highContrast) {
            applyHighContrast(true);
        }
    }

    private void setupListeners() {
        themeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            String newTheme;
            if (checkedId == R.id.radio_light) {
                newTheme = "light";
                appearanceManager.setTheme(AppearanceManager.THEME_LIGHT);
            } else if (checkedId == R.id.radio_dark) {
                newTheme = "dark";
                appearanceManager.setTheme(AppearanceManager.THEME_DARK);
            } else {
                newTheme = "system";
                appearanceManager.setTheme(AppearanceManager.THEME_SYSTEM);
            }
            sessionManager.setAppearancePreference("theme", newTheme);

            // Show confirmation and inform about restart
            Snackbar.make(findViewById(android.R.id.content),
                            R.string.theme_changed, Snackbar.LENGTH_LONG)
                    .setAction(R.string.restart_now, v -> recreateApp())
                    .show();
        });

        fontSizeSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateFontSizeText(progress);
                updateSampleTextSize(progress);
                sessionManager.setIntPreference("font_size", progress);
                appearanceManager.setFontScale(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                Snackbar.make(findViewById(android.R.id.content),
                                R.string.font_size_changed, Snackbar.LENGTH_LONG)
                        .setAction(R.string.restart_now, v -> recreateApp())
                        .show();
            }
        });

        switchHighContrast.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sessionManager.setAppearancePreference("high_contrast", isChecked ? "true" : "false");
            appearanceManager.setHighContrast(isChecked);

            // Apply high contrast to sample text
            applyHighContrast(isChecked);

            // Show confirmation and inform about restart
            Snackbar.make(findViewById(android.R.id.content),
                            isChecked ? R.string.high_contrast_enabled : R.string.high_contrast_disabled,
                            Snackbar.LENGTH_LONG)
                    .setAction(R.string.restart_now, v -> recreateApp())
                    .show();
        });
    }

    private void updateFontSizeText(int progress) {
        String[] sizes = {"Small", "Default", "Large", "X-Large", "XX-Large"};
        fontSizeValue.setText(sizes[progress]);
    }

    private void updateSampleTextSize(int progress) {
        float[] scaleFactor = {0.8f, 1.0f, 1.3f, 1.6f, 2.0f};

        // Set the text size based on the selected scale factor
        // This only affects this sample text, not the entire app
        sampleText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16 * scaleFactor[progress]);
    }

    private void applyHighContrast(boolean enabled) {
        if (enabled) {
            // Apply high contrast to sample text
            sampleText.setTextColor(ContextCompat.getColor(this, R.color.high_contrast_text));
            sampleText.setBackgroundColor(ContextCompat.getColor(this, R.color.high_contrast_background));
        } else {
            // Reset to default
            sampleText.setTextColor(ContextCompat.getColor(this, android.R.color.primary_text_light));
            sampleText.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent));
        }
    }

    private void recreateApp() {
        // This will restart the activity with the new theme/font size
        Intent intent = new Intent(this, AppearanceSettingsActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        finish();
        startActivity(intent);

        // Override transition animations
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
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
        Toast.makeText(this, R.string.appearance_settings_saved, Toast.LENGTH_SHORT).show();
        super.onBackPressed();
    }
}