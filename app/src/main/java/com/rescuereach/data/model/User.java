package com.rescuereach.data.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class User {
    private String userId;
    private String phoneNumber;
    private String firstName;
    private String lastName;
    private String email;
    private String profileImageUrl;
    private String homeAddress;
    private List<EmergencyContact> emergencyContacts;
    private Map<String, String> medicalInfo;
    private long lastUpdated;

    // Required for Firestore
    public User() {
        emergencyContacts = new ArrayList<>();
        medicalInfo = new HashMap<>();
    }

    public User(String userId, String phoneNumber) {
        this.userId = userId;
        this.phoneNumber = phoneNumber;
        this.emergencyContacts = new ArrayList<>();
        this.medicalInfo = new HashMap<>();
        this.lastUpdated = System.currentTimeMillis();
    }

    // Getters and setters
    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    public void setProfileImageUrl(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }

    public String getHomeAddress() {
        return homeAddress;
    }

    public void setHomeAddress(String homeAddress) {
        this.homeAddress = homeAddress;
    }

    public List<EmergencyContact> getEmergencyContacts() {
        return emergencyContacts;
    }

    public void setEmergencyContacts(List<EmergencyContact> emergencyContacts) {
        this.emergencyContacts = emergencyContacts;
    }

    public void addEmergencyContact(EmergencyContact contact) {
        if (emergencyContacts == null) {
            emergencyContacts = new ArrayList<>();
        }
        emergencyContacts.add(contact);
    }

    public Map<String, String> getMedicalInfo() {
        return medicalInfo;
    }

    public void setMedicalInfo(Map<String, String> medicalInfo) {
        this.medicalInfo = medicalInfo;
    }

    public void addMedicalInfo(String key, String value) {
        if (medicalInfo == null) {
            medicalInfo = new HashMap<>();
        }
        medicalInfo.put(key, value);
    }

    public long getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(long lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    // Inner class for emergency contacts
    public static class EmergencyContact {
        private String name;
        private String phoneNumber;
        private String relationship;

        public EmergencyContact() {
            // Required empty constructor for Firestore
        }

        public EmergencyContact(String name, String phoneNumber, String relationship) {
            this.name = name;
            this.phoneNumber = phoneNumber;
            this.relationship = relationship;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getPhoneNumber() {
            return phoneNumber;
        }

        public void setPhoneNumber(String phoneNumber) {
            this.phoneNumber = phoneNumber;
        }

        public String getRelationship() {
            return relationship;
        }

        public void setRelationship(String relationship) {
            this.relationship = relationship;
        }
    }
}