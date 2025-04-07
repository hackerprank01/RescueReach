package com.rescuereach.data.room;

import androidx.room.Database;
import androidx.room.RoomDatabase;

import com.rescuereach.data.room.dao.SOSDao;
import com.rescuereach.data.room.dao.IncidentDao;
import com.rescuereach.data.room.dao.MediaDao;
import com.rescuereach.data.room.entity.SOSEntity;
import com.rescuereach.data.room.entity.IncidentEntity;
import com.rescuereach.data.room.entity.MediaEntity;

/**
 * Room database for the application
 */
@Database(
        entities = {SOSEntity.class, IncidentEntity.class, MediaEntity.class},
        version = 1,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {
    public abstract SOSDao sosDao();
    public abstract IncidentDao incidentDao();
    public abstract MediaDao mediaDao();
}