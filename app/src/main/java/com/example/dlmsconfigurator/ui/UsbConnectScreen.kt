package com.example.dlmsconfigurator

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dlmsconfigurator.core.data.DataRepository
import com.example.dlmsconfigurator.core.dlms.DlmsEngine
import com.example.dlmsconfigurator.core.transport.BleScanner
import com.example.dlmsconfigurator.core.transport.BleTransport
import com.example.dlmsconfigurator.core.transport.DlmsTransport
import com.example.dlmsconfigurator.core.transport.TcpTransport
import com.example.dlmsconfigurator.core.transport.UsbSerialTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    // Active Transport Mode: "usb", "ble", "tcp"
    var activeTransportMode by remember { mutableStateOf(params.transportType) }

    var status by remember { mutableStateOf("Ready to connect") }
    var isConnecting by remember { mutableStateOf(false) }
    var showPermissionButton by remember { mutableStateOf(false) }
    var showRetryButton by remember { mutableStateOf(false) }
    var isConnectedToDlms by remember { mutableStateOf(false) }

    // BLE scanner state
    val bleScanner = remember { BleScanner(context) }
    val isScanningBle by bleScanner.isScanning.collectAsState()
    val discoveredBleDevices by bleScanner.discoveredDevices.collectAsState()
    var selectedBleMac by remember { mutableStateOf(params.bleDeviceAddress ?: "") }

    // TCP settings (Supports IPv4 and IPv6)
    var simHost by remember { mutableStateOf(params.tcpHost ?: "10.92.170.72") }
    var simPort by remember { mutableStateOf((params.tcpPort ?: 4059).toString()) }

    val connection = currentFile.parsedContent?.connection ?: return
    val targetBaud = params.overrideBaud ?: connection.baudRate
    val usbTransport = remember { UsbSerialTransport(context, targetBaud) }

    // Permission launcher for BLE
    val blePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            status = "BLE permissions granted. Starting scan..."
            bleScanner.startScan()
        } else {
            status = "BLE permissions denied. Please grant Bluetooth permissions in settings."
            showPermissionButton = true
        }
    }

    fun requestBlePermissions() {
        blePermissionLauncher.launch(bleScanner.getRequiredPermissions())
    }

    fun requestUsbPermission() {
        try {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val intent = Intent(ACTION_USB_PERMISSION).apply {
                setPackage(context.packageName)
            }
            val permissionIntent = PendingIntent.getBroadcast(
                context, 0, intent, flags
            )
            usbTransport.requestPermission(permissionIntent)
            status = "Waiting for USB permission..."
        } catch (e: Exception) {
            status = "Error requesting USB permission: ${e.message}"
            showRetryButton = true
        }
    }

    fun startDlmsAssociation(activeTransport: DlmsTransport, transportLabel: String) {
        isConnecting = true
        status = "Connecting via $transportLabel..."
        showRetryButton = false
        showPermissionButton = false

        scope.launch {
            var attempt = 1
            val maxAttempts = 3
            var success = false
            var currentEngine: DlmsEngine? = null

            while (attempt <= maxAttempts && !success) {
                status = if (attempt > 1) "Retrying $transportLabel connection (Attempt $attempt)..." else "Connecting via $transportLabel..."

                try {
                    if (attempt > 1) {
                        withContext(Dispatchers.IO) {
                            currentEngine?.disconnect()
                            delay(200)
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
                        ciphering = params.overrideCiphering ?: connection.ciphering,
                        useInvocationCounter = if (params.overrideUseInvocationCounter != null) (if (params.overrideUseInvocationCounter) 1 else 0) else connection.useInvocationCounter
                    )

                    withContext(Dispatchers.IO) {
                        activeTransport.open()
                        val engine = DlmsEngine(activeConnection, activeTransport)
                        currentEngine = engine
                        engine.associate { step ->
                            scope.launch(Dispatchers.Main) {
                                status = step
                            }
                        }

                        DlmsSessionHolder.activeTransport = activeTransport
                        DlmsSessionHolder.activeEngine = engine
                        DlmsSessionHolder.activeConnection = activeConnection
                    }

                    success = true
                } catch (e: Exception) {
                    withContext(Dispatchers.IO) {
                        try { currentEngine?.disconnect() } catch (ignored: Exception) {}
                    }
                    attempt++
                    if (attempt > maxAttempts) {
                        status = "$transportLabel connection failed: ${e.message}"
                        showRetryButton = true
                        isConnecting = false
                    } else {
                        delay(1000)
                    }
                }
            }

            if (success) {
                status = "DLMS connected via $transportLabel!"
                isConnectedToDlms = true
                isConnecting = false

                delay(1200)

                val overrideUsed = params.overrideBaud != null || params.overrideClient != null ||
                        params.overrideServer != null || params.overrideSecurity != null ||
                        params.overridePassword != null || params.overrideSystemTitle != null ||
                        params.overrideAuthKey != null || params.overrideEncKey != null ||
                        params.overrideCounterObis != null || params.overrideCiphering != null ||
                        params.overrideUseInvocationCounter != null

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

        when (activeTransportMode) {
            "ble" -> {
                if (selectedBleMac.isBlank()) {
                    status = "Please select or enter a Bluetooth device MAC address"
                    showRetryButton = true
                    return
                }
                if (!bleScanner.hasPermissions()) {
                    status = "Bluetooth permissions required."
                    showPermissionButton = true
                    return
                }
                val bleTransport = BleTransport(context, selectedBleMac)
                startDlmsAssociation(bleTransport, "Bluetooth BLE ($selectedBleMac)")
            }
            "tcp" -> {
                val host = simHost.ifBlank { "10.92.170.72" }
                val port = simPort.toIntOrNull() ?: 4059
                val tcpTransport = TcpTransport(host, port)
                startDlmsAssociation(tcpTransport, "TCP IPv4/v6 ($host:$port)")
            }
            else -> {
                // USB OTG Optical Probe
                val device = usbTransport.getDevice()
                if (device == null) {
                    status = "No USB OTG optical probe detected. Ensure OTG is turned ON in Phone Settings."
                    showRetryButton = true
                    return
                }

                if (usbTransport.hasPermission()) {
                    startDlmsAssociation(usbTransport, "USB OTG Optical Probe")
                } else {
                    status = "USB OTG permission required."
                    showPermissionButton = true
                }
            }
        }
    }

    // Auto-start scan when switching to BLE
    LaunchedEffect(activeTransportMode) {
        if (activeTransportMode == "ble") {
            if (bleScanner.hasPermissions()) {
                bleScanner.startScan()
            } else {
                requestBlePermissions()
            }
        } else {
            bleScanner.stopScan()
        }
    }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (ACTION_USB_PERMISSION == intent.action) {
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    if (granted) {
                        startDlmsAssociation(usbTransport, "USB OTG Optical Probe")
                    } else {
                        status = "USB OTG permission denied."
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
            bleScanner.stopScan()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DLMS Connection", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F0F1A))
            )
        },
        containerColor = Color(0xFF0F0F1A)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF121222), Color(0xFF0A0A10))
                    )
                )
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Interactive 3-Option Connection Selector Bar (OTG / Bluetooth / TCP IPv4/v6)
            Text(
                text = "SELECT CONNECTION METHOD",
                color = Color(0xFF007AFF),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TransportSelectorChip(
                    title = "OTG",
                    subtitle = "USB Probe",
                    icon = Icons.Default.Usb,
                    isSelected = activeTransportMode == "usb",
                    onClick = { activeTransportMode = "usb" },
                    modifier = Modifier.weight(1f)
                )

                TransportSelectorChip(
                    title = "Bluetooth",
                    subtitle = "BLE Probe",
                    icon = Icons.Default.Bluetooth,
                    isSelected = activeTransportMode == "ble",
                    onClick = { activeTransportMode = "ble" },
                    modifier = Modifier.weight(1f)
                )

                TransportSelectorChip(
                    title = "TCP IPv4/v6",
                    subtitle = "Network/Sim",
                    icon = Icons.Default.Language,
                    isSelected = activeTransportMode == "tcp",
                    onClick = { activeTransportMode = "tcp" },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Transport Status & Dynamic Controls Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2F)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (isConnecting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            color = Color(0xFF007AFF),
                            strokeWidth = 4.dp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    } else {
                        Icon(
                            imageVector = when (activeTransportMode) {
                                "ble" -> Icons.Default.Bluetooth
                                "tcp" -> Icons.Default.Language
                                else -> Icons.Default.Usb
                            },
                            contentDescription = null,
                            tint = Color(0xFF007AFF),
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    Text(
                        text = status,
                        color = if (isConnectedToDlms) Color(0xFF34C759) else Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    // Mode Specific Interactive UI Controls
                    when (activeTransportMode) {
                        "ble" -> {
                            if (!isConnectedToDlms) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = if (isScanningBle) Icons.AutoMirrored.Filled.BluetoothSearching else Icons.Default.Bluetooth,
                                            contentDescription = null,
                                            tint = Color(0xFF007AFF),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (isScanningBle) "Scanning BLE meters..." else "Nearby BLE Devices",
                                            color = Color.White,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp
                                        )
                                    }

                                    Button(
                                        onClick = {
                                            if (isScanningBle) bleScanner.stopScan() else {
                                                if (bleScanner.hasPermissions()) bleScanner.startScan() else requestBlePermissions()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E2E42)),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(if (isScanningBle) "Stop" else "Scan", fontSize = 11.sp)
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                OutlinedTextField(
                                    value = selectedBleMac,
                                    onValueChange = { selectedBleMac = it },
                                    label = { Text("Bluetooth Device MAC Address") },
                                    placeholder = { Text("e.g. AA:BB:CC:DD:EE:FF", color = Color.DarkGray) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = TextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedContainerColor = Color(0xFF161625),
                                        unfocusedContainerColor = Color(0xFF161625),
                                        focusedLabelColor = Color(0xFF007AFF)
                                    )
                                )

                                if (discoveredBleDevices.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text("Tap device to select:", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.fillMaxWidth())
                                    Spacer(modifier = Modifier.height(4.dp))

                                    LazyColumn(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(130.dp)
                                    ) {
                                        items(discoveredBleDevices) { dev ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(if (selectedBleMac.equals(dev.address, ignoreCase = true)) Color(0xFF007AFF).copy(alpha = 0.25f) else Color(0xFF161625))
                                                    .clickable { selectedBleMac = dev.address }
                                                    .padding(8.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column {
                                                    Text(dev.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                                    Text(dev.address, color = Color.Gray, fontSize = 10.sp)
                                                }
                                                Text("${dev.rssi} dBm", color = Color(0xFF007AFF), fontSize = 11.sp)
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                        }
                                    }
                                }
                            }
                        }
                        "tcp" -> {
                            if (!isConnectedToDlms) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("TCP IPv4 / IPv6 Configuration", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.fillMaxWidth())
                                Spacer(modifier = Modifier.height(10.dp))

                                OutlinedTextField(
                                    value = simHost,
                                    onValueChange = { simHost = it },
                                    label = { Text("IP Address (IPv4 / IPv6)") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = TextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedContainerColor = Color(0xFF161625),
                                        unfocusedContainerColor = Color(0xFF161625),
                                        focusedLabelColor = Color(0xFF007AFF)
                                    )
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = simPort,
                                    onValueChange = { simPort = it },
                                    label = { Text("Port Number") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = TextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedContainerColor = Color(0xFF161625),
                                        unfocusedContainerColor = Color(0xFF161625),
                                        focusedLabelColor = Color(0xFF007AFF)
                                    )
                                )
                            }
                        }
                        else -> {
                            // OTG USB Probe
                            if (!isConnectedToDlms) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Connect USB Optical Probe via OTG adapter to phone",
                                    color = Color.Gray,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (showPermissionButton) {
                Button(
                    onClick = {
                        if (activeTransportMode == "ble") requestBlePermissions() else requestUsbPermission()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text(if (activeTransportMode == "ble") "Grant Bluetooth Permissions" else "Grant USB OTG Permission")
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            Button(
                onClick = { checkAndConnect() },
                enabled = !isConnecting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isConnectedToDlms) Color(0xFFFF3B30) else Color(0xFF007AFF)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                if (isConnectedToDlms) {
                    Text("Disconnect", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when (activeTransportMode) {
                            "ble" -> "Connect Bluetooth (BLE)"
                            "tcp" -> "Connect TCP (IPv4/v6)"
                            else -> "Connect OTG (USB Probe)"
                        },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun TransportSelectorChip(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) Color(0xFF1E293B) else Color(0xFF1E1E2F))
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) Color(0xFF007AFF) else Color(0xFF2E2E42),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (isSelected) Color(0xFF007AFF) else Color.Gray,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
            Text(
                text = subtitle,
                color = if (isSelected) Color(0xFF007AFF) else Color.Gray,
                fontSize = 9.sp
            )
        }
    }
}

object DlmsSessionHolder {
    var activeTransport: DlmsTransport? = null
    var activeEngine: DlmsEngine? = null
    var activeConnection: com.example.dlmsconfigurator.core.data.ConnectionParams? = null
}
