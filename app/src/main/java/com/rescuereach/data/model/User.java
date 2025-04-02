package com.rescuereach.data.model;

import java.util.Date;

public class User {
    private String userId;
    private String firstName;
    private String lastName;
    private String phoneNumber; // Always starts with +91
    private String emergencyContact; // Always starts with +91
    private Date createdAt;

    // Required for Firestore
    public User() {
    }

    public User(String userId, String phoneNumber) {
        this.userId = userId;
        this.phoneNumber = phoneNumber;
        this.createdAt = new Date();
    }

    public User(String userId, String firstName, String lastName, String phoneNumber, String emergencyContact) {
        this.userId = userId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.phoneNumber = phoneNumber;
        this.emergencyContact = emergencyContact;
        this.createdAt = new Date();
    }

    // Getters and setters
    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
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

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getEmergencyContact() {
        return emergencyContact;
    }

    public void setEmergencyContact(String emergencyContact) {
        this.emergencyContact = emergencyContact;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public String getFullName() {
        if (firstName != null && lastName != null) {
            return firstName + " " + lastName;
        } else if (firstName != null) {
            return firstName;
        } else if (lastName != null) {
            return lastName;
        } else {
            return "Unknown";
        }
    }
}