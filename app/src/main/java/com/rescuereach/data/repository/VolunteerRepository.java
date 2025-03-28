package com.rescuereach.data.repository;

import com.rescuereach.data.model.Volunteer;

import java.util.List;

public interface VolunteerRepository {
    interface OnVolunteerFetchedListener {
        void onSuccess(Volunteer volunteer);
        void onError(Exception e);
    }

    interface OnVolunteerListFetchedListener {
        void onSuccess(List<Volunteer> volunteers);
        void onError(Exception e);
    }

    void getVolunteerById(String volunteerId, OnVolunteerFetchedListener listener);
    void getVolunteerByUserId(String userId, OnVolunteerFetchedListener listener);
    void getVolunteersByZone(String zone, OnVolunteerListFetchedListener listener);
    void getAvailableVolunteers(OnVolunteerListFetchedListener listener);
    void getAllVolunteers(OnVolunteerListFetchedListener listener);
    void saveVolunteer(Volunteer volunteer, OnCompleteListener listener);
    void updateVolunteer(Volunteer volunteer, OnCompleteListener listener);
    void updateVolunteerAvailability(String volunteerId, boolean isAvailable, OnCompleteListener listener);
    void deleteVolunteer(String volunteerId, OnCompleteListener listener);
}