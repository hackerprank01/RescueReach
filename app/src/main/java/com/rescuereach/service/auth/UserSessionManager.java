package com.rescuereach.service.auth;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;

import com.rescuereach.data.model.User;
import com.rescuereach.data.repository.OnCompleteListener;
import com.rescuereach.data.repository.RepositoryProvider;
import com.rescuereach.data.repository.StatusRepository;
import com.rescuereach.data.repository.UserRepository;

/**
 * Manages user session and provides common user operations.
 */
public class UserSessionManager {
    private static final String TAG = "UserSessionManager";
    private static final String PREF_NAME = "RescueReachUserSession";
    private static final String KEY_USER_PHONE = "user_phone";

    private static UserSessionManager instance;

    private final Context context;
    private final SharedPreferences sharedPreferences;
    private final AuthService authService;
    private final UserRepository userRepository;
    private final StatusRepository statusRepository;
    private final ConnectivityManager.NetworkCallback networkCallback;

    private User currentUser;
    private boolean isNetworkAvailable = false;

    private UserSessionManager(Context context) {
        this.context = context.getApplicationContext();
        this.sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.authService = AuthServiceProvider.getInstance().getAuthService();
        this.userRepository = RepositoryProvider.getInstance().getUserRepository();
        this.statusRepository = RepositoryProvider.getInstance().getStatusRepository();

        // Setup network callback
        this.networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                isNetworkAvailable = true;
                updateOnlineStatus();
            }

            @Override
            public void onLost(@NonNull Network network) {
                isNetworkAvailable = false;
            }
        };

        // Register network callback
        registerNetworkCallback();

        // Check initial network state
        checkNetworkState();
    }

    public static synchronized UserSessionManager getInstance(Context context) {
        if (instance == null) {
            instance = new UserSessionManager(context);
        }
        return instance;
    }

    private void registerNetworkCallback() {
        try {
            ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connectivityManager != null) {
                NetworkRequest.Builder builder = new NetworkRequest.Builder();
                connectivityManager.registerNetworkCallback(builder.build(), networkCallback);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error registering network callback", e);
        }
    }

    private void checkNetworkState() {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Network network = connectivityManager.getActiveNetwork();
                if (network != null) {
                    NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
                    isNetworkAvailable = capabilities != null &&
                            (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR));
                }
            } else {
                // For older devices
                isNetworkAvailable = connectivityManager.getActiveNetworkInfo() != null &&
                        connectivityManager.getActiveNetworkInfo().isConnected();
            }
        }
    }

    private void updateOnlineStatus() {
        if (authService.isLoggedIn() && isNetworkAvailable) {
            String userId = authService.getCurrentUserId();
            if (userId != null) {
                statusRepository.updateUserStatus(userId, "online", new OnCompleteListener() {
                    @Override
                    public void onSuccess() {
                        Log.d(TAG, "User online status updated");
                    }

                    @Override
                    public void onError(Exception e) {
                        Log.e(TAG, "Failed to update online status", e);
                    }
                });
            }
        }
    }

    public void saveUserPhoneNumber(String phoneNumber) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY_USER_PHONE, phoneNumber);
        editor.apply();
    }

    public String getSavedPhoneNumber() {
        return sharedPreferences.getString(KEY_USER_PHONE, null);
    }

    public void loadCurrentUser(final OnUserLoadedListener listener) {
        if (!authService.isLoggedIn()) {
            listener.onError(new Exception("No user logged in"));
            return;
        }

        final String userId = authService.getCurrentUserId();
        if (userId == null) {
            listener.onError(new Exception("User ID is null"));
            return;
        }

        userRepository.getUserById(userId, new UserRepository.OnUserFetchedListener() {
            @Override
            public void onSuccess(User user) {
                currentUser = user;

                // Update online status
                if (isNetworkAvailable) {
                    updateOnlineStatus();
                }

                listener.onUserLoaded(user);
            }

            @Override
            public void onError(Exception e) {
                // If there's an error getting the user, check if it's a permission issue
                if (e.getMessage().contains("PERMISSION_DENIED")) {
                    // Try to create the user if they don't exist
                    String phoneNumber = getSavedPhoneNumber();
                    if (phoneNumber != null) {
                        createNewUser(phoneNumber, new OnCompleteListener() {
                            @Override
                            public void onSuccess() {
                                // Try to load again
                                loadCurrentUser(listener);
                            }

                            @Override
                            public void onError(Exception e) {
                                listener.onError(e);
                            }
                        });
                    } else {
                        listener.onError(new Exception("User not found and no phone number available"));
                    }
                } else {
                    listener.onError(e);
                }
            }
        });
    }

    public void createNewUser(String phoneNumber, OnCompleteListener listener) {
        String userId = authService.getCurrentUserId();
        if (userId == null) {
            listener.onError(new Exception("User ID is null"));
            return;
        }

        User newUser = new User(userId, phoneNumber);
        userRepository.saveUser(newUser, new OnCompleteListener() {
            @Override
            public void onSuccess() {
                currentUser = newUser;
                saveUserPhoneNumber(phoneNumber);

                // Set initial online status
                if (isNetworkAvailable) {
                    updateOnlineStatus();
                }

                listener.onSuccess();
            }

            @Override
            public void onError(Exception e) {
                listener.onError(e);
            }
        });
    }

    public User getCurrentUser() {
        return currentUser;
    }

    public void logout(final OnCompleteListener listener) {
        final String userId = authService.getCurrentUserId();

        // Set offline status before logout
        if (userId != null && isNetworkAvailable) {
            statusRepository.updateUserStatus(userId, "offline", new OnCompleteListener() {
                @Override
                public void onSuccess() {
                    // Now proceed with logout
                    performLogout(listener);
                }

                @Override
                public void onError(Exception e) {
                    // Log error but still proceed with logout
                    Log.e(TAG, "Failed to update offline status", e);
                    performLogout(listener);
                }
            });
        } else {
            performLogout(listener);
        }
    }

    private void performLogout(OnCompleteListener listener) {
        authService.signOut(new AuthService.AuthCallback() {
            @Override
            public void onSuccess() {
                currentUser = null;
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.clear();
                editor.apply();
                listener.onSuccess();
            }

            @Override
            public void onError(Exception e) {
                listener.onError(e);
            }
        });
    }

    public void unregisterNetworkCallback() {
        try {
            ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connectivityManager != null) {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error unregistering network callback", e);
        }
    }

    public interface OnUserLoadedListener {
        void onUserLoaded(User user);
        void onError(Exception e);
    }
}