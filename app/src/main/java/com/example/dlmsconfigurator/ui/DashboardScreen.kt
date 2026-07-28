package com.example.dlmsconfigurator

import android.net.Uri
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dlmsconfigurator.core.data.DataRepository
import com.example.dlmsconfigurator.core.data.SessionEntity
import com.example.dlmsconfigurator.core.data.StagedFile
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    repository: DataRepository,
    onExecute: (String) -> Unit,
    onSetup: (String) -> Unit,
    onViewHistoryDetail: (Long) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val stagedFiles by repository.stagedFiles.collectAsState(initial = emptyList())
    val sessions by repository.getSessionsFlow().collectAsState(initial = emptyList())

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        scope.launch {
            val names = mutableListOf<String>()
            val contents = mutableListOf<String>()
            uris.forEach { uri ->
                val name = getFileName(context, uri) ?: "unknown.json"
                val content = readUriContent(context, uri)
                if (content != null) {
                    names.add(name)
                    contents.add(content)
                }
            }
            if (names.isNotEmpty()) {
                repository.importFiles(names, contents)
            }
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF0F0F1A),
                modifier = Modifier.fillMaxWidth()
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Add, contentDescription = "Import") },
                    label = { Text("Import", fontSize = 12.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF007AFF),
                        unselectedIconColor = Color.Gray,
                        selectedTextColor = Color(0xFF007AFF),
                        unselectedTextColor = Color.Gray,
                        indicatorColor = Color(0xFF1E1E2F)
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.History, contentDescription = "History") },
                    label = { Text("History", fontSize = 12.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF007AFF),
                        unselectedIconColor = Color.Gray,
                        selectedTextColor = Color(0xFF007AFF),
                        unselectedTextColor = Color.Gray,
                        indicatorColor = Color(0xFF1E1E2F)
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings", fontSize = 12.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF007AFF),
                        unselectedIconColor = Color.Gray,
                        selectedTextColor = Color(0xFF007AFF),
                        unselectedTextColor = Color.Gray,
                        indicatorColor = Color(0xFF1E1E2F)
                    )
                )
            }
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
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = when (selectedTab) {
                        0 -> "Staged Operations"
                        1 -> "Session History"
                        else -> "App Settings"
                    },
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            when (selectedTab) {
                0 -> ImportTab(
                    stagedFiles = stagedFiles,
                    onImportClick = { launcher.launch(arrayOf("application/json", "*/*")) },
                    onRun = onExecute,
                    onSetup = onSetup,
                    onDelete = { repository.removeStagedFile(it) },
                    onClearAll = { repository.clearStagedFiles() },
                    onResetDefaults = { repository.resetToDefaultTemplates() }
                )
                1 -> HistoryTab(
                    sessions = sessions,
                    onSessionClick = onViewHistoryDetail
                )
                2 -> SettingsTab(
                    repository = repository
                )
            }
        }
    }
}

@Composable
fun ImportTab(
    stagedFiles: List<StagedFile>,
    onImportClick: () -> Unit,
    onRun: (String) -> Unit,
    onSetup: (String) -> Unit,
    onDelete: (String) -> Unit,
    onClearAll: () -> Unit,
    onResetDefaults: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onImportClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Import JSON Files")
            }

            if (stagedFiles.isNotEmpty()) {
                Button(
                    onClick = onClearAll,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3B30).copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Clear All", color = Color(0xFFFF3B30))
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (stagedFiles.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("No files staged. Import a JSON file to begin.", color = Color.Gray, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onResetDefaults,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34C759)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Restore Built-in Templates")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(stagedFiles) { file ->
                    StagedFileCard(
                        file = file,
                        onRun = { onRun(file.id) },
                        onCustomize = { onSetup(file.id) },
                        onDelete = { onDelete(file.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun StagedFileCard(
    file: StagedFile,
    onRun: () -> Unit,
    onCustomize: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2F)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (file.isValid) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (file.isValid) Color(0xFF34C759) else Color(0xFFFF9500),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = file.fileName,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                }

                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Gray)
                }
            }

            if (!file.isValid) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Validation Error: ${file.validationError}",
                    color = Color(0xFFFF3B30),
                    fontSize = 13.sp
                )
            } else {
                val opCount = file.parsedContent?.operations?.size ?: 0
                val baud = file.parsedContent?.connection?.baudRate ?: 9600
                val sec = file.parsedContent?.connection?.security ?: "none"
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$opCount operations • Baud: $baud • Security: $sec",
                    color = Color.LightGray,
                    fontSize = 13.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onCustomize,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White).copy(
                            containerColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.padding(end = 10.dp)
                    ) {
                        Text("Customize")
                    }

                    Button(
                        onClick = onRun,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34C759)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Run", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryTab(
    sessions: List<SessionEntity>,
    onSessionClick: (Long) -> Unit
) {
    if (sessions.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("No session history found.", color = Color.Gray, fontSize = 14.sp)
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(sessions) { session ->
                HistorySessionCard(session = session, onClick = { onSessionClick(session.id) })
            }
        }
    }
}

@Composable
fun HistorySessionCard(
    session: SessionEntity,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val dateStr = DateFormat.format("yyyy-MM-dd HH:mm:ss", session.startTime).toString()
    val duration = if (session.endTime != null) {
        val seconds = (session.endTime - session.startTime) / 1000
        "${seconds}s"
    } else {
        "Running"
    }

    val statusColor = when (session.status) {
        "COMPLETED" -> Color(0xFF34C759)
        "ABORTED" -> Color(0xFFFF3B30)
        else -> Color(0xFFFF9500)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2F)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.jsonSourceFileName,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Start: $dateStr • Duration: $duration",
                    color = Color.LightGray,
                    fontSize = 12.sp
                )
                if (session.meterSerial != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Meter Serial: ${session.meterSerial}",
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(statusColor.copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = session.status,
                    color = statusColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTab(
    repository: DataRepository
) {
    val scope = rememberCoroutineScope()
    var defaultBaud by remember { mutableStateOf(repository.getDefaultBaudRate().toString()) }
    var detailedLogging by remember { mutableStateOf(repository.getDefaultLoggingLevel()) }
    var selectedTheme by remember { mutableStateOf(repository.getAppTheme()) }

    var storedSerials by remember { mutableStateOf(repository.getStoredMeterSerials()) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        item {
                    Text(
                        text = "Default Parameters",
                        color = Color(0xFF007AFF),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                item {
                    OutlinedTextField(
                        value = defaultBaud,
                        onValueChange = {
                            defaultBaud = it
                            it.toIntOrNull()?.let { b -> repository.setDefaultBaudRate(b) }
                        },
                        label = { Text("Default Baud Rate") },
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
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF1E1E2F))
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Detailed Logging", color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text("Save hex raw values in operations log", color = Color.Gray, fontSize = 12.sp)
                        }
                        Switch(
                            checked = detailedLogging,
                            onCheckedChange = {
                                detailedLogging = it
                                repository.setDefaultLoggingLevel(it)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF34C759)
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                item {
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it }
                    ) {
                        OutlinedTextField(
                            value = when(selectedTheme) {
                                "SYSTEM" -> "System Default"
                                "LIGHT" -> "Light Theme"
                                else -> "Dark Theme"
                            },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("App Theme") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
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
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("System Default") },
                                onClick = {
                                    selectedTheme = "SYSTEM"
                                    repository.setAppTheme("SYSTEM")
                                    expanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Light Theme") },
                                onClick = {
                                    selectedTheme = "LIGHT"
                                    repository.setAppTheme("LIGHT")
                                    expanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Dark Theme") },
                                onClick = {
                                    selectedTheme = "DARK"
                                    repository.setAppTheme("DARK")
                                    expanded = false
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(28.dp))
                }

                item {
                    Text(
                        text = "Stored Credentials",
                        color = Color(0xFF007AFF),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (storedSerials.isEmpty()) {
                    item {
                        Text(
                            text = "No credentials securely stored.",
                            color = Color.Gray,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                } else {
                    items(storedSerials) { serial ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF1E1E2F))
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Meter Serial: $serial", color = Color.White, fontSize = 14.sp)
                            IconButton(
                                onClick = {
                                    repository.deleteMeterPassword(serial)
                                    storedSerials = repository.getStoredMeterSerials()
                                }
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFFF3B30))
                            }
                        }
                    }
                }
            }
    }

// Helpers to read file metadata and contents
private fun getFileName(context: android.content.Context, uri: Uri): String? {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index >= 0) {
                    result = it.getString(index)
                }
            }
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/') ?: -1
        if (cut != -1) {
            result = result?.substring(cut + 1)
        }
    }
    return result
}

private fun readUriContent(context: android.content.Context, uri: Uri): String? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri)
        val reader = BufferedReader(InputStreamReader(inputStream))
        val stringBuilder = StringBuilder()
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            stringBuilder.append(line).append("\n")
        }
        inputStream?.close()
        stringBuilder.toString()
    } catch (e: Exception) {
        null
    }
}
