# Android UI Framework Recommendations

## Recommendation

Use **Jetpack Compose + Material 3** as the main UI framework.

Reason:

- The app already uses Compose and Material 3.
- Compose is the current Android-first UI direction.
- It is well suited for dialog-heavy forms, status screens, tree/list UIs, and reactive connection state.
- Compose Material 3 Adaptive can support phone, tablet, foldable, and desktop-window layouts without separate XML layouts.

## Suggested Stack

Primary:

- Jetpack Compose
- Compose Material 3
- Compose Material Icons
- Navigation 3, already present in the project
- Compose Material 3 Adaptive for list-detail and larger-screen layouts

Useful patterns for this app:

- `Scaffold` for the Meters page.
- `FloatingActionButton` or top-bar add action for adding meters.
- `AlertDialog` or modal bottom sheet for DLMS configuration.
- `LazyColumn` for the meter list and expandable object tree.
- `ListDetailPaneScaffold` for tablet/large-screen layouts where the meter tree and object details can be visible together.
- Expand/collapse row state for COSEM categories.
- Chips/segmented controls for BLE, OTG, and TCP selection.
- Outlined text fields for DLMS parameters.
- Switches for binary settings such as ciphering and invocation-counter sync.

## COSEM Tree Component

Recommended as an app-level component, not a separate framework.

The COSEM association view should be a custom Compose tree built from `LazyColumn` rows with explicit expand/collapse state. This gives full control over grouping by DLMS object class, access-right indicators, and object actions.

## UI Direction For Requested Flow

Use a meter-first layout:

1. **Meters screen** as the app entry point.
2. Add button opens a **DLMS Configuration** dialog.
3. Transport selection uses a segmented control: BLE, OTG, TCP.
4. Save returns to the meter list.
5. Selecting a meter reveals Connect, Disconnect, Properties, and Delete actions.
6. Connection state appears inline on the selected meter.
7. Association view appears as a collapsed expandable tree grouped by COSEM class/category.

## Final Choice

Stay with Compose and extend the existing UI. Add Material 3 Adaptive only if the target includes tablets, foldables, or desktop-style Android windows.
