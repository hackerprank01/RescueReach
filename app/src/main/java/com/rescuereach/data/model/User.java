package com.rescuereach.data.model;

import java.util.Date;

public class User {
    private String userId;
    private String fullName;
    private String phoneNumber; // Always starts with +91
    private String emergencyContact; // Always starts with +91
    private Date createdAt;
    // New fields
    private Date dateOfBirth;
    private String gender;
    private String state;
    private boolean isVolunteer;

    // Required for Firestore
    public User() {
    }

    public User(String userId, String phoneNumber) {
        this.userId = userId;
        this.phoneNumber = phoneNumber;
        this.createdAt = new Date();
    }

    public User(String userId, String fullName, String phoneNumber, String emergencyContact) {
        this.userId = userId;
        this.fullName = fullName;
        this.phoneNumber = phoneNumber;
        this.emergencyContact = emergencyContact;
        this.createdAt = new Date();
    }

    // Full constructor with all fields
    public User(String userId, String fullName, String phoneNumber, String emergencyContact,
                Date dateOfBirth, String gender, String state, boolean isVolunteer) {
        this.userId = userId;
        this.fullName = fullName;
        this.phoneNumber = phoneNumber;
        this.emergencyContact = emergencyContact;
        this.dateOfBirth = dateOfBirth;
        this.gender = gender;
        this.state = state;
        this.isVolunteer = isVolunteer;
        this.createdAt = new Date();
    }

    // Getters and setters
    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
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

    // New getters and setters
    public Date getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(Date dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public boolean isVolunteer() {
        return isVolunteer;
    }

    public void setVolunteer(boolean volunteer) {
        isVolunteer = volunteer;
    }

    // For backward compatibility with code that may still use firstName/lastName
    @Deprecated
    public String getFirstName() {
        if (fullName != null && fullName.contains(" ")) {
            return fullName.substring(0, fullName.indexOf(" "));
        }
        return fullName;
    }

    @Deprecated
    public String getLastName() {
        if (fullName != null && fullName.contains(" ")) {
            return fullName.substring(fullName.indexOf(" ") + 1);
        }
        return "";
    }

    @Deprecated
    public void setFirstName(String firstName) {
        if (this.fullName == null) {
            this.fullName = firstName;
        } else if (this.fullName.contains(" ")) {
            this.fullName = firstName + this.fullName.substring(this.fullName.indexOf(" "));
        } else {
            this.fullName = firstName;
        }
    }

    @Deprecated
    public void setLastName(String lastName) {
        if (this.fullName == null) {
            this.fullName = lastName;
        } else if (this.fullName.contains(" ")) {
            this.fullName = this.fullName.substring(0, this.fullName.indexOf(" ") + 1) + lastName;
        } else {
            this.fullName = this.fullName + " " + lastName;
        }
    }
}