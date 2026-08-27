package com.example.dlmsconfigurator

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.dlmsconfigurator.core.data.DataRepository
import com.example.dlmsconfigurator.core.data.OperationItem
import com.example.dlmsconfigurator.core.dlms.DlmsProfileControls
import com.example.dlmsconfigurator.core.dlms.DlmsProfileReadMode
import com.example.dlmsconfigurator.core.dlms.DlmsProfileReadRequest
import com.example.dlmsconfigurator.core.dlms.DlmsProfileTable
import com.example.dlmsconfigurator.core.dlms.DlmsVisualKind
import com.example.dlmsconfigurator.core.dlms.DlmsVisualRow
import com.example.dlmsconfigurator.core.dlms.DlmsVisualSection
import com.example.dlmsconfigurator.core.dlms.DlmsVisualSnapshot
import com.example.dlmsconfigurator.ui.ConnectionState
import com.example.dlmsconfigurator.ui.DeviceSessionViewModel
import com.example.dlmsconfigurator.ui.RawTrafficEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObjectDetailScreen(
    deviceId: Long,
    obisCode: String,
    classId: Int,
    repository: DataRepository,
    onBack: () -> Unit,
    sessionViewModel: DeviceSessionViewModel = viewModel()
) {
    val scope = rememberCoroutineScope()
    val connectionState by sessionViewModel.connectionState.collectAsState()
    val rawTrafficLog by sessionViewModel.rawTrafficLog.collectAsState()
    val engine = sessionViewModel.activeEngine
    val isConnected = connectionState is ConnectionState.Connected && engine != null

    // Cached object metadata
    var objectName by remember { mutableStateOf("") }
    var smartSnapshot by remember { mutableStateOf<DlmsVisualSnapshot?>(null) }
    var smartError by remember { mutableStateOf<String?>(null) }
    var isLoadingSmartView by remember { mutableStateOf(false) }
    var attrAccessJson by remember { mutableStateOf("{}") }
    var writeTarget by remember { mutableStateOf<DlmsVisualRow?>(null) }
    var writeValue by remember { mutableStateOf("") }
    var writeError by remember { mutableStateOf<String?>(null) }
    var isWriting by remember { mutableStateOf(false) }

    LaunchedEffect(deviceId, obisCode) {
        val obj = repository.getAssociationObjects(deviceId).find { it.obisCode == obisCode }
        objectName = obj?.className?.ifBlank { "Class $classId Object" } ?: "Class $classId Object"
        attrAccessJson = obj?.attrAccessJson ?: "{}"
    }

    fun loadSmartView(profileReadRequest: DlmsProfileReadRequest = DlmsProfileReadRequest()) {
        val activeEngine = sessionViewModel.activeEngine ?: return
        isLoadingSmartView = true
        smartError = null
        scope.launch(Dispatchers.IO) {
            try {
                val snapshot = activeEngine.readObjectSnapshot(
                    classId,
                    obisCode,
                    profileReadRequest,
                    onPartialSnapshot = { partial ->
                        scope.launch(Dispatchers.Main) {
                            smartSnapshot = partial
                            smartError = null
                        }
                    }
                )
                withContext(Dispatchers.Main) {
                    smartSnapshot = snapshot
                    smartError = null
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    smartError = e.message ?: "Smart read failed"
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isLoadingSmartView = false
                }
            }
        }
    }

    val accentBlue = Color(0xFF006C6F)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(objectName, color = Color(0xFF0F2527), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(
                            obisCode,
                            color = Color(0xFF00575A),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color(0xFF0F2527))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFF8FBFB))
            )
        },
        containerColor = Color(0xFFF4F7F7)
    ) { innerPadding ->
        val pageScrollState = rememberScrollState()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(pageScrollState)
            ) {
                // Object Info bar
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFDDEDEE), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Text("Class $classId", color = accentBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = if (isConnected) "● Connected" else "● Not connected",
                            color = if (isConnected) Color(0xFF238B45) else Color(0xFFD71920),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                SmartObjectView(
                    snapshot = smartSnapshot,
                    error = smartError,
                    loading = isLoadingSmartView,
                    connected = isConnected,
                    classId = classId,
                    obisCode = obisCode,
                    attrAccessJson = attrAccessJson,
                    onWriteRequest = { row ->
                        writeTarget = row
                        writeValue = row.raw ?: row.value
                        writeError = null
                    },
                    onRefresh = { loadSmartView() },
                    onProfileRead = { loadSmartView(it) }
                )
                Spacer(Modifier.height(92.dp))
            }
            RawTrafficFloatingBar(
                latest = rawTrafficLog.lastOrNull(),
                count = rawTrafficLog.size,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
    }

    writeTarget?.let { row ->
        AlertDialog(
            onDismissRequest = { if (!isWriting) writeTarget = null },
            containerColor = Color.White,
            title = { Text("Write ${row.label}", color = Color(0xFF0F2527), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Attribute ${row.attribute ?: "-"}", color = Color(0xFF5E7375), fontSize = 12.sp)
                    OutlinedTextField(
                        value = writeValue,
                        onValueChange = { writeValue = it },
                        enabled = !isWriting,
                        singleLine = false,
                        colors = profileTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    writeError?.let { Text(it, color = Color(0xFFD71920), fontSize = 12.sp) }
                }
            },
            confirmButton = {
                Button(
                    enabled = !isWriting && row.attribute != null,
                    colors = ButtonDefaults.buttonColors(containerColor = accentBlue),
                    onClick = {
                        val activeEngine = sessionViewModel.activeEngine ?: return@Button
                        val attribute = row.attribute ?: return@Button
                        isWriting = true
                        writeError = null
                        scope.launch(Dispatchers.IO) {
                            try {
                                val op = OperationItem(
                                    type = "set",
                                    obis = obisCode,
                                    classId = classId,
                                    attribute = attribute,
                                    value = parseWriteValue(writeValue),
                                    name = objectName
                                )
                                activeEngine.executeSet(op) { _, _ -> }
                                withContext(Dispatchers.Main) {
                                    writeTarget = null
                                    loadSmartView()
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    writeError = e.message ?: "Write failed"
                                }
                            } finally {
                                withContext(Dispatchers.Main) {
                                    isWriting = false
                                }
                            }
                        }
                    }
                ) {
                    if (isWriting) CircularProgressIndicator(Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                    else Text("Write")
                }
            },
            dismissButton = {
                TextButton(onClick = { if (!isWriting) writeTarget = null }) {
                    Text("Cancel", color = Color(0xFF5E7375))
                }
            }
        )
    }
}

@Composable
private fun SmartObjectView(
    snapshot: DlmsVisualSnapshot?,
    error: String?,
    loading: Boolean,
    connected: Boolean,
    classId: Int,
    obisCode: String,
    attrAccessJson: String,
    onWriteRequest: (DlmsVisualRow) -> Unit,
    onRefresh: () -> Unit,
    onProfileRead: (DlmsProfileReadRequest) -> Unit
) {
    val accentBlue = Color(0xFF006C6F)
    if (classId == 7) {
        ProfileGenericWindow(
            snapshot = snapshot,
            error = error,
            loading = loading,
            connected = connected,
            obisCode = obisCode,
            onRead = onProfileRead
        )
        return
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Smart View", color = Color(0xFF0F2527), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(classVisualizationLabel(classId), color = Color(0xFF5E7375), fontSize = 11.sp)
                }
                Button(
                    onClick = onRefresh,
                    enabled = connected && !loading,
                    colors = ButtonDefaults.buttonColors(containerColor = accentBlue),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("Read", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            when {
                loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), color = accentBlue, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Reading standard COSEM attributes...", color = Color(0xFF5E7375), fontSize = 12.sp)
                }
                error != null -> ResultBlock("Smart View Error", error, Color(0xFFD71920))
                !connected -> Text("Connect to read class-specific attributes.", color = Color(0xFF7A8F91), fontSize = 12.sp)
                snapshot == null -> Text("Click Read to load class-specific attributes.", color = Color(0xFF7A8F91), fontSize = 12.sp)
                else -> {
                    snapshot.sections.forEach { section ->
                        VisualSectionCard(
                            section = section,
                            attrAccessJson = attrAccessJson,
                            onWriteRequest = onWriteRequest
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileGenericWindow(
    snapshot: DlmsVisualSnapshot?,
    error: String?,
    loading: Boolean,
    connected: Boolean,
    obisCode: String,
    onRead: (DlmsProfileReadRequest) -> Unit
) {
    val accentBlue = Color(0xFF006C6F)
    val controls = snapshot?.profileControls ?: DlmsProfileControls(
        logicalName = obisCode,
        capturePeriod = "",
        entriesInUse = "",
        profileEntries = "",
        sortMode = "",
        sortObject = ""
    )
    var selectedTab by remember { mutableIntStateOf(0) }
    var readMode by remember { mutableStateOf(DlmsProfileReadMode.ALL) }
    var startEntry by remember { mutableStateOf("1") }
    var entryCount by remember { mutableStateOf("20") }
    var lastDays by remember { mutableStateOf("1") }
    var fromDateTime by remember { mutableStateOf("") }
    var toDateTime by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }
    fun buildReadRequest(): DlmsProfileReadRequest? {
        val start = startEntry.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val count = entryCount.toIntOrNull()?.coerceAtLeast(1) ?: 20
        val days = lastDays.toIntOrNull()?.coerceAtLeast(1) ?: 1
        if (readMode == DlmsProfileReadMode.RANGE && (fromDateTime.isBlank() || toDateTime.isBlank())) {
            validationError = "From and To are required for range read"
            return null
        }
        validationError = null
        return DlmsProfileReadRequest(
            mode = readMode,
            startEntry = start,
            entryCount = count,
            lastDays = days,
            fromDateTime = fromDateTime,
            toDateTime = toDateTime
        )
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F3F3)),
        shape = RoundedCornerShape(4.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ProfileGenericPanel(
                controls = controls,
                loading = loading,
                connected = connected,
                readMode = readMode,
                onReadModeChange = { readMode = it },
                startEntry = startEntry,
                onStartEntryChange = { startEntry = it.filter(Char::isDigit) },
                entryCount = entryCount,
                onEntryCountChange = { entryCount = it.filter(Char::isDigit) },
                lastDays = lastDays,
                onLastDaysChange = { lastDays = it.filter(Char::isDigit) },
                fromDateTime = fromDateTime,
                onFromDateTimeChange = { fromDateTime = it },
                toDateTime = toDateTime,
                onToDateTimeChange = { toDateTime = it },
                onRead = {
                    buildReadRequest()?.let(onRead)
                }
            )

            when {
                validationError != null -> ResultBlock("Profile Generic Error", validationError ?: "", Color(0xFFD71920))
                error != null -> ResultBlock("Profile Generic Error", error, Color(0xFFD71920))
                !connected -> Text("Connect to read profile metadata and buffer rows.", color = Color(0xFF7A8F91), fontSize = 12.sp)
                loading -> Text("Reading profile generic attributes...", color = Color(0xFF5E7375), fontSize = 12.sp)
            }

            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color(0xFFF1F3F3),
                contentColor = Color(0xFF0F2527),
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = accentBlue,
                        height = 2.dp
                    )
                }
            ) {
                listOf("Data", "Capture Objects").forEachIndexed { idx, label ->
                    Tab(
                        selected = selectedTab == idx,
                        onClick = { selectedTab = idx },
                        text = {
                            Text(
                                label,
                                fontWeight = if (selectedTab == idx) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 12.sp
                            )
                        }
                    )
                }
            }

            val activeTable = if (selectedTab == 0) snapshot?.profileDataTable else snapshot?.profileCaptureTable
            if (activeTable == null) {
                ProfileEmptyTable(
                    if (selectedTab == 0) "Click Read to load profile data rows."
                    else "Click Read to load capture objects."
                )
            } else {
                ProfileTable(
                    title = if (selectedTab == 0) "Data" else "Capture Objects",
                    table = activeTable,
                    maxRows = if (selectedTab == 0) 80 else 120
                )
            }
        }
    }
}

@Composable
private fun ProfileGenericPanel(
    controls: DlmsProfileControls,
    loading: Boolean,
    connected: Boolean,
    readMode: DlmsProfileReadMode,
    onReadModeChange: (DlmsProfileReadMode) -> Unit,
    startEntry: String,
    onStartEntryChange: (String) -> Unit,
    entryCount: String,
    onEntryCountChange: (String) -> Unit,
    lastDays: String,
    onLastDaysChange: (String) -> Unit,
    fromDateTime: String,
    onFromDateTimeChange: (String) -> Unit,
    toDateTime: String,
    onToDateTimeChange: (String) -> Unit,
    onRead: () -> Unit
) {
    val accentBlue = Color(0xFF006C6F)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF6F7F7), RoundedCornerShape(4.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(8.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Reading", color = Color(0xFF5E7375), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Button(
                    onClick = onRead,
                    enabled = connected && !loading,
                    colors = ButtonDefaults.buttonColors(containerColor = accentBlue),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    if (loading) {
                        CircularProgressIndicator(Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text("Read", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                ProfileOptionRow(
                    label = "Read",
                    selected = readMode == DlmsProfileReadMode.ENTRY,
                    modifier = Modifier.weight(1f),
                    onSelect = { onReadModeChange(DlmsProfileReadMode.ENTRY) },
                    trailing = {
                        ProfileInputField(
                            value = startEntry,
                            onValueChange = onStartEntryChange,
                            modifier = Modifier.width(62.dp),
                            enabled = readMode == DlmsProfileReadMode.ENTRY,
                            numeric = true
                        )
                    }
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.width(132.dp)) {
                    Text("Count", color = Color(0xFF0F2527), fontSize = 11.sp, modifier = Modifier.width(42.dp))
                    ProfileInputField(
                        value = entryCount,
                        onValueChange = onEntryCountChange,
                        modifier = Modifier.weight(1f),
                        enabled = readMode == DlmsProfileReadMode.ENTRY,
                        numeric = true
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                ProfileOptionRow(
                    label = "Read last",
                    selected = readMode == DlmsProfileReadMode.LAST_DAYS,
                    modifier = Modifier.weight(1f),
                    onSelect = { onReadModeChange(DlmsProfileReadMode.LAST_DAYS) },
                    trailing = {
                        ProfileInputField(
                            value = lastDays,
                            onValueChange = onLastDaysChange,
                            modifier = Modifier.width(62.dp),
                            enabled = readMode == DlmsProfileReadMode.LAST_DAYS,
                            numeric = true
                        )
                        Text("Days", color = Color(0xFF0F2527), fontSize = 11.sp, modifier = Modifier.width(36.dp))
                    }
                )
                ProfileOptionRow(
                    label = "All",
                    selected = readMode == DlmsProfileReadMode.ALL,
                    modifier = Modifier.width(132.dp),
                    onSelect = { onReadModeChange(DlmsProfileReadMode.ALL) }
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                ProfileOptionRow(
                    label = "Read From",
                    selected = readMode == DlmsProfileReadMode.RANGE,
                    modifier = Modifier.width(104.dp),
                    onSelect = { onReadModeChange(DlmsProfileReadMode.RANGE) }
                )
                ProfileInputField(
                    value = fromDateTime,
                    onValueChange = onFromDateTimeChange,
                    modifier = Modifier.weight(1f),
                    enabled = readMode == DlmsProfileReadMode.RANGE,
                    numeric = false,
                    placeholder = "yyyy-MM-dd HH:mm"
                )
                Spacer(Modifier.width(6.dp))
                Text("To", color = Color(0xFF0F2527), fontSize = 11.sp, modifier = Modifier.width(16.dp))
                Spacer(Modifier.width(6.dp))
                ProfileInputField(
                    value = toDateTime,
                    onValueChange = onToDateTimeChange,
                    modifier = Modifier.weight(1f),
                    enabled = readMode == DlmsProfileReadMode.RANGE,
                    numeric = false,
                    placeholder = "yyyy-MM-dd HH:mm"
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ProfileField("Logical Name", controls.logicalName, Modifier.weight(1f))
            ProfileField("Period", controls.capturePeriod, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ProfileField("Entries", controls.entriesInUse, Modifier.weight(1f))
            ProfileField("Profile Entries", controls.profileEntries, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ProfileField("Sort Mode", controls.sortMode, Modifier.weight(1f))
            ProfileField("Sort Object", controls.sortObject, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ProfileOptionRow(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onSelect: () -> Unit,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier.clickable(onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelect,
            colors = RadioButtonDefaults.colors(
                selectedColor = Color(0xFF006C6F),
                unselectedColor = Color(0xFF9AA8AA)
            ),
            modifier = Modifier.size(24.dp)
        )
        Text(label, color = Color(0xFF0F2527), fontSize = 11.sp, modifier = Modifier.width(62.dp))
        trailing?.invoke()
    }
}

@Composable
private fun ProfileInputField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    numeric: Boolean,
    placeholder: String = ""
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        placeholder = {
            if (placeholder.isNotBlank()) {
                Text(placeholder, color = Color(0xFF9AA8AA), fontSize = 10.sp)
            }
        },
        textStyle = androidx.compose.ui.text.TextStyle(
            color = Color(0xFF0F2527),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        ),
        keyboardOptions = KeyboardOptions(
            keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text
        ),
        colors = profileTextFieldColors(),
        modifier = modifier.height(54.dp)
    )
}

@Composable
private fun profileTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Color(0xFF006C6F),
    unfocusedBorderColor = Color(0xFFD7E1E2),
    disabledBorderColor = Color(0xFFE2EAEA),
    focusedContainerColor = Color.White,
    unfocusedContainerColor = Color.White,
    disabledContainerColor = Color(0xFFEFF3F3),
    cursorColor = Color(0xFF006C6F)
)

@Composable
private fun ProfileField(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, color = Color(0xFF5E7375), fontSize = 10.sp)
        Spacer(Modifier.height(3.dp))
        Text(
            value.ifBlank { "-" },
            color = Color(0xFF0F2527),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(5.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun VisualSectionCard(
    section: DlmsVisualSection,
    attrAccessJson: String,
    onWriteRequest: (DlmsVisualRow) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(section.title, color = Color(0xFF006C6F), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        section.rows.forEach { row ->
            VisualRow(
                row = row,
                writable = row.attribute?.let { isAttributeWritable(attrAccessJson, it) } == true,
                onWriteRequest = onWriteRequest
            )
        }
    }
}

@Composable
private fun VisualRow(
    row: DlmsVisualRow,
    writable: Boolean,
    onWriteRequest: (DlmsVisualRow) -> Unit
) {
    val valueColor = when (row.kind) {
        DlmsVisualKind.ERROR -> Color(0xFFD71920)
        DlmsVisualKind.BOOLEAN -> if (row.value.equals("true", ignoreCase = true)) Color(0xFF238B45) else Color(0xFFB7791F)
        DlmsVisualKind.NUMBER -> Color(0xFF00575A)
        DlmsVisualKind.DATE_TIME -> Color(0xFF5B4B00)
        DlmsVisualKind.HEX -> Color(0xFF4D5B6A)
        else -> Color(0xFF0F2527)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(160))
            .background(Color(0xFFFAFCFC), RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(row.label, color = Color(0xFF5E7375), fontSize = 11.sp, modifier = Modifier.weight(0.42f))
            Column(modifier = Modifier.weight(0.58f)) {
                Text(
                    row.value,
                    color = valueColor,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    fontFamily = if (row.kind == DlmsVisualKind.HEX || row.kind == DlmsVisualKind.STRUCTURE) FontFamily.Monospace else FontFamily.Default,
                    fontWeight = if (row.kind == DlmsVisualKind.NUMBER) FontWeight.SemiBold else FontWeight.Normal
                )
                if (writable) {
                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = { onWriteRequest(row) }) {
                        Text("Write back", color = Color(0xFF006C6F), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        if (row.raw != null && row.raw != row.value) {
            Spacer(Modifier.height(4.dp))
            Text("Raw: ${row.raw}", color = Color(0xFF7A8F91), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun ProfileEmptyTable(message: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .background(Color.White, RoundedCornerShape(4.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(message, color = Color(0xFF7A8F91), fontSize = 12.sp)
    }
}

@Composable
private fun ProfileTable(title: String, table: DlmsProfileTable, maxRows: Int) {
    var page by remember(table) { mutableIntStateOf(0) }
    val rowsPerPage = maxRows.coerceAtLeast(20)
    val pageCount = ((table.rows.size + rowsPerPage - 1) / rowsPerPage).coerceAtLeast(1)
    if (page >= pageCount) page = pageCount - 1
    val fromIndex = (page * rowsPerPage).coerceAtMost(table.rows.size)
    val toIndex = (fromIndex + rowsPerPage).coerceAtMost(table.rows.size)
    val visibleRows = table.rows.subList(fromIndex, toIndex)
    Column(
        modifier = Modifier.animateContentSize(animationSpec = tween(180)),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Color(0xFF006C6F), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (table.rows.size > rowsPerPage) {
                TextButton(onClick = { page = (page - 1).coerceAtLeast(0) }, enabled = page > 0) {
                    Text("Prev", fontSize = 11.sp)
                }
                Text("${fromIndex + 1}-$toIndex / ${table.rows.size}", color = Color(0xFF5E7375), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                TextButton(onClick = { page = (page + 1).coerceAtMost(pageCount - 1) }, enabled = page < pageCount - 1) {
                    Text("Next", fontSize = 11.sp)
                }
            }
        }
        val horizontalState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(horizontalState)
                .background(Color.White, RoundedCornerShape(4.dp))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            ProfileTableRow(table.columns, header = true, dataTable = title == "Data")
            visibleRows.forEach { row -> ProfileTableRow(row, header = false, dataTable = title == "Data") }
            if (table.rows.isEmpty()) {
                Text("No rows returned", color = Color(0xFF7A8F91), fontSize = 11.sp, modifier = Modifier.padding(8.dp))
            }
            Spacer(Modifier.height(52.dp).fillMaxWidth().background(Color(0xFFB8B8B8)))
        }
    }
}

@Composable
private fun ProfileTableRow(cells: List<String>, header: Boolean, dataTable: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
        cells.forEachIndexed { index, cell ->
            val width = when {
                index == 0 -> 42.dp
                dataTable -> 148.dp
                index == 2 -> 132.dp
                else -> 108.dp
            }
            Text(
                cell.ifBlank { "-" },
                color = if (header) Color(0xFF006C6F) else Color(0xFF0F2527),
                fontSize = 10.sp,
                fontWeight = if (header) FontWeight.Bold else FontWeight.Normal,
                fontFamily = FontFamily.Monospace,
                lineHeight = 13.sp,
                modifier = Modifier
                    .width(width)
                    .height(if (header && dataTable) 78.dp else 32.dp)
                    .background(if (header) Color(0xFFF9FAFA) else Color.White)
                    .padding(horizontal = 6.dp, vertical = 5.dp)
            )
        }
    }
}

private fun classVisualizationLabel(classId: Int): String = when (classId) {
    1 -> "Data: typed attribute value"
    3 -> "Register: scaled value with engineering unit"
    4 -> "Extended Register: value, scaler, status and capture time"
    5 -> "Demand Register: current/last average with period metadata"
    7 -> "Profile Generic: capture-object table and profile metadata"
    8 -> "Clock: time, timezone and daylight-saving attributes"
    29, 40 -> "Push Setup: object list, destination, communication window and retries"
    else -> "Generic COSEM fallback"
}

@Composable
private fun RawTrafficFloatingBar(
    latest: RawTrafficEntry?,
    count: Int,
    modifier: Modifier = Modifier
) {
    val isTx = latest?.direction == "TX"
    val accent = if (isTx) Color(0xFFB7791F) else Color(0xFF006C6F)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(160)),
        colors = CardDefaults.cardColors(containerColor = Color(0xEEF8FBFB)),
        shape = RoundedCornerShape(6.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("RAW", color = Color(0xFF006C6F), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text("$count", color = Color(0xFF5E7375), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.width(10.dp))
            Text(
                latest?.let { "${it.direction} ${it.hex.chunked(2).take(22).joinToString(" ").uppercase()}" } ?: "No TX/RX frames yet",
                color = if (latest == null) Color(0xFF7A8F91) else accent,
                fontSize = 10.sp,
                lineHeight = 13.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 2,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

private fun isAttributeWritable(attrAccessJson: String, attribute: Int): Boolean {
    return try {
        val value = Json.parseToJsonElement(attrAccessJson)
            .jsonObject[attribute.toString()]
            ?.jsonPrimitive
            ?.content
            ?: return false
        value.contains("WRITE", ignoreCase = true) || value == "2" || value == "3"
    } catch (_: Exception) {
        false
    }
}

private fun parseWriteValue(value: String): JsonPrimitive {
    val trimmed = value.trim()
    return trimmed.toLongOrNull()?.let { JsonPrimitive(it) }
        ?: trimmed.toDoubleOrNull()?.let { JsonPrimitive(it) }
        ?: JsonPrimitive(trimmed)
}

@Composable
private fun ResultBlock(title: String, content: String, color: Color) {
    Column {
        Text(title, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                content,
                color = color,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}
