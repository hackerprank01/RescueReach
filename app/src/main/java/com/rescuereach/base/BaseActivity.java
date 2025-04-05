package com.rescuereach.base;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.rescuereach.service.appearance.AppearanceManager;

/**
 * Base activity that all activities should extend to ensure
 * consistent appearance settings across the app
 */
public class BaseActivity extends AppCompatActivity {

    private static final String TAG = "BaseActivity";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // Apply current theme before calling super.onCreate
        AppearanceManager.getInstance(this).applyCurrentTheme();

        super.onCreate(savedInstanceState);
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        // Get the current font scale factor from appearance manager
        AppearanceManager appearanceManager = AppearanceManager.getInstance(newBase);
        float fontScale = appearanceManager.getFontScaleFactor();

        // Log the font scale being applied
        Log.d(TAG, "Applying font scale: " + fontScale);

        // Create a new configuration with the adjusted font scale
        Configuration configuration = new Configuration(newBase.getResources().getConfiguration());
        configuration.fontScale = fontScale;

        // Create a new context with the adjusted configuration
        Context context = newBase.createConfigurationContext(configuration);

        // Pass the modified context to the parent
        super.attachBaseContext(context);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        // Handle configuration changes if needed
        // This is called when system font size or other configuration changes
    }
}