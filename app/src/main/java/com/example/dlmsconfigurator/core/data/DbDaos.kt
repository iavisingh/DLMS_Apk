package com.example.dlmsconfigurator.core.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(session: SessionEntity): Long

    @Update
    fun update(session: SessionEntity)

    @Query("SELECT * FROM sessions WHERE id = :id")
    fun getById(id: Long): SessionEntity?

    @Query("SELECT * FROM sessions ORDER BY startTime DESC")
    fun getAllSessionsFlow(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions ORDER BY startTime DESC")
    fun getAllSessions(): List<SessionEntity>

    @Delete
    fun delete(session: SessionEntity)
}

@Dao
interface OperationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(operation: OperationEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(operations: List<OperationEntity>)

    @Query("SELECT * FROM operations WHERE sessionId = :sessionId ORDER BY sequenceNo ASC, attemptNumber ASC")
    fun getOperationsForSession(sessionId: Long): List<OperationEntity>

    @Query("SELECT * FROM operations WHERE sessionId = :sessionId ORDER BY sequenceNo ASC, attemptNumber ASC")
    fun getOperationsForSessionFlow(sessionId: Long): Flow<List<OperationEntity>>

    @Query("SELECT * FROM operations WHERE id = :id")
    fun getById(id: Long): OperationEntity?
}

@Dao
interface AuthEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(authEvent: AuthEventEntity): Long

    @Query("SELECT * FROM auth_events ORDER BY timestamp DESC")
    fun getAllAuthEvents(): List<AuthEventEntity>

    @Query("SELECT * FROM auth_events ORDER BY timestamp DESC")
    fun getAllAuthEventsFlow(): Flow<List<AuthEventEntity>>
}
