package com.rescuereach.util;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;

import com.rescuereach.service.auth.UserSessionManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Manages media operations (image/video sharing, capturing, etc.) with privacy controls
 */
public class MediaManager {
    private static final String TAG = "MediaManager";

    private final Context context;
    private final UserSessionManager sessionManager;

    public interface MediaShareListener {
        void onMediaShared(Uri mediaUri);
        void onError(String message);
    }

    public MediaManager(Context context) {
        this.context = context.getApplicationContext();
        this.sessionManager = UserSessionManager.getInstance(context);
    }

    /**
     * Check if media sharing is enabled in privacy settings
     */
    public boolean isMediaSharingEnabled() {
        return sessionManager.getPrivacyPreference("media_sharing", true);
    }

    /**
     * Check if app has camera permissions
     */
    public boolean hasCameraPermission() {
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Check if app has storage permissions
     */
    public boolean hasStoragePermission() {
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Create a temporary file for storing captured media
     */
    public File createTempMediaFile(boolean isVideo) throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String prefix = isVideo ? "VID_" : "IMG_";
        String suffix = isVideo ? ".mp4" : ".jpg";

        File storageDir = context.getExternalFilesDir(isVideo ? "video" : "images");
        return File.createTempFile(prefix + timeStamp, suffix, storageDir);
    }

    /**
     * Share media with emergency services (respects privacy settings)
     */
    public void shareMediaWithEmergencyServices(Uri mediaUri, MediaShareListener listener) {
        // Check if media sharing is enabled in privacy settings
        if (!isMediaSharingEnabled()) {
            if (listener != null) {
                listener.onError("Media sharing is disabled in privacy settings");
            }
            return;
        }

        // In a real app, this would upload the media to your backend
        // For this example, we just simulate successful sharing
        Log.d(TAG, "Media shared with emergency services: " + mediaUri);

        if (listener != null) {
            listener.onMediaShared(mediaUri);
        }
    }

    /**
     * Save bitmap to a file and get its URI (useful for captured photos)
     */
    public Uri saveBitmapToFile(Bitmap bitmap) throws IOException {
        if (!isMediaSharingEnabled()) {
            throw new IOException("Media sharing is disabled in privacy settings");
        }

        File imageFile = createTempMediaFile(false);

        try (FileOutputStream out = new FileOutputStream(imageFile)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
        }

        // Get content URI using FileProvider
        return FileProvider.getUriForFile(context,
                context.getPackageName() + ".fileprovider",
                imageFile);
    }

    /**
     * Delete media file if it exists
     */
    public boolean deleteMediaFile(Uri mediaUri) {
        try {
            File file = new File(mediaUri.getPath());
            return file.exists() && file.delete();
        } catch (Exception e) {
            Log.e(TAG, "Error deleting media file", e);
            return false;
        }
    }
}