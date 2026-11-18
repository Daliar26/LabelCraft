# LabelCraft

**Offline label editor and Bluetooth thermal-printer client.** This is a clean-room implementation; no Tiny Print code or assets are used.

## Project status

The source tree and CI configuration are prepared. An APK was not built in the current workspace because Android SDK and Gradle are unavailable and DNS/network access to Google and GitHub is blocked. No physical printer was attached, so hardware compatibility has not been verified. See [DECISIONS.md](DECISIONS.md).

## Quick start

Requires JDK 17+, Android SDK Platform 36 and Build Tools 36.0.0.

```bash
# once Gradle 8.13 is available on PATH:
gradle wrapper --gradle-version 8.13 --distribution-type bin

# after the standard gradle-wrapper.jar has been generated:
./gradlew assembleDebug test
```

Debug APK output: `app/build/outputs/apk/debug/app-debug.apk`.

CI uses Gradle 8.13 through `gradle/actions/setup-gradle`, so hosted runners do not depend on the missing local wrapper JAR.

## Implemented source features

- Bluetooth Classic SPP/RFCOMM and BLE GATT writes; bonded/classic/BLE discovery and reconnect attempts.
- TSPL2, ESC/POS, CPCL and ZPL encoders; protocol detection from a returned capability signature only. Silent devices require manual protocol selection.
- WYSIWYG preview; text, Code128, EAN-13, QR, gallery/camera images, lines/frames and text rotation. Presets 40×30, 58×40 and 100×150 mm plus custom sizes. JSON templates and CSV token substitution (`{{column}}`).
- 20–110 mm width, 10–300 mm length, 203/300 DPI conversion, ARGB_8888 rendering and Floyd–Steinberg 1-bit rasterization.
- Local print queue with copies, per-job status, retry and persistence.
- Hardware scroll keys: volume, D-pad, Page Up/Down; custom key mapping, step, speed, acceleration and inversion, persisted per printer.
- Calibration commands for TSPL gap/black-mark and ZPL `~JC`, plus saved manual profiles.

## Bluetooth and protocols

Grant Bluetooth Scan/Connect on Android 12+ (Location for legacy Android), scan, connect, and choose the printer's actual language. BLE selects the first writable GATT characteristic. Some manufacturers require proprietary UUIDs, packet framing, authentication, MTU negotiation or status notifications; those models need dedicated adapters.

TSPL2 sends `SIZE`, `GAP`/`BLINE`, `CLS`, `BITMAP`, `PRINT`. ESC/POS sends a `GS v 0` raster. CPCL emits a baseline `EG` raster job. ZPL uses `^GFA` and `~JC`. Vendor firmware varies, so test a single label before production batches.

## Calibration

A physical automatic measurement requires independent encoder/sensor values from printer firmware. The current adapter parses:

```text
LABEL_PULSES=400;GAP_PULSES=24;PULSES_PER_MM=8
```

At least three readings are required; the estimator uses medians. Unit tests cover a sample dataset with error within ±1 mm. Sending `GAPDETECT` or `~JC` alone is not proof of a physical measurement. Use the manual calibration screen when firmware does not expose telemetry. BLE notifications and vendor-specific sensor protocols still need dedicated adapters.

## Hardware scrolling

Default mappings are `KEYCODE_VOLUME_UP/DOWN`, `DPAD_UP/DOWN`, and `PAGE_UP/DOWN`. Example custom map:

```text
24:SCROLL_UP,25:SCROLL_DOWN,92:PAGE_UP,93:PAGE_DOWN
```

Step is 0.5–30 mm, speed 0.25–5×, acceleration 0.5–4×, plus inversion. Profiles are stored locally by printer Bluetooth address. BLE HID pointer-scroll events are handled separately from key events and use the same step, speed, acceleration and inversion profile; confirm that the connected device exposes wheel events to Android.

## CSV batch printing

Provide a header row and use tokens such as `{{name}}` and `{{sku}}` in text elements. Each CSV row creates a queue job. Quoted commas, escaped double quotes and CRLF are supported.

## CI and signed release

- `.github/workflows/android-ci.yml` assembles debug/release and runs the root `test` task, which aggregates each Android module’s `testDebugUnitTest`.
- `.github/workflows/release.yml` reacts to `v*` tags, signs the APK and creates a GitHub Release.
- Configure Actions secrets: `RELEASE_KEYSTORE_BASE64`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`. Never commit the keystore or passwords.

## Privacy and licensing

All label data remains on-device. There are no ads, analytics, telemetry, accounts or required cloud services. Bluetooth and image access are used only for their matching features. ZXing Core and AndroidX are Apache-2.0; this project is Apache-2.0 and permits commercial and closed-source derivatives.

Manual publication: install Gradle 8.13, JDK 17 and Android SDK 36; run `gh auth login`, configure the four signing secrets listed above, then run `gh repo create <owner>/labelcraft --source . --remote origin --push --public && git tag v0.1.0 && git push origin v0.1.0` (replace `<owner>` and choose an available repository name).
