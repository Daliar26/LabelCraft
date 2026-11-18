# DECISIONS

| Area | Decision | Reason / limitation |
|---|---|---|
| Product identity | LabelCraft; package `dev.openlabel.labelcraft` | Clean-room name and namespace; no Tiny Print assets/code copied |
| Android baseline | minSdk 26, compileSdk/targetSdk 36 | Current target level for new Play submissions; Android 16 |
| Build stack | AGP 8.13.2, Gradle 8.13, Kotlin 2.2.21, Compose, Hilt, Navigation | Conservative stable-compatible build set; JDK 17 |
| Architecture | app + core:label + core:printer + three feature libraries; MVVM state holder + repository | Matches requested module boundaries; app privately stores queue and profiles |
| Raster pipeline | mm → dots at 203/300 DPI → ARGB_8888 bitmap → Floyd–Steinberg 1-bit → protocol encoder | Monochrome thermal-printer output and deterministic unit-testable geometry |
| Protocol selection | Detect only from actual response signature; manual selection otherwise | No universal safe probe exists for silent or vendor-specific printers |
| Bluetooth | SPP RFCOMM UUID; BLE first writable GATT characteristic, 20-byte conservative chunks | Generic transport baseline; proprietary UUID/framing/status may need device adapter |
| Calibration | TSPL2 GAPDETECT/BLINEDETECT and ZPL ~JC; require ≥3 independent pulse readings for measured dimensions | A command alone is not a sensor measurement; unsupported firmware uses saved manual profile |
| Sensor accuracy | Median-based pulse-to-mm estimator, synthetic reference dataset in unit tests | ±1 mm requirement can be demonstrated for the supplied test data, not guaranteed for unknown hardware |
| Hardware scroll | KeyEvent mapper for volume/D-pad/Page keys; keyCode/action profile in app-private preferences per printer | HID pointer-wheel events are not key events and require separate Pointer Scroll implementation |
| Templates/batch | JSON codec + app-private files; RFC-style CSV parser and `{{column}}` substitution | Offline use, transparent data format, no cloud dependency |
| Queue | Sequential coroutine worker; SharedPreferences JSON persistence; explicit retry | Local-first baseline; no cross-device/shared queue |
| Licensing/privacy | Apache-2.0 project; ZXing Core Apache-2.0; no ads/analytics/trackers | Allows closed-source commercial derivatives; no mandatory network access |
| Local build | Not verified in this session | No Gradle, Android SDK, adb or network/DNS; wrapper JAR cannot be fetched/generated here |
| Publication | Deferred; CI/release workflows prepared only | No `gh` CLI, GitHub credentials, remote URL or outbound GitHub DNS in the workspace |
