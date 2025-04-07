package com.rescuereach.data.room.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.rescuereach.data.room.entity.MediaEntity;

import java.util.List;

/**
 * Data Access Object for media files
 */
@Dao
public interface MediaDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(MediaEntity mediaEntity);

    @Query("SELECT * FROM media_files WHERE incidentId = :incidentId")
    List<MediaEntity> getByIncidentId(String incidentId);

    // Other methods will be implemented later
}