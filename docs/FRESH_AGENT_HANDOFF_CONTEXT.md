# Fresh Agent Handoff Context

## Purpose

This document is intended to be enough context for a fresh AI coding agent or developer to continue the DLMS Configurator app without needing prior conversation history.

The goal is not just to describe the app. The goal is to remove ambiguity about the chosen product flow, UI theme, logo direction, and implementation priorities.

## Repository

- GitHub: https://github.com/iavisingh/DLMS_Apk
- Android package: `com.example.dlmsconfigurator`
- App display name: `DLMS_Tool(Genus_Cav)`
- Project type: Android Kotlin app.
- UI toolkit: Jetpack Compose with Material 3.

## Current App Capabilities

The local app currently contains:

- Android Kotlin/Compose application.
- DLMS/COSEM communication through Gurux DLMS.
- Transport support for:
  - USB OTG serial optical probe.
  - Bluetooth BLE.
  - TCP/IP socket.
- JSON operation import for DLMS `get`, `set`, and `action`.
- Session execution and operation logging.
- Result summary screens.
- JSON and CSV export.
- Room database persistence.
- Encrypted secret storage.
- Biometric/PIN lock.
- Saved device profiles.
- Cached association objects for meter object lists.

## Non-Negotiable Product Direction

The app must become **meter-first**.

The first meaningful app page should be **Meters**, not an import dashboard or marketing screen.

The core workflow:

1. User opens app.
2. App shows saved meters.
3. User clicks add button.
4. App opens a DLMS detailed configuration popup/sheet.
5. User configures one meter.
6. User selects the transport medium for that meter only.
7. User saves.
8. Meter appears in the meter list/tree.
9. User selects a meter.
10. App shows Connect, Disconnect, Properties, Delete.
11. User clicks Connect.
12. App connects using that meter's saved transport and DLMS configuration.
13. App performs DLMS association.
14. If no cached association view exists, app asks whether to read the association view.
15. If user says yes, app reads the full object list.
16. App stores the association view cache for that meter.
17. App displays the object list as a collapsed expandable COSEM tree.

## Critical Clarification: Transport Is Per Meter

Transport medium selection is **not global**.

Each saved meter profile has its own transport configuration.

Examples:

- `Genus Main Meter` can use OTG.
- `Lab BLE Meter` can use BLE.
- `Gateway Meter` can use TCP.

These meters can coexist in the saved meter list simultaneously. Selecting or editing one meter must not change the transport settings of another meter.

## Chosen UI Theme

Use **Option A: Meter Console**.

This is the final selected UI theme direction.

Theme character:

- Professional engineering-console UI.
- Dense but readable.
- Operational and technical.
- Touch-friendly but not oversized.
- Minimal decoration.
- No marketing-style landing page.
- No large hero sections.
- No decorative gradients.
- No purple-heavy or beige/brown visual direction.

Color system:

- Primary: deep teal or blue-green.
- Secondary: slate/cool neutral.
- Info: blue for scanning, associating, and in-progress states.
- Success: green for connected and successful operations.
- Warning: amber for cautions and attention states.
- Error: red for failed operations and delete/destructive actions.
- Background: very light neutral in light mode.
- Dark mode: dark gray surfaces, not pure black.

Dynamic color:

- Do not enable Android dynamic color by default.
- This app needs stable operational colors because status meaning matters.
- A future optional setting named **Use system colors** is acceptable.

Typography:

- Use normal sans-serif UI typography for labels, menus, buttons, and dialogs.
- Use monospace typography for OBIS codes, class IDs, APDU/hex frames, keys, serial numbers, and raw values.

Shapes and density:

- Use compact rows and clear dividers.
- Keep rounded corners modest, around 6dp to 8dp.
- Avoid nested cards.
- Avoid oversized dashboard typography.

## Chosen Logo

Use **Logo Concept 1: Meter Signal**.

Logo description:

- Rounded-square Android adaptive icon.
- Deep teal background.
- Simple white smart-meter face/body.
- Signal arcs in the upper-right area.
- Optional small circular port/status dot on the meter face.

Why this logo:

- It reads clearly as a meter app.
- It communicates connectivity.
- It does not lock the app to BLE, OTG, or TCP specifically.
- It is simple enough for launcher-icon sizes.

Do not:

- Put tiny `DLMS` text inside the launcher icon.
- Use a complex meter schematic.
- Use a generic lightning bolt as the primary mark.
- Use decorative gradients as the main identity.

## Screen Templates

### Meters Screen

Purpose:

- Main entry screen.
- Lists saved meters.
- Allows adding, selecting, connecting, editing, and deleting meter profiles.

Required UI:

- Top app bar titled **Meters**.
- Add meter icon button.
- Optional search/filter.
- Saved meter list/tree.
- Empty state when no meters exist.

Each meter row should show:

- Meter name.
- OBIS or serial summary if available.
- Transport chip: OTG, BLE, or TCP.
- Connection state.
- Last known meter serial if available.
- Last connected time if available.

Selecting a meter should reveal:

- Connect.
- Disconnect.
- Properties.
- Delete.

### Add/Edit Meter Configuration

Purpose:

- GXDirector-style configuration for one meter.

Presentation:

- Dialog or sheet on larger devices.
- Full-screen dialog/sheet on phones if needed for space.

Sections:

- Identity.
- Per-meter transport selector.
- Transport-specific settings.
- DLMS addressing.
- DLMS interface.
- Authentication/security.
- Ciphering keys.
- Invocation counter.
- Advanced timeout/retry/frame options.

Transport selector:

- BLE.
- OTG.
- TCP.

Transport-specific fields:

- BLE: device name, MAC address, scan/select option.
- OTG: baud rate and serial probe settings.
- TCP: host/IP and port.

DLMS fields:

- Client address.
- Server address.
- Interface type: HDLC or WRAPPER.
- Logical name referencing.
- Security/authentication mode.
- Password.
- System title.
- Authentication key.
- Encryption/block cipher key.
- Invocation counter OBIS.
- Use/read invocation counter before secure association.
- Timeout/wait time.
- Retry count.
- Frame size.

Footer:

- Cancel.
- Save.

Validation:

- Meter name is required.
- BLE requires a device address or selected device.
- TCP requires host and port.
- Required security fields must be validated based on selected security mode.

### Meter Action Panel

Purpose:

- Operate on selected meter.

Actions:

- Connect.
- Disconnect.
- Properties.
- Delete.
- Read association view when connected.
- Refresh association view when cache exists.

Status display:

- Transport state.
- DLMS association state.
- Current security mode.
- Last error.

### Connection Flow

When Connect is clicked:

1. Load selected meter profile.
2. Build transport from that meter's own transport settings.
3. Open transport.
4. Build DLMS connection parameters from that meter's profile.
5. Resolve secrets from secure storage.
6. Start DLMS association.
7. Update connection state.
8. If association fails, show clear error and keep user on meter screen.
9. If association succeeds and cache exists, show cached tree.
10. If association succeeds and cache does not exist, ask whether to read association view.

### Association View Prompt

Trigger:

- Successful DLMS connection.
- No cached association view for the selected meter.

Prompt text:

**No cached association view found. Read meter association view now?**

Actions:

- Yes, read now.
- No, stay connected.

Yes behavior:

- Read complete association/object list.
- Parse class IDs, versions, OBIS codes, class names, attribute access rights, and method access rights.
- Store cache linked to this meter.
- Show object tree.

No behavior:

- Keep meter connected.
- Do not load object tree.
- Allow user to manually read association view later.

### COSEM Object Tree

Purpose:

- Display meter object list from association view.

Default state:

- All groups collapsed.

Top-level groups:

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

Each object leaf should show:

- Object name if known.
- OBIS code.
- Class name.
- Class ID.
- Version.
- Attribute access summary.
- Method access summary.

Clicking an object opens object detail.

### Object Detail

Purpose:

- Inspect and operate on one COSEM object.

Tabs:

- Attributes.
- Methods.
- Raw/Decoded.

Actions:

- Read/Get attribute.
- Write/Set attribute if access rights allow.
- Execute Action/Method if access rights allow.

Display:

- OBIS code.
- Class ID.
- Version.
- Access rights.
- Latest decoded value.
- Raw APDU/hex when detailed logging is enabled.

## Data Model Expectations

Meter/device profile should include:

- ID.
- Name.
- Transport settings JSON or equivalent structured model.
- Client address.
- Server address.
- Interface type.
- Logical name referencing flag.
- Security/authentication mode.
- Secret references, not plaintext secrets.
- Ciphering flag.
- Invocation counter OBIS.
- Use invocation counter flag.
- Last connected timestamp.
- Last known meter serial.

Association object cache should include:

- ID.
- Device ID.
- Class ID.
- Version.
- OBIS code.
- Class name.
- Attribute access JSON.
- Method access JSON.
- Cached timestamp.

Secrets:

- Store actual secrets in encrypted storage.
- Store only aliases/references in database rows.

## Development Order

Recommended implementation order:

1. Make Meters the first app screen.
2. Ensure device profiles fully represent per-meter transport and DLMS settings.
3. Rework add/edit meter UI into the detailed configuration dialog/sheet.
4. Implement selected meter action panel.
5. Connect using selected meter profile.
6. Add missing association-view prompt.
7. Read full association view and save cache.
8. Build collapsed COSEM object tree.
9. Add object detail tabs and operations.
10. Polish operation logs and export behavior.
11. Apply Option A theme across all screens.
12. Replace launcher icon with Logo Concept 1 when creating final assets.

## Verification References

Decisions were checked against these Android references:

- Android Compose-first direction: https://android-developers.googleblog.com/2026/05/android-ui-development-is-compose-first.html
- Material 3 in Compose: https://developer.android.google.cn/develop/ui/compose/designsystems/material3
- Android adaptive apps: https://developer.android.google.cn/develop/adaptive-apps/guides/get-started-with-adaptive-apps
- Compose Material 3 Adaptive releases: https://developer.android.com/jetpack/androidx/releases/compose-material3-adaptive

## Instruction To Future AI Agent

Do not reinterpret the UI from scratch. Use **Option A: Meter Console** and **Logo Concept 1: Meter Signal** unless the user explicitly changes direction.

Do not treat transport selection as global. It belongs inside each meter profile.

Do not create a landing page. The app opens to **Meters**.

Do not store passwords, authentication keys, encryption keys, or system title values as plaintext in database rows.

Do not introduce a new UI framework unless there is a concrete missing capability. Use Compose Material 3 and app-level custom components.
