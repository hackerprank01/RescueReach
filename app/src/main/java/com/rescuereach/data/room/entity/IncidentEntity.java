package com.rescuereach.data.room.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Room entity for storing incident reports locally
 */
@Entity(tableName = "incidents")
public class IncidentEntity {
    @PrimaryKey
    @NonNull
    private String id;

    private String userId;
    private String category;
    private String subcategory;
    private double latitude;
    private double longitude;
    private String address;
    private String description;
    private int severity;
    private String status;
    private long createdAt;
    private long updatedAt;
    private boolean needsSync;

    // Getters and setters will be implemented later
    @NonNull
    public String getId() {
        return id;
    }

    public void setId(@NonNull String id) {
        this.id = id;
    }

    // Placeholder for now - will be fully implemented when needed

    public boolean isNeedsSync() {
        return needsSync;
    }

    public void setNeedsSync(boolean needsSync) {
        this.needsSync = needsSync;
    }
}