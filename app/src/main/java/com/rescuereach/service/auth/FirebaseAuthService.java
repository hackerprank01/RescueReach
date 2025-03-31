package com.rescuereach.service.auth;

import android.app.Activity;

import com.google.firebase.FirebaseException;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;

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

    @Override
    public void signOut(AuthCallback callback) {
        firebaseAuth.signOut();
        callback.onSuccess();
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
                .setCallbacks(new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                    @Override
                    public void onCodeSent(String verificationId,
                                           PhoneAuthProvider.ForceResendingToken token) {
                        callback.onCodeSent(verificationId);
                    }

                    @Override
                    public void onVerificationCompleted(PhoneAuthCredential credential) {
                        callback.onVerificationCompleted(credential);
                        signInWithPhoneCredential(credential, new AuthCallback() {
                            @Override
                            public void onSuccess() {
                                // Authentication successful
                            }

                            @Override
                            public void onError(Exception e) {
                                // Handle error
                            }
                        });
                    }

                    @Override
                    public void onVerificationFailed(FirebaseException e) {
                        // Pass the specific exception type for better handling
                        if (e instanceof FirebaseTooManyRequestsException) {
                            callback.onVerificationFailed(e);
                        } else if (e instanceof FirebaseAuthInvalidCredentialsException) {
                            callback.onVerificationFailed(e);
                        } else {
                            callback.onVerificationFailed(e);
                        }
                    }
                })
                .build();

        PhoneAuthProvider.verifyPhoneNumber(options);
    }

    @Override
    public void verifyPhoneWithCode(String verificationId, String code, AuthCallback callback) {
        PhoneAuthCredential credential = PhoneAuthProvider.getCredential(verificationId, code);
        signInWithPhoneCredential(credential, callback);
    }

    private void signInWithPhoneCredential(PhoneAuthCredential credential, AuthCallback callback) {
        firebaseAuth.signInWithCredential(credential)
                .addOnSuccessListener(authResult -> callback.onSuccess())
                .addOnFailureListener(e -> {
                    // Preserve exception type for better error handling
                    callback.onError(e);
                });
    }

    @Override
    public void signInWithEmailAndPassword(String email, String password, AuthCallback callback) {
        firebaseAuth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError(e));
    }
}