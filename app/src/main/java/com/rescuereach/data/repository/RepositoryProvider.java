package com.rescuereach.data.repository;

import com.rescuereach.data.repository.firebase.FirebaseIncidentRepository;
import com.rescuereach.data.repository.firebase.FirebaseResponderRepository;
import com.rescuereach.data.repository.firebase.FirebaseUserRepository;
import com.rescuereach.data.repository.firebase.FirebaseVolunteerRepository;

/**
 * Singleton provider class for repositories.
 * This allows easy access to repositories throughout the app.
 */
public class RepositoryProvider {
    private static RepositoryProvider instance;

    private final UserRepository userRepository;
    private final IncidentRepository incidentRepository;
    private final ResponderRepository responderRepository;
    private final VolunteerRepository volunteerRepository;

    private RepositoryProvider() {
        // Initialize Firebase repositories
        userRepository = new FirebaseUserRepository();
        incidentRepository = new FirebaseIncidentRepository();
        responderRepository = new FirebaseResponderRepository();
        volunteerRepository = new FirebaseVolunteerRepository();
    }

    public static synchronized RepositoryProvider getInstance() {
        if (instance == null) {
            instance = new RepositoryProvider();
        }
        return instance;
    }

    public UserRepository getUserRepository() {
        return userRepository;
    }

    public IncidentRepository getIncidentRepository() {
        return incidentRepository;
    }

    public ResponderRepository getResponderRepository() {
        return responderRepository;
    }

    public VolunteerRepository getVolunteerRepository() {
        return volunteerRepository;
    }
}