package com.example.dlmsconfigurator.core.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// ─────────────────────────────────────────────────────────────────────────────
//  CommSettings — transport configuration, stored as a JSON column in Room
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
sealed class CommSettings {
    @Serializable
    @SerialName("otg")
    data class Otg(
        val baudRate: Int = 9600
    ) : CommSettings()

    @Serializable
    @SerialName("ble")
    data class Ble(
        /** MAC address of the target BLE device */
        val deviceAddress: String = "",
        val deviceName: String = ""
    ) : CommSettings()

    @Serializable
    @SerialName("tcp")
    data class Tcp(
        val host: String = "",
        val port: Int = 4059
    ) : CommSettings()

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun toJson(settings: CommSettings): String = json.encodeToString(serializer(), settings)
        fun fromJson(raw: String): CommSettings = json.decodeFromString(serializer(), raw)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  DeviceEntity — one row per saved device profile
// ─────────────────────────────────────────────────────────────────────────────

@Entity(tableName = "devices")
data class DeviceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    // Human-readable label shown in the device list
    val name: String,

    // Serialized CommSettings (JSON)
    val commSettingsJson: String,

    // ── DLMS Session Parameters ──────────────────────────────────────────────
    val clientAddress: Int = 16,
    val serverAddress: Int = 1,
    val authenticationRole: String = "PC",
    val addressType: String = "Default",
    val logicalServer: Int = 0,
    val physicalServer: Int = 1,

    /** "none" | "authentication" | "authenticationencryption" */
    val security: String = "none",
    val securitySuite: String = "Suite0",

    /** "HDLC" | "WRAPPER" */
    val interfaceType: String = "HDLC",

    val logicalNameReferencing: Boolean = true,

    // Secrets are NOT stored here as plaintext.
    // Instead, each field below holds a SecureKeyStore alias key.
    // Read the actual secret via SecureKeyStore.getDeviceSecret(alias).
    val passwordKeyRef: String? = null,
    val systemTitleKeyRef: String? = null,
    val authKeyRef: String? = null,
    val encKeyRef: String? = null,

    val ciphering: Boolean = false,

    /** OBIS code of the invocation counter object, e.g. "0.0.43.1.0.255" */
    val invocationCounterObis: String? = null,
    val useInvocationCounter: Boolean = false,
    val invocationCounterInitial: Long = 0,
    val retryCount: Int = 3,
    val retryIntervalMs: Int = 1000,

    // ── Housekeeping ─────────────────────────────────────────────────────────
    val lastConnectedAt: Long? = null,
    val lastKnownMeterSerial: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

// ─────────────────────────────────────────────────────────────────────────────
//  AssociationObjectEntity — one row per COSEM object returned by Class-15 read
// ─────────────────────────────────────────────────────────────────────────────

@Entity(
    tableName = "association_objects",
    foreignKeys = [
        ForeignKey(
            entity = DeviceEntity::class,
            parentColumns = ["id"],
            childColumns = ["deviceId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("deviceId")]
)
data class AssociationObjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deviceId: Long,

    val classId: Int,
    val version: Short = 0,
    val obisCode: String,

    /** Human-readable class name resolved from classId, e.g. "Data", "Clock" */
    val className: String = "",

    /** Access-right flags for attributes 1-9 (packed bits, 0=no access, 1=read, 2=write, 3=read-write) */
    val attrAccessJson: String = "{}",

    /** Access-right flags for methods 1-5 */
    val methodAccessJson: String = "{}",

    val cachedAt: Long = System.currentTimeMillis()
)

// ─────────────────────────────────────────────────────────────────────────────
//  Helpers
// ─────────────────────────────────────────────────────────────────────────────

/** Convenience extension to deserialize CommSettings from the entity's JSON column. */
val DeviceEntity.commSettings: CommSettings
    get() = CommSettings.fromJson(commSettingsJson)

/** Convenience extension to get the transport type label string. */
val DeviceEntity.transportLabel: String
    get() = when (commSettings) {
        is CommSettings.Otg -> "OTG"
        is CommSettings.Ble -> "BLE"
        is CommSettings.Tcp -> "TCP"
    }
