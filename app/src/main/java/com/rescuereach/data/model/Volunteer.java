package com.rescuereach.data.model;

import java.util.Date;

public class Volunteer {
    private String volunteerId;
    private String userId;
    private boolean isVerified;
    private String zone;
    private boolean isAvailable;
    private Date registeredAt;
    private Date lastActiveAt;

    // Default constructor required for Firestore
    public Volunteer() {
    }

    public Volunteer(String volunteerId, String userId) {
        this.volunteerId = volunteerId;
        this.userId = userId;
        this.isVerified = false;
        this.isAvailable = false;
        this.registeredAt = new Date();
        this.lastActiveAt = new Date();
    }

    // Getters and Setters
    public String getVolunteerId() {
        return volunteerId;
    }

    public void setVolunteerId(String volunteerId) {
        this.volunteerId = volunteerId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public boolean isVerified() {
        return isVerified;
    }

    public void setVerified(boolean verified) {
        isVerified = verified;
    }

    public String getZone() {
        return zone;
    }

    public void setZone(String zone) {
        this.zone = zone;
    }

    public boolean isAvailable() {
        return isAvailable;
    }

    public void setAvailable(boolean available) {
        isAvailable = available;
        this.lastActiveAt = new Date();
    }

    public Date getRegisteredAt() {
        return registeredAt;
    }

    public void setRegisteredAt(Date registeredAt) {
        this.registeredAt = registeredAt;
    }

    public Date getLastActiveAt() {
        return lastActiveAt;
    }

    public void setLastActiveAt(Date lastActiveAt) {
        this.lastActiveAt = lastActiveAt;
    }
}