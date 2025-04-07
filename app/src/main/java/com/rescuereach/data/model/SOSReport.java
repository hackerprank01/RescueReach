package com.rescuereach.data.model;

import com.google.firebase.firestore.GeoPoint;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Data model representing an emergency SOS report
 */
public class SOSReport {
    // Category constants
    public static final String CATEGORY_POLICE = "police";
    public static final String CATEGORY_FIRE = "fire";
    public static final String CATEGORY_MEDICAL = "medical";

    // Status constants
    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_RECEIVED = "received";
    public static final String STATUS_RESPONDING = "responding";
    public static final String STATUS_RESOLVED = "resolved";

    // SMS Status constants
    public static final String SMS_STATUS_PENDING = "pending";
    public static final String SMS_STATUS_SENT = "sent";
    public static final String SMS_STATUS_DELIVERED = "delivered";
    public static final String SMS_STATUS_FAILED = "failed";

    private String id;
    private String userId;  // Phone number
    private String userName;
    private String category;
    private GeoPoint location;
    private String address;
    private Map<String, Object> deviceInfo;
    private int batteryLevel;
    private String status;
    private String smsStatus;
    private Date createdAt;
    private Date updatedAt;

    public SOSReport() {
        // Required empty constructor for Firestore
        this.id = UUID.randomUUID().toString();
        this.createdAt = new Date();
        this.updatedAt = new Date();
        this.status = STATUS_PENDING;
        this.smsStatus = SMS_STATUS_PENDING;
        this.deviceInfo = new HashMap<>();
    }

    public SOSReport(String userId, String userName, String category, GeoPoint location,
                     String address, Map<String, Object> deviceInfo, int batteryLevel) {
        this();
        this.userId = userId;
        this.userName = userName;
        this.category = category;
        this.location = location;
        this.address = address;
        this.deviceInfo = deviceInfo;
        this.batteryLevel = batteryLevel;
    }

    // Getters and setters
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

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
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
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public Map<String, Object> getDeviceInfo() {
        return deviceInfo;
    }

    public void setDeviceInfo(Map<String, Object> deviceInfo) {
        this.deviceInfo = deviceInfo;
    }

    public int getBatteryLevel() {
        return batteryLevel;
    }

    public void setBatteryLevel(int batteryLevel) {
        this.batteryLevel = batteryLevel;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
        this.updatedAt = new Date();
    }

    public String getSmsStatus() {
        return smsStatus;
    }

    public void setSmsStatus(String smsStatus) {
        this.smsStatus = smsStatus;
        this.updatedAt = new Date();
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public Date getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Date updatedAt) {
        this.updatedAt = updatedAt;
    }

    /**
     * Convert SOSReport to a Map for Firestore storage
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("userId", userId);
        map.put("userName", userName);
        map.put("category", category);
        map.put("location", location);
        map.put("address", address);
        map.put("deviceInfo", deviceInfo);
        map.put("batteryLevel", batteryLevel);
        map.put("status", status);
        map.put("smsStatus", smsStatus);
        map.put("createdAt", createdAt);
        map.put("updatedAt", updatedAt);
        return map;
    }
}