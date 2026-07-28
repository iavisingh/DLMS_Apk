package com.example.dlmsconfigurator

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import com.example.dlmsconfigurator.core.data.OperationEntity
import com.example.dlmsconfigurator.core.data.OperationItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive

sealed interface OpStatus {
    object Pending : OpStatus
    object Running : OpStatus
    data class Success(val result: String) : OpStatus
    data class Failed(val error: String) : OpStatus
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExecutionScreen(
    sessionId: Long,
    stagedFileId: String,
    onFinished: () -> Unit,
    onAborted: () -> Unit,
    repository: DataRepository
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val stagedFiles by repository.stagedFiles.collectAsState(initial = emptyList())
    val currentFile = stagedFiles.find { it.id == stagedFileId } ?: return

    val operations = currentFile.parsedContent?.operations ?: return
    
    // Track status and current values for each operation
    val opStatuses = remember { mutableStateListOf<OpStatus>().apply { 
        addAll(List(operations.size) { OpStatus.Pending })
    }}
    
    val writeValues = remember { mutableStateListOf<String>().apply {
        addAll(operations.map { it.defaultValue?.toString()?.removeSurrounding("\"") ?: "" })
    }}

    var discoveredSerial by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    fun runOperation(index: Int, isWrite: Boolean) {
        val op = operations[index]
        val engine = DlmsSessionHolder.activeEngine ?: return

        scope.launch {
            opStatuses[index] = OpStatus.Running
            
            val startTime = System.currentTimeMillis()
            var reqHex: String? = null
            var respHex: String? = null
            var decodedVal: String? = null
            var errorMsg: String? = null

            val result = withContext(Dispatchers.IO) {
                try {
                    if (isWrite) {
                        val newValue = writeValues[index]
                        // Create a modified operation item for the write
                        val writeOp = op.copy(type = "set", value = JsonPrimitive(newValue))
                        engine.executeSet(writeOp) { req, resp ->
                            reqHex = req
                            respHex = resp
                        }
                        decodedVal = "Written: $newValue"
                    } else {
                        // Read
                        val readOp = op.copy(type = "get")
                        decodedVal = engine.executeGet(readOp) { req, resp ->
                            reqHex = req
                            respHex = resp
                        }
                        if (op.obis == "0.0.96.1.0.255" && !decodedVal.isNullOrBlank()) {
                            discoveredSerial = decodedVal
                        }
                    }
                    true
                } catch (e: Exception) {
                    errorMsg = e.message ?: "Unknown Error"
                    false
                }
            }

            val endTime = System.currentTimeMillis()

            withContext(Dispatchers.IO) {
                repository.logOperation(
                    OperationEntity(
                        sessionId = sessionId,
                        sequenceNo = index,
                        opType = if (isWrite) "SET" else "GET",
                        obisCode = op.obis,
                        classId = op.classId,
                        attributeOrMethod = op.attribute ?: op.method ?: 1,
                        status = if (result) "SUCCESS" else "FAIL",
                        startTime = startTime,
                        endTime = endTime,
                        errorMessage = errorMsg,
                        attemptNumber = 1,
                        maxAttemptsConfigured = 1,
                        rawRequestHex = reqHex,
                        rawResponseHex = respHex,
                        decodedValue = decodedVal
                    )
                )
            }

            withContext(Dispatchers.Main) {
                if (result) {
                    opStatuses[index] = OpStatus.Success(decodedVal ?: "Success")
                } else {
                    opStatuses[index] = OpStatus.Failed(errorMsg ?: "Failed")
                }
            }
        }
    }

    fun resetAll() {
        opStatuses.indices.forEach { opStatuses[it] = OpStatus.Pending }
        // Potentially also clear write values to defaults
        operations.forEachIndexed { index, op ->
            writeValues[index] = op.defaultValue?.toString()?.removeSurrounding("\"") ?: ""
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Interactive Operations", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onAborted) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    Button(
                        onClick = { resetAll() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = Color(0xFF007AFF))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reset All", color = Color(0xFF007AFF))
                    }
                    IconButton(
                        onClick = {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    DlmsSessionHolder.activeEngine?.disconnect()
                                }
                                onAborted()
                            }
                        }
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = "Disconnect", tint = Color(0xFFFF3B30))
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
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(10.dp))
            
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(operations) { index, op ->
                    InteractiveOperationRow(
                        op = op,
                        status = opStatuses[index],
                        writeValue = writeValues[index],
                        onValueChange = { writeValues[index] = it },
                        onRead = { runOperation(index, false) },
                        onWrite = { runOperation(index, true) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            DlmsSessionHolder.activeEngine?.disconnect()
                        }
                        onFinished()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34C759)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .padding(bottom = 10.dp)
            ) {
                Text("Complete Session", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InteractiveOperationRow(
    op: OperationItem,
    status: OpStatus,
    writeValue: String,
    onValueChange: (String) -> Unit,
    onRead: () -> Unit,
    onWrite: () -> Unit
) {
    val permission = op.permission?.lowercase() ?: "read"
    val canRead = permission.contains("read")
    val canWrite = permission.contains("write")

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2F)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = op.name ?: "Object ${op.obis}",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "OBIS: ${op.obis} • Class: ${op.classId}",
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )
                }

                if (status is OpStatus.Running) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color(0xFF007AFF), strokeWidth = 2.dp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (canWrite) {
                OutlinedTextField(
                    value = writeValue,
                    onValueChange = onValueChange,
                    label = { Text("Value to Write", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0xFF161625),
                        unfocusedContainerColor = Color(0xFF161625),
                        focusedLabelColor = Color(0xFF007AFF)
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (canRead) {
                    Button(
                        onClick = onRead,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Read")
                    }
                }
                if (canWrite) {
                    Button(
                        onClick = onWrite,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34C759)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Write")
                    }
                }
            }

            if (status is OpStatus.Success) {
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF34C759).copy(alpha = 0.1f))
                        .padding(10.dp)
                ) {
                    Text(text = "Result: ${status.result}", color = Color(0xFF34C759), fontSize = 13.sp)
                }
            } else if (status is OpStatus.Failed) {
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFFF3B30).copy(alpha = 0.1f))
                        .padding(10.dp)
                ) {
                    Text(text = "Error: ${status.error}", color = Color(0xFFFF3B30), fontSize = 13.sp)
                }
            }
        }
    }
}
