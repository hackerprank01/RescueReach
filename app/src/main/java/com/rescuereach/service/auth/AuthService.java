package com.rescuereach.service.auth;

import android.app.Activity;

import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthProvider;

/**
 * Interface for authentication services
 * Defines methods for phone authentication and user management
 */
public interface AuthService {

    /**
     * Interface for authentication callbacks
     */
    interface AuthCallback {
        void onSuccess();
        void onError(Exception e);
    }

    /**
     * Interface for phone verification callbacks
     */
    interface PhoneVerificationCallback {
        void onVerificationCompleted(PhoneAuthCredential credential);
        void onVerificationFailed(Exception e);
        void onCodeSent(String verificationId, PhoneAuthProvider.ForceResendingToken token);
    }

    /**
     * Interface for auth state changes
     */
    interface AuthStateListener {
        void onAuthStateChanged(boolean isLoggedIn);
    }

    /**
     * Get the current user ID
     * @return User ID or null if not authenticated
     */
    String getCurrentUserId();

    /**
     * Check if a user is currently logged in
     * @return true if logged in, false otherwise
     */
    boolean isLoggedIn();

    /**
     * Sign out the current user
     * @param callback Callback for operation result
     */
    void signOut(AuthCallback callback);

    /**
     * Add a listener for auth state changes
     * @param listener The listener to add
     */
    void addAuthStateListener(AuthStateListener listener);

    /**
     * Remove a listener for auth state changes
     * @param listener The listener to remove
     */
    void removeAuthStateListener(AuthStateListener listener);

    /**
     * Start phone verification process
     * @param phoneNumber The phone number to verify
     * @param activity The activity for binding
     * @param callback Callback for verification process
     */
    void startPhoneVerification(String phoneNumber, Activity activity, PhoneVerificationCallback callback);

    /**
     * Verify phone with the received code
     * @param verificationId The verification ID received
     * @param code The verification code entered by user
     * @param callback Callback for operation result
     */
    void verifyPhoneWithCode(String verificationId, String code, AuthCallback callback);

    /**
     * Resend verification code
     * @param phoneNumber The phone number to verify
     * @param token The token for resending
     * @param activity The activity for binding
     * @param callback Callback for verification process
     */
    void resendVerificationCode(String phoneNumber, PhoneAuthProvider.ForceResendingToken token,
                                Activity activity, PhoneVerificationCallback callback);

    /**
     * Sign in with email and password
     * @param email User's email
     * @param password User's password
     * @param callback Callback for operation result
     */
    void signInWithEmailAndPassword(String email, String password, AuthCallback callback);
}