package com.example.dlmsconfigurator

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dlmsconfigurator.core.data.DataRepository
import com.example.dlmsconfigurator.core.data.DefaultDataRepository
import com.example.dlmsconfigurator.core.dlms.DlmsEngine
import com.example.dlmsconfigurator.core.transport.TcpTransport
import com.example.dlmsconfigurator.core.transport.UsbSerialTransport
import com.example.dlmsconfigurator.core.data.ConnectionParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

private const val ACTION_USB_PERMISSION = "com.example.dlmsconfigurator.USB_PERMISSION"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsbConnectScreen(
    params: UsbConnect,
    onConnected: (Long) -> Unit,
    onBack: () -> Unit,
    repository: DataRepository
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val stagedFiles by repository.stagedFiles.collectAsState(initial = emptyList())
    val currentFile = stagedFiles.find { it.id == params.stagedFileId } ?: return

    var status by remember { mutableStateOf("Ready to connect") }
    var isConnecting by remember { mutableStateOf(false) }
    var showPermissionButton by remember { mutableStateOf(false) }
    var showRetryButton by remember { mutableStateOf(false) }
    var isConnectedToDlms by remember { mutableStateOf(false) }
    
    // Simulator settings
    var showSimulatorConfig by remember { mutableStateOf(false) }
    var simHost by remember { mutableStateOf("10.92.170.72") }
    var simPort by remember { mutableStateOf("4059") }

    val connection = currentFile.parsedContent?.connection ?: return
    val targetBaud = params.overrideBaud ?: connection.baudRate
    val transport = remember { UsbSerialTransport(context, targetBaud) }

    fun requestUsbPermission() {
        try {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val intent = PendingIntent.getBroadcast(
                context, 0, Intent(ACTION_USB_PERMISSION), flags
            )
            transport.requestPermission(intent)
            status = "Waiting for USB permission..."
        } catch (e: Exception) {
            status = "Error requesting permission: ${e.message}"
            showRetryButton = true
        }
    }

    fun startDlmsAssociation(isSimulator: Boolean) {
        isConnecting = true
        status = "Connecting..."
        showRetryButton = false
        showPermissionButton = false

        scope.launch {
            var attempt = 1
            val maxAttempts = 2
            var success = false
            
            while (attempt <= maxAttempts && !success) {
                status = if (attempt > 1) "Retrying connection (Attempt $attempt)..." else "Connecting..."
                
                try {
                    // Increased timeout for multi-session association (Public Client pre-read + US association)
                    withTimeout(45000) {
                        val activeTransport = if (isSimulator) {
                            TcpTransport(simHost, simPort.toIntOrNull() ?: 4059)
                        } else {
                            transport
                        }
                        
                        // Force a reset of the transport/meter state for retries
                        if (attempt > 1) {
                            withContext(Dispatchers.IO) {
                                activeTransport.close()
                                delay(500)
                            }
                        }

                        val activeConnection = connection.copy(
                            baudRate = targetBaud,
                            clientAddress = params.overrideClient ?: connection.clientAddress,
                            serverAddress = params.overrideServer ?: connection.serverAddress,
                            security = params.overrideSecurity ?: connection.security,
                            password = params.overridePassword ?: connection.password,
                            systemTitle = params.overrideSystemTitle ?: connection.systemTitle,
                            authenticationKey = params.overrideAuthKey ?: connection.authenticationKey,
                            encryptionKey = params.overrideEncKey ?: connection.encryptionKey,
                            blockCipherKey = params.overrideEncKey ?: connection.blockCipherKey,
                            invocationCounterObis = params.overrideCounterObis ?: connection.invocationCounterObis,
                            ciphering = params.overrideCiphering ?: connection.ciphering
                        )

                        withContext(Dispatchers.IO) {
                            activeTransport.open()
                            val engine = DlmsEngine(activeConnection, activeTransport)
                            engine.associate()
                            
                            DlmsSessionHolder.activeTransport = activeTransport
                            DlmsSessionHolder.activeEngine = engine
                            DlmsSessionHolder.activeConnection = activeConnection
                        }
                        
                        success = true
                    }
                } catch (e: Exception) {
                    attempt++
                    if (attempt > maxAttempts) {
                        status = "Connection failed after $maxAttempts attempts: ${e.message}"
                        showRetryButton = true
                        isConnecting = false
                    } else {
                        delay(1000) // Small delay before retry
                    }
                }
            }

            if (success) {
                status = "DLMS connect!!!"
                isConnectedToDlms = true
                isConnecting = false
                
                // Small delay to show success message before moving to next screen
                delay(1500)
                
                val overrideUsed = params.overrideBaud != null || params.overrideClient != null || 
                                 params.overrideServer != null || params.overrideSecurity != null || 
                                 params.overridePassword != null || params.overrideSystemTitle != null || 
                                 params.overrideAuthKey != null || params.overrideEncKey != null || 
                                 params.overrideCounterObis != null || params.overrideCiphering != null
                
                val detailedLogging = params.overrideDetailed ?: repository.getDefaultLoggingLevel()

                val sessionId = withContext(Dispatchers.IO) {
                    repository.startSession(
                        fileName = currentFile.fileName,
                        detailedLogging = detailedLogging,
                        overrideUsed = overrideUsed
                    )
                }
                
                onConnected(sessionId)
            }
        }
    }

    fun disconnectDlms() {
        scope.launch(Dispatchers.IO) {
            DlmsSessionHolder.activeEngine?.disconnect()
            DlmsSessionHolder.activeEngine = null
            DlmsSessionHolder.activeTransport = null
            withContext(Dispatchers.Main) {
                isConnectedToDlms = false
                status = "Disconnected"
            }
        }
    }

    fun checkAndConnect() {
        if (isConnectedToDlms) {
            disconnectDlms()
            return
        }
        
        showRetryButton = false
        showPermissionButton = false
        status = "Checking hardware..."

        val device = transport.getDevice()
        if (device == null) {
            status = "No USB optical probe detected."
            showRetryButton = true
            showSimulatorConfig = true
            return
        }

        if (transport.hasPermission()) {
            startDlmsAssociation(isSimulator = false)
        } else {
            status = "USB permission required."
            showPermissionButton = true
        }
    }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (ACTION_USB_PERMISSION == intent.action) {
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    if (granted) {
                        startDlmsAssociation(isSimulator = false)
                    } else {
                        status = "USB permission denied."
                        showRetryButton = true
                    }
                }
            }
        }
        
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }

        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Connect to Meter", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F0F1A))
            )
        },
        containerColor = Color(0xFF0F0F1A)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF121222), Color(0xFF0A0A10))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (isConnecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(56.dp),
                        color = Color(0xFF007AFF),
                        strokeWidth = 4.dp
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }

                Text(
                    text = status,
                    color = if (isConnectedToDlms) Color(0xFF34C759) else Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(32.dp))

                if (showPermissionButton) {
                    Button(
                        onClick = { requestUsbPermission() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text("Grant USB Permission")
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                Button(
                    onClick = { checkAndConnect() },
                    enabled = !isConnecting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isConnectedToDlms) Color(0xFFFF3B30) else Color(0xFF007AFF)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    if (isConnectedToDlms) {
                        Text("Disconnect")
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Connect")
                    }
                }

                if (showSimulatorConfig && !isConnectedToDlms) {
                    Spacer(modifier = Modifier.height(40.dp))
                    Text(
                        text = "Connect to TCP Simulator:",
                        color = Color.LightGray,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = simHost,
                        onValueChange = { simHost = it },
                        label = { Text("IP Address (v4 or v6)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color(0xFF1E1E2F),
                            unfocusedContainerColor = Color(0xFF1E1E2F),
                            focusedLabelColor = Color(0xFF007AFF)
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = simPort,
                        onValueChange = { simPort = it },
                        label = { Text("Port") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color(0xFF1E1E2F),
                            unfocusedContainerColor = Color(0xFF1E1E2F),
                            focusedLabelColor = Color(0xFF007AFF)
                        )
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = { startDlmsAssociation(isSimulator = true) },
                        enabled = !isConnecting,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34C759)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text("Connect to Simulator (TCP)")
                    }
                }
            }
        }
    }
}

object DlmsSessionHolder {
    var activeTransport: com.example.dlmsconfigurator.core.transport.DlmsTransport? = null
    var activeEngine: com.example.dlmsconfigurator.core.dlms.DlmsEngine? = null
    var activeConnection: com.example.dlmsconfigurator.core.data.ConnectionParams? = null
}
