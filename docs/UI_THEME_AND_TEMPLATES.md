# UI Theme And Templates

## Decision

Use **Option A: Meter Console**, a professional engineering-console theme built on Jetpack Compose Material 3.

The app should feel like a precise meter configuration and diagnostics tool, not a marketing app. The visual language should support scanning, comparison, field work, and repeated technical operations.

Selected logo direction:

- **Logo Concept 1: Meter Signal**
- Smart meter face with communication arcs.
- Deep teal adaptive icon background with a simple white meter mark.

Reference assets:

- [Option A UI Reference](assets/ui-option-a-meter-console-reference.svg)
- [Selected Meter Signal Logo](assets/logo-meter-signal.svg)

## Verified Basis

The recommendation is based on current Android guidance:

- Android UI development is Compose-first.
- Material 3 in Compose provides color schemes, typography, shapes, and component theming.
- Material 3 supports light/dark theming and dynamic color, but this app should prefer a stable brand palette for operational consistency.
- Compose Material 3 Adaptive is the recommended route for layouts that scale to tablets, foldables, and desktop-style Android windows.

References:

- https://android-developers.googleblog.com/2026/05/android-ui-development-is-compose-first.html
- https://developer.android.google.cn/develop/ui/compose/designsystems/material3
- https://developer.android.google.cn/develop/adaptive-apps/guides/get-started-with-adaptive-apps
- https://developer.android.com/jetpack/androidx/releases/compose-material3-adaptive

## Theme Recommendation

### Name

**Option A: Meter Console**

### Personality

- Dense but readable.
- Calm and technical.
- High contrast where status matters.
- Minimal decoration.
- Clear action hierarchy.
- Designed for field engineers, DLMS developers, and support teams.

### Color Direction

Use a restrained multi-accent palette:

- Primary: deep teal or blue-green for main actions and selected states.
- Secondary: slate/neutral for app chrome, dividers, and quiet controls.
- Tertiary: amber for warning states and attention markers.
- Error: red for failed connection, failed operation, delete confirmations.
- Success: green for connected state and successful reads/writes.
- Info: blue for scanning, associating, and in-progress DLMS communication.

Avoid:

- Purple-heavy dashboards.
- Beige/tan/brown palettes.
- Decorative gradients.
- Large hero artwork.
- Marketing-style cards.

### Light Theme

Use light theme for normal field use.

Suggested roles:

- Background: very light neutral.
- Surface: white or near-white.
- Primary: deep teal.
- On primary: white.
- Outline: cool gray.
- Success: medium green.
- Warning: amber.
- Error: red.

### Dark Theme

Support dark theme for low-light testing.

Guidelines:

- Use dark gray surfaces instead of pure black.
- Reduce color saturation slightly.
- Keep status colors readable but not neon.
- Preserve red/amber/green semantic consistency.

### Dynamic Color

Do not make dynamic color the default.

Reason:

- This is an engineering tool where consistent status colors matter.
- Dynamic wallpaper-derived colors can make meter state and operation results less predictable.

Acceptable option:

- Add a future setting named **Use system colors** for Android 12+ if desired.

## Typography

Use two text styles:

- Inter or Android default sans-serif for labels, menus, buttons, dialogs, and normal content.
- JetBrains Mono for OBIS codes, hex frames, serial numbers, class IDs, raw APDUs, keys, and decoded values.

Rules:

- Keep headings compact.
- Use monospace only where precision matters.
- Avoid oversized dashboard headings.
- Use consistent row heights in trees and operation logs.

## Shape And Density

Recommended shape:

- Small radius: 6dp to 8dp.
- Dialog radius: 8dp.
- Buttons: Material defaults or 8dp.
- Avoid large rounded cards.

Recommended density:

- Compact row layout.
- Clear dividers between technical rows.
- Status chips can be compact.
- Forms should group fields into sections without nesting cards inside cards.

## Component Templates

### 1. Meters Screen Template

Purpose:

- First page of the app.
- Shows all saved meters.

Layout:

- Top app bar: title **Meters**, optional search/filter.
- Add button: icon button or floating action button.
- Main area: meter list/tree.
- Empty state: compact message plus add action.

Meter row content:

- Meter name.
- Per-meter transport chip: BLE, OTG, or TCP.
- Connection state: Disconnected, Connecting, Connected, Failed.
- Last known meter serial if available.
- Last connected time if available.

Primary interactions:

- Click row to select/expand meter actions.
- Actions: Connect, Disconnect, Properties, Delete.

Important rule:

- Transport selection is stored inside each individual meter profile. It is not a global app setting. Multiple saved meters can use different transport media simultaneously.

### 2. Add/Edit Meter Dialog Template

Purpose:

- GXDirector-style DLMS detailed configuration.

Recommended presentation:

- Dialog on larger screens.
- Full-screen dialog or modal sheet on phones.

Sections:

- Identity: meter name.
- Transport: BLE/OTG/TCP segmented selector for this meter only.
- Transport settings.
- DLMS addressing.
- Security/authentication.
- Invocation counter.
- Advanced timing/retry settings.

Footer:

- Cancel.
- Save.

Validation:

- Meter name required.
- BLE requires MAC address or selected device.
- TCP requires host and port.
- Security modes requiring keys should validate required key fields.

### 3. Meter Action Panel Template

Purpose:

- Shows available commands for the selected meter.

Actions:

- Connect.
- Disconnect.
- Properties.
- Delete.
- Read association view if connected.
- Refresh association view if cache exists.

Status area:

- Transport state.
- DLMS association state.
- Last error.
- Active security mode.

### 4. Association View Prompt Template

Trigger:

- After successful connection when no cached association view exists.

Message:

**No cached association view found. Read meter association view now?**

Actions:

- Yes, read now.
- No, stay connected.

Behavior:

- Yes reads complete association view and stores cache.
- No leaves meter connected without object tree.

### 5. COSEM Object Tree Template

Purpose:

- Display the meter association view in a structured tree.

Default state:

- All top-level categories collapsed.

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

Object row content:

- Object name if known.
- OBIS code.
- Class name.
- Class ID.
- Attribute access summary.
- Method access summary.

Interactions:

- Click category to expand/collapse.
- Click object to open object detail.

### 6. Object Detail Template

Purpose:

- Inspect and operate on one COSEM object.

Tabs:

- Attributes
- Methods
- Raw/Decoded

Actions:

- Read attribute.
- Write attribute when access rights allow.
- Execute method/action when access rights allow.

Display:

- OBIS code.
- Class ID and version.
- Access rights.
- Latest value.
- Raw frame/APDU when detailed logging is enabled.

### 7. Operation Log Template

Purpose:

- Show all DLMS operations for a session.

Columns/fields:

- Sequence.
- Operation type.
- OBIS.
- Class ID.
- Attribute or method.
- Status.
- Attempt count.
- Start/end time.
- Error.
- Decoded value.

Use:

- Compact list on phones.
- Table-like layout on larger screens.

### 8. Session Result Template

Purpose:

- Summarize a completed session.

Sections:

- Meter/session info.
- Transport and security mode.
- Total operations.
- Success/failure counts.
- Export actions: JSON and CSV.

## Implementation Preference

No new third-party visual framework is needed.

Use:

- Compose Material 3 components.
- A custom app-level COSEM tree component.
- Compose Material 3 Adaptive only when implementing large-screen layouts.

## Final Recommendation

Adopt **Option A: Meter Console** as the target UI theme and use the templates above as the screen blueprint for upcoming development.

Logo alignment:

- Use the selected **Meter Signal** logo direction with the same deep teal primary color used in the app theme.
- Do not introduce a separate logo palette.
