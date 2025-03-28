package com.rescuereach.service.auth;

/**
 * Singleton provider for AuthService.
 */
public class AuthServiceProvider {
    private static AuthServiceProvider instance;
    private final AuthService authService;

    private AuthServiceProvider() {
        authService = new FirebaseAuthService();
    }

    public static synchronized AuthServiceProvider getInstance() {
        if (instance == null) {
            instance = new AuthServiceProvider();
        }
        return instance;
    }

    public AuthService getAuthService() {
        return authService;
    }
}