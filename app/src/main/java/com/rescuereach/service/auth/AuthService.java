package com.rescuereach.service.auth;

import android.app.Activity;

import com.google.firebase.auth.PhoneAuthCredential;

public interface AuthService {

    interface AuthStateListener {
        void onAuthStateChanged(boolean isLoggedIn);
    }

    interface AuthCallback {
        void onSuccess();
        void onError(Exception e);
    }

    interface PhoneVerificationCallback {
        void onCodeSent(String verificationId);
        void onVerificationCompleted(PhoneAuthCredential credential);
        void onVerificationFailed(Exception e);
    }

    // Common methods
    String getCurrentUserId();
    boolean isLoggedIn();
    void signOut(AuthCallback callback);
    void addAuthStateListener(AuthStateListener listener);
    void removeAuthStateListener(AuthStateListener listener);

    // Phone authentication (for citizen app)
    void startPhoneVerification(String phoneNumber, Activity activity, PhoneVerificationCallback callback);
    void verifyPhoneWithCode(String verificationId, String code, AuthCallback callback);

    // Email/password authentication (for responder app)
    void signInWithEmailAndPassword(String email, String password, AuthCallback callback);
}