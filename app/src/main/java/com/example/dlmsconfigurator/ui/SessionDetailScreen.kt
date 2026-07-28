package com.example.dlmsconfigurator

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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowRight
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dlmsconfigurator.core.data.DataRepository
import com.example.dlmsconfigurator.core.data.DefaultDataRepository
import com.example.dlmsconfigurator.core.data.OperationEntity
import com.example.dlmsconfigurator.core.data.SessionEntity
import com.example.dlmsconfigurator.core.data.AppDatabase
import com.example.dlmsconfigurator.core.data.SecureKeyStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(
    sessionId: Long,
    onOperationSelected: (Long) -> Unit,
    onBack: () -> Unit,
    repository: DataRepository
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var session by remember { mutableStateOf<SessionEntity?>(null) }
    var operations by remember { mutableStateOf<List<OperationEntity>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var filterTab by remember { mutableIntStateOf(0) } // 0 = All, 1 = Success, 2 = Failed

    LaunchedEffect(sessionId) {
        scope.launch(Dispatchers.IO) {
            val db = AppDatabase.getInstance(context, SecureKeyStore(context))
            session = db.sessionDao().getById(sessionId)
            operations = db.operationDao().getOperationsForSession(sessionId)
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(session?.jsonSourceFileName ?: "Session Log", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
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
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF007AFF))
            }
            return@Scaffold
        }

        val filteredOps = when (filterTab) {
            1 -> operations.filter { it.status == "SUCCESS" }
            2 -> operations.filter { it.status != "SUCCESS" }
            else -> operations
        }

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
            TabRow(
                selectedTabIndex = filterTab,
                containerColor = Color(0xFF0F0F1A),
                contentColor = Color.White,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[filterTab]),
                        color = Color(0xFF007AFF)
                    )
                }
            ) {
                Tab(
                    selected = filterTab == 0,
                    onClick = { filterTab = 0 },
                    text = { Text("All (${operations.size})", fontSize = 13.sp) }
                )
                Tab(
                    selected = filterTab == 1,
                    onClick = { filterTab = 1 },
                    text = { Text("Success (${operations.count { it.status == "SUCCESS" }})", fontSize = 13.sp) }
                )
                Tab(
                    selected = filterTab == 2,
                    onClick = { filterTab = 2 },
                    text = { Text("Failure (${operations.count { it.status != "SUCCESS" }})", fontSize = 13.sp) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (filteredOps.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No operations found.", color = Color.Gray, fontSize = 14.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredOps) { op ->
                        OperationLogCard(
                            op = op,
                            detailedLogging = session?.detailedLogging ?: false,
                            onClick = { onOperationSelected(op.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun OperationLogCard(
    op: OperationEntity,
    detailedLogging: Boolean,
    onClick: () -> Unit
) {
    val statusColor = when (op.status) {
        "SUCCESS" -> Color(0xFF34C759)
        else -> Color(0xFFFF3B30)
    }

    val isClickable = detailedLogging

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isClickable) Modifier.clickable(onClick = onClick) else Modifier),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2F)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(statusColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (op.status) {
                        "SUCCESS" -> Icons.Default.Check
                        else -> Icons.Default.Clear
                    },
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(14.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${op.opType}: ${op.obisCode}",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                
                val duration = (op.endTime - op.startTime)
                val attemptStr = if (op.attemptNumber > 1) " • Attempt ${op.attemptNumber}" else ""
                Text(
                    text = "Attr/Meth: ${op.attributeOrMethod} • Duration: ${duration}ms$attemptStr",
                    color = Color.LightGray,
                    fontSize = 12.sp
                )

                if (!op.errorMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = op.errorMessage,
                        color = Color(0xFFFF3B30),
                        fontSize = 12.sp
                    )
                } else if (!op.decodedValue.isNullOrBlank() && op.decodedValue != "Method Executed") {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Value: ${op.decodedValue}",
                        color = Color(0xFF34C759),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (isClickable) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowRight,
                    contentDescription = "Details",
                    tint = Color.Gray,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
