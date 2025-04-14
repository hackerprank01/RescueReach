package com.rescuereach.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Set;

/**
 * Utility class to manage SharedPreferences operations
 */
public class SharedPreferencesManager {

    private static final String PREFS_NAME = "RescueReachPrefs";
    private final SharedPreferences sharedPreferences;
    private final SharedPreferences.Editor editor;

    /**
     * Create a new SharedPreferencesManager
     * @param context Application context
     */
    public SharedPreferencesManager(Context context) {
        sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        editor = sharedPreferences.edit();
    }

    /**
     * Store a string value
     * @param key The key
     * @param value The value
     */
    public void putString(String key, String value) {
        editor.putString(key, value);
        editor.apply();
    }

    /**
     * Retrieve a string value
     * @param key The key
     * @param defaultValue The default value if key doesn't exist
     * @return The stored string or defaultValue if not found
     */
    public String getString(String key, String defaultValue) {
        return sharedPreferences.getString(key, defaultValue);
    }

    /**
     * Store an integer value
     * @param key The key
     * @param value The value
     */
    public void putInt(String key, int value) {
        editor.putInt(key, value);
        editor.apply();
    }

    /**
     * Retrieve an integer value
     * @param key The key
     * @param defaultValue The default value if key doesn't exist
     * @return The stored integer or defaultValue if not found
     */
    public int getInt(String key, int defaultValue) {
        return sharedPreferences.getInt(key, defaultValue);
    }

    /**
     * Store a boolean value
     * @param key The key
     * @param value The value
     */
    public void putBoolean(String key, boolean value) {
        editor.putBoolean(key, value);
        editor.apply();
    }

    /**
     * Retrieve a boolean value
     * @param key The key
     * @param defaultValue The default value if key doesn't exist
     * @return The stored boolean or defaultValue if not found
     */
    public boolean getBoolean(String key, boolean defaultValue) {
        return sharedPreferences.getBoolean(key, defaultValue);
    }

    /**
     * Store a float value
     * @param key The key
     * @param value The value
     */
    public void putFloat(String key, float value) {
        editor.putFloat(key, value);
        editor.apply();
    }

    /**
     * Retrieve a float value
     * @param key The key
     * @param defaultValue The default value if key doesn't exist
     * @return The stored float or defaultValue if not found
     */
    public float getFloat(String key, float defaultValue) {
        return sharedPreferences.getFloat(key, defaultValue);
    }

    /**
     * Store a long value
     * @param key The key
     * @param value The value
     */
    public void putLong(String key, long value) {
        editor.putLong(key, value);
        editor.apply();
    }

    /**
     * Retrieve a long value
     * @param key The key
     * @param defaultValue The default value if key doesn't exist
     * @return The stored long or defaultValue if not found
     */
    public long getLong(String key, long defaultValue) {
        return sharedPreferences.getLong(key, defaultValue);
    }

    /**
     * Store a set of strings
     * @param key The key
     * @param values The set of strings
     */
    public void putStringSet(String key, Set<String> values) {
        editor.putStringSet(key, values);
        editor.apply();
    }

    /**
     * Retrieve a set of strings
     * @param key The key
     * @param defaultValues The default set if key doesn't exist
     * @return The stored string set or defaultValues if not found
     */
    public Set<String> getStringSet(String key, Set<String> defaultValues) {
        return sharedPreferences.getStringSet(key, defaultValues);
    }

    /**
     * Check if the SharedPreferences contains the specified key
     * @param key The key to check
     * @return True if the key exists, false otherwise
     */
    public boolean contains(String key) {
        return sharedPreferences.contains(key);
    }

    /**
     * Remove a value from SharedPreferences
     * @param key The key to remove
     */
    public void remove(String key) {
        editor.remove(key);
        editor.apply();
    }

    /**
     * Clear all SharedPreferences values
     */
    public void clear() {
        editor.clear();
        editor.apply();
    }
}