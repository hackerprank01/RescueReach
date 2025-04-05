package com.rescuereach.service.appearance;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;

import androidx.appcompat.app.AppCompatDelegate;

import com.rescuereach.service.auth.UserSessionManager;

/**
 * Manages app appearance settings including theme and font size
 */
public class AppearanceManager {

    // Theme constants
    public static final String THEME_LIGHT = "light";
    public static final String THEME_DARK = "dark";
    public static final String THEME_SYSTEM = "system";

    // Font scale factors (simplified to 3 options)
    private static final float[] FONT_SCALE_FACTORS = {1.0f, 1.2f, 1.4f};

    private static AppearanceManager instance;
    private final Context context;
    private final UserSessionManager sessionManager;

    private AppearanceManager(Context context) {
        this.context = context.getApplicationContext();
        this.sessionManager = UserSessionManager.getInstance(context);
    }

    public static synchronized AppearanceManager getInstance(Context context) {
        if (instance == null) {
            instance = new AppearanceManager(context);
        }
        return instance;
    }

    /**
     * Apply current theme based on saved preferences
     */
    public void applyCurrentTheme() {
        String themeSetting = sessionManager.getAppearancePreference("theme", THEME_SYSTEM);

        switch (themeSetting) {
            case THEME_LIGHT:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case THEME_DARK:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
    }

    /**
     * Set theme mode
     */
    public void setTheme(String theme) {
        switch (theme) {
            case THEME_LIGHT:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case THEME_DARK:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }

        sessionManager.setAppearancePreference("theme", theme);
    }

    /**
     * Set font scale
     * @param sizeIndex 0=default, 1=medium, 2=large
     */
    public void setFontScale(int sizeIndex) {
        if (sizeIndex >= 0 && sizeIndex < FONT_SCALE_FACTORS.length) {
            float scaleFactor = FONT_SCALE_FACTORS[sizeIndex];

            // Store the font scale factor
            sessionManager.setFloatPreference("font_scale_factor", scaleFactor);
            sessionManager.setIntPreference("font_size", sizeIndex);
        }
    }

    /**
     * Get current font scale factor
     */
    public float getFontScaleFactor() {
        int fontSizeIndex = sessionManager.getIntPreference("font_size", 0);
        if (fontSizeIndex >= 0 && fontSizeIndex < FONT_SCALE_FACTORS.length) {
            return FONT_SCALE_FACTORS[fontSizeIndex];
        }
        return 1.0f; // Default scale
    }

    /**
     * Check if dark mode is currently active
     */
    public boolean isDarkModeActive() {
        int currentNightMode = context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return currentNightMode == Configuration.UI_MODE_NIGHT_YES;
    }

    /**
     * Apply font scaling globally by restarting all activities
     */
    public void applyFontScalingGlobally(Activity currentActivity) {
        // Create a new intent for the main activity
        Intent intent = new Intent(currentActivity, currentActivity.getClass());
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        // Start with fade animation
        currentActivity.startActivity(intent);
        currentActivity.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }
}