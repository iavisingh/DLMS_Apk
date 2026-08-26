package com.example.dlmsconfigurator.core.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(device: DeviceEntity): Long

    @Update
    fun update(device: DeviceEntity)

    @Delete
    fun delete(device: DeviceEntity)

    @Query("SELECT * FROM devices ORDER BY lastConnectedAt DESC, createdAt DESC")
    fun getAllDevicesFlow(): Flow<List<DeviceEntity>>

    @Query("SELECT * FROM devices WHERE id = :id LIMIT 1")
    fun getById(id: Long): DeviceEntity?

    @Query("SELECT * FROM devices WHERE name = :name LIMIT 1")
    fun getByName(name: String): DeviceEntity?

    @Query("UPDATE devices SET lastConnectedAt = :ts WHERE id = :id")
    fun touchLastConnected(id: Long, ts: Long)

    @Query("UPDATE devices SET lastKnownMeterSerial = :serial WHERE id = :id")
    fun updateMeterSerial(id: Long, serial: String)
}

@Dao
interface AssociationObjectDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(objects: List<AssociationObjectEntity>)

    @Query("DELETE FROM association_objects WHERE deviceId = :deviceId")
    fun deleteByDeviceId(deviceId: Long)

    @Query("SELECT * FROM association_objects WHERE deviceId = :deviceId ORDER BY classId ASC, obisCode ASC")
    fun getByDeviceIdFlow(deviceId: Long): Flow<List<AssociationObjectEntity>>

    @Query("SELECT * FROM association_objects WHERE deviceId = :deviceId ORDER BY classId ASC, obisCode ASC")
    fun getByDeviceId(deviceId: Long): List<AssociationObjectEntity>

    @Query("SELECT COUNT(*) FROM association_objects WHERE deviceId = :deviceId")
    fun countByDeviceId(deviceId: Long): Int
}
