package com.rescuereach.data.room.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.rescuereach.data.room.entity.SOSEntity;

import java.util.List;

/**
 * Data Access Object for SOS reports
 */
@Dao
public interface SOSDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(SOSEntity sosEntity);

    @Update
    void update(SOSEntity sosEntity);

    @Query("SELECT * FROM sos_reports WHERE id = :sosId")
    SOSEntity getById(String sosId);

    @Query("SELECT * FROM sos_reports WHERE needsSync = 1 ORDER BY createdAt DESC")
    List<SOSEntity> getAllPendingSync();

    @Query("DELETE FROM sos_reports WHERE id = :sosId")
    void deleteById(String sosId);
}