package com.example.dlmsconfigurator.core.dlms

fun resolveObisDisplayName(obisCode: String, classId: Int): String? {
    obisNames[obisCode]?.let { return it }

    val parts = obisCode.split(".").mapNotNull { it.toIntOrNull() }
    if (parts.size != 6) return null

    val medium = parts[0]
    val channel = parts[1]
    val quantity = parts[2]
    val measurement = parts[3]
    val tariff = parts[4]
    val storage = parts[5]
    if (medium != 1 || channel != 0 || storage != 255) return null

    val tariffSuffix = if (tariff == 0) "" else " - tariff $tariff"
    return when (measurement) {
        7 -> instantaneousName(quantity)?.let { "$it$tariffSuffix" }
        8 -> energyName(quantity)?.let { "$it cumulative energy$tariffSuffix" }
        6 -> demandName(quantity)?.let { "$it maximum demand$tariffSuffix" }
        else -> null
    } ?: if (classId == 7 && quantity == 99) {
        "Profile Generic"
    } else {
        null
    }
}

private fun instantaneousName(quantity: Int): String? = when (quantity) {
    11 -> "Current"
    12 -> "Voltage"
    13 -> "Power factor"
    14 -> "Frequency"
    15 -> "Active power"
    16 -> "Active power import"
    17 -> "Active power export"
    21 -> "Active power L1"
    22 -> "Active power L2"
    23 -> "Active power L3"
    31 -> "Current L1"
    32 -> "Voltage L1"
    33 -> "Power factor L1"
    51 -> "Current L2"
    52 -> "Voltage L2"
    53 -> "Power factor L2"
    71 -> "Current L3"
    72 -> "Voltage L3"
    73 -> "Power factor L3"
    else -> null
}

private fun energyName(quantity: Int): String? = when (quantity) {
    1 -> "Active import"
    2 -> "Active export"
    3 -> "Reactive import QI"
    4 -> "Reactive import QII"
    5 -> "Reactive export QIII"
    6 -> "Reactive export QIV"
    9 -> "Apparent import"
    10 -> "Apparent export"
    else -> null
}

private fun demandName(quantity: Int): String? = when (quantity) {
    1 -> "Active import"
    2 -> "Active export"
    9 -> "Apparent import"
    10 -> "Apparent export"
    else -> null
}

private val obisNames = mapOf(
    "0.0.1.0.0.255" to "Clock",
    "0.0.10.0.1.255" to "Activity calendar active",
    "0.0.10.0.2.255" to "Activity calendar passive",
    "0.0.13.0.0.255" to "Activity calendar active time",
    "0.0.15.0.0.255" to "Single action schedule for billing dates",
    "0.0.15.0.2.255" to "Single action schedule for image activation",
    "0.0.15.0.4.255" to "Single action schedule for push",
    "0.0.17.0.0.255" to "Load limit value",
    "0.0.25.9.0.255" to "Instant push setup",
    "0.4.25.9.0.255" to "Alert push setup",
    "0.0.40.0.0.255" to "Current association",
    "0.0.40.0.1.255" to "PC association",
    "0.0.40.0.2.255" to "MR association",
    "0.0.40.0.3.255" to "US association",
    "0.0.40.0.4.255" to "Push association",
    "0.0.40.0.5.255" to "Firmware upgrade association",
    "0.0.40.0.6.255" to "IHD association",
    "0.0.41.0.0.255" to "SAP assignment",
    "0.0.42.0.0.255" to "Logical device name",
    "0.0.43.0.0.255" to "Security setup",
    "0.0.43.1.0.255" to "Frame counter",
    "0.0.43.1.3.255" to "Invocation counter",
    "0.0.44.0.0.255" to "Image activation info",
    "0.0.94.91.10.255" to "Nameplate profile",
    "0.0.94.91.9.255" to "Meter current rating",
    "0.0.94.91.11.255" to "Meter category",
    "0.0.94.91.12.255" to "Meter type",
    "0.0.96.1.0.255" to "Meter serial number",
    "0.0.96.1.1.255" to "Manufacturer name",
    "0.0.96.1.2.255" to "Device ID",
    "0.0.96.1.4.255" to "Meter year of manufacture",
    "0.0.96.2.0.255" to "Cumulative programming count",
    "0.0.96.3.10.255" to "Disconnect control",
    "0.0.96.7.0.255" to "Power failure count",
    "0.0.96.7.9.255" to "Power failure duration",
    "0.0.96.11.0.255" to "Event voltage",
    "0.0.96.11.1.255" to "Event current",
    "0.0.96.11.2.255" to "Event power",
    "0.0.96.11.3.255" to "Event transaction",
    "0.0.96.11.4.255" to "Event others",
    "0.0.96.11.5.255" to "Event non-roll over",
    "0.0.96.11.6.255" to "Event control",
    "0.0.96.15.0.255" to "Event log sequence voltage",
    "0.0.96.15.1.255" to "Event log sequence current",
    "0.0.96.15.2.255" to "Event log sequence power",
    "0.0.96.15.3.255" to "Event log sequence transaction",
    "0.0.96.15.4.255" to "Event log sequence others",
    "0.0.96.15.5.255" to "Event log sequence non-roll over",
    "0.0.96.15.6.255" to "Event log sequence control",
    "0.0.96.15.128.255" to "Tamper count for billing period",
    "0.0.97.97.0.255" to "Fatal error status",
    "0.0.97.98.0.255" to "Alarm register object",
    "0.0.98.1.0.255" to "Billing profile",
    "0.0.99.98.0.255" to "Event log profile",
    "0.0.99.98.1.255" to "Voltage event log profile",
    "0.0.99.98.2.255" to "Current event log profile",
    "0.0.99.98.3.255" to "Power failure event log profile",
    "0.0.99.98.4.255" to "Transaction event log profile",
    "0.0.99.98.5.255" to "Other event log profile",
    "0.0.99.98.6.255" to "Control event log profile",
    "1.0.0.2.0.255" to "Firmware version for meter",
    "1.0.0.4.2.255" to "CTR",
    "1.0.0.4.3.255" to "PTR",
    "1.0.0.8.0.255" to "Demand integration period",
    "1.0.0.8.4.255" to "Profile capture period",
    "1.0.94.91.0.255" to "Instantaneous profile",
    "1.0.94.91.3.255" to "Scaler: instantaneous profile",
    "1.0.94.91.4.255" to "Scaler: block load profile",
    "1.0.94.91.5.255" to "Scaler: daily load profile",
    "1.0.94.91.6.255" to "Scaler: billing profile",
    "1.0.94.91.7.255" to "Scaler: events profile",
    "1.0.94.91.10.255" to "Nameplate details",
    "1.0.98.1.0.255" to "Billing profile",
    "1.0.99.1.0.255" to "Block load profile",
    "1.0.99.2.0.255" to "Daily load profile",
    "1.0.96.128.25.255" to "Active relay time",
    "1.0.96.128.30.255" to "Passive relay time"
)
