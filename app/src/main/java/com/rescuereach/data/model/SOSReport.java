package com.rescuereach.data.model;

import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.Exclude;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.firestore.ServerTimestamp;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Model class representing an SOS emergency report
 */
public class SOSReport implements Serializable {
    @DocumentId
    private String reportId;
    private String userId;
    private String emergencyType; // POLICE, FIRE, MEDICAL
    private GeoPoint location;
    private String address;
    private String city;
    private String state;
    @ServerTimestamp
    private Date timestamp;
    private Map<String, Object> userInfo;
    private Map<String, Object> deviceInfo;
    private List<EmergencyService> nearbyServices; // Nearest relevant emergency services
    private List<String> emergencyContactNumbers; // Emergency contact phone numbers
    private String status; // PENDING, RECEIVED, RESPONDING, RESOLVED
    private boolean isOnline;
    private boolean smsSent;
    private String smsStatus;
    private Map<String, Object> responderInfo;

    // Non-persistent fields (excluded from Firestore)
    @Exclude
    private List<byte[]> mediaData;

    // Constants for status values
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_RECEIVED = "RECEIVED";
    public static final String STATUS_RESPONDING = "RESPONDING";
    public static final String STATUS_RESOLVED = "RESOLVED";

    // Default constructor required for Firestore
    public SOSReport() {
        this.nearbyServices = new ArrayList<>();
        this.emergencyContactNumbers = new ArrayList<>();
        this.userInfo = new HashMap<>();
        this.deviceInfo = new HashMap<>();
        this.responderInfo = new HashMap<>();
        this.status = STATUS_PENDING;
    }

    // Constructor with essential fields
    public SOSReport(String userId, String emergencyType, GeoPoint location,
                     String address, String city, String state) {
        this();
        this.userId = userId;
        this.emergencyType = emergencyType;
        this.location = location;
        this.address = address;
        this.city = city;
        this.state = state;
    }

    // Getters and setters
    public String getReportId() {
        return reportId;
    }

    public void setReportId(String reportId) {
        this.reportId = reportId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getEmergencyType() {
        return emergencyType;
    }

    public void setEmergencyType(String emergencyType) {
        this.emergencyType = emergencyType;
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

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }

    public Map<String, Object> getUserInfo() {
        return userInfo;
    }

    public void setUserInfo(Map<String, Object> userInfo) {
        this.userInfo = userInfo;
    }

    public Map<String, Object> getDeviceInfo() {
        return deviceInfo;
    }

    public void setDeviceInfo(Map<String, Object> deviceInfo) {
        this.deviceInfo = deviceInfo;
    }

    public List<EmergencyService> getNearbyServices() {
        return nearbyServices;
    }

    public void setNearbyServices(List<EmergencyService> nearbyServices) {
        this.nearbyServices = nearbyServices;
    }

    public List<String> getEmergencyContactNumbers() {
        return emergencyContactNumbers;
    }

    public void setEmergencyContactNumbers(List<String> emergencyContactNumbers) {
        this.emergencyContactNumbers = emergencyContactNumbers;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isOnline() {
        return isOnline;
    }

    public void setOnline(boolean online) {
        isOnline = online;
    }

    public boolean isSmsSent() {
        return smsSent;
    }

    public void setSmsSent(boolean smsSent) {
        this.smsSent = smsSent;
    }

    public String getSmsStatus() {
        return smsStatus;
    }

    public void setSmsStatus(String smsStatus) {
        this.smsStatus = smsStatus;
    }

    public Map<String, Object> getResponderInfo() {
        return responderInfo;
    }

    public void setResponderInfo(Map<String, Object> responderInfo) {
        this.responderInfo = responderInfo;
    }

    @Exclude
    public List<byte[]> getMediaData() {
        return mediaData;
    }

    public void setMediaData(List<byte[]> mediaData) {
        this.mediaData = mediaData;
    }

    public void addNearbyService(EmergencyService service) {
        if (nearbyServices == null) {
            nearbyServices = new ArrayList<>();
        }
        nearbyServices.add(service);
    }

    public void addEmergencyContactNumber(String phoneNumber) {
        if (emergencyContactNumbers == null) {
            emergencyContactNumbers = new ArrayList<>();
        }
        emergencyContactNumbers.add(phoneNumber);
    }

    public void addUserInfo(String key, Object value) {
        if (userInfo == null) {
            userInfo = new HashMap<>();
        }
        userInfo.put(key, value);
    }

    public void addDeviceInfo(String key, Object value) {
        if (deviceInfo == null) {
            deviceInfo = new HashMap<>();
        }
        deviceInfo.put(key, value);
    }
}