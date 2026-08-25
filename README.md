# DLMS Configurator

Android application for configuring and communicating with DLMS/COSEM energy meters over USB OTG optical probe, Bluetooth BLE, and TCP/IP transports.

## Current Capabilities

- Kotlin Android app built with Jetpack Compose and Material 3.
- DLMS/COSEM communication using Gurux DLMS.
- Transport support for USB OTG serial probe, BLE, and TCP/IP.
- JSON-based operation import for `get`, `set`, and `action` commands.
- Session execution with operation history and result summaries.
- Device profile storage for meter connection and DLMS security parameters.
- Association-view caching for discovered COSEM objects.
- Session export to JSON and CSV.
- Biometric/PIN lock support.
- Local persistence with Room and encrypted secret storage.

## Product Direction

The next UI flow should become meter-first:

1. The first screen shows a **Meters** tree/list and an add button.
2. Add opens a DLMS detailed configuration dialog inspired by Gurux GXDirector.
3. Configuration includes the transport medium: BLE, OTG, or TCP.
4. Save returns to the meter list and adds the meter entry.
5. Selecting a meter exposes actions: Connect, Disconnect, Properties, Delete.
6. Connect performs DLMS association over the selected transport.
7. If no cached association view exists, ask whether to read the association view.
8. If accepted, read the full meter object list and display it as a collapsed expandable tree grouped by COSEM category.

See [docs/APP_DEVELOPMENT_REQUIREMENTS.md](docs/APP_DEVELOPMENT_REQUIREMENTS.md) for the detailed flow.

## Recommended UI Direction

Use Jetpack Compose Material 3 as the main framework, with Compose Material 3 Adaptive for list-detail layouts on tablets and large screens. The app already uses Compose, so this keeps development aligned with the existing codebase.

See [docs/UI_FRAMEWORK_RECOMMENDATIONS.md](docs/UI_FRAMEWORK_RECOMMENDATIONS.md) for options and suggestions.
