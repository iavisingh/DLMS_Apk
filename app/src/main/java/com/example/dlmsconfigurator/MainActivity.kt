package com.example.dlmsconfigurator

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.example.dlmsconfigurator.core.biometric.BiometricAuthManager
import com.example.dlmsconfigurator.core.data.DefaultDataRepository
import com.example.dlmsconfigurator.theme.DLMSConfiguratorTheme
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    private val repository by lazy { DefaultDataRepository(applicationContext) }
    private var isLocked by mutableStateOf(true)
    private var hasSecurityLock by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            DLMSConfiguratorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (isLocked) {
                        LockScreen(
                            hasSecurityLock = hasSecurityLock,
                            onAuthenticate = { triggerBiometricAuth() },
                            onCheckSecurity = { checkSecurity() }
                        )
                    } else {
                        MainNavigation(repository)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkSecurity()
        if (hasSecurityLock) {
            isLocked = true
            triggerBiometricAuth()
        } else {
            isLocked = true // Keep locked to show blocker
        }
    }

    private fun checkSecurity() {
        hasSecurityLock = BiometricAuthManager.isSecurityLockEnabled(this)
    }

    private fun triggerBiometricAuth() {
        if (!hasSecurityLock) return
        BiometricAuthManager.showBiometricPrompt(
            activity = this,
            onSuccess = {
                isLocked = false
                lifecycleScope.launch {
                    repository.logAuthEvent(
                        timestamp = System.currentTimeMillis(),
                        result = "SUCCESS",
                        method = "BIOMETRIC_OR_CREDENTIAL"
                    )
                }
            },
            onFailure = { error ->
                lifecycleScope.launch {
                    repository.logAuthEvent(
                        timestamp = System.currentTimeMillis(),
                        result = if (error == "Authentication failed") "FAILED" else "CANCELLED",
                        method = "BIOMETRIC_OR_CREDENTIAL"
                    )
                }
            }
        )
    }
}
