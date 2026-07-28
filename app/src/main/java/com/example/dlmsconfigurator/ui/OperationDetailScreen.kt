package com.example.dlmsconfigurator

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dlmsconfigurator.core.data.DataRepository
import com.example.dlmsconfigurator.core.data.DefaultDataRepository
import com.example.dlmsconfigurator.core.data.OperationEntity
import com.example.dlmsconfigurator.core.data.AppDatabase
import com.example.dlmsconfigurator.core.data.SecureKeyStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OperationDetailScreen(
    operationId: Long,
    onBack: () -> Unit,
    repository: DataRepository
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var operation by remember { mutableStateOf<OperationEntity?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(operationId) {
        scope.launch(Dispatchers.IO) {
            val db = AppDatabase.getInstance(context, SecureKeyStore(context))
            operation = db.operationDao().getById(operationId)
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Operation Detail", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
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

        val op = operation ?: return@Scaffold

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
            // General info card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2F)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("General Parameters", color = Color(0xFF007AFF), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(10.dp))

                    DetailRow("Operation Type", op.opType)
                    DetailRow("OBIS Code", op.obisCode)
                    DetailRow("Class ID", op.classId.toString())
                    DetailRow("Attr / Method Index", op.attributeOrMethod.toString())
                    DetailRow("Status", op.status)
                    DetailRow("Attempt Number", op.attemptNumber.toString())
                    DetailRow("Max Configured", op.maxAttemptsConfigured.toString())
                    DetailRow("Execution Duration", "${op.endTime - op.startTime}ms")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Value card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2F)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Decoded Result", color = Color(0xFF007AFF), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = op.decodedValue ?: "No decoded value",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (!op.errorMessage.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = op.errorMessage,
                            color = Color(0xFFFF3B30),
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Hex logs
            HexBlock("TX Raw Request (Hex)", op.rawRequestHex ?: "No request payload logged")
            Spacer(modifier = Modifier.height(16.dp))
            HexBlock("RX Raw Response (Hex)", op.rawResponseHex ?: "No response payload logged")
        }
    }
}

@Composable
fun HexBlock(title: String, hexContent: String) {
    Text(
        text = title,
        color = Color.LightGray,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 6.dp)
    )
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF090910)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Box(modifier = Modifier.padding(14.dp)) {
            Text(
                text = hexContent.chunked(32).joinToString("\n") { line ->
                    line.chunked(2).joinToString(" ")
                },
                color = Color(0xFF34C759),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
        }
    }
}
