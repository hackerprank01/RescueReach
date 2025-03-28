package com.rescuereach.data.model;

import java.util.Date;

public class Responder {
    private String responderId;
    private String username;
    private String name;
    private String email;
    private String phoneNumber;
    private String profileImageUrl;
    private ResponderRole role;
    private Date createdAt;
    private Date lastLoginAt;
    private boolean isActive;

    // Enum for responder roles
    public enum ResponderRole {
        FIRE("Fire"),
        MEDICAL("Medical"),
        POLICE("Police"),
        ADMIN("Admin");

        private final String displayName;

        ResponderRole(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    // Default constructor required for Firestore
    public Responder() {
    }

    public Responder(String responderId, String username, ResponderRole role) {
        this.responderId = responderId;
        this.username = username;
        this.role = role;
        this.createdAt = new Date();
        this.lastLoginAt = new Date();
        this.isActive = true;
    }

    // Getters and Setters
    public String getResponderId() {
        return responderId;
    }

    public void setResponderId(String responderId) {
        this.responderId = responderId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
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

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    public void setProfileImageUrl(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }

    public ResponderRole getRole() {
        return role;
    }

    public void setRole(ResponderRole role) {
        this.role = role;
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

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }
}