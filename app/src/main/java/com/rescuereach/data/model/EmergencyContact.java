package com.rescuereach.data.model;

/**
 * Represents an emergency contact that can be notified during emergencies
 */
public class EmergencyContact {
    private String name;
    private String phoneNumber;
    private String relationship;
    private boolean isPrimary;

    public EmergencyContact() {
        // Default constructor required for Firebase
    }

    public EmergencyContact(String phoneNumber) {
        this.phoneNumber = phoneNumber;
        this.isPrimary = true;
    }

    public EmergencyContact(String name, String phoneNumber, String relationship, boolean isPrimary) {
        this.name = name;
        this.phoneNumber = phoneNumber;
        this.relationship = relationship;
        this.isPrimary = isPrimary;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getRelationship() {
        return relationship;
    }

    public void setRelationship(String relationship) {
        this.relationship = relationship;
    }

    public boolean isPrimary() {
        return isPrimary;
    }

    public void setPrimary(boolean primary) {
        isPrimary = primary;
    }

    /**
     * Format the phone number to ensure it has the proper country code format
     * @return Properly formatted phone number
     */
    public String getFormattedPhoneNumber() {
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            return "";
        }

        // Remove any non-digit characters except the + symbol
        String cleaned = phoneNumber.replaceAll("[^\\d+]", "");

        // Ensure it starts with +91 for India
        if (!cleaned.startsWith("+")) {
            cleaned = "+91" + cleaned;
        } else if (!cleaned.startsWith("+91") && cleaned.length() > 1) {
            cleaned = "+91" + cleaned.substring(1);
        }

        return cleaned;
    }

    /**
     * Get a display-friendly version of the phone number
     */
    public String getDisplayPhoneNumber() {
        String formatted = getFormattedPhoneNumber();
        if (formatted.startsWith("+91")) {
            return formatted.substring(0, 3) + " " + formatted.substring(3);
        }
        return formatted;
    }
}