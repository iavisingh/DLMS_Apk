package com.example.dlmsconfigurator

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dlmsconfigurator.core.data.CommSettings
import com.example.dlmsconfigurator.core.data.DataRepository
import com.example.dlmsconfigurator.core.data.DeviceEntity
import kotlinx.coroutines.launch
import java.net.InetAddress
import java.net.Inet4Address
import java.net.Inet6Address

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditDeviceScreen(
    deviceId: Long?,
    repository: DataRepository,
    onSaved: () -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var existingDevice by remember { mutableStateOf<DeviceEntity?>(null) }

    // ── Form state ────────────────────────────────────────────────────────────
    var deviceName by remember { mutableStateOf("") }
    var transportTab by remember { mutableIntStateOf(0) } // 0=OTG, 1=BLE, 2=TCP

    // OTG
    var baudRate by remember { mutableStateOf("9600") }

    // BLE
    var bleAddress by remember { mutableStateOf("") }
    var bleName by remember { mutableStateOf("") }

    // TCP
    var tcpHost by remember { mutableStateOf("") }
    var tcpPort by remember { mutableStateOf("4059") }

    // DLMS params
    var authenticationRole by remember { mutableStateOf("PC") }
    var clientAddress by remember { mutableStateOf("16") }
    var serverAddress by remember { mutableStateOf("1") }
    var addressType by remember { mutableStateOf("Default") }
    var logicalNameReferencing by remember { mutableStateOf(true) }
    var logicalServer by remember { mutableStateOf("0") }
    var physicalServer by remember { mutableStateOf("1") }
    var security by remember { mutableStateOf("None") }
    var securitySuite by remember { mutableStateOf("Suite0") }
    var interfaceType by remember { mutableStateOf("HDLC") }
    var password by remember { mutableStateOf("") }
    var passwordAscii by remember { mutableStateOf(true) }
    var systemTitle by remember { mutableStateOf("") }
    var systemTitleAscii by remember { mutableStateOf(true) }
    var authKey by remember { mutableStateOf("") }
    var authKeyAscii by remember { mutableStateOf(true) }
    var encKey by remember { mutableStateOf("") }
    var encKeyAscii by remember { mutableStateOf(true) }
    var ciphering by remember { mutableStateOf(false) }
    var invCounterObis by remember { mutableStateOf("0.0.43.1.0.255") }
    var invocationCounterInitial by remember { mutableStateOf("0") }
    var useInvCounter by remember { mutableStateOf(false) }
    var retryCount by remember { mutableStateOf("3") }
    var retryIntervalMs by remember { mutableStateOf("1000") }

    var isSaving by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    // Load existing device if editing
    LaunchedEffect(deviceId) {
        if (deviceId != null) {
            val d = repository.getDevice(deviceId)
            if (d != null) {
                existingDevice = d
                deviceName = d.name
                val comm = try { CommSettings.fromJson(d.commSettingsJson) } catch (_: Exception) { CommSettings.Otg() }
                transportTab = when (comm) { is CommSettings.Otg -> 0; is CommSettings.Ble -> 1; is CommSettings.Tcp -> 2 }
                when (comm) {
                    is CommSettings.Otg -> baudRate = comm.baudRate.toString()
                    is CommSettings.Ble -> { bleAddress = comm.deviceAddress; bleName = comm.deviceName }
                    is CommSettings.Tcp -> { tcpHost = comm.host; tcpPort = comm.port.toString() }
                }
                authenticationRole = d.authenticationRole
                clientAddress = d.clientAddress.toString()
                addressType = d.addressType
                serverAddress = if (d.addressType == "SerialNumber") d.serverAddress.toString() else d.physicalServer.toString()
                logicalNameReferencing = d.logicalNameReferencing
                logicalServer = d.logicalServer.toString()
                physicalServer = d.physicalServer.toString()
                security = when (d.security.lowercase()) {
                    "authentication" -> "Authentication"
                    "authenticationencryption" -> "AuthenticationEncryption"
                    else -> "None"
                }
                securitySuite = d.securitySuite
                interfaceType = d.interfaceType
                ciphering = d.ciphering
                invCounterObis = d.invocationCounterObis ?: "0.0.43.1.0.255"
                invocationCounterInitial = d.invocationCounterInitial.toString()
                useInvCounter = d.useInvocationCounter
                retryCount = d.retryCount.toString()
                retryIntervalMs = d.retryIntervalMs.toString()
                password = repository.resolveDeviceSecret(d.passwordKeyRef).orEmpty()
                systemTitle = repository.resolveDeviceSecret(d.systemTitleKeyRef).orEmpty()
                authKey = repository.resolveDeviceSecret(d.authKeyRef).orEmpty()
                encKey = repository.resolveDeviceSecret(d.encKeyRef).orEmpty()
                passwordAscii = false
                systemTitleAscii = false
                authKeyAscii = false
                encKeyAscii = false
            }
        }
    }

    val clientRoles = listOf("PC", "MR", "US")
    val clientAddressByRole = mapOf("PC" to 16, "MR" to 32, "US" to 48)
    val addressTypes = listOf("Default", "SerialNumber")
    val securityOptions = listOf("None", "Authentication", "AuthenticationEncryption")
    val securitySuites = listOf("Suite0", "Suite1", "Suite2")
    val invocationCounters = mapOf(
        "PC" to "0.0.43.1.1.255",
        "MR" to "0.0.43.1.2.255",
        "US" to "0.0.43.1.3.255",
        "Push" to "0.0.43.1.4.255",
        "FW" to "0.0.43.1.5.255",
        "IHD" to "0.0.43.1.6.255"
    )
    val accentBlue = Color(0xFF006C6F)
    val bgCard = Color.White
    val textPrimary = Color(0xFF0F2527)
    val textSecondary = Color(0xFF5E7375)
    val tfColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = accentBlue,
        unfocusedBorderColor = Color(0xFFD7E1E2),
        focusedTextColor = textPrimary,
        unfocusedTextColor = textPrimary,
        cursorColor = accentBlue,
        focusedLabelColor = accentBlue,
        unfocusedLabelColor = textSecondary,
        focusedContainerColor = Color.White,
        unfocusedContainerColor = Color.White
    )

    fun isPlaceholder(value: String) = value.startsWith("•")

    fun asciiToHex(value: String): String =
        value.toByteArray(Charsets.US_ASCII).joinToString("") { "%02X".format(it) }

    fun cleanHex(value: String): String =
        value.removePrefix("0x").removePrefix("0X").replace(" ", "").replace(":", "")

    fun validateHex(label: String, value: String): String {
        val clean = cleanHex(value)
        if (clean.length % 2 != 0 || clean.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }) {
            throw IllegalArgumentException("$label must be valid HEX with an even number of characters")
        }
        return clean.uppercase()
    }

    fun normalizeTcpHost(value: String): String {
        val trimmed = value.trim()
        return if (trimmed.startsWith("[") && trimmed.endsWith("]") && trimmed.length > 2) {
            trimmed.substring(1, trimmed.length - 1)
        } else {
            trimmed
        }
    }

    fun validateIpAddress(value: String) {
        val host = normalizeTcpHost(value)
        if (host.isBlank()) {
            throw IllegalArgumentException("TCP host is required")
        }
        if (host.contains(":")) {
            val scopeSeparatorCount = host.count { it == '%' }
            if (scopeSeparatorCount > 1) {
                throw IllegalArgumentException("IPv6 scope can only be specified once")
            }
            val addressText = host.substringBefore('%')
            val scopeText = host.substringAfter('%', missingDelimiterValue = "")
            val addressCharsValid = addressText.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' || it == ':' || it == '.' }
            val scopeCharsValid = scopeText.isEmpty() || scopeText.all { it.isLetterOrDigit() || it == '_' || it == '-' || it == '.' }
            if (!addressCharsValid || !scopeCharsValid) {
                throw IllegalArgumentException("TCP host must be a valid IPv6 address")
            }
            val address = try {
                InetAddress.getByName(addressText)
            } catch (_: Exception) {
                throw IllegalArgumentException("TCP host must be a valid IPv6 address")
            }
            if (address !is Inet6Address) {
                throw IllegalArgumentException("TCP host must be a valid IPv6 address")
            }
            if (address.isLinkLocalAddress && scopeText.isBlank()) {
                throw IllegalArgumentException("IPv6 link-local TCP host must include an interface scope, for example $addressText%wlan0")
            }
            return
        }
        if (host.contains(".")) {
            val parts = host.split(".")
            if (parts.size == 4 &&
                parts.all { part -> part.isNotEmpty() && part.all(Char::isDigit) && part.toIntOrNull() in 0..255 } &&
                InetAddress.getByName(host) is Inet4Address
            ) {
                return
            }
        }
        throw IllegalArgumentException("TCP host must be a valid IPv4 or IPv6 address")
    }

    fun validateTcpSettings() {
        if (transportTab != 2) return
        val host = tcpHost.trim()
        val port = tcpPort.toIntOrNull()
        validateIpAddress(host)
        if (port == null || port !in 1..65535) {
            throw IllegalArgumentException("TCP port must be between 1 and 65535")
        }
    }

    fun secretForSave(label: String, value: String, ascii: Boolean): String? {
        if (value.isBlank() || isPlaceholder(value)) return null
        return if (ascii) asciiToHex(value) else validateHex(label, value)
    }

    fun switchSecretMode(label: String, value: String, fromAscii: Boolean, setValue: (String) -> Unit, setAscii: (Boolean) -> Unit) {
        if (isPlaceholder(value)) {
            setAscii(!fromAscii)
            return
        }
        try {
            if (fromAscii) {
                setValue(asciiToHex(value))
            } else {
                val clean = validateHex(label, value)
                val bytes = clean.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                val ascii = bytes.toString(Charsets.US_ASCII)
                if (ascii.any { it.code < 32 || it.code > 126 }) {
                    throw IllegalArgumentException("$label HEX is not printable ASCII")
                }
                setValue(ascii)
            }
            setAscii(!fromAscii)
        } catch (e: Exception) {
            validationError = e.message
        }
    }

    fun buildAndSave() {
        if (deviceName.isBlank()) return
        isSaving = true
        scope.launch {
            val secretsMap = try {
                validateTcpSettings()
                buildMap<String, String> {
                    secretForSave("Password", password, passwordAscii)?.let { put("password", it) }
                    secretForSave("System title", systemTitle, systemTitleAscii)?.let { put("systemTitle", it) }
                    secretForSave("Authentication key", authKey, authKeyAscii)?.let { put("authKey", it) }
                    secretForSave("Block cipher key", encKey, encKeyAscii)?.let { put("encKey", it) }
                }
            } catch (e: Exception) {
                validationError = e.message
                isSaving = false
                return@launch
            }
            val commJson = CommSettings.toJson(
                when (transportTab) {
                    0 -> CommSettings.Otg(baudRate.toIntOrNull() ?: 9600)
                    1 -> CommSettings.Ble(bleAddress.trim(), bleName.trim())
                    else -> CommSettings.Tcp(tcpHost.trim(), tcpPort.toIntOrNull() ?: 4059)
                }
            )
            val secStr = when (security) {
                "Authentication" -> "authentication"
                "AuthenticationEncryption" -> "authenticationencryption"
                else -> "none"
            }
            val deviceBase = DeviceEntity(
                id = existingDevice?.id ?: 0,
                name = deviceName.trim(),
                commSettingsJson = commJson,
                authenticationRole = authenticationRole,
                clientAddress = clientAddress.toIntOrNull() ?: 16,
                serverAddress = serverAddress.toIntOrNull() ?: 1,
                addressType = addressType,
                logicalNameReferencing = logicalNameReferencing,
                logicalServer = logicalServer.toIntOrNull() ?: 0,
                physicalServer = if (addressType == "SerialNumber") physicalServer.toIntOrNull() ?: 1 else serverAddress.toIntOrNull() ?: 1,
                security = secStr,
                securitySuite = securitySuite,
                interfaceType = interfaceType,
                ciphering = ciphering,
                invocationCounterObis = invCounterObis.trim().ifBlank { null },
                invocationCounterInitial = invocationCounterInitial.toLongOrNull() ?: 0,
                useInvocationCounter = useInvCounter,
                retryCount = retryCount.toIntOrNull() ?: 3,
                retryIntervalMs = retryIntervalMs.toIntOrNull() ?: 1000,
                // Key refs preserved from existing; new ones written by repository
                passwordKeyRef = existingDevice?.passwordKeyRef,
                authKeyRef = existingDevice?.authKeyRef,
                encKeyRef = existingDevice?.encKeyRef,
                systemTitleKeyRef = existingDevice?.systemTitleKeyRef,
                lastConnectedAt = existingDevice?.lastConnectedAt,
                lastKnownMeterSerial = existingDevice?.lastKnownMeterSerial
            )
            if (existingDevice == null) {
                repository.addDevice(deviceBase, secretsMap)
            } else {
                repository.updateDevice(deviceBase, secretsMap)
            }
            isSaving = false
            onSaved()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (deviceId == null) "Add Meter" else "Meter Properties", color = textPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = textPrimary) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFF8FBFB))
            )
        },
        bottomBar = {
            Surface(
                color = Color.White,
                shadowElevation = 8.dp
            ) {
                Button(
                    onClick = { if (!isSaving) buildAndSave() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accentBlue),
                    shape = RoundedCornerShape(8.dp),
                    enabled = !isSaving && deviceName.isNotBlank()
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (deviceId == null) "Save Meter" else "Save Changes", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
        },
        containerColor = Color(0xFFF4F7F7)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Device Name ───────────────────────────────────────────────────
            SectionCard(title = "Device Name", bgCard) {
                OutlinedTextField(
                    value = deviceName,
                    onValueChange = { deviceName = it },
                    label = { Text("Label (e.g. \"Genus Meter 1\")") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = tfColors
                )
            }

            // ── Comm Channel ──────────────────────────────────────────────────
            SectionCard(title = "Comm Channel", bgCard) {
                // Transport selector chips
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        Triple(0, "OTG", Icons.Default.Usb),
                        Triple(1, "BLE", Icons.Default.Bluetooth),
                        Triple(2, "TCP", Icons.Default.Language)
                    ).forEach { (idx, label, icon) ->
                        val selected = transportTab == idx
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) accentBlue else Color(0xFFF4F7F7))
                                .border(1.dp, if (selected) accentBlue else Color(0xFFD7E1E2), RoundedCornerShape(8.dp))
                                .clickable { transportTab = idx }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(icon, contentDescription = null, tint = if (selected) Color.White else textSecondary, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(label, color = if (selected) Color.White else textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                when (transportTab) {
                    0 -> { // OTG
                        OutlinedTextField(
                            value = baudRate,
                            onValueChange = { baudRate = it.filter { c -> c.isDigit() } },
                            label = { Text("Baud Rate") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = tfColors
                        )
                    }
                    1 -> { // BLE
                        OutlinedTextField(value = bleName, onValueChange = { bleName = it }, label = { Text("Device Name (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, colors = tfColors)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = bleAddress, onValueChange = { bleAddress = it.uppercase() },
                            label = { Text("MAC Address (e.g. AA:BB:CC:DD:EE:FF)") },
                            modifier = Modifier.fillMaxWidth(), singleLine = true, colors = tfColors,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
                        )
                    }
                    2 -> { // TCP
                        OutlinedTextField(value = tcpHost, onValueChange = { tcpHost = it }, label = { Text("Host / IP Address") }, modifier = Modifier.fillMaxWidth(), singleLine = true, colors = tfColors)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = tcpPort, onValueChange = { tcpPort = it.filter { c -> c.isDigit() } },
                            label = { Text("Port") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(), singleLine = true, colors = tfColors
                        )
                    }
                }
            }

            // ── DLMS Session Parameters ───────────────────────────────────────
            SectionCard(title = "DLMS Session Parameters", bgCard) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DropdownField(
                        label = "Client Role",
                        value = authenticationRole,
                        options = clientRoles,
                        modifier = Modifier.weight(1f),
                        colors = tfColors
                    ) {
                        authenticationRole = it
                        clientAddress = (clientAddressByRole[it] ?: 16).toString()
                        invCounterObis = invocationCounters[it] ?: invCounterObis
                    }
                    OutlinedTextField(
                        value = clientAddress,
                        onValueChange = { clientAddress = it.filter(Char::isDigit) },
                        label = { Text("Client Addr") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = tfColors
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DropdownField("Address Type", addressType, addressTypes, Modifier.weight(1f), tfColors) {
                        addressType = it
                    }
                    OutlinedTextField(
                        value = serverAddress,
                        onValueChange = { serverAddress = it.filter(Char::isDigit) },
                        label = { Text(if (addressType == "SerialNumber") "Serial Number" else "Physical Address") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = tfColors
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = logicalServer,
                        onValueChange = { logicalServer = it.filter(Char::isDigit) },
                        label = { Text("Logical Address") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = tfColors
                    )
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("LN Ref", color = textSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Switch(
                            checked = logicalNameReferencing,
                            onCheckedChange = { logicalNameReferencing = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = accentBlue)
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DropdownField("Security Suite", securitySuite, securitySuites, Modifier.weight(1f), tfColors) {
                        securitySuite = it
                    }
                    DropdownField("Security", security, securityOptions, Modifier.weight(1f), tfColors) {
                        security = it
                        ciphering = it != "None"
                    }
                }
                Spacer(Modifier.height(12.dp))
                SecretField(
                    label = "Password / HLS Key",
                    value = password,
                    onValueChange = { password = it },
                    ascii = passwordAscii,
                    onToggleAscii = { switchSecretMode("Password", password, passwordAscii, { password = it }, { passwordAscii = it }) },
                    colors = tfColors
                )
                Spacer(Modifier.height(12.dp))
                // Interface type
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Interface:", color = textSecondary, fontSize = 13.sp)
                    Spacer(Modifier.width(10.dp))
                    listOf("HDLC", "WRAPPER").forEach { iface ->
                        val sel = interfaceType == iface
                        Box(
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (sel) accentBlue else Color(0xFFF4F7F7))
                                .clickable { interfaceType = iface }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) { Text(iface, color = if (sel) Color.White else textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                    }
                }
            }

            // ── Ciphering ─────────────────────────────────────────────────────
            SectionCard(title = "Ciphering & Keys", bgCard) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Enable Ciphering", color = textPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Switch(checked = ciphering, onCheckedChange = { ciphering = it }, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = accentBlue))
                }
                if (ciphering) {
                    Spacer(Modifier.height(10.dp))
                    SecretField(
                        label = "System Title",
                        value = systemTitle,
                        onValueChange = { systemTitle = it },
                        ascii = systemTitleAscii,
                        onToggleAscii = { switchSecretMode("System title", systemTitle, systemTitleAscii, { systemTitle = it }, { systemTitleAscii = it }) },
                        colors = tfColors
                    )
                    Spacer(Modifier.height(8.dp))
                    SecretField(
                        label = "Authentication Key",
                        value = authKey,
                        onValueChange = { authKey = it },
                        ascii = authKeyAscii,
                        onToggleAscii = { switchSecretMode("Authentication key", authKey, authKeyAscii, { authKey = it }, { authKeyAscii = it }) },
                        colors = tfColors
                    )
                    Spacer(Modifier.height(8.dp))
                    SecretField(
                        label = "Block Cipher Key",
                        value = encKey,
                        onValueChange = { encKey = it },
                        ascii = encKeyAscii,
                        onToggleAscii = { switchSecretMode("Block cipher key", encKey, encKeyAscii, { encKey = it }, { encKeyAscii = it }) },
                        colors = tfColors
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Use Invocation Counter", color = textPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Switch(checked = useInvCounter, onCheckedChange = { useInvCounter = it }, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = accentBlue))
                    }
                    if (useInvCounter) {
                        Spacer(Modifier.height(8.dp))
                        DropdownField("Counter Association", authenticationRole, clientRoles, Modifier.fillMaxWidth(), tfColors) {
                            authenticationRole = it
                            clientAddress = (clientAddressByRole[it] ?: 16).toString()
                            invCounterObis = invocationCounters[it] ?: invCounterObis
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = invCounterObis, onValueChange = { invCounterObis = it },
                            label = { Text("Invocation Counter OBIS") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                            colors = tfColors, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, color = Color(0xFF00575A), fontSize = 14.sp)
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = invocationCounterInitial,
                            onValueChange = { invocationCounterInitial = it.filter(Char::isDigit) },
                            label = { Text("Initial Invocation Counter") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = tfColors
                        )
                    }
                }
            }

            SectionCard(title = "Retry", bgCard) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = retryCount,
                        onValueChange = { retryCount = it.filter(Char::isDigit) },
                        label = { Text("Retry Count") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = tfColors
                    )
                    OutlinedTextField(
                        value = retryIntervalMs,
                        onValueChange = { retryIntervalMs = it.filter(Char::isDigit) },
                        label = { Text("Retry Interval ms") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = tfColors
                    )
                }
            }

            Spacer(Modifier.height(78.dp))
        }
    }

    validationError?.let { error ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { validationError = null },
            containerColor = Color.White,
            title = { Text("Invalid DLMS Configuration", color = Color(0xFFD71920), fontWeight = FontWeight.Bold) },
            text = { Text(error, color = Color(0xFF5E7375)) },
            confirmButton = {
                TextButton(onClick = { validationError = null }) {
                    Text("OK", color = Color(0xFF006C6F))
                }
            }
        )
    }
}

@Composable
private fun SectionCard(title: String, bg: Color, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = bg),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, color = Color(0xFF006C6F), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SecretField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    ascii: Boolean,
    onToggleAscii: () -> Unit,
    colors: androidx.compose.material3.TextFieldColors
) {
    var visible by remember { mutableStateOf(false) }
    Column {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text("$label (${if (ascii) "ASCII" else "HEX"})") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = colors,
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation('•'),
            trailingIcon = {
                TextButton(onClick = { visible = !visible }, modifier = Modifier.padding(end = 4.dp)) {
                    Text(if (visible) "Hide" else "Show", color = Color(0xFF006C6F), fontSize = 11.sp)
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = if (ascii) KeyboardType.Password else KeyboardType.Ascii),
            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, color = Color(0xFF00575A), fontSize = 13.sp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = ascii, onCheckedChange = { onToggleAscii() })
            Text("ASCII input", color = Color(0xFF5E7375), fontSize = 12.sp)
            Spacer(Modifier.width(8.dp))
            Text(if (ascii) "Untick to convert to HEX" else "Tick to convert to ASCII", color = Color(0xFF7A8F91), fontSize = 11.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownField(
    label: String,
    value: String,
    options: List<String>,
    modifier: Modifier = Modifier,
    colors: androidx.compose.material3.TextFieldColors,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = colors,
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = Color.White
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}
