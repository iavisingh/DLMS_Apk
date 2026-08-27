package com.example.dlmsconfigurator

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.navigation3.scene.Scene
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.dlmsconfigurator.core.data.DataRepository
import com.example.dlmsconfigurator.ui.DeviceSessionViewModel

@Composable
fun MainNavigation(repository: DataRepository, onThemeChange: (String) -> Unit = {}) {
    val backStack = rememberNavBackStack(DeviceList)
    val deviceSessionViewModel: DeviceSessionViewModel = viewModel()

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        transitionSpec = sharedAxisForward(),
        popTransitionSpec = sharedAxisBackward(),
        predictivePopTransitionSpec = { sharedAxisBackward<androidx.navigation3.runtime.NavKey>().invoke(this) },
        entryProvider = entryProvider {
            entry<Home> {
                DashboardScreen(
                    repository = repository,
                    onExecute = { stagedFileId ->
                        backStack.add(SessionSetup(stagedFileId = stagedFileId))
                    },
                    onViewHistoryDetail = { sessionId ->
                        backStack.add(SessionDetail(sessionId = sessionId))
                    },
                    onOpenDevices = {
                        backStack.add(DeviceList)
                    },
                    onThemeChange = onThemeChange
                )
            }
            entry<SessionSetup> { key ->
                SessionSetupScreen(
                    stagedFileId = key.stagedFileId,
                    repository = repository,
                    onConfirm = { transportType, bleAddr, tcpH, tcpP, baud, client, server, sec, pwd, logging, ciphering, systemTitle, authKey, encKey, counterObis, useInvocationCounter ->
                        backStack.add(
                            UsbConnect(
                                stagedFileId = key.stagedFileId,
                                transportType = transportType,
                                bleDeviceAddress = bleAddr,
                                tcpHost = tcpH,
                                tcpPort = tcpP,
                                overrideBaud = baud,
                                overrideClient = client,
                                overrideServer = server,
                                overrideSecurity = sec,
                                overridePassword = pwd,
                                overrideDetailed = logging,
                                overrideSystemTitle = systemTitle,
                                overrideAuthKey = authKey,
                                overrideEncKey = encKey,
                                overrideCounterObis = counterObis,
                                overrideCiphering = ciphering,
                                overrideUseInvocationCounter = useInvocationCounter
                            )
                        )
                    },
                    onCancel = {
                        backStack.removeLastOrNull()
                    }
                )
            }
            entry<UsbConnect> { key ->
                UsbConnectScreen(
                    params = key,
                    repository = repository,
                    onConnected = { sessionId ->
                        backStack.add(Execution(sessionId = sessionId, stagedFileId = key.stagedFileId))
                    },
                    onBack = {
                        backStack.removeLastOrNull()
                    }
                )
            }
            entry<Execution> { key ->
                ExecutionScreen(
                    sessionId = key.sessionId,
                    stagedFileId = key.stagedFileId,
                    repository = repository,
                    onFinished = {
                        backStack.add(ResultSummary(sessionId = key.sessionId))
                    },
                    onAborted = {
                        backStack.removeLastOrNull()
                    }
                )
            }
            entry<ResultSummary> { key ->
                ResultSummaryScreen(
                    sessionId = key.sessionId,
                    repository = repository,
                    onViewDetails = {
                        backStack.add(SessionDetail(sessionId = key.sessionId))
                    },
                    onDone = {
                        // Return to Home by clearing backstack
                        backStack.removeLastOrNull() // Remove ResultSummary
                        backStack.removeLastOrNull() // Remove Execution
                        backStack.removeLastOrNull() // Remove UsbConnect
                        backStack.removeLastOrNull() // Remove SessionSetup (if we came from it)
                    }
                )
            }
            entry<SessionDetail> { key ->
                SessionDetailScreen(
                    sessionId = key.sessionId,
                    repository = repository,
                    onOperationSelected = { opId ->
                        backStack.add(OperationDetail(operationId = opId))
                    },
                    onBack = {
                        backStack.removeLastOrNull()
                    }
                )
            }
            entry<OperationDetail> { key ->
                OperationDetailScreen(
                    operationId = key.operationId,
                    repository = repository,
                    onBack = {
                        backStack.removeLastOrNull()
                    }
                )
            }

            // ── Device-centric flow ───────────────────────────────────────────

            entry<DeviceList> {
                DeviceListScreen(
                    repository = repository,
                    onAddDevice = { backStack.add(AddEditDevice(deviceId = null)) },
                    onEditDevice = { id -> backStack.add(AddEditDevice(deviceId = id)) },
                    onConnectDevice = { id -> backStack.add(DeviceSession(deviceId = id)) },
                    onDisconnectDevice = { id -> deviceSessionViewModel.disconnectDevice(id) },
                    onBack = { backStack.removeLastOrNull() }
                )
            }

            entry<AddEditDevice> { key ->
                AddEditDeviceScreen(
                    deviceId = key.deviceId,
                    repository = repository,
                    onSaved = { backStack.removeLastOrNull() },
                    onBack = { backStack.removeLastOrNull() }
                )
            }

            entry<DeviceSession> { key ->
                DeviceSessionScreen(
                    deviceId = key.deviceId,
                    repository = repository,
                    onObjectSelected = { obisCode, classId ->
                        backStack.add(ObjectDetail(deviceId = key.deviceId, obisCode = obisCode, classId = classId))
                    },
                    onBack = { backStack.removeLastOrNull() },
                    viewModel = deviceSessionViewModel
                )
            }

            entry<ObjectDetail> { key ->
                ObjectDetailScreen(
                    deviceId = key.deviceId,
                    obisCode = key.obisCode,
                    classId = key.classId,
                    repository = repository,
                    onBack = { backStack.removeLastOrNull() },
                    sessionViewModel = deviceSessionViewModel
                )
            }
        }
    )
}

private const val SharedAxisDurationMs = 150

private fun <T : Any> sharedAxisForward(): AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform = {
    val easing = FastOutSlowInEasing
    (fadeIn(tween(SharedAxisDurationMs, easing = easing)) +
        slideInHorizontally(tween(SharedAxisDurationMs, easing = easing)) { width -> width / 8 })
        .togetherWith(
            fadeOut(tween(SharedAxisDurationMs, easing = easing)) +
                slideOutHorizontally(tween(SharedAxisDurationMs, easing = easing)) { width -> -width / 10 }
        )
}

private fun <T : Any> sharedAxisBackward(): AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform = {
    val easing = FastOutSlowInEasing
    (fadeIn(tween(SharedAxisDurationMs, easing = easing)) +
        slideInHorizontally(tween(SharedAxisDurationMs, easing = easing)) { width -> -width / 8 })
        .togetherWith(
            fadeOut(tween(SharedAxisDurationMs, easing = easing)) +
                slideOutHorizontally(tween(SharedAxisDurationMs, easing = easing)) { width -> width / 10 }
        )
}
