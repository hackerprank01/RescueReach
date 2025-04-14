package com.rescuereach.util;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

/**
 * Utility class for displaying Toast messages safely from any thread
 */
public class ToastUtil {
    private static final String TAG = "ToastUtil";

    /**
     * Show a toast message safely from any thread
     * @param context Application context
     * @param message Message to display
     * @param duration Toast duration (Toast.LENGTH_SHORT or Toast.LENGTH_LONG)
     */
    public static void show(final Context context, final String message, final int duration) {
        if (context == null) {
            Log.e(TAG, "Cannot show toast: Context is null");
            return;
        }

        if (message == null) {
            Log.e(TAG, "Cannot show toast: Message is null");
            return;
        }

        // Always use application context to prevent leaks
        final Context appContext = context.getApplicationContext();

        // If we're on the main thread, show directly
        if (Looper.myLooper() == Looper.getMainLooper()) {
            try {
                Toast.makeText(appContext, message, duration).show();
            } catch (Exception e) {
                Log.e(TAG, "Error showing toast on main thread: " + e.getMessage());
            }
        } else {
            // Otherwise post to main thread
            try {
                final Handler mainHandler = new Handler(Looper.getMainLooper());
                mainHandler.post(() -> {
                    try {
                        Toast.makeText(appContext, message, duration).show();
                    } catch (Exception e) {
                        Log.e(TAG, "Error showing toast on main thread (from background): " + e.getMessage());
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Error posting toast to main thread: " + e.getMessage());
            }
        }
    }

    /**
     * Show a short toast message
     * @param context Application context
     * @param message Message to display
     */
    public static void showShort(Context context, String message) {
        show(context, message, Toast.LENGTH_SHORT);
    }

    /**
     * Show a long toast message
     * @param context Application context
     * @param message Message to display
     */
    public static void showLong(Context context, String message) {
        show(context, message, Toast.LENGTH_LONG);
    }
}