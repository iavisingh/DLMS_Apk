package com.example.dlmsconfigurator

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Splash : NavKey
@Serializable data object Blocker : NavKey
@Serializable data object Home : NavKey

@Serializable data class SessionSetup(
    val stagedFileId: String
) : NavKey

@Serializable data class UsbConnect(
    val stagedFileId: String,
    val overrideBaud: Int? = null,
    val overrideClient: Int? = null,
    val overrideServer: Int? = null,
    val overrideSecurity: String? = null,
    val overridePassword: String? = null,
    val overrideDetailed: Boolean? = null,
    val overrideSystemTitle: String? = null,
    val overrideAuthKey: String? = null,
    val overrideEncKey: String? = null,
    val overrideCounterObis: String? = null,
    val overrideCiphering: Boolean? = null
) : NavKey

@Serializable data class Execution(
    val sessionId: Long,
    val stagedFileId: String
) : NavKey

@Serializable data class ResultSummary(
    val sessionId: Long
) : NavKey

@Serializable data class SessionDetail(
    val sessionId: Long
) : NavKey

@Serializable data class OperationDetail(
    val operationId: Long
) : NavKey
