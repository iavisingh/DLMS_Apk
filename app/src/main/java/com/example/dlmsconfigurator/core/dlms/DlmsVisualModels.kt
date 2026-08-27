package com.example.dlmsconfigurator.core.dlms

data class DlmsVisualSnapshot(
    val title: String,
    val classId: Int,
    val obisCode: String,
    val sections: List<DlmsVisualSection>,
    val profileControls: DlmsProfileControls? = null,
    val profileTable: DlmsProfileTable? = null,
    val profileDataTable: DlmsProfileTable? = null,
    val profileCaptureTable: DlmsProfileTable? = null
)

data class DlmsVisualSection(
    val title: String,
    val rows: List<DlmsVisualRow>
)

data class DlmsVisualRow(
    val label: String,
    val value: String,
    val kind: DlmsVisualKind = DlmsVisualKind.TEXT,
    val raw: String? = null
)

data class DlmsProfileTable(
    val columns: List<String>,
    val rows: List<List<String>>
)

data class DlmsProfileControls(
    val logicalName: String,
    val capturePeriod: String,
    val entriesInUse: String,
    val profileEntries: String,
    val sortMode: String,
    val sortObject: String
)

data class DlmsProfileReadRequest(
    val mode: DlmsProfileReadMode = DlmsProfileReadMode.ALL,
    val startEntry: Int = 1,
    val entryCount: Int = 20,
    val lastDays: Int = 1,
    val fromDateTime: String = "",
    val toDateTime: String = ""
)

enum class DlmsProfileReadMode {
    ENTRY,
    LAST_DAYS,
    RANGE,
    ALL
}

enum class DlmsVisualKind {
    BOOLEAN,
    NUMBER,
    TEXT,
    HEX,
    DATE_TIME,
    STRUCTURE,
    ERROR
}

data class ScalerUnit(
    val scaler: Int,
    val unitCode: Int,
    val unitName: String
)
