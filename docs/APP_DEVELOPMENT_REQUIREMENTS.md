# App Development Requirements

## Goal

Build a GXDirector-inspired DLMS meter management experience for Android. The app should start from a meter list/tree, allow detailed meter configuration, connect over the selected medium, and display the DLMS association view as an expandable COSEM object tree.

## First Page: Meters

The first page should be a **Meters** screen.

Required elements:

- Meter list or tree.
- Add meter button.
- Empty state when no meters exist.
- Saved meter entries showing at least meter name, transport type, and last connection status if available.

## Add Meter Flow

Clicking the add button opens a popup/dialog for detailed DLMS configuration.

The configuration should be similar in spirit to Gurux GXDirector and should include:

- Meter name.
- Transport medium:
  - BLE
  - OTG
  - TCP
- Transport-specific settings:
  - BLE device name/MAC and scan/select option.
  - OTG baud rate and serial probe parameters.
  - TCP host/IP and port.
- DLMS settings:
  - Client address.
  - Server address.
  - Interface type: HDLC or WRAPPER.
  - Referencing mode: logical name.
  - Authentication/security mode.
  - Password.
  - System title.
  - Authentication key.
  - Encryption/block cipher key.
  - Invocation counter OBIS.
  - Whether to sync/read invocation counter before secure association.
  - Frame size, timeout, retry count, and wait time where applicable.

Clicking **Save** closes the popup and returns to the Meters page with the newly added meter visible in the list/tree.

## Meter Actions

Clicking a saved meter entry in the tree/list should expose actions:

- Connect.
- Disconnect.
- Properties.
- Delete.

Expected behavior:

- **Connect** starts DLMS connection using the selected transport medium and saved DLMS settings.
- **Disconnect** closes the active DLMS session and transport.
- **Properties** reopens the meter configuration dialog for editing.
- **Delete** removes the meter profile and its cached association view after confirmation.

## Connection Flow

When the user clicks **Connect**:

1. Open selected transport:
   - BLE: connect to selected BLE device.
   - OTG: request USB permission if needed, then open serial probe.
   - TCP: open socket connection to host and port.
2. Perform DLMS association.
3. Show progress and status messages.
4. If association succeeds, update meter connection status.
5. If association fails, show a clear error and keep the meter entry available.

## Association View Cache

After connection succeeds:

- If cached association view exists for the meter, show the cached tree immediately.
- If no cached association view exists, show a popup:

  **No cached association view found. Read meter association view now?**

Options:

- **Yes**: read the complete association/object list from the meter.
- **No**: stay connected but do not load the object tree.

When the user selects **Yes**:

1. Read the complete DLMS association view.
2. Parse class IDs, OBIS codes, names, versions, attribute access rights, and method access rights.
3. Save the result as the meter's cached association view.
4. Display the result in a tree format.

## Object Tree Display

The association view should be grouped by COSEM object category and collapsed by default.

Example top-level groups:

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

Tree behavior:

- All categories collapsed initially.
- Clicking a category expands/collapses its children.
- Each object leaf shows OBIS code, class name, class ID, and access summary.
- Clicking an object opens details.

Object detail should expose operations based on access rights:

- Read/Get attribute.
- Write/Set attribute when write access exists.
- Action/Method execution when method access exists.
- Display raw and decoded DLMS values where available.

## UX Notes

- Keep the UI operational and dense, closer to an engineering tool than a marketing app.
- Use dialogs/sheets for meter configuration and confirmation.
- Use tree rows with expand/collapse icons for object categories.
- Show connection state clearly: disconnected, connecting, associating, connected, failed.
- Keep transport and DLMS security settings editable through Properties.

## Out of Scope For This Document

- Protocol implementation details beyond DLMS association-view behavior.
- UI code or implementation patches.
- Firmware-specific object semantics unless required by a specific meter profile.
