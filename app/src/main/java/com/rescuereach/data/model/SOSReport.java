package com.rescuereach.data.model;

import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.Exclude;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.firestore.ServerTimestamp;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Model class representing an SOS emergency report
 */
public class SOSReport {
    @DocumentId
    private String reportId;
    private String userId;
    private String userPhoneNumber;
    private String userFullName;
    private String emergencyType; // POLICE, FIRE, MEDICAL
    private GeoPoint location;
    private String address;
    private String city;
    private String state;
    private String zipCode;
    private double latitude;
    private double longitude;
    private float locationAccuracy;

    @ServerTimestamp
    private Date timestamp;

    private Map<String, Object> userInfo;
    private Map<String, Object> deviceInfo;
    private List<EmergencyService> nearbyServices;
    private List<EmergencyContact> emergencyContacts;

    private String status; // PENDING, RECEIVED, RESPONDING, RESOLVED
    private boolean isOnline;
    private boolean smsSent;
    private String smsStatus;
    private Date lastStatusUpdate;

    public SOSReport() {
        // Required empty constructor for Firestore
        this.reportId = UUID.randomUUID().toString();
        this.timestamp = new Date();
        this.status = "PENDING";
        this.isOnline = false;
        this.smsSent = false;
        this.smsStatus = "NOT_SENT";
        this.userInfo = new HashMap<>();
        this.deviceInfo = new HashMap<>();
        this.nearbyServices = new ArrayList<>();
        this.emergencyContacts = new ArrayList<>();
        this.lastStatusUpdate = new Date();
    }

    public SOSReport(String emergencyType) {
        this();
        this.emergencyType = emergencyType;
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

    public String getUserPhoneNumber() {
        return userPhoneNumber;
    }

    public void setUserPhoneNumber(String userPhoneNumber) {
        this.userPhoneNumber = userPhoneNumber;
    }

    public String getUserFullName() {
        return userFullName;
    }

    public void setUserFullName(String userFullName) {
        this.userFullName = userFullName;
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
        if (location != null) {
            this.latitude = location.getLatitude();
            this.longitude = location.getLongitude();
        }
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

    public String getZipCode() {
        return zipCode;
    }

    public void setZipCode(String zipCode) {
        this.zipCode = zipCode;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public float getLocationAccuracy() {
        return locationAccuracy;
    }

    public void setLocationAccuracy(float locationAccuracy) {
        this.locationAccuracy = locationAccuracy;
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

    public List<EmergencyContact> getEmergencyContacts() {
        return emergencyContacts;
    }

    public void setEmergencyContacts(List<EmergencyContact> emergencyContacts) {
        this.emergencyContacts = emergencyContacts;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
        this.lastStatusUpdate = new Date();
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

    public Date getLastStatusUpdate() {
        return lastStatusUpdate;
    }

    public void setLastStatusUpdate(Date lastStatusUpdate) {
        this.lastStatusUpdate = lastStatusUpdate;
    }

    /**
     * Add nearby emergency service
     */
    public void addNearbyService(EmergencyService service) {
        if (nearbyServices == null) {
            nearbyServices = new ArrayList<>();
        }
        nearbyServices.add(service);
    }

    /**
     * Add emergency contact
     */
    public void addEmergencyContact(EmergencyContact contact) {
        if (emergencyContacts == null) {
            emergencyContacts = new ArrayList<>();
        }
        emergencyContacts.add(contact);
    }

    /**
     * Add user info
     */
    public void addUserInfo(String key, Object value) {
        if (userInfo == null) {
            userInfo = new HashMap<>();
        }
        userInfo.put(key, value);
    }

    /**
     * Add device info
     */
    public void addDeviceInfo(String key, Object value) {
        if (deviceInfo == null) {
            deviceInfo = new HashMap<>();
        }
        deviceInfo.put(key, value);
    }

    /**
     * Get a shorter ID for display purposes
     */
    @Exclude
    public String getShortId() {
        if (reportId != null && reportId.length() > 8) {
            return reportId.substring(0, 8).toUpperCase();
        }
        return reportId != null ? reportId.toUpperCase() : "";
    }

    /**
     * Get a formatted display ID
     */
    @Exclude
    public String getDisplayId() {
        return "SOS-" + getShortId();
    }

    /**
     * Get the most appropriate emergency service based on the emergency type
     */
    @Exclude
    public EmergencyService getPrimaryEmergencyService() {
        if (nearbyServices == null || nearbyServices.isEmpty()) {
            return null;
        }

        // Look for matching type
        for (EmergencyService service : nearbyServices) {
            if (service.getType().equalsIgnoreCase(emergencyType)) {
                return service;
            }
        }

        // Return first one if no match
        return nearbyServices.get(0);
    }
}