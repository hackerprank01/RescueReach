package com.rescuereach.data.repository;

import com.rescuereach.data.repository.firebase.FirebaseIncidentRepository;
import com.rescuereach.data.repository.firebase.FirebaseResponderRepository;
import com.rescuereach.data.repository.firebase.FirebaseUserRepository;
import com.rescuereach.data.repository.firebase.FirebaseVolunteerRepository;
import com.rescuereach.data.repository.realtime.RealtimeStatusRepository;

/**
 * Singleton provider for repositories.
 */
public class RepositoryProvider {
    private static RepositoryProvider instance;

    private final UserRepository userRepository;
    private final ResponderRepository responderRepository;
    private final IncidentRepository incidentRepository;
    private final VolunteerRepository volunteerRepository;
    private final StatusRepository statusRepository;

    private RepositoryProvider() {
        userRepository = new FirebaseUserRepository();
        responderRepository = new FirebaseResponderRepository();
        incidentRepository = new FirebaseIncidentRepository();
        volunteerRepository = new FirebaseVolunteerRepository();
        statusRepository = new RealtimeStatusRepository();
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

    public ResponderRepository getResponderRepository() {
        return responderRepository;
    }

    public IncidentRepository getIncidentRepository() {
        return incidentRepository;
    }

    public VolunteerRepository getVolunteerRepository() {
        return volunteerRepository;
    }

    public StatusRepository getStatusRepository() {
        return statusRepository;
    }
}