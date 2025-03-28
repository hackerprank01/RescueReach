package com.rescuereach.data.model;

import java.util.Date;

public class User {
    private String userId;
    private String phoneNumber;
    private String name;
    private String email;
    private String profileImageUrl;
    private Date createdAt;
    private Date lastLoginAt;
    private boolean isVolunteer;

    // Default constructor required for Firestore
    public User() {
    }

    public User(String userId, String phoneNumber) {
        this.userId = userId;
        this.phoneNumber = phoneNumber;
        this.createdAt = new Date();
        this.lastLoginAt = new Date();
        this.isVolunteer = false;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public Date getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(Date lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    public boolean isVolunteer() {
        return isVolunteer;
    }

    public void setVolunteer(boolean volunteer) {
        isVolunteer = volunteer;
    }
}