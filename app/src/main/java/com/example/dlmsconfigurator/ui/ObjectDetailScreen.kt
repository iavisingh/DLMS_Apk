package com.example.dlmsconfigurator

import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.dlmsconfigurator.core.data.DataRepository
import com.example.dlmsconfigurator.core.dlms.DlmsProfileControls
import com.example.dlmsconfigurator.core.dlms.DlmsProfileTable
import com.example.dlmsconfigurator.core.dlms.DlmsVisualKind
import com.example.dlmsconfigurator.core.dlms.DlmsVisualRow
import com.example.dlmsconfigurator.core.dlms.DlmsVisualSection
import com.example.dlmsconfigurator.core.dlms.DlmsVisualSnapshot
import com.example.dlmsconfigurator.ui.ConnectionState
import com.example.dlmsconfigurator.ui.DeviceSessionViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val engine = sessionViewModel.activeEngine
    val isConnected = connectionState is ConnectionState.Connected && engine != null

    // Cached object metadata
    var objectName by remember { mutableStateOf("") }
    var smartSnapshot by remember { mutableStateOf<DlmsVisualSnapshot?>(null) }
    var smartError by remember { mutableStateOf<String?>(null) }
    var isLoadingSmartView by remember { mutableStateOf(false) }

    LaunchedEffect(deviceId, obisCode) {
        val obj = repository.getAssociationObjects(deviceId).find { it.obisCode == obisCode }
        objectName = obj?.className?.ifBlank { "Class $classId Object" } ?: "Class $classId Object"
    }

    fun loadSmartView() {
        val activeEngine = sessionViewModel.activeEngine ?: return
        isLoadingSmartView = true
        smartError = null
        scope.launch(Dispatchers.IO) {
            try {
                val snapshot = activeEngine.readObjectSnapshot(classId, obisCode)
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
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
                onRefresh = { loadSmartView() }
            )
            Spacer(Modifier.height(16.dp))
        }
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
    onRefresh: () -> Unit
) {
    val accentBlue = Color(0xFF006C6F)
    if (classId == 7) {
        ProfileGenericWindow(
            snapshot = snapshot,
            error = error,
            loading = loading,
            connected = connected,
            obisCode = obisCode,
            onRead = onRefresh
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
                    snapshot.sections.forEach { section -> VisualSectionCard(section) }
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
    onRead: () -> Unit
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
                onRead = onRead
            )

            when {
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
                ProfileOptionRow("Read", selected = false, modifier = Modifier.weight(1f), trailing = {
                    ProfileInlineField("1", Modifier.width(56.dp))
                })
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.width(126.dp)) {
                    Text("Count:", color = Color(0xFF0F2527), fontSize = 11.sp)
                    Spacer(Modifier.width(6.dp))
                    ProfileInlineField("20", Modifier.weight(1f))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                ProfileOptionRow("Read last", selected = false, modifier = Modifier.weight(1f), trailing = {
                    ProfileInlineField("0", Modifier.width(56.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Days", color = Color(0xFF0F2527), fontSize = 11.sp, modifier = Modifier.width(34.dp))
                })
                ProfileOptionRow("All", selected = true, modifier = Modifier.width(126.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                ProfileRadioDot(selected = false)
                Spacer(Modifier.width(8.dp))
                Text("Read From", color = Color(0xFF0F2527), fontSize = 11.sp, modifier = Modifier.width(62.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    ProfileInlineField("", Modifier.weight(1f), enabled = false)
                    Spacer(Modifier.width(6.dp))
                    Text("To", color = Color(0xFF0F2527), fontSize = 11.sp, modifier = Modifier.width(16.dp))
                    Spacer(Modifier.width(6.dp))
                    ProfileInlineField("", Modifier.weight(1f), enabled = false)
                }
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
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ProfileRadioDot(selected)
        Text(label, color = Color(0xFF0F2527), fontSize = 11.sp, modifier = Modifier.width(62.dp))
        trailing?.invoke()
    }
}

@Composable
private fun ProfileRadioDot(selected: Boolean) {
    Box(
        modifier = Modifier
            .size(13.dp)
            .background(if (selected) Color(0xFF006C6F) else Color.White, RoundedCornerShape(7.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Box(Modifier.size(5.dp).background(Color.White, RoundedCornerShape(3.dp)))
        }
    }
}

@Composable
private fun ProfileInlineField(value: String, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Text(
        value.ifBlank { " " },
        color = if (enabled) Color(0xFF0F2527) else Color(0xFF9AA8AA),
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace,
        modifier = modifier
            .background(if (enabled) Color(0xFFF9FAFA) else Color(0xFFE9EDED), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    )
}

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
private fun VisualSectionCard(section: DlmsVisualSection) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(section.title, color = Color(0xFF006C6F), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        section.rows.forEach { row -> VisualRow(row) }
    }
}

@Composable
private fun VisualRow(row: DlmsVisualRow) {
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
            .background(Color(0xFFFAFCFC), RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(row.label, color = Color(0xFF5E7375), fontSize = 11.sp, modifier = Modifier.weight(0.42f))
            Text(
                row.value,
                color = valueColor,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                fontFamily = if (row.kind == DlmsVisualKind.HEX || row.kind == DlmsVisualKind.STRUCTURE) FontFamily.Monospace else FontFamily.Default,
                fontWeight = if (row.kind == DlmsVisualKind.NUMBER) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.weight(0.58f)
            )
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
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = Color(0xFF006C6F), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
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
            table.rows.take(maxRows).forEach { row -> ProfileTableRow(row, header = false, dataTable = title == "Data") }
            if (table.rows.size > maxRows) {
                Text("${table.rows.size - maxRows} more rows hidden", color = Color(0xFF7A8F91), fontSize = 10.sp)
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
