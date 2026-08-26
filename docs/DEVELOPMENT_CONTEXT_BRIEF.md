# Development Context Brief

## Project Summary

This project is an Android DLMS/COSEM meter configurator. It is intended to connect to energy meters over USB OTG optical probe, Bluetooth BLE, or TCP/IP, perform DLMS association, execute configured operations, and inspect meter objects through the association view.

Repository:

- https://github.com/iavisingh/DLMS_Apk

Local app package:

- `com.example.dlmsconfigurator`

App display name:

- `DLMS_Tool(Genus_Cav)`

## Current Technical Stack

- Android Kotlin app.
- Jetpack Compose UI.
- Material 3 components.
- Navigation 3.
- Room database.
- Encrypted local secret storage.
- Biometric/PIN lock.
- Gurux DLMS library for DLMS/COSEM protocol operations.
- USB serial library for OTG optical probe support.
- BLE transport implementation.
- TCP socket transport implementation.
- Kotlin serialization for JSON configuration files.

## Important Existing Concepts

### Transports

The app supports three transport paths:

- OTG USB serial optical probe.
- BLE.
- TCP/IP.

Each transport implements a shared DLMS transport interface with open, close, read, write, flush, and state behavior.

### DLMS Engine

The DLMS engine is responsible for:

- Opening DLMS communication through a transport.
- Applying DLMS connection parameters.
- Handling association.
- Supporting secure client configuration.
- Reading DLMS packets and data blocks.
- Performing get, set, and action operations.
- Reading association/object information.

### Device Profiles

Device profiles represent saved meter configurations.

A device profile should contain:

- Human-readable name.
- Its own transport configuration.
- Client/server addressing.
- Interface type.
- Security/authentication mode.
- Secret references for password/system title/authentication key/encryption key.
- Ciphering and invocation counter settings.
- Last connection metadata.

Secrets should not be stored as plaintext in Room rows. Store secret values in secure storage and keep only key references in the database.

Transport configuration is per meter, not global. For example, one saved meter can use OTG, another can use BLE, and another can use TCP at the same time.

### Association View Cache

Association objects represent the cached COSEM object list for a meter.

Each cached object should include:

- Device ID.
- Class ID.
- Version.
- OBIS code.
- Human-readable class name.
- Attribute access rights.
- Method access rights.
- Cache timestamp.

The cache should be invalidated or refreshed when the user explicitly refreshes the association view or changes important meter connection/security properties.

## Target Product Flow

The target product flow is meter-first:

1. App opens to **Meters**.
2. User clicks add button.
3. App opens DLMS detailed configuration popup.
4. User selects BLE, OTG, or TCP and fills DLMS settings.
5. User saves meter.
6. Meter appears in the meter list/tree.
7. User clicks meter and sees Connect, Disconnect, Properties, Delete.
8. User clicks Connect.
9. App connects over the selected transport.
10. App performs DLMS association.
11. If no cached association view exists, app asks whether to read it.
12. If user accepts, app reads complete meter object list.
13. App displays object tree grouped by COSEM category.

## Target Object Tree

The association view should be collapsed by default and grouped by COSEM class/category.

Expected groups:

- Data
- Register
- Extended Register
- Demand Register
- Profile Generic
- Clock
- Script Table
- Schedule
- Activity Calendar
- Register Monitor
- Disconnect Control
- Limiter
- Association LN
- Security Setup
- SAP Assignment
- Push Setup
- Other/Unknown

Object leaves should show OBIS, class name, class ID, access rights, and available actions.

## UI Direction

Chosen UI theme:

- **Option A: Meter Console**

Chosen logo:

- **Logo Concept 1: Meter Signal**
- Simple meter icon with communication arcs.
- Deep teal background, white meter mark, minimal details.

Chosen UI framework:

- Jetpack Compose + Material 3.

Chosen template direction:

- Meters screen as first page.
- Add/Edit Meter dialog for GXDirector-style configuration.
- Per-meter BLE/OTG/TCP transport selection inside each meter profile.
- Meter action panel for Connect, Disconnect, Properties, Delete.
- Association prompt when cache is missing.
- Expandable COSEM object tree.
- Object detail page with Attributes, Methods, and Raw/Decoded views.
- Operation log and session result screens for audit/export.

See:

- `docs/UI_THEME_AND_TEMPLATES.md`
- `docs/UI_FRAMEWORK_RECOMMENDATIONS.md`
- `docs/APP_DEVELOPMENT_REQUIREMENTS.md`
- `docs/LOGO_RECOMMENDATIONS.md`
- `docs/FRESH_AGENT_HANDOFF_CONTEXT.md`

## Development Priorities

Recommended implementation order:

1. Make Meters the first screen.
2. Consolidate add/edit device into a complete DLMS configuration dialog.
3. Ensure saved device profiles support all transport and DLMS security fields.
4. Implement meter row action panel.
5. Connect using saved meter profile.
6. Add association-view cache prompt.
7. Read and cache association view.
8. Build expandable COSEM object tree.
9. Add object detail operations based on access rights.
10. Polish operation logs and exports around the meter-first flow.

## Constraints

- Keep UI dense and operational.
- Avoid decorative landing pages.
- Avoid large hero sections.
- Avoid introducing a new UI framework unless there is a concrete missing capability.
- Do not store passwords or keys in plaintext database rows.
- Keep transport-specific logic separated from UI components.
- Keep DLMS protocol operations inside core DLMS/service layers.

## Verification References

Current product/design decisions were checked against:

- Android Compose-first direction: https://android-developers.googleblog.com/2026/05/android-ui-development-is-compose-first.html
- Material 3 in Compose: https://developer.android.google.cn/develop/ui/compose/designsystems/material3
- Android adaptive apps: https://developer.android.google.cn/develop/adaptive-apps/guides/get-started-with-adaptive-apps
- Compose Material 3 Adaptive releases: https://developer.android.com/jetpack/androidx/releases/compose-material3-adaptive

## Notes For Future Developers

Gurux GXDirector should be treated as a workflow reference for DLMS configuration depth and object-tree behavior, not as a visual clone. The Android app should remain touch-friendly, compact, and reliable for field use.

The highest-value UX improvement is making the association view feel natural: connect meter, read object list once, cache it, group it by COSEM category, and let the user drill into objects for read/write/action workflows.
