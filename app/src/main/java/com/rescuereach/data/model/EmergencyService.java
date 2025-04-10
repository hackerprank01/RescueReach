package com.rescuereach.data.model;

import com.google.firebase.firestore.Exclude;
import com.google.firebase.firestore.GeoPoint;

/**
 * Model class representing an emergency service location
 * (police station, fire station, hospital)
 */
public class EmergencyService {
    private String placeId;
    private String name;
    private String type; // POLICE, FIRE, MEDICAL
    private GeoPoint location;
    private String address;
    private String phoneNumber;
    private double distance; // in km
    private String tollFreeNumber; // Emergency service toll-free number

    public EmergencyService() {
        // Required empty constructor for Firestore
    }

    public String getPlaceId() {
        return placeId;
    }

    public void setPlaceId(String placeId) {
        this.placeId = placeId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
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

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public double getDistance() {
        return distance;
    }

    public void setDistance(double distance) {
        this.distance = distance;
    }

    public String getTollFreeNumber() {
        return tollFreeNumber;
    }

    public void setTollFreeNumber(String tollFreeNumber) {
        this.tollFreeNumber = tollFreeNumber;
    }

    /**
     * Get formatted distance string
     */
    @Exclude
    public String getFormattedDistance() {
        if (distance < 0) {
            return "Unknown";
        } else if (distance < 1) {
            return String.format("%.0f m", distance * 1000);
        } else {
            return String.format("%.1f km", distance);
        }
    }

    /**
     * Get the most appropriate phone number to call
     */
    @Exclude
    public String getEmergencyNumber() {
        // Prefer toll-free emergency number if available
        if (tollFreeNumber != null && !tollFreeNumber.isEmpty()) {
            return tollFreeNumber;
        }

        // Fallback to direct phone number
        if (phoneNumber != null && !phoneNumber.isEmpty()) {
            return phoneNumber;
        }

        // Default emergency numbers based on type
        switch (type) {
            case "POLICE":
                return "100";
            case "FIRE":
                return "101";
            case "MEDICAL":
                return "108";
            default:
                return "112";  // Universal emergency number
        }
    }
}