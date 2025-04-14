package com.rescuereach.data.repository;

import android.content.Context;
import com.rescuereach.data.repository.firebase.FirebaseSOSRepository;
import com.rescuereach.data.repository.firebase.FirebaseUserRepository;

/**
 * Singleton provider for repositories
 * Ensures single instances of repositories are used throughout the app
 */
public class RepositoryProvider {
    private static RepositoryProvider instance;
    private final UserRepository userRepository;
    private SOSRepository sosRepository;

    /**
     * Private constructor to prevent direct instantiation
     * Initializes all repository implementations
     */
    private RepositoryProvider() {
        // Initialize repository implementations
        userRepository = new FirebaseUserRepository();
        sosRepository = new FirebaseSOSRepository();
    }

    /**
     * Get the singleton instance of RepositoryProvider
     * @return RepositoryProvider instance
     */
    public static synchronized RepositoryProvider getInstance() {
        if (instance == null) {
            instance = new RepositoryProvider();
        }
        return instance;
    }

    /**
     * Get the UserRepository instance
     * @return UserRepository instance
     */
    public UserRepository getUserRepository() {
        return userRepository;
    }

    /**
     * Get the SOSRepository instance
     * @return SOSRepository instance
     */
    SOSRepository getSosRepositoryInternal() {
        return sosRepository;
    }

    /**
     * Static convenience method to get UserRepository directly
     * @param context Application context
     * @return UserRepository instance
     */
    public static UserRepository getUserRepository(Context context) {
        return getInstance().getUserRepository();
    }

    /**
     * Static convenience method to get SOSRepository directly
     * @return SOSRepository instance
     */
    public static SOSRepository getSOSRepository() {
        return getInstance().getSosRepositoryInternal();
    }

    /**
     * Reset all repositories (for testing or reset purposes)
     * Creates a new instance of RepositoryProvider
     */
    public static void reset() {
        instance = null;
    }

    /**
     * Set a custom SOSRepository implementation (primarily for testing)
     * @param repository The SOSRepository implementation to use
     */
    public static void setSOSRepository(SOSRepository repository) {
        if (repository != null) {
            getInstance().sosRepository = repository;
        }
    }
}