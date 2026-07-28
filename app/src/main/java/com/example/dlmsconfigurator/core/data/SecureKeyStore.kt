package com.example.dlmsconfigurator.core.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom

class SecureKeyStore(context: Context) {
    private val masterKeySpec = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPrefs = EncryptedSharedPreferences.create(
        context,
        "secure_prefs",
        masterKeySpec,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun getDatabasePassphrase(): String {
        var passphrase = sharedPrefs.getString("db_passphrase", null)
        if (passphrase == null) {
            val randomBytes = ByteArray(32)
            SecureRandom().nextBytes(randomBytes)
            passphrase = bytesToHex(randomBytes)
            sharedPrefs.edit().putString("db_passphrase", passphrase).apply()
        }
        return passphrase
    }

    fun storeMeterPassword(meterSerial: String, password: String) {
        sharedPrefs.edit().putString("pwd_$meterSerial", password).apply()
    }

    fun getMeterPassword(meterSerial: String): String? {
        return sharedPrefs.getString("pwd_$meterSerial", null)
    }

    fun deleteMeterPassword(meterSerial: String) {
        sharedPrefs.edit().remove("pwd_$meterSerial").apply()
    }

    fun getAllStoredMeterSerials(): List<String> {
        return sharedPrefs.all.keys
            .filter { it.startsWith("pwd_") }
            .map { it.removePrefix("pwd_") }
    }

    private fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
