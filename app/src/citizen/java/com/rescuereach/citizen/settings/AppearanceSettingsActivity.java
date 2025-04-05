package com.rescuereach.citizen.settings;

import android.content.Intent;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.MenuItem;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RadioGroup.OnCheckedChangeListener;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.snackbar.Snackbar;
import com.rescuereach.R;
import com.rescuereach.service.appearance.AppearanceManager;
import com.rescuereach.service.auth.UserSessionManager;

public class AppearanceSettingsActivity extends AppCompatActivity {

    private UserSessionManager sessionManager;
    private AppearanceManager appearanceManager;
    private RadioGroup themeGroup;
    private RadioButton radioLight, radioDark, radioSystem;
    private RadioGroup fontSizeGroup;
    private RadioButton radioFontDefault, radioFontMedium, radioFontLarge;
    private TextView sampleText;

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
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.appearance);
        }

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

        fontSizeGroup = findViewById(R.id.radio_group_font_size);
        radioFontDefault = findViewById(R.id.radio_font_default);
        radioFontMedium = findViewById(R.id.radio_font_medium);
        radioFontLarge = findViewById(R.id.radio_font_large);

        sampleText = findViewById(R.id.sample_text);
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

        // Font size (0=default, 1=medium, 2=large)
        int fontSizeIndex = sessionManager.getIntPreference("font_size", 0);
        switch (fontSizeIndex) {
            case 1:
                radioFontMedium.setChecked(true);
                break;
            case 2:
                radioFontLarge.setChecked(true);
                break;
            default:
                radioFontDefault.setChecked(true);
                break;
        }

        // Update sample text to show the current font size
        updateSampleTextSize(fontSizeIndex);
    }

    private void setupListeners() {
        themeGroup.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
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
            }
        });

        fontSizeGroup.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                int fontSizeIndex;
                if (checkedId == R.id.radio_font_medium) {
                    fontSizeIndex = 1;
                } else if (checkedId == R.id.radio_font_large) {
                    fontSizeIndex = 2;
                } else {
                    fontSizeIndex = 0;
                }

                sessionManager.setIntPreference("font_size", fontSizeIndex);
                appearanceManager.setFontScale(fontSizeIndex);
                updateSampleTextSize(fontSizeIndex);

                // Show notification
                Snackbar.make(findViewById(android.R.id.content),
                                R.string.font_size_changed, Snackbar.LENGTH_LONG)
                        .setAction(R.string.restart_now, v -> recreateApp())
                        .show();
            }
        });
    }

    private void updateSampleTextSize(int fontSizeIndex) {
        // Map fontSizeIndex to scale factors: 0=1.0, 1=1.2, 2=1.4
        float[] scaleFactor = {1.0f, 1.2f, 1.4f};

        // Set the text size based on the selected scale factor
        // This only affects this sample text, not the entire app
        float defaultSize = getResources().getDimension(R.dimen.text_size_normal) / getResources().getDisplayMetrics().density;
        sampleText.setTextSize(TypedValue.COMPLEX_UNIT_SP, defaultSize * scaleFactor[fontSizeIndex]);
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