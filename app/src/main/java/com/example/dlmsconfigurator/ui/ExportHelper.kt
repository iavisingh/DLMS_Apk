package com.example.dlmsconfigurator.ui

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.dlmsconfigurator.core.data.OperationEntity
import com.example.dlmsconfigurator.core.data.SessionEntity
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File

object ExportHelper {
    fun exportSessionJson(
        context: Context,
        session: SessionEntity,
        operations: List<OperationEntity>
    ) {
        val jsonObject = buildJsonObject {
            put("sessionId", session.id)
            put("startTime", session.startTime)
            put("endTime", session.endTime ?: 0)
            put("meterSerial", session.meterSerial ?: "unknown")
            put("detailedLogging", session.detailedLogging)
            put("operations", buildJsonArray {
                operations.forEach { op ->
                    add(buildJsonObject {
                        put("id", op.id)
                        put("sessionId", op.sessionId)
                        put("sequenceNo", op.sequenceNo)
                        put("opType", op.opType)
                        put("obisCode", op.obisCode)
                        put("classId", op.classId)
                        put("attributeOrMethod", op.attributeOrMethod)
                        put("status", op.status)
                        put("startTime", op.startTime)
                        put("endTime", op.endTime)
                        put("errorMessage", op.errorMessage)
                        put("attemptNumber", op.attemptNumber)
                        put("maxAttemptsConfigured", op.maxAttemptsConfigured)
                        if (session.detailedLogging) {
                            put("rawRequestHex", op.rawRequestHex)
                            put("rawResponseHex", op.rawResponseHex)
                            put("decodedValue", op.decodedValue)
                        }
                    })
                }
            })
        }
        
        val jsonStr = jsonObject.toString()
        shareTextFile(context, "session_${session.id}.json", jsonStr, "application/json")
    }

    fun exportSessionCsv(
        context: Context,
        session: SessionEntity,
        operations: List<OperationEntity>
    ) {
        val csv = StringBuilder()
        csv.append("Sequence,Type,OBIS,ClassID,Attr/Method,Status,Start,End,Attempt,MaxAttempts,Error,DecodedValue\n")
        
        operations.forEach { op ->
            val decodedEscaped = op.decodedValue?.replace("\"", "\"\"") ?: ""
            val errorEscaped = op.errorMessage?.replace("\"", "\"\"") ?: ""
            csv.append("${op.sequenceNo},${op.opType},${op.obisCode},${op.classId},${op.attributeOrMethod},${op.status},${op.startTime},${op.endTime},${op.attemptNumber},${op.maxAttemptsConfigured},\"$errorEscaped\",\"$decodedEscaped\"\n")
        }

        shareTextFile(context, "session_${session.id}.csv", csv.toString(), "text/csv")
    }

    private fun shareTextFile(context: Context, fileName: String, content: String, mimeType: String) {
        val cacheDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(cacheDir, fileName)
        file.writeText(content)

        val uri = FileProvider.getUriForFile(context, "com.example.dlmsconfigurator.fileprovider", file)
        
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        val chooser = Intent.createChooser(intent, "Export Session Log").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
