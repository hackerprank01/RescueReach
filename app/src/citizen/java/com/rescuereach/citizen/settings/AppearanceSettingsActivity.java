package com.rescuereach.citizen.settings;

import android.app.UiModeManager;
import android.content.Context;
import android.os.Bundle;
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

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.rescuereach.R;
import com.rescuereach.service.auth.UserSessionManager;

public class AppearanceSettingsActivity extends AppCompatActivity {

    private UserSessionManager sessionManager;
    private RadioGroup themeGroup;
    private RadioButton radioLight, radioDark, radioSystem;
    private SeekBar fontSizeSeekbar;
    private TextView fontSizeValue;
    private SwitchMaterial switchHighContrast;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_appearance_settings);

        sessionManager = UserSessionManager.getInstance(this);

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

        // High contrast
        boolean highContrast = sessionManager.getAppearancePreference("high_contrast", false).equals("true");
        switchHighContrast.setChecked(highContrast);
    }

    private void setupListeners() {
        themeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            String newTheme;
            if (checkedId == R.id.radio_light) {
                newTheme = "light";
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            } else if (checkedId == R.id.radio_dark) {
                newTheme = "dark";
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            } else {
                newTheme = "system";
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
            }
            sessionManager.setAppearancePreference("theme", newTheme);
        });

        fontSizeSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateFontSizeText(progress);
                sessionManager.setIntPreference("font_size", progress);

                // In a real app, you would apply the font size change here
                // This would require a custom solution, possibly with a custom Configuration
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        switchHighContrast.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sessionManager.setAppearancePreference("high_contrast", isChecked ? "true" : "false");

            // In a real app, you would apply the high contrast mode here
            // This would potentially involve setting a different theme
        });
    }

    private void updateFontSizeText(int progress) {
        String[] sizes = {"Small", "Default", "Large", "X-Large", "XX-Large"};
        fontSizeValue.setText(sizes[progress]);
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