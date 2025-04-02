package com.rescuereach.service.auth;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.util.Base64;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * This is a helper class to generate your app's hash string for SMS Retriever API.
 * You can use this hash string to format the SMS message correctly.
 */
public class AppSignatureHelper extends ContextWrapper {
    private static final String TAG = "AppSignatureHelper";
    private static final String HASH_TYPE = "SHA-256";
    public static final int NUM_HASHED_BYTES = 9;
    public static final int NUM_BASE64_CHAR = 11;

    public AppSignatureHelper(Context context) {
        super(context);
    }

    /**
     * Get the app's hash string that will be included in SMS messages for verification
     */
    public ArrayList<String> getAppSignatures() {
        ArrayList<String> appSignatures = new ArrayList<>();

        try {
            // Get package info
            String packageName = getPackageName();
            PackageManager packageManager = getPackageManager();
            PackageInfo packageInfo = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES);

            // For each signature, get the hash
            for (Signature signature : packageInfo.signatures) {
                String hash = hash(packageName, signature.toCharsString());
                if (hash != null) {
                    appSignatures.add(String.format("%s", hash));
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Unable to find package to obtain signature", e);
        }

        return appSignatures;
    }

    /**
     * Get a single app signature hash - usually you only have one signature
     */
    public String getAppSignature() {
        ArrayList<String> signatures = getAppSignatures();
        if (!signatures.isEmpty()) {
            return signatures.get(0);
        }
        return null;
    }

    private String hash(String packageName, String signature) {
        String appInfo = packageName + " " + signature;
        try {
            MessageDigest messageDigest = MessageDigest.getInstance(HASH_TYPE);
            messageDigest.update(appInfo.getBytes(StandardCharsets.UTF_8));

            byte[] hashBytes = messageDigest.digest();

            // Convert to Base64 and truncate
            byte[] truncatedHash = Arrays.copyOfRange(hashBytes, 0, NUM_HASHED_BYTES);
            String base64Hash = Base64.encodeToString(truncatedHash, Base64.NO_PADDING | Base64.NO_WRAP);

            // Ensure the hash is the correct length
            if (base64Hash.length() > NUM_BASE64_CHAR) {
                base64Hash = base64Hash.substring(0, NUM_BASE64_CHAR);
            }

            return base64Hash;
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "Unable to get MessageDigest", e);
        }

        return null;
    }
}