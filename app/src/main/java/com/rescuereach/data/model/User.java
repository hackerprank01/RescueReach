package com.rescuereach.data.model;

import java.io.Serializable;
import java.util.Date;

/**
 * Model class for User data
 * Represents a user in the RescueReach application
 */
public class User implements Serializable {
    private String userId;
    private String phoneNumber;
    private String fullName;
    private String firstName;
    private String lastName;
    private String gender;
    private Date dateOfBirth;
    private String state;
    private String emergencyContact;
    private boolean isVolunteer;
    private Date createdAt;
    private String status; // For online status tracking

    /**
     * Default constructor required for Firestore
     */
    public User() {
        // Required empty constructor for Firestore
    }

    /**
     * Constructor with essential fields
     * @param userId Unique identifier for the user
     * @param phoneNumber User's phone number
     * @param fullName User's full name
     */
    public User(String userId, String phoneNumber, String fullName) {
        this.userId = userId;
        this.phoneNumber = phoneNumber;
        this.fullName = fullName;

        // Parse full name into first and last name for backwards compatibility
        parseFullName(fullName);

        this.createdAt = new Date();
        this.isVolunteer = false;
        this.status = "offline";
    }

    /**
     * Full constructor with all fields
     */
    public User(String userId, String phoneNumber, String fullName, String gender,
                Date dateOfBirth, String state, String emergencyContact, boolean isVolunteer) {
        this.userId = userId;
        this.phoneNumber = phoneNumber;
        this.fullName = fullName;

        // Parse full name into first and last name
        parseFullName(fullName);

        this.gender = gender;
        this.dateOfBirth = dateOfBirth;
        this.state = state;
        this.emergencyContact = emergencyContact;
        this.isVolunteer = isVolunteer;
        this.createdAt = new Date();
        this.status = "offline";
    }

    /**
     * Parse full name into first and last name
     * @param fullName Full name to parse
     */
    private void parseFullName(String fullName) {
        if (fullName != null && !fullName.isEmpty()) {
            int spaceIndex = fullName.indexOf(' ');
            if (spaceIndex > 0) {
                this.firstName = fullName.substring(0, spaceIndex);
                this.lastName = fullName.substring(spaceIndex + 1);
            } else {
                this.firstName = fullName;
                this.lastName = "";
            }
        }
    }

    /**
     * Update full name and parse it into first and last name
     * @param fullName New full name
     */
    public void updateFullName(String fullName) {
        this.fullName = fullName;
        parseFullName(fullName);
    }

    // Getters and Setters

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

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
        parseFullName(fullName);
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;

        // Update full name when setting first name
        if (this.lastName != null) {
            this.fullName = firstName + (this.lastName.isEmpty() ? "" : " " + this.lastName);
        } else {
            this.fullName = firstName;
        }
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;

        // Update full name when setting last name
        if (this.firstName != null) {
            this.fullName = this.firstName + (lastName.isEmpty() ? "" : " " + lastName);
        } else if (lastName != null && !lastName.isEmpty()) {
            this.fullName = lastName;
        }
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public Date getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(Date dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getEmergencyContact() {
        return emergencyContact;
    }

    public void setEmergencyContact(String emergencyContact) {
        this.emergencyContact = emergencyContact;
    }

    public boolean isVolunteer() {
        return isVolunteer;
    }

    public void setVolunteer(boolean volunteer) {
        isVolunteer = volunteer;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return "User{" +
                "userId='" + userId + '\'' +
                ", phoneNumber='" + phoneNumber + '\'' +
                ", fullName='" + fullName + '\'' +
                ", isVolunteer=" + isVolunteer +
                '}';
    }
}