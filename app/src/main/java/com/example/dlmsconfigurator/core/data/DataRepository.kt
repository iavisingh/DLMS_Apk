package com.example.dlmsconfigurator.core.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

interface DataRepository {
    val stagedFiles: Flow<List<StagedFile>>
    
    fun importFiles(fileNames: List<String>, fileContents: List<String>)
    fun removeStagedFile(id: String)
    fun clearStagedFiles()
    fun resetToDefaultTemplates()

    suspend fun startSession(fileName: String, detailedLogging: Boolean, overrideUsed: Boolean): Long
    suspend fun updateSessionEnd(sessionId: Long, status: String, meterSerial: String?)
    suspend fun logOperation(op: OperationEntity): Long
    suspend fun getOperations(sessionId: Long): List<OperationEntity>
    fun getSessionsFlow(): Flow<List<SessionEntity>>
    fun getOperationsFlow(sessionId: Long): Flow<List<OperationEntity>>

    suspend fun logAuthEvent(timestamp: Long, result: String, method: String): Long
    fun getAuthEventsFlow(): Flow<List<AuthEventEntity>>

    // Credentials
    fun storeMeterPassword(meterSerial: String, password: String)
    fun getMeterPassword(meterSerial: String): String?
    fun deleteMeterPassword(meterSerial: String)
    fun getStoredMeterSerials(): List<String>

    // Settings
    fun getDefaultBaudRate(): Int
    fun setDefaultBaudRate(baud: Int)
    fun getDefaultLoggingLevel(): Boolean
    fun setDefaultLoggingLevel(detailed: Boolean)
    fun getAppTheme(): String
    fun setAppTheme(theme: String)

    // ── Device management ────────────────────────────────────────────────────
    fun getDevicesFlow(): Flow<List<DeviceEntity>>
    suspend fun getDevice(id: Long): DeviceEntity?
    /**
     * Saves a device record. Secrets (password, authKey, encKey, systemTitle) should be
     * passed in the [secrets] map with keys: "password", "authKey", "encKey", "systemTitle".
     * They will be stored in SecureKeyStore and the aliases will be written into the entity.
     * Returns the new row id.
     */
    suspend fun addDevice(device: DeviceEntity, secrets: Map<String, String> = emptyMap()): Long
    suspend fun updateDevice(device: DeviceEntity, secrets: Map<String, String> = emptyMap())
    suspend fun deleteDevice(device: DeviceEntity)
    suspend fun touchDeviceConnected(id: Long, meterSerial: String? = null)
    suspend fun ensureDefaultDevices()

    // ── Association object cache ─────────────────────────────────────────────
    fun getAssociationObjectsFlow(deviceId: Long): Flow<List<AssociationObjectEntity>>
    suspend fun getAssociationObjects(deviceId: Long): List<AssociationObjectEntity>
    suspend fun saveAssociationObjects(deviceId: Long, objects: List<AssociationObjectEntity>)
    suspend fun clearAssociationObjects(deviceId: Long)
    suspend fun hasAssociationCache(deviceId: Long): Boolean

    // ── Device secret resolution ─────────────────────────────────────────────
    fun resolveDeviceSecret(alias: String?): String?
}

class DefaultDataRepository(private val context: Context) : DataRepository {
    private val secureKeyStore = SecureKeyStore(context)
    private val database = AppDatabase.getInstance(context, secureKeyStore)
    private val sessionDao = database.sessionDao()
    private val operationDao = database.operationDao()
    private val authEventDao = database.authEventDao()
    private val deviceDao = database.deviceDao()
    private val assocObjectDao = database.associationObjectDao()

    private val _stagedFiles = MutableStateFlow<List<StagedFile>>(emptyList())
    override val stagedFiles = _stagedFiles.asStateFlow()

    private val sharedPrefs: SharedPreferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    init {
        resetToDefaultTemplates()
    }

    override fun importFiles(fileNames: List<String>, fileContents: List<String>) {
        val currentList = _stagedFiles.value.toMutableList()
        fileNames.forEachIndexed { index, name ->
            val content = fileContents[index]
            val validationResult = JsonConfigValidator.validate(content)
            val staged = StagedFile(
                id = UUID.randomUUID().toString(),
                fileName = name,
                rawContent = content,
                isValid = validationResult.isSuccess,
                validationError = validationResult.exceptionOrNull()?.message,
                parsedContent = validationResult.getOrNull()
            )
            // Replace if filename matches or just add new
            currentList.removeAll { it.fileName == name }
            currentList.add(staged)
        }
        _stagedFiles.value = currentList
    }

    override fun removeStagedFile(id: String) {
        _stagedFiles.value = _stagedFiles.value.filter { it.id != id }
    }

    override fun clearStagedFiles() {
        _stagedFiles.value = emptyList()
    }

    override suspend fun startSession(fileName: String, detailedLogging: Boolean, overrideUsed: Boolean): Long = withContext(Dispatchers.IO) {
        val session = SessionEntity(
            startTime = System.currentTimeMillis(),
            jsonSourceFileName = fileName,
            detailedLogging = detailedLogging,
            status = "RUNNING",
            connectionOverrideUsed = overrideUsed
        )
        sessionDao.insert(session)
    }

    override suspend fun updateSessionEnd(sessionId: Long, status: String, meterSerial: String?) = withContext(Dispatchers.IO) {
        val session = sessionDao.getById(sessionId) ?: return@withContext
        val updated = session.copy(
            endTime = System.currentTimeMillis(),
            status = status,
            meterSerial = meterSerial ?: session.meterSerial
        )
        sessionDao.update(updated)
    }

    override suspend fun logOperation(op: OperationEntity): Long = withContext(Dispatchers.IO) {
        operationDao.insert(op)
    }

    override suspend fun getOperations(sessionId: Long): List<OperationEntity> = withContext(Dispatchers.IO) {
        operationDao.getOperationsForSession(sessionId)
    }

    override fun getSessionsFlow(): Flow<List<SessionEntity>> {
        return sessionDao.getAllSessionsFlow()
    }

    override fun getOperationsFlow(sessionId: Long): Flow<List<OperationEntity>> {
        return operationDao.getOperationsForSessionFlow(sessionId)
    }

    override suspend fun logAuthEvent(timestamp: Long, result: String, method: String): Long = withContext(Dispatchers.IO) {
        val event = AuthEventEntity(
            timestamp = timestamp,
            result = result,
            authMethod = method
        )
        authEventDao.insert(event)
    }

    override fun getAuthEventsFlow(): Flow<List<AuthEventEntity>> {
        return authEventDao.getAllAuthEventsFlow()
    }

    // Credentials delegation to Keystore
    override fun storeMeterPassword(meterSerial: String, password: String) {
        secureKeyStore.storeMeterPassword(meterSerial, password)
    }

    override fun getMeterPassword(meterSerial: String): String? {
        return secureKeyStore.getMeterPassword(meterSerial)
    }

    override fun deleteMeterPassword(meterSerial: String) {
        secureKeyStore.deleteMeterPassword(meterSerial)
    }

    override fun getStoredMeterSerials(): List<String> {
        return secureKeyStore.getAllStoredMeterSerials()
    }

    // Settings
    override fun getDefaultBaudRate(): Int {
        return sharedPrefs.getInt("default_baud_rate", 9600)
    }

    override fun setDefaultBaudRate(baud: Int) {
        sharedPrefs.edit().putInt("default_baud_rate", baud).apply()
    }

    override fun getDefaultLoggingLevel(): Boolean {
        return sharedPrefs.getBoolean("default_logging_level", false)
    }

    override fun setDefaultLoggingLevel(detailed: Boolean) {
        sharedPrefs.edit().putBoolean("default_logging_level", detailed).apply()
    }

    override fun getAppTheme(): String {
        return sharedPrefs.getString("app_theme", "SYSTEM") ?: "SYSTEM"
    }

    override fun setAppTheme(theme: String) {
        sharedPrefs.edit().putString("app_theme", theme).apply()
    }

    // ── Device management ────────────────────────────────────────────────────

    override fun getDevicesFlow(): Flow<List<DeviceEntity>> = deviceDao.getAllDevicesFlow()

    override suspend fun getDevice(id: Long): DeviceEntity? = withContext(Dispatchers.IO) {
        deviceDao.getById(id)
    }

    override suspend fun addDevice(device: DeviceEntity, secrets: Map<String, String>): Long = withContext(Dispatchers.IO) {
        val id = deviceDao.insert(device.copy(id = 0)) // let autoGenerate assign id
        storeSecrets(id, secrets, device)
        // Now update the entity to carry the key-refs
        val withRefs = buildDeviceWithRefs(deviceDao.getById(id)!!, id, secrets)
        deviceDao.update(withRefs)
        id
    }

    override suspend fun updateDevice(device: DeviceEntity, secrets: Map<String, String>) = withContext(Dispatchers.IO) {
        storeSecrets(device.id, secrets, device)
        val withRefs = buildDeviceWithRefs(device, device.id, secrets)
        deviceDao.update(withRefs)
    }

    override suspend fun deleteDevice(device: DeviceEntity) = withContext(Dispatchers.IO) {
        // Delete secrets from keystore
        listOf(device.passwordKeyRef, device.systemTitleKeyRef, device.authKeyRef, device.encKeyRef)
            .filterNotNull()
            .forEach { secureKeyStore.deleteDeviceSecret(it) }
        // Row + cascade-deletes association_objects
        deviceDao.delete(device)
    }

    override suspend fun touchDeviceConnected(id: Long, meterSerial: String?) = withContext(Dispatchers.IO) {
        deviceDao.touchLastConnected(id, System.currentTimeMillis())
        if (meterSerial != null) {
            deviceDao.updateMeterSerial(id, meterSerial)
        }
    }

    override suspend fun ensureDefaultDevices() = withContext(Dispatchers.IO) {
        if (deviceDao.getByName("Genus US") == null) {
            addDevice(
                DeviceEntity(
                    name = "Genus US",
                    commSettingsJson = CommSettings.toJson(CommSettings.Otg(baudRate = 9600)),
                    authenticationRole = "US",
                    logicalNameReferencing = true,
                    clientAddress = 48,
                    addressType = "Default",
                    logicalServer = 0,
                    physicalServer = 1,
                    serverAddress = 1,
                    securitySuite = "Suite0",
                    security = "authenticationencryption",
                    ciphering = true,
                    invocationCounterInitial = 0,
                    invocationCounterObis = "0.0.43.1.3.255",
                    useInvocationCounter = true,
                    retryCount = 3,
                    retryIntervalMs = 1000
                ),
                secrets = mapOf(
                    "password" to asciiToHex("AeMlHlSugaPl01ab"),
                    "systemTitle" to asciiToHex("GOE00000"),
                    "authKey" to asciiToHex("AeMlEkAkgaPl01ab"),
                    "encKey" to asciiToHex("AeMlEkAkgaPl01ab")
                )
            )
        }

        if (deviceDao.getByName("LnG") == null) {
            addDevice(
                DeviceEntity(
                    name = "LnG",
                    commSettingsJson = CommSettings.toJson(CommSettings.Otg(baudRate = 9600)),
                    authenticationRole = "US",
                    logicalNameReferencing = true,
                    clientAddress = 48,
                    addressType = "Default",
                    logicalServer = 0,
                    physicalServer = 1,
                    serverAddress = 1,
                    securitySuite = "Suite0",
                    security = "authenticationencryption",
                    ciphering = true,
                    invocationCounterInitial = 0,
                    invocationCounterObis = "0.0.43.1.3.255",
                    useInvocationCounter = true,
                    retryCount = 3,
                    retryIntervalMs = 5000
                ),
                secrets = mapOf(
                    "password" to cleanHex("0x000102030405060708090A0B0C0D0E0F"),
                    "systemTitle" to asciiToHex("ESYA0000"),
                    "authKey" to cleanHex("0x000102030405060708090A0B0C0D0E0F"),
                    "encKey" to cleanHex("0x000102030405060708090A0B0C0D0E0F")
                )
            )
        }
    }

    private fun storeSecrets(deviceId: Long, secrets: Map<String, String>, existing: DeviceEntity) {
        // Only write secrets that are non-blank in the incoming map
        secrets["password"]?.takeIf { it.isNotBlank() }
            ?.let { secureKeyStore.storeDeviceSecret("dev_${deviceId}_password", it) }
        secrets["authKey"]?.takeIf { it.isNotBlank() }
            ?.let { secureKeyStore.storeDeviceSecret("dev_${deviceId}_authKey", it) }
        secrets["encKey"]?.takeIf { it.isNotBlank() }
            ?.let { secureKeyStore.storeDeviceSecret("dev_${deviceId}_encKey", it) }
        secrets["systemTitle"]?.takeIf { it.isNotBlank() }
            ?.let { secureKeyStore.storeDeviceSecret("dev_${deviceId}_systemTitle", it) }
    }

    private fun buildDeviceWithRefs(device: DeviceEntity, deviceId: Long, secrets: Map<String, String>): DeviceEntity {
        return device.copy(
            passwordKeyRef = if (secrets["password"]?.isNotBlank() == true) "dev_${deviceId}_password" else device.passwordKeyRef,
            authKeyRef = if (secrets["authKey"]?.isNotBlank() == true) "dev_${deviceId}_authKey" else device.authKeyRef,
            encKeyRef = if (secrets["encKey"]?.isNotBlank() == true) "dev_${deviceId}_encKey" else device.encKeyRef,
            systemTitleKeyRef = if (secrets["systemTitle"]?.isNotBlank() == true) "dev_${deviceId}_systemTitle" else device.systemTitleKeyRef
        )
    }

    // ── Association object cache ─────────────────────────────────────────────

    override fun getAssociationObjectsFlow(deviceId: Long): Flow<List<AssociationObjectEntity>> =
        assocObjectDao.getByDeviceIdFlow(deviceId)

    override suspend fun getAssociationObjects(deviceId: Long): List<AssociationObjectEntity> = withContext(Dispatchers.IO) {
        assocObjectDao.getByDeviceId(deviceId)
    }

    override suspend fun saveAssociationObjects(deviceId: Long, objects: List<AssociationObjectEntity>) = withContext(Dispatchers.IO) {
        assocObjectDao.deleteByDeviceId(deviceId)
        assocObjectDao.insertAll(objects)
    }

    override suspend fun clearAssociationObjects(deviceId: Long) = withContext(Dispatchers.IO) {
        assocObjectDao.deleteByDeviceId(deviceId)
    }

    override suspend fun hasAssociationCache(deviceId: Long): Boolean = withContext(Dispatchers.IO) {
        assocObjectDao.countByDeviceId(deviceId) > 0
    }

    override fun resolveDeviceSecret(alias: String?): String? {
        if (alias.isNullOrBlank()) return null
        return secureKeyStore.getDeviceSecret(alias)
    }

    override fun resetToDefaultTemplates() {
        val defaultTemplates = listOf(
            createTemplate(
                name = "Default Secure Profile (Genus)",
                jsonContent = """
                    {
                      "connection": {
                        "name": "Genus",
                        "interface": "HDLC",
                        "authentication": "US",
                        "logical_name_referencing": 1,
                        "client_address": 48,
                        "server_address": 1,
                        "password": "41654D6C486C53756761506C30316162",
                        "security_suite": "Suite0",
                        "security": "AuthenticationEncryption",
                        "system_title": "4553594130303030",
                        "block_cipher_key": "41654D6C456B416B6761506C30316162",
                        "authentication_key": "41654D6C456B416B6761506C30316162",
                        "invocation_counter_ln": "0.0.43.1.3.255",
                        "use_invocation_counter": 0,
                        "ciphering": true
                      },
                      "operations": [
                        {
                          "name": "Meter Clock",
                          "type": "get",
                          "obis": "0.0.1.0.0.255",
                          "class_id": 8,
                          "attribute": 2,
                          "permission": "read",
                          "default_value": "2026-07-27T12:00:00"
                        },
                        {
                          "name": "firmware version",
                          "type": "get",
                          "obis": "1.0.0.2.0.255",
                          "class_id": 1,
                          "attribute": 2,
                          "permission": "read"
                        },
                        {
                          "name": "Meter Serial Number",
                          "type": "get",
                          "obis": "0.0.96.1.0.255",
                          "class_id": 1,
                          "attribute": 2,
                          "permission": "read"
                        },
                        {
                          "name": "Instantaneous Destination",
                          "type": "get",
                          "obis": "0.0.25.9.0.255",
                          "class_id": 40,
                          "attribute": 3,
                          "permission": "read-write"
                        },
                        {
                          "name": "Alert Destination",
                          "type": "get",
                          "obis": "0.4.25.9.0.255",
                          "class_id": 40,
                          "attribute": 3,
                          "permission": "read-write"
                        },
                        {
                          "name": "Push Action schedule",
                          "type": "get",
                          "obis": "0.0.15.0.4.255",
                          "class_id": 22,
                          "attribute": 4,
                          "permission": "read-write",
                          "default_value": "*-*-* *:30:00"
                        }
                      ]
                    }
                """.trimIndent()
            )
        )
        _stagedFiles.value = defaultTemplates
    }

    private fun createTemplate(name: String, jsonContent: String): StagedFile {
        val validation = JsonConfigValidator.validate(jsonContent)
        return StagedFile(
            id = UUID.randomUUID().toString(),
            fileName = name,
            rawContent = jsonContent,
            isValid = validation.isSuccess,
            validationError = validation.exceptionOrNull()?.message,
            parsedContent = validation.getOrNull()
        )
    }

    private fun asciiToHex(value: String): String =
        value.toByteArray(Charsets.US_ASCII).joinToString("") { "%02X".format(it) }

    private fun cleanHex(value: String): String =
        value.removePrefix("0x").removePrefix("0X").replace(" ", "").replace(":", "").uppercase()
}
