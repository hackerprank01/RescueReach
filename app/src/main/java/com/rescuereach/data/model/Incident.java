package com.rescuereach.data.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Incident {
    private String incidentId;
    private String reporterId;
    private EmergencyCategory category;
    private String description;
    private Location location;
    private List<String> mediaUrls;
    private IncidentStatus status;
    private String assignedResponderId;
    private Date reportedAt;
    private Date updatedAt;
    private List<StatusUpdate> statusUpdates;

    // Enum for emergency categories
    public enum EmergencyCategory {
        FIRE("Fire"),
        MEDICAL("Medical"),
        POLICE("Police"),
        OTHER("Other");

        private final String displayName;

        EmergencyCategory(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    // Enum for incident statuses
    public enum IncidentStatus {
        REPORTED("Reported"),
        ASSIGNED("Assigned"),
        EN_ROUTE("En Route"),
        ON_SCENE("On Scene"),
        RESOLVED("Resolved"),
        CLOSED("Closed");

        private final String displayName;

        IncidentStatus(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    // Status update class
    public static class StatusUpdate {
        private IncidentStatus status;
        private String responderId;
        private String notes;
        private Date timestamp;

        public StatusUpdate() {
        }

        public StatusUpdate(IncidentStatus status, String responderId, String notes) {
            this.status = status;
            this.responderId = responderId;
            this.notes = notes;
            this.timestamp = new Date();
        }

        // Getters and Setters
        public IncidentStatus getStatus() {
            return status;
        }

        public void setStatus(IncidentStatus status) {
            this.status = status;
        }

        public String getResponderId() {
            return responderId;
        }

        public void setResponderId(String responderId) {
            this.responderId = responderId;
        }

        public String getNotes() {
            return notes;
        }

        public void setNotes(String notes) {
            this.notes = notes;
        }

        public Date getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(Date timestamp) {
            this.timestamp = timestamp;
        }
    }

    // Location inner class
    public static class Location {
        private double latitude;
        private double longitude;
        private String address;

        public Location() {
        }

        public Location(double latitude, double longitude) {
            this.latitude = latitude;
            this.longitude = longitude;
        }

        // Getters and Setters
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

        public String getAddress() {
            return address;
        }

        public void setAddress(String address) {
            this.address = address;
        }
    }

    // Default constructor required for Firestore
    public Incident() {
        mediaUrls = new ArrayList<>();
        statusUpdates = new ArrayList<>();
    }

    public Incident(String incidentId, String reporterId, EmergencyCategory category, String description, Location location) {
        this.incidentId = incidentId;
        this.reporterId = reporterId;
        this.category = category;
        this.description = description;
        this.location = location;
        this.status = IncidentStatus.REPORTED;
        this.reportedAt = new Date();
        this.updatedAt = new Date();
        this.mediaUrls = new ArrayList<>();
        this.statusUpdates = new ArrayList<>();

        // Add initial status update
        this.statusUpdates.add(new StatusUpdate(IncidentStatus.REPORTED, reporterId, "Incident reported"));
    }

    // Getters and Setters
    public String getIncidentId() {
        return incidentId;
    }

    public void setIncidentId(String incidentId) {
        this.incidentId = incidentId;
    }

    public String getReporterId() {
        return reporterId;
    }

    public void setReporterId(String reporterId) {
        this.reporterId = reporterId;
    }

    public EmergencyCategory getCategory() {
        return category;
    }

    public void setCategory(EmergencyCategory category) {
        this.category = category;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public List<String> getMediaUrls() {
        return mediaUrls;
    }

    public void setMediaUrls(List<String> mediaUrls) {
        this.mediaUrls = mediaUrls;
    }

    public void addMediaUrl(String mediaUrl) {
        if (this.mediaUrls == null) {
            this.mediaUrls = new ArrayList<>();
        }
        this.mediaUrls.add(mediaUrl);
    }

    public IncidentStatus getStatus() {
        return status;
    }

    public void setStatus(IncidentStatus status) {
        this.status = status;
        this.updatedAt = new Date();
    }

    public String getAssignedResponderId() {
        return assignedResponderId;
    }

    public void setAssignedResponderId(String assignedResponderId) {
        this.assignedResponderId = assignedResponderId;
        this.updatedAt = new Date();
    }

    public Date getReportedAt() {
        return reportedAt;
    }

    public void setReportedAt(Date reportedAt) {
        this.reportedAt = reportedAt;
    }

    public Date getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Date updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<StatusUpdate> getStatusUpdates() {
        return statusUpdates;
    }

    public void setStatusUpdates(List<StatusUpdate> statusUpdates) {
        this.statusUpdates = statusUpdates;
    }

    public void addStatusUpdate(IncidentStatus status, String responderId, String notes) {
        if (this.statusUpdates == null) {
            this.statusUpdates = new ArrayList<>();
        }
        this.statusUpdates.add(new StatusUpdate(status, responderId, notes));
        this.status = status;
        this.updatedAt = new Date();
    }
}