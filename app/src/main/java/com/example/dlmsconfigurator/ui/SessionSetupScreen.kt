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
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dlmsconfigurator.core.data.DataRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionSetupScreen(
    stagedFileId: String,
    onConfirm: (
        transportType: String, // "usb", "ble", "tcp"
        bleAddress: String?,
        tcpHost: String?,
        tcpPort: Int?,
        baud: Int?,
        client: Int?,
        server: Int?,
        sec: String?,
        pwd: String?,
        logging: Boolean?,
        ciphering: Boolean,
        systemTitle: String?,
        authKey: String?,
        encKey: String?,
        counterObis: String?,
        useInvocationCounter: Boolean?
    ) -> Unit,
    onCancel: () -> Unit,
    repository: DataRepository
) {
    val stagedFiles by repository.stagedFiles.collectAsState(initial = emptyList())
    val currentFile = stagedFiles.find { it.id == stagedFileId } ?: return

    val connection = currentFile.parsedContent?.connection

    // Transport selection: "usb", "ble", "tcp"
    var selectedTransport by remember { mutableStateOf("usb") }

    // BLE specific params
    var bleMacAddress by remember { mutableStateOf("") }

    // TCP specific params
    var tcpHost by remember { mutableStateOf(connection?.pushServerV4Ip ?: "10.92.170.72") }
    var tcpPort by remember { mutableStateOf((connection?.pushServerV4Port ?: 4059).toString()) }

    // Serial/USB params
    var baudRate by remember { mutableStateOf(connection?.baudRate?.toString() ?: "9600") }

    // DLMS Params
    var clientAddress by remember { mutableStateOf(connection?.clientAddress?.toString() ?: "16") }
    var serverAddress by remember { mutableStateOf(connection?.serverAddress?.toString() ?: "1") }
    var security by remember { mutableStateOf(connection?.security ?: "none") }
    var password by remember { mutableStateOf(connection?.password ?: "") }
    var detailedLogging by remember { mutableStateOf(repository.getDefaultLoggingLevel()) }

    var ciphering by remember { mutableStateOf(connection?.ciphering ?: true) }
    var useInvocationCounter by remember { mutableStateOf(connection?.isUseInvocationCounter ?: true) }
    var systemTitle by remember { mutableStateOf(connection?.systemTitle ?: "") }
    var authenticationKey by remember { mutableStateOf(connection?.authenticationKey ?: "") }
    var encryptionKey by remember { mutableStateOf(connection?.blockCipherKey ?: connection?.encryptionKey ?: "") }
    var invocationCounterObis by remember {
        mutableStateOf(
            connection?.invocationCounterObis
                ?: connection?.invocationCounterLN
                ?: if (connection?.authentication?.uppercase() == "US") "0.0.43.1.3.255" else "0.0.43.1.0.255"
        )
    }

    // Dynamic pre-fill when client address or security changes
    androidx.compose.runtime.LaunchedEffect(clientAddress, security) {
        if (invocationCounterObis.isBlank() || invocationCounterObis == "0.0.43.1.0.255") {
            if (clientAddress == "48" || (security != "none" && connection?.authentication?.uppercase() == "US")) {
                invocationCounterObis = "0.0.43.1.3.255"
            }
        }
    }

    var securityExpanded by remember { mutableStateOf(false) }

    Scaffold(
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
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Customize Run: ${currentFile.fileName}",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Select transport layer interface and customize DLMS communication settings.",
                color = Color.Gray,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Transport Selection Header
            Text(
                text = "TRANSPORT LAYER INTERFACE",
                color = Color(0xFF007AFF),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TransportCard(
                    title = "OTG",
                    subtitle = "USB Probe",
                    icon = Icons.Default.Usb,
                    isSelected = selectedTransport == "usb",
                    onClick = { selectedTransport = "usb" },
                    modifier = Modifier.weight(1f)
                )

                TransportCard(
                    title = "Bluetooth",
                    subtitle = "BLE Probe",
                    icon = Icons.Default.Bluetooth,
                    isSelected = selectedTransport == "ble",
                    onClick = { selectedTransport = "ble" },
                    modifier = Modifier.weight(1f)
                )

                TransportCard(
                    title = "TCP IPv4/v6",
                    subtitle = "Network / Sim",
                    icon = Icons.Default.Language,
                    isSelected = selectedTransport == "tcp",
                    onClick = { selectedTransport = "tcp" },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Transport Specific Configuration Section
            when (selectedTransport) {
                "usb" -> {
                    OutlinedTextField(
                        value = baudRate,
                        onValueChange = { baudRate = it },
                        label = { Text("USB Serial Baud Rate") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color(0xFF1E1E2F),
                            unfocusedContainerColor = Color(0xFF1E1E2F),
                            focusedLabelColor = Color(0xFF007AFF),
                            unfocusedLabelColor = Color.Gray
                        )
                    )
                }
                "ble" -> {
                    OutlinedTextField(
                        value = bleMacAddress,
                        onValueChange = { bleMacAddress = it },
                        label = { Text("BLE Device MAC Address (Optional - can scan next)") },
                        placeholder = { Text("e.g. AA:BB:CC:DD:EE:FF", color = Color.DarkGray) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color(0xFF1E1E2F),
                            unfocusedContainerColor = Color(0xFF1E1E2F),
                            focusedLabelColor = Color(0xFF007AFF),
                            unfocusedLabelColor = Color.Gray
                        )
                    )
                }
                "tcp" -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = tcpHost,
                            onValueChange = { tcpHost = it },
                            label = { Text("TCP Host IP") },
                            modifier = Modifier.weight(2f),
                            shape = RoundedCornerShape(10.dp),
                            colors = TextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedContainerColor = Color(0xFF1E1E2F),
                                unfocusedContainerColor = Color(0xFF1E1E2F),
                                focusedLabelColor = Color(0xFF007AFF),
                                unfocusedLabelColor = Color.Gray
                            )
                        )

                        OutlinedTextField(
                            value = tcpPort,
                            onValueChange = { tcpPort = it },
                            label = { Text("Port") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = TextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedContainerColor = Color(0xFF1E1E2F),
                                unfocusedContainerColor = Color(0xFF1E1E2F),
                                focusedLabelColor = Color(0xFF007AFF),
                                unfocusedLabelColor = Color.Gray
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "DLMS PROTOCOL SETTINGS",
                color = Color(0xFF007AFF),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = clientAddress,
                onValueChange = { clientAddress = it },
                label = { Text("Client Address (Decimal)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = TextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = Color(0xFF1E1E2F),
                    unfocusedContainerColor = Color(0xFF1E1E2F),
                    focusedLabelColor = Color(0xFF007AFF),
                    unfocusedLabelColor = Color.Gray
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = serverAddress,
                onValueChange = { serverAddress = it },
                label = { Text("Server Address (Decimal)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = TextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = Color(0xFF1E1E2F),
                    unfocusedContainerColor = Color(0xFF1E1E2F),
                    focusedLabelColor = Color(0xFF007AFF),
                    unfocusedLabelColor = Color.Gray
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            ExposedDropdownMenuBox(
                expanded = securityExpanded,
                onExpandedChange = { securityExpanded = it }
            ) {
                OutlinedTextField(
                    value = when (security.lowercase()) {
                        "none" -> "No Security (None)"
                        "low" -> "Low Level Security (LLS)"
                        else -> "High Level Security (HLS / AES)"
                    },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Security Level") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = securityExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    shape = RoundedCornerShape(10.dp),
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0xFF1E1E2F),
                        unfocusedContainerColor = Color(0xFF1E1E2F),
                        focusedLabelColor = Color(0xFF007AFF),
                        unfocusedLabelColor = Color.Gray
                    )
                )
                ExposedDropdownMenu(
                    expanded = securityExpanded,
                    onDismissRequest = { securityExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("No Security (None)") },
                        onClick = {
                            security = "none"
                            securityExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Low Level Security (LLS)") },
                        onClick = {
                            security = "low"
                            securityExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("High Level Security (HLS)") },
                        onClick = {
                            security = "high"
                            securityExpanded = false
                        }
                    )
                }
            }

            if (security.lowercase() != "none") {
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    enabled = false,
                    label = { Text("Association Password (Hidden)") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = TextFieldDefaults.colors(
                        disabledTextColor = Color.Gray,
                        disabledContainerColor = Color(0xFF161625),
                        disabledLabelColor = Color.Gray
                    )
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF1E1E2F))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Enable Ciphering", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text("Enable Encryption & Authentication (Security Suite 0)", color = Color.Gray, fontSize = 12.sp)
                }
                Switch(
                    checked = ciphering,
                    onCheckedChange = { ciphering = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF007AFF)
                    )
                )
            }

            if (ciphering) {
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = systemTitle,
                    onValueChange = { systemTitle = it },
                    label = { Text("Client System Title (8-byte Hex)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0xFF1E1E2F),
                        unfocusedContainerColor = Color(0xFF1E1E2F),
                        focusedLabelColor = Color(0xFF007AFF),
                        unfocusedLabelColor = Color.Gray
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = authenticationKey,
                    onValueChange = { authenticationKey = it },
                    enabled = false,
                    label = { Text("Authentication Key (Hidden)") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = TextFieldDefaults.colors(
                        disabledTextColor = Color.Gray,
                        disabledContainerColor = Color(0xFF161625),
                        disabledLabelColor = Color.Gray
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = encryptionKey,
                    onValueChange = { encryptionKey = it },
                    enabled = false,
                    label = { Text("Encryption / Cipher Key (Hidden)") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = TextFieldDefaults.colors(
                        disabledTextColor = Color.Gray,
                        disabledContainerColor = Color(0xFF161625),
                        disabledLabelColor = Color.Gray
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                val defaultObis = if (security.lowercase() != "none" && (connection?.authentication?.uppercase() == "US" || clientAddress == "48")) "0.0.43.1.3.255" else "0.0.43.1.0.255"
                OutlinedTextField(
                    value = invocationCounterObis,
                    onValueChange = { invocationCounterObis = it },
                    label = { Text("Invocation Counter OBIS (e.g. $defaultObis)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0xFF1E1E2F),
                        unfocusedContainerColor = Color(0xFF1E1E2F),
                        focusedLabelColor = Color(0xFF007AFF),
                        unfocusedLabelColor = Color.Gray
                    )
                )
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF1E1E2F))
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Use Invocation Counter", color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text("Sync counter via Public Client before association", color = Color.Gray, fontSize = 12.sp)
                    }
                    Switch(
                        checked = useInvocationCounter,
                        onCheckedChange = { useInvocationCounter = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF007AFF)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF1E1E2F))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Detailed Logging", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text("Save hex raw values in operations log", color = Color.Gray, fontSize = 12.sp)
                }
                Switch(
                    checked = detailedLogging,
                    onCheckedChange = { detailedLogging = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF34C759)
                    )
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = onCancel,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White).copy(
                        containerColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(50.dp)
                ) {
                    Text("Cancel")
                }

                Button(
                    onClick = {
                        val baud = baudRate.toIntOrNull()
                        val clientAddressVal = clientAddress.toIntOrNull()
                        val serverAddressVal = serverAddress.toIntOrNull()
                        val sysTitle = if (ciphering && systemTitle.isNotBlank()) systemTitle else null
                        val authKey = if (ciphering && authenticationKey.isNotBlank()) authenticationKey else null
                        val encKey = if (ciphering && encryptionKey.isNotBlank()) encryptionKey else null
                        val counterObis = if (ciphering && invocationCounterObis.isNotBlank()) invocationCounterObis else null
                        val bleAddr = if (selectedTransport == "ble" && bleMacAddress.isNotBlank()) bleMacAddress.trim() else null
                        val host = if (selectedTransport == "tcp" && tcpHost.isNotBlank()) tcpHost.trim() else null
                        val port = if (selectedTransport == "tcp") tcpPort.toIntOrNull() else null

                        onConfirm(
                            selectedTransport,
                            bleAddr,
                            host,
                            port,
                            baud,
                            clientAddressVal,
                            serverAddressVal,
                            security,
                            password,
                            detailedLogging,
                            ciphering,
                            sysTitle,
                            authKey,
                            encKey,
                            counterObis,
                            useInvocationCounter
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(50.dp)
                ) {
                    Text("Confirm")
                }
            }
        }
    }
}

@Composable
fun TransportCard(
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
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (isSelected) Color(0xFF007AFF) else Color.Gray,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
            Text(
                text = subtitle,
                color = if (isSelected) Color(0xFF007AFF) else Color.Gray,
                fontSize = 10.sp
            )
        }
    }
}
