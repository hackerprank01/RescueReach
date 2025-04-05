package com.rescuereach.service.appearance;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;

import androidx.appcompat.app.AppCompatDelegate;

import com.rescuereach.R;
import com.rescuereach.service.auth.UserSessionManager;

/**
 * Manages app appearance settings including theme, font size, and high contrast mode
 */
public class AppearanceManager {

    // Theme constants
    public static final String THEME_LIGHT = "light";
    public static final String THEME_DARK = "dark";
    public static final String THEME_SYSTEM = "system";

    // Font scale factors
    private static final float[] FONT_SCALE_FACTORS = {0.8f, 1.0f, 1.3f, 1.6f, 2.0f};

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

        // Apply high contrast if enabled
        boolean highContrast = sessionManager.getAppearancePreference("high_contrast", "false").equals("true");
        if (highContrast) {
            // In a real app, this would apply a high contrast theme
            // For now, we'll just log it
            android.util.Log.d("AppearanceManager", "High contrast mode is enabled");
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
     * @param sizeIndex 0=small, 1=default, 2=large, 3=x-large, 4=xx-large
     */
    public void setFontScale(int sizeIndex) {
        if (sizeIndex >= 0 && sizeIndex < FONT_SCALE_FACTORS.length) {
            float scaleFactor = FONT_SCALE_FACTORS[sizeIndex];

            // Store the font scale factor
            sessionManager.setFloatPreference("font_scale_factor", scaleFactor);

            // In a real app, you would apply this to the Configuration
            // For demonstration, we just log it
            android.util.Log.d("AppearanceManager", "Font scale set to: " + scaleFactor);

            // In a complete implementation, you would apply this to all activities
            // by creating a custom Application class that overrides attachBaseContext()
        }
    }

    /**
     * Get current font scale factor
     */
    public float getFontScaleFactor() {
        int fontSizeIndex = sessionManager.getIntPreference("font_size", 1);
        if (fontSizeIndex >= 0 && fontSizeIndex < FONT_SCALE_FACTORS.length) {
            return FONT_SCALE_FACTORS[fontSizeIndex];
        }
        return 1.0f; // Default scale
    }

    /**
     * Set high contrast mode
     */
    public void setHighContrast(boolean enabled) {
        sessionManager.setAppearancePreference("high_contrast", enabled ? "true" : "false");

        // In a real app, this would apply a high contrast theme
        // For now, we'll just log it
        android.util.Log.d("AppearanceManager", "High contrast mode set to: " + enabled);
    }

    /**
     * Check if high contrast mode is enabled
     */
    public boolean isHighContrastEnabled() {
        return sessionManager.getAppearancePreference("high_contrast", "false").equals("true");
    }

    /**
     * Check if dark mode is currently active
     */
    public boolean isDarkModeActive() {
        int currentNightMode = context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return currentNightMode == Configuration.UI_MODE_NIGHT_YES;
    }
}