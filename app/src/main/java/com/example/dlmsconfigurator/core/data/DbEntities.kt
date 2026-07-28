package com.example.dlmsconfigurator.core.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long,
    val endTime: Long? = null,
    val meterSerial: String? = null,
    val jsonSourceFileName: String,
    val detailedLogging: Boolean,
    val status: String, // RUNNING, COMPLETED, ABORTED
    val connectionOverrideUsed: Boolean,
    val syncedAt: Long? = null,
    val remoteId: String? = null
)

@Entity(
    tableName = "operations",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["sessionId"])]
)
data class OperationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val sequenceNo: Int,
    val opType: String, // GET, SET, ACTION
    val obisCode: String,
    val classId: Int,
    val attributeOrMethod: Int,
    val status: String, // SUCCESS, FAIL, TIMEOUT
    val startTime: Long,
    val endTime: Long,
    val errorMessage: String? = null,
    val attemptNumber: Int = 1,
    val maxAttemptsConfigured: Int = 1,
    val rawRequestHex: String? = null,
    val rawResponseHex: String? = null,
    val decodedValue: String? = null
)

@Entity(tableName = "auth_events")
data class AuthEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val result: String, // SUCCESS, FAILED, CANCELLED
    val authMethod: String // BIOMETRIC_FINGERPRINT, BIOMETRIC_FACE, DEVICE_CREDENTIAL
)
