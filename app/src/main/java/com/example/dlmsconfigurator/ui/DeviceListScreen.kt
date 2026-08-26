package com.example.dlmsconfigurator

import android.text.format.DateFormat
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dlmsconfigurator.core.data.CommSettings
import com.example.dlmsconfigurator.core.data.DataRepository
import com.example.dlmsconfigurator.core.data.DeviceEntity
import com.example.dlmsconfigurator.core.data.commSettings
import com.example.dlmsconfigurator.core.data.transportLabel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceListScreen(
    repository: DataRepository,
    onAddDevice: () -> Unit,
    onEditDevice: (Long) -> Unit,
    onConnectDevice: (Long) -> Unit,
    onDisconnectDevice: (Long) -> Unit,
    onBack: () -> Unit
) {
    val devices by repository.getDevicesFlow().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    var deleteConfirmDevice by remember { mutableStateOf<DeviceEntity?>(null) }
    var selectedDeviceId by remember { mutableStateOf<Long?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Meters", color = Color(0xFF0F2527), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text("${devices.size} meter${if (devices.size == 1) "" else "s"}", color = Color(0xFF5E7375), fontSize = 12.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFF8FBFB))
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddDevice,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add Meter", fontWeight = FontWeight.SemiBold) },
                containerColor = Color(0xFF006C6F),
                contentColor = Color.White
            )
        },
        containerColor = Color(0xFFF4F7F7)
    ) { innerPadding ->
        if (devices.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .background(
                                Brush.radialGradient(listOf(Color(0xFF006C6F).copy(alpha = 0.22f), Color.Transparent)),
                                RoundedCornerShape(20.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Router, contentDescription = null, tint = Color(0xFF006C6F), modifier = Modifier.size(40.dp))
                    }
                    Spacer(Modifier.height(20.dp))
                    Text("No meters saved", color = Color(0xFF0F2527), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text("Tap + to add your first meter profile", color = Color(0xFF5E7375), fontSize = 14.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(devices, key = { it.id }) { device ->
                    DeviceCard(
                        device = device,
                        selected = selectedDeviceId == device.id,
                        onClick = { selectedDeviceId = if (selectedDeviceId == device.id) null else device.id },
                        onLongClick = { selectedDeviceId = if (selectedDeviceId == device.id) null else device.id },
                        onConnect = { onConnectDevice(device.id) },
                        onDisconnect = {
                            onDisconnectDevice(device.id)
                            selectedDeviceId = null
                        },
                        onEdit = { onEditDevice(device.id) },
                        onDelete = { deleteConfirmDevice = device }
                    )
                }
                item { Spacer(Modifier.height(80.dp)) } // FAB clearance
            }
        }
    }

    // ── Delete confirmation dialog ─────────────────────────────────────────────
    deleteConfirmDevice?.let { device ->
        AlertDialog(
            onDismissRequest = { deleteConfirmDevice = null },
            containerColor = Color.White,
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFD71920)) },
            title = { Text("Delete Meter?", color = Color(0xFF0F2527), fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "\"${device.name}\" and all its cached objects will be permanently deleted. This cannot be undone.",
                    color = Color(0xFF5E7375)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch { repository.deleteDevice(device) }
                        deleteConfirmDevice = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD71920))
                ) { Text("Delete", fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmDevice = null }) {
                    Text("Cancel", color = Color(0xFF5E7375))
                }
            }
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun DeviceCard(
    device: DeviceEntity,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val accentColor = when (device.commSettings) {
        is CommSettings.Otg -> Color(0xFF006C6F)
        is CommSettings.Ble -> Color(0xFF1976D2)
        is CommSettings.Tcp -> Color(0xFF238B45)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(containerColor = if (selected) Color(0xFFEDF7F7) else Color.White),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color(0xFFDDEDEE), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Router, contentDescription = null, tint = accentColor, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(device.name, color = Color(0xFF0F2527), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(2.dp))
                    val commDetail = when (val c = device.commSettings) {
                        is CommSettings.Otg -> "${c.baudRate} baud"
                        is CommSettings.Ble -> c.deviceName.ifBlank { c.deviceAddress.ifBlank { "BLE address not set" } }
                        is CommSettings.Tcp -> "${c.host}:${c.port}"
                    }
                    Text(commDetail, color = Color(0xFF5E7375), fontSize = 12.sp)
                    device.lastConnectedAt?.let { ts ->
                        val fmt = DateFormat.format("dd MMM yyyy HH:mm", ts).toString()
                        Text("Last connected  $fmt", color = Color(0xFF7A8F91), fontSize = 11.sp)
                    }
                }

                Box(
                    modifier = Modifier
                        .background(accentColor, RoundedCornerShape(14.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        device.transportLabel,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            if (selected) {
                Column {
                    HorizontalDivider(color = Color(0xFFD7E1E2))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF2FAFA))
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        MeterActionButton(Icons.Default.PlayArrow, "Connect", Color(0xFF006C6F), onConnect)
                        MeterActionButton(Icons.Default.Close, "Disconnect", Color(0xFF5E7375), onDisconnect)
                        MeterActionButton(Icons.Default.Edit, "Properties", Color(0xFF1976D2), onEdit)
                        MeterActionButton(Icons.Default.Delete, "Delete", Color(0xFFD71920), onDelete)
                    }
                }
            }
        }
    }
}

@Composable
private fun MeterActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}
