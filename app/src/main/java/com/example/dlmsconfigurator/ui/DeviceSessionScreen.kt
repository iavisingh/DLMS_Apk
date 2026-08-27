package com.example.dlmsconfigurator

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.dlmsconfigurator.core.data.AssociationObjectEntity
import com.example.dlmsconfigurator.core.data.DataRepository
import com.example.dlmsconfigurator.core.dlms.DlmsVisualKind
import com.example.dlmsconfigurator.core.dlms.DlmsVisualSnapshot
import com.example.dlmsconfigurator.ui.ConnectionState
import com.example.dlmsconfigurator.ui.DeviceSessionViewModel
import com.example.dlmsconfigurator.ui.RawTrafficEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DeviceSessionScreen(
    deviceId: Long,
    repository: DataRepository,
    onObjectSelected: (obisCode: String, classId: Int) -> Unit,
    onBack: () -> Unit,
    viewModel: DeviceSessionViewModel = viewModel()
) {
    val context = LocalContext.current
    val connectionState by viewModel.connectionState.collectAsState()
    val assocObjects by viewModel.associationObjects.collectAsState()
    val statusLog by viewModel.statusLog.collectAsState()
    val rawTrafficLog by viewModel.rawTrafficLog.collectAsState()
    val isReadingAssocView by viewModel.isReadingAssocView.collectAsState()
    val vmError by viewModel.error.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    var deviceName by remember { mutableStateOf("") }
    var showAssocPrompt by remember { mutableStateOf(false) }
    var showRawTraffic by remember { mutableStateOf(false) }
    var rawCopied by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val categoryReadValues = remember(deviceId) { mutableStateMapOf<String, String>() }
    var pendingCategoryRead by remember { mutableStateOf<Pair<String, List<AssociationObjectEntity>>?>(null) }
    var readingCategory by remember { mutableStateOf<String?>(null) }

    // Load device name
    LaunchedEffect(deviceId) {
        val d = repository.getDevice(deviceId)
        deviceName = d?.name ?: "Device #$deviceId"
    }

    // Auto-connect on enter
    LaunchedEffect(deviceId) {
        if (connectionState is ConnectionState.Idle) {
            viewModel.connect(deviceId, repository, context)
        }
    }

    // Show assoc-view prompt once connected and no cache
    LaunchedEffect(connectionState) {
        if (connectionState is ConnectionState.Connected && assocObjects.isEmpty()) {
            showAssocPrompt = true
        }
    }

    LaunchedEffect(rawTrafficLog.size) {
        rawCopied = false
    }

    val accentBlue = Color(0xFF006C6F)
    val statusColor by animateColorAsState(
        targetValue = when (connectionState) {
            is ConnectionState.Connected  -> Color(0xFF238B45)
            is ConnectionState.Connecting,
            is ConnectionState.Associating -> Color(0xFFB7791F)
            is ConnectionState.Failed     -> Color(0xFFD71920)
            else                          -> Color(0xFF7A8F91)
        },
        animationSpec = tween(160),
        label = "statusColor"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(deviceName, color = Color(0xFF0F2527), fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val isPulsing = connectionState is ConnectionState.Connecting || connectionState is ConnectionState.Associating
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .background(statusColor, CircleShape)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = when (connectionState) {
                                    is ConnectionState.Idle       -> "Idle"
                                    is ConnectionState.Connecting -> (connectionState as ConnectionState.Connecting).message
                                    is ConnectionState.Associating -> (connectionState as ConnectionState.Associating).message
                                    is ConnectionState.Connected  -> "Connected"
                                    is ConnectionState.Failed     -> "Failed"
                                },
                                color = statusColor,
                                fontSize = 12.sp
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        onBack()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color(0xFF0F2527))
                    }
                },
                actions = {
                    if (connectionState is ConnectionState.Connected) {
                        TextButton(onClick = { viewModel.readAssociationView(deviceId, repository) }) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = accentBlue, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Refresh", color = accentBlue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    TextButton(
                        enabled = connectionState !is ConnectionState.Connecting && connectionState !is ConnectionState.Associating,
                        onClick = {
                            if (connectionState is ConnectionState.Connected) {
                                viewModel.disconnect()
                            } else {
                                viewModel.connect(deviceId, repository, context)
                            }
                        }
                    ) {
                        Text(
                            if (connectionState is ConnectionState.Connected) "Disconnect" else "Connect",
                            color = if (connectionState is ConnectionState.Connected) Color(0xFFD71920) else accentBlue,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFF8FBFB))
            )
        },
        containerColor = Color(0xFFF4F7F7)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (showRawTraffic) {
                RawTrafficPanel(
                    entries = rawTrafficLog,
                    copied = rawCopied,
                    onBack = { showRawTraffic = false },
                    onCopy = {
                        clipboardManager.setText(AnnotatedString(formatRawTraffic(rawTrafficLog)))
                        rawCopied = true
                    }
                )
            } else {
                // ── Status / Log strip ────────────────────────────────────────
                if (connectionState !is ConnectionState.Connected || statusLog.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                statusLog.takeLast(6).forEach { msg ->
                                    Text(
                                        "> $msg",
                                        color = if (msg.startsWith("✗")) Color(0xFFD71920)
                                                else if (msg.startsWith("✓")) Color(0xFF238B45)
                                                else Color(0xFF5E7375),
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        lineHeight = 16.sp
                                    )
                                }
                                if (connectionState is ConnectionState.Connecting || connectionState is ConnectionState.Associating || isReadingAssocView) {
                                    Spacer(Modifier.height(8.dp))
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = Color(0xFFB7791F),
                                        strokeWidth = 2.dp
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        RawTrafficPreview(
                            latest = rawTrafficLog.lastOrNull(),
                            count = rawTrafficLog.size,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(70.dp)
                                .clickable {
                                    rawCopied = false
                                    showRawTraffic = true
                                }
                        )
                    }
                }

                // ── Failed state ──────────────────────────────────────────────
                if (connectionState is ConnectionState.Failed) {
                    val errMsg = (connectionState as ConnectionState.Failed).error
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Connection Failed", color = Color(0xFFD71920), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Spacer(Modifier.height(4.dp))
                                Text(errMsg, color = Color(0xFF5E7375), fontSize = 12.sp, lineHeight = 18.sp)
                                Spacer(Modifier.height(12.dp))
                                Button(
                                    onClick = { viewModel.connect(deviceId, repository, context) },
                                    colors = ButtonDefaults.buttonColors(containerColor = accentBlue),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Retry")
                                }
                            }
                        }
                    }
                }

                // ── Object List ───────────────────────────────────────────────
                val listState = rememberLazyListState()
                if (assocObjects.isNotEmpty()) {
                    val expandedGroups = remember(deviceId) { mutableStateMapOf<String, Boolean>() }
                    val groupedObjects = remember(assocObjects) {
                        assocObjects
                            .groupBy { objectCategory(it) }
                            .toSortedMap(compareBy { categorySortOrder(it) })
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${assocObjects.size} COSEM Objects", color = Color(0xFF5E7375), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        if (isReadingAssocView) {
                            Text("Refreshing...", color = Color(0xFFB7791F), fontSize = 11.sp)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        groupedObjects.forEach { (category, objects) ->
                            stickyHeader(key = "category_$category") {
                                CosemCategoryRow(
                                    name = category,
                                    count = objects.size,
                                    expanded = expandedGroups[category] == true,
                                    isReading = readingCategory == category,
                                    onClick = { expandedGroups[category] = expandedGroups[category] != true },
                                    onLongClick = {
                                        expandedGroups[category] = true
                                        pendingCategoryRead = category to objects
                                    }
                                )
                            }
                            if (expandedGroups[category] == true) {
                                items(objects, key = { it.id }) { obj ->
                                    ObjectListItem(
                                        obj = obj,
                                        readValue = categoryReadValues[obj.obisCode],
                                        isConnected = connectionState is ConnectionState.Connected,
                                        onClick = {
                                            if (connectionState is ConnectionState.Connected) {
                                                onObjectSelected(obj.obisCode, obj.classId)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                        item { Spacer(Modifier.height(16.dp)) }
                    }
                } else if (connectionState is ConnectionState.Connected) {
                    // No cache, no prompt (prompt handled via dialog below)
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Router, contentDescription = null, tint = accentBlue, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("Connected - no objects cached yet", color = Color(0xFF5E7375), fontSize = 14.sp)
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = { viewModel.readAssociationView(deviceId, repository) },
                                colors = ButtonDefaults.buttonColors(containerColor = accentBlue)
                            ) { Text("Read Association View") }
                        }
                    }
                }
            }
        }
    }

    // ── Read Association View Prompt ───────────────────────────────────────────
    if (showAssocPrompt && connectionState is ConnectionState.Connected) {
        AlertDialog(
            onDismissRequest = { showAssocPrompt = false },
            containerColor = Color.White,
            title = { Text("Read Association View?", color = Color(0xFF0F2527), fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "No COSEM object cache found for this device. Would you like to read the Association View (Class 15) now?\n\nThis fetches the full object directory from the meter.",
                    color = Color(0xFF5E7375),
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showAssocPrompt = false
                        viewModel.readAssociationView(deviceId, repository)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accentBlue)
                ) { Text("Read Now", fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { showAssocPrompt = false }) {
                    Text("Skip", color = Color(0xFF5E7375))
                }
            }
        )
    }

    pendingCategoryRead?.let { (category, objects) ->
        AlertDialog(
            onDismissRequest = { pendingCategoryRead = null },
            containerColor = Color.White,
            title = { Text("Read $category?", color = Color(0xFF0F2527), fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Read all ${objects.size} objects under $category and show decoded values beside each OBIS?",
                    color = Color(0xFF5E7375),
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingCategoryRead = null
                        val activeEngine = viewModel.activeEngine ?: return@Button
                        readingCategory = category
                        scope.launch(Dispatchers.IO) {
                            objects.forEach { obj ->
                                val displayValue = try {
                                    summarizeObjectSnapshot(activeEngine.readObjectSnapshot(obj.classId, obj.obisCode))
                                } catch (e: Exception) {
                                    "Read failed: ${e.message ?: "unknown error"}"
                                }
                                withContext(Dispatchers.Main) {
                                    categoryReadValues[obj.obisCode] = displayValue
                                }
                            }
                            withContext(Dispatchers.Main) {
                                readingCategory = null
                            }
                        }
                    },
                    enabled = connectionState is ConnectionState.Connected && readingCategory == null,
                    colors = ButtonDefaults.buttonColors(containerColor = accentBlue)
                ) { Text("Read", fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { pendingCategoryRead = null }) {
                    Text("Cancel", color = Color(0xFF5E7375))
                }
            }
        )
    }

    // ── ViewModel error dialog ─────────────────────────────────────────────────
    vmError?.let { err ->
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            containerColor = Color.White,
            title = { Text("Error", color = Color(0xFFD71920), fontWeight = FontWeight.Bold) },
            text = { Text(err, color = Color(0xFF5E7375)) },
            confirmButton = {
                TextButton(onClick = viewModel::clearError) { Text("OK", color = accentBlue) }
            }
        )
    }
}

@Composable
private fun RawTrafficPreview(
    latest: RawTrafficEntry?,
    count: Int,
    modifier: Modifier = Modifier
) {
    val isTx = latest?.direction == "TX"
    val accent = if (isTx) Color(0xFFB7791F) else Color(0xFF006C6F)
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = if (latest == null) Color(0xFFE8F0F1) else Color(0xFFDDEDEE)),
        shape = RoundedCornerShape(4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.width(72.dp)) {
                Text("RAW", color = Color(0xFF006C6F), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("$count", color = Color(0xFF5E7375), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }
            Spacer(Modifier.width(8.dp))
            if (latest == null) {
                Text("No TX/RX frames yet", color = Color(0xFF7A8F91), fontSize = 10.sp, modifier = Modifier.weight(1f))
            } else {
                Text(
                    "${latest.direction} ${latest.hex.chunked(2).take(28).joinToString(" ").uppercase()}",
                    color = accent,
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 3,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun RawTrafficPanel(
    entries: List<RawTrafficEntry>,
    copied: Boolean,
    onBack: () -> Unit,
    onCopy: () -> Unit
) {
    val accentBlue = Color(0xFF006C6F)
    val listState = rememberLazyListState()

    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) {
            listState.animateScrollToItem(entries.lastIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text("Back", color = Color(0xFF5E7375), fontWeight = FontWeight.SemiBold)
            }
            Text(
                "Raw Communication",
                color = Color(0xFF0F2527),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onCopy, enabled = entries.isNotEmpty()) {
                Text(if (copied) "Copied" else "Copy", color = accentBlue, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(6.dp))
        Card(
            modifier = Modifier.fillMaxSize(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(8.dp)
        ) {
            if (entries.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No raw TX/RX frames yet", color = Color(0xFF7A8F91), fontSize = 12.sp)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(entries) { entry ->
                        RawTrafficRow(entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun RawTrafficRow(entry: RawTrafficEntry) {
    val isTx = entry.direction == "TX"
    val labelColor = if (isTx) Color(0xFFB7791F) else Color(0xFF006C6F)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFFAFCFC), RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFFE2ECEC), RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(entry.direction, color = labelColor, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.width(8.dp))
            Text(formatTrafficTime(entry.timestampMs), color = Color(0xFF7A8F91), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.height(5.dp))
        Text(
            formatHexForDisplay(entry.hex),
            color = Color(0xFF0F2527),
            fontSize = 11.sp,
            lineHeight = 16.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

private fun formatRawTraffic(entries: List<RawTrafficEntry>): String {
    return entries.joinToString("\n\n") { entry ->
        "${formatTrafficTime(entry.timestampMs)} ${entry.direction}\n${formatHexForDisplay(entry.hex)}"
    }
}

private fun formatTrafficTime(timestampMs: Long): String {
    return SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(timestampMs))
}

private fun formatHexForDisplay(hex: String): String {
    return hex.chunked(2).joinToString(" ")
}

@Composable
private fun CosemCategoryRow(
    name: String,
    count: Int,
    expanded: Boolean,
    isReading: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(180))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (expanded) "v" else ">",
                color = Color(0xFF006C6F),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(24.dp)
            )
            Text(
                name,
                color = Color(0xFF0F2527),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            if (isReading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    color = Color(0xFFB7791F),
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(8.dp))
                Text("Reading...", color = Color(0xFFB7791F), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
            }
            Text("$count", color = Color(0xFF5E7375), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

private fun objectCategory(obj: AssociationObjectEntity): String {
    return when (obj.classId) {
        1 -> "Data"
        3 -> "Register"
        4 -> "Extended Register"
        5 -> "Demand Register"
        7 -> "Profile Generic"
        8 -> "Clock"
        9 -> "Script Table"
        10, 22 -> "Schedule"
        15 -> "Association LN"
        17 -> "SAP Assignment"
        20 -> "Register Monitor"
        23 -> "Activity Calendar"
        24 -> "Security Setup"
        29, 40 -> "Push Setup"
        70 -> "Disconnect Control"
        71 -> "Limiter"
        else -> if (obj.className.isNotBlank() && !obj.className.startsWith("Class ")) obj.className else "Other/Unknown"
    }
}

private fun categorySortOrder(category: String): Int {
    val order = listOf(
        "Data",
        "Register",
        "Extended Register",
        "Demand Register",
        "Profile Generic",
        "Clock",
        "Script Table",
        "Schedule",
        "Activity Calendar",
        "Register Monitor",
        "Disconnect Control",
        "Limiter",
        "Association LN",
        "Security Setup",
        "SAP Assignment",
        "Push Setup",
        "Other/Unknown"
    )
    val idx = order.indexOf(category)
    return if (idx >= 0) idx else order.lastIndex
}

@Composable
private fun ObjectListItem(
    obj: AssociationObjectEntity,
    readValue: String?,
    isConnected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(160))
            .clickable(enabled = isConnected, onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFAFCFC)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Class ID badge
            Box(
                modifier = Modifier
                    .background(Color(0xFFDDEDEE), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "C${obj.classId}",
                    color = Color(0xFF006C6F),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        obj.obisCode,
                        color = Color(0xFF00575A),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f)
                    )
                    readValue?.let {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            it,
                            color = if (it.startsWith("Read failed")) Color(0xFFD71920) else Color(0xFF0F2527),
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                if (obj.className.isNotBlank()) {
                    Text(obj.className, color = Color(0xFF5E7375), fontSize = 11.sp)
                }
            }
            if (isConnected) {
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color(0xFF7A8F91), modifier = Modifier.size(18.dp))
            }
        }
    }
}

private fun summarizeObjectSnapshot(snapshot: DlmsVisualSnapshot): String {
    if (snapshot.classId == 7) {
        val rows = snapshot.profileDataTable?.rows?.size ?: 0
        val captures = snapshot.profileCaptureTable?.rows?.size ?: 0
        return when {
            rows > 0 -> "$rows rows, $captures captures"
            captures > 0 -> "$captures capture objects"
            else -> "No profile rows"
        }
    }

    val rows = snapshot.sections.flatMap { it.rows }
    val primary = rows.firstOrNull { it.kind != DlmsVisualKind.ERROR && it.label !in listOf("Scaler", "Unit") }
        ?: rows.firstOrNull()
        ?: return "No value"
    val value = if (primary.raw != null && primary.raw != primary.value && primary.label.contains("Scaled", ignoreCase = true)) {
        primary.value
    } else {
        primary.value
    }
    return value
        .replace('\n', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(96)
        .ifBlank { "No value" }
}
