package com.rescuereach.data.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.GeoPoint;

import java.util.HashMap;
import java.util.Map;

public class SOSReport {
    public static final String CATEGORY_POLICE = "police";
    public static final String CATEGORY_FIRE = "fire";
    public static final String CATEGORY_MEDICAL = "medical";

    private String id;
    private String userId;
    private String phoneNumber;
    private String emergencyContact;
    private String category;
    private GeoPoint location;
    private double latitude;
    private double longitude;
    private Timestamp timestamp;
    private String status;
    private String deviceInfo;
    private boolean isOffline;
    private String address;

    // Default constructor required for Firestore
    public SOSReport() {
    }

    public SOSReport(String userId, String phoneNumber, String emergencyContact,
                     String category, double latitude, double longitude,
                     Timestamp timestamp, String deviceInfo, boolean isOffline, String address) {
        this.userId = userId;
        this.phoneNumber = phoneNumber;
        this.emergencyContact = emergencyContact;
        this.category = category;
        this.latitude = latitude;
        this.longitude = longitude;
        this.location = new GeoPoint(latitude, longitude);
        this.timestamp = timestamp;
        this.status = "pending";
        this.deviceInfo = deviceInfo;
        this.isOffline = isOffline;
        this.address = address;
    }

    // Convert to Map for Firestore
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("userId", userId);
        map.put("phoneNumber", phoneNumber);
        map.put("emergencyContact", emergencyContact);
        map.put("category", category);
        map.put("location", new GeoPoint(latitude, longitude));
        map.put("latitude", latitude);
        map.put("longitude", longitude);
        map.put("timestamp", timestamp);
        map.put("status", status);
        map.put("deviceInfo", deviceInfo);
        map.put("isOffline", isOffline);
        map.put("address", address);
        return map;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

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

    public String getEmergencyContact() {
        return emergencyContact;
    }

    public void setEmergencyContact(String emergencyContact) {
        this.emergencyContact = emergencyContact;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public GeoPoint getLocation() {
        return location;
    }

    public void setLocation(GeoPoint location) {
        this.location = location;
        if (location != null) {
            this.latitude = location.getLatitude();
            this.longitude = location.getLongitude();
        }
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
        updateGeoPoint();
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
        updateGeoPoint();
    }

    private void updateGeoPoint() {
        this.location = new GeoPoint(latitude, longitude);
    }

    public Timestamp getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Timestamp timestamp) {
        this.timestamp = timestamp;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDeviceInfo() {
        return deviceInfo;
    }

    public void setDeviceInfo(String deviceInfo) {
        this.deviceInfo = deviceInfo;
    }

    public boolean isOffline() {
        return isOffline;
    }

    public void setOffline(boolean offline) {
        isOffline = offline;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }
}