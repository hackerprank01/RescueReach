package com.rescuereach.service.auth;

import static android.content.ContentValues.TAG;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.firebase.FirebaseException;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class FirebaseAuthService implements AuthService {

    private final FirebaseAuth firebaseAuth;
    private final List<AuthStateListener> authStateListeners;
    private final FirebaseAuth.AuthStateListener firebaseAuthStateListener;

    public FirebaseAuthService() {
        firebaseAuth = FirebaseAuth.getInstance();
        authStateListeners = new ArrayList<>();

        // Create a Firebase AuthStateListener
        firebaseAuthStateListener = firebaseAuth -> {
            boolean isLoggedIn = firebaseAuth.getCurrentUser() != null;
            notifyAuthStateListeners(isLoggedIn);
        };

        // Add the listener to Firebase Auth
        firebaseAuth.addAuthStateListener(firebaseAuthStateListener);
    }

    private void notifyAuthStateListeners(boolean isLoggedIn) {
        for (AuthStateListener listener : authStateListeners) {
            listener.onAuthStateChanged(isLoggedIn);
        }
    }

    @Override
    public String getCurrentUserId() {
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        return currentUser != null ? currentUser.getUid() : null;
    }

    @Override
    public boolean isLoggedIn() {
        return firebaseAuth.getCurrentUser() != null;
    }

// Replace the signOut method in FirebaseAuthService.java

    public void signOut(AuthCallback callback) {
        try {
            // *** IMPORTANT: DO NOT terminate Firestore - this is causing crashes ***
            // Instead, sign out more carefully, preserving Firestore integrity

            // Get the FirebaseAuth instance
            final FirebaseAuth auth = FirebaseAuth.getInstance();

            // Disable the auth state listener temporarily to prevent race conditions
            if (firebaseAuthStateListener != null) {
                try {
                    auth.removeAuthStateListener(firebaseAuthStateListener);
                } catch (Exception e) {
                    Log.e("FirebaseAuthService", "Error removing auth listener", e);
                }
            }

            // Use a background thread for cleanup operations
            new Thread(() -> {
                try {
                    // Clear cached data without terminating connections
                    try {
                        // For Firestore, just clear cache, don't terminate
                        FirebaseFirestore.getInstance().clearPersistence()
                                .addOnSuccessListener(aVoid -> Log.d("FirebaseAuthService", "Firestore persistence cleared"))
                                .addOnFailureListener(e -> Log.e("FirebaseAuthService", "Error clearing Firestore persistence", e));

                        // For Realtime Database, purge writes without closing
                        FirebaseDatabase.getInstance().purgeOutstandingWrites();
                    } catch (Exception e) {
                        Log.e("FirebaseAuthService", "Error clearing Firebase cache", e);
                    }

                    // Wait a moment for operations to complete
                    Thread.sleep(300);

                    // Finally sign out on the main thread to ensure proper UI updates
                    new Handler(Looper.getMainLooper()).post(() -> {
                        try {
                            // Actually sign out from Firebase Auth
                            auth.signOut();

                            // Restore the auth state listener
                            if (firebaseAuthStateListener != null) {
                                auth.addAuthStateListener(firebaseAuthStateListener);
                            }

                            // Notify successful signout
                            callback.onSuccess();
                        } catch (Exception e) {
                            Log.e("FirebaseAuthService", "Error in final signout step", e);
                            callback.onError(e);
                        }
                    });
                } catch (Exception e) {
                    Log.e("FirebaseAuthService", "Error during sign out cleanup", e);

                    // Ensure callback is always called, even on error
                    new Handler(Looper.getMainLooper()).post(() -> {
                        try {
                            callback.onError(e);
                        } catch (Exception ex) {
                            Log.e("FirebaseAuthService", "Error in error callback", ex);
                        }
                    });
                }
            }).start();
        } catch (Exception e) {
            Log.e("FirebaseAuthService", "Error initiating sign out", e);
            callback.onError(e);
        }
    }

    // Helper method to finish sign out process
    private void finishSignOut(AuthCallback callback) {
        try {
            // Give Firebase some time to complete operations
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try {
                    callback.onSuccess();
                } catch (Exception e) {
                    Log.e("FirebaseAuthService", "Error in sign out callback", e);
                }
            }, 300); // 300ms delay
        } catch (Exception e) {
            Log.e("FirebaseAuthService", "Error in delayed sign out", e);
            callback.onSuccess(); // Call callback immediately as fallback
        }
    }

    @Override
    public void addAuthStateListener(AuthStateListener listener) {
        authStateListeners.add(listener);
        // Call immediately with current state
        listener.onAuthStateChanged(isLoggedIn());
    }

    @Override
    public void removeAuthStateListener(AuthStateListener listener) {
        authStateListeners.remove(listener);
    }

    @Override
    public void startPhoneVerification(String phoneNumber, Activity activity, PhoneVerificationCallback callback) {
        PhoneAuthOptions options = PhoneAuthOptions.newBuilder(firebaseAuth)
                .setPhoneNumber(phoneNumber)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(activity)
                .setCallbacks(createCallbacks(callback))
                .build();

        PhoneAuthProvider.verifyPhoneNumber(options);
    }

    @Override
    public void verifyPhoneWithCode(String verificationId, String code, AuthCallback callback) {
        PhoneAuthCredential credential = PhoneAuthProvider.getCredential(verificationId, code);
        signInWithPhoneCredential(credential, callback);
    }

    @Override
    public void resendVerificationCode(String phoneNumber, PhoneAuthProvider.ForceResendingToken token, Activity activity, PhoneVerificationCallback callback) {
        PhoneAuthOptions options = PhoneAuthOptions.newBuilder(firebaseAuth)
                .setPhoneNumber(phoneNumber)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(activity)
                .setCallbacks(createCallbacks(callback))
                .setForceResendingToken(token) // Use the token for resending
                .build();

        PhoneAuthProvider.verifyPhoneNumber(options);
    }

    private PhoneAuthProvider.OnVerificationStateChangedCallbacks createCallbacks(PhoneVerificationCallback callback) {
        return new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            @Override
            public void onVerificationCompleted(PhoneAuthCredential credential) {
                callback.onVerificationCompleted(credential);
            }

            @Override
            public void onVerificationFailed(FirebaseException e) {
                callback.onVerificationFailed(e);
            }

            @Override
            public void onCodeSent(String verificationId, PhoneAuthProvider.ForceResendingToken token) {
                callback.onCodeSent(verificationId, token);
            }
        };
    }

    private void signInWithPhoneCredential(PhoneAuthCredential credential, AuthCallback callback) {
        firebaseAuth.signInWithCredential(credential)
                .addOnSuccessListener(authResult -> callback.onSuccess())
                .addOnFailureListener(e -> {
                    // Preserve exception type for better error handling
                    callback.onError(e);
                });
    }

    private void performSignOut() {

    }

    @Override
    public void signInWithEmailAndPassword(String email, String password, AuthCallback callback) {
        firebaseAuth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError(e));
    }
}