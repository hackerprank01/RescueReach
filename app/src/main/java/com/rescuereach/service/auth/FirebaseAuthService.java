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

    @Override
    public void signOut(AuthCallback callback) {
        try {
            // First sign out from Firebase Auth
            firebaseAuth.signOut();

            // Use a background thread to avoid blocking the UI
            new Thread(() -> {
                try {
                    // Clear any locally cached Firestore data
                    try {
                        FirebaseFirestore.getInstance().terminate();
                    } catch (Exception e) {
                        Log.e("FirebaseAuthService", "Error terminating Firestore", e);
                    }

                    // Clear Firebase Database cache
                    try {
                        FirebaseDatabase.getInstance().purgeOutstandingWrites();
                    } catch (Exception e) {
                        Log.e("FirebaseAuthService", "Error purging database writes", e);
                    }

                    // Small delay to ensure operations complete
                    Thread.sleep(500);

                    // Notify on main thread
                    new Handler(Looper.getMainLooper()).post(() -> {
                        try {
                            callback.onSuccess();
                        } catch (Exception e) {
                            Log.e("FirebaseAuthService", "Error in callback", e);
                        }
                    });
                } catch (Exception e) {
                    Log.e("FirebaseAuthService", "Error during sign out cleanup", e);

                    // Notify on main thread even if there was an error
                    new Handler(Looper.getMainLooper()).post(() -> {
                        try {
                            callback.onSuccess();
                        } catch (Exception ex) {
                            Log.e("FirebaseAuthService", "Error in callback", ex);
                        }
                    });
                }
            }).start();
        } catch (Exception e) {
            Log.e("FirebaseAuthService", "Error during sign out", e);

            // Always call callback
            try {
                callback.onSuccess();
            } catch (Exception ex) {
                Log.e("FirebaseAuthService", "Error in callback", ex);
            }
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