package com.rescuereach.data.room.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Room entity for storing media files locally
 */
@Entity(tableName = "media_files")
public class MediaEntity {
    @PrimaryKey
    @NonNull
    private String id;

    private String incidentId;
    private String localPath;
    private String remoteUrl;
    private String type; // photo, video, audio
    private boolean uploaded;

    // Getters and setters will be implemented later
    @NonNull
    public String getId() {
        return id;
    }

    public void setId(@NonNull String id) {
        this.id = id;
    }

    // Placeholder for now - will be fully implemented when needed
}