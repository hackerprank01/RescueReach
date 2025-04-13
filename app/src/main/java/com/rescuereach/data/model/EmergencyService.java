package com.rescuereach.data.model;

import com.google.firebase.firestore.GeoPoint;

import java.io.Serializable;

/**
 * Model class representing an emergency service location (hospital, police station, fire station)
 */
public class EmergencyService implements Serializable {
    private String placeId;
    private String name;
    private String type; // POLICE, FIRE, HOSPITAL
    private GeoPoint location;
    private String address;
    private String phoneNumber;
    private double distance; // in km
    private String tollFreeNumber; // Emergency service toll-free number

    // Default constructor required for Firestore
    public EmergencyService() {
    }

    public EmergencyService(String placeId, String name, String type, GeoPoint location,
                            String address, String phoneNumber, double distance, String tollFreeNumber) {
        this.placeId = placeId;
        this.name = name;
        this.type = type;
        this.location = location;
        this.address = address;
        this.phoneNumber = phoneNumber;
        this.distance = distance;
        this.tollFreeNumber = tollFreeNumber;
    }

    // Getters and setters
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
}