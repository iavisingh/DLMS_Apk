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
}

class DefaultDataRepository(private val context: Context) : DataRepository {
    private val secureKeyStore = SecureKeyStore(context)
    private val database = AppDatabase.getInstance(context, secureKeyStore)
    private val sessionDao = database.sessionDao()
    private val operationDao = database.operationDao()
    private val authEventDao = database.authEventDao()

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

    override fun resetToDefaultTemplates() {
        val defaultTemplates = listOf(
            createTemplate(
                name = "Default Secure Profile (LnG)",
                jsonContent = """
                    {
                      "connection": {
                        "name": "LnG",
                        "interface": "HDLC",
                        "authentication": "US",
                        "logical_name_referencing": 1,
                        "client_address": 48,
                        "server_address": 1,
                        "password": "000102030405060708090A0B0C0D0E0F",
                        "security_suite": "Suite0",
                        "security": "AuthenticationEncryption",
                        "system_title": "4553594130303030",
                        "block_cipher_key": "000102030405060708090A0B0C0D0E0F",
                        "authentication_key": "000102030405060708090A0B0C0D0E0F",
                        "invocation_counter_ln": "0.0.43.1.3.255",
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
                          "name": "Manufacturer Specific",
                          "type": "get",
                          "obis": "0.0.96.128.8.255",
                          "class_id": 1,
                          "attribute": 2,
                          "permission": "read"
                        },
                        {
                          "name": "Device ID",
                          "type": "get",
                          "obis": "0.0.96.1.2.255",
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
                          "name": "Billing Date schedule",
                          "type": "get",
                          "obis": "0.0.15.0.0.255",
                          "class_id": 22,
                          "attribute": 4,
                          "permission": "read-write",
                          "default_value": "01-*-* 00:00:*"
                        }
                      ]
                    }
                """.trimIndent()
            ),
            createTemplate(
                name = "Read Meter Clock (Public Client)",
                jsonContent = """
                    {
                      "connection": {
                        "baud_rate": 9600,
                        "client_address": 16,
                        "server_address": 1,
                        "security": "none",
                        "interface": "HDLC"
                      },
                      "default_retry": {
                        "max_attempts": 3,
                        "delay_ms": 2000
                      },
                      "operations": [
                        {
                          "type": "get",
                          "obis": "0.0.1.0.0.255",
                          "class_id": 8,
                          "attribute": 2
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
}
