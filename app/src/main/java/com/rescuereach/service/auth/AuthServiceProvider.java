package com.rescuereach.service.auth;

/**
 * Singleton provider for AuthService
 * Ensures a single instance of AuthService is used throughout the app
 */
public class AuthServiceProvider {
    private static AuthServiceProvider instance;
    private final AuthService authService;

    /**
     * Private constructor to prevent direct instantiation
     */
    private AuthServiceProvider() {
        authService = new FirebaseAuthService();
    }

    /**
     * Get the singleton instance of AuthServiceProvider
     * @return AuthServiceProvider instance
     */
    public static synchronized AuthServiceProvider getInstance() {
        if (instance == null) {
            instance = new AuthServiceProvider();
        }
        return instance;
    }

    /**
     * Get the AuthService instance
     * @return AuthService instance
     */
    public AuthService getAuthService() {
        return authService;
    }
}