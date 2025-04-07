package com.rescuereach.data.room.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.rescuereach.data.room.entity.IncidentEntity;

import java.util.List;

/**
 * Data Access Object for incident reports
 */
@Dao
public interface IncidentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(IncidentEntity incidentEntity);

    @Update
    void update(IncidentEntity incidentEntity);

    @Query("SELECT * FROM incidents WHERE needsSync = 1 ORDER BY createdAt DESC")
    List<IncidentEntity> getAllPendingSync();

    // Other methods will be implemented later
}