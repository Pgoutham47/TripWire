# Tripwire

On-device scam protection for Android. Tripwire follows each stranger who contacts you across WhatsApp, Telegram, SMS, calls, app installs and UPI payments. It recognises when you are partway through a known scam script and warns you at the step that can't be undone: the payment.

- Product spec: [Tripwire Product Requirements Document.md](Tripwire%20Product%20Requirements%20Document.md)
- Research and market case: [Tripwire.md](Tripwire.md)

## Layout

| Path | What it is |
| --- | --- |
| `core/` | The detection brain in plain Kotlin/JVM, with no Android dependency. Covers entity rules, tactic tagging, the progression engine, grounded checks, explanations, the pipeline, evidence packs and the replay harness. |
| `app/` | The Android app: collectors, encrypted ledger, on-device model, warnings, screens and workers. |
| `demoapp/` | A harmless fictional "SATFIN Pro" app used to test the install warning. |
| `tools/model/` | The tactic-reader model pipeline: dataset, LoRA training, conversion to `.litertlm`, evaluation. See [tools/model/README.md](tools/model/README.md). |
| `core/src/main/resources/script_pack.json` | The bundled script pack. Generated from `tools/scriptpack/build_pack.py`; do not edit by hand. |
| `core/src/main/resources/scenarios/` | Replay scenarios. Generated from `tools/scenarios/build_scenarios.py`. |

### How the app maps to the PRD

| PRD module | Where |
| --- | --- |
| SIG: notifications, calls, payment SMS | `app/.../collect/NotificationCollector.kt`, `Contacts.kt` |
| SIG: installs, payment and remote-access apps | `app/.../collect/SystemWatchers.kt` (run by `service/ProtectionService.kt`) |
| SIG-08: UPI links | `app/.../collect/UpiLinkActivity.kt` |
| SIG-11: install screen (optional on-screen reading) | `app/.../collect/InstallScreenWatcher.kt` |
| LED: encrypted ledger | `app/.../data/Database.kt` (Room and SQLCipher, key in the Android Keystore), `RoomLedgerStore.kt` |
| TAC: tactic reader | `core/.../tactic/`; `app/.../ai/LlmService.kt` runs LiteRT-LM in its own `:llm` process, and `LlmTacticReader.kt` is the client |
| ENG, CHK, EXP | `core/.../engine/`, `core/.../checks/`, `core/.../explain/` |
| INT: warnings | `app/.../intervene/WarningActivity.kt`, `notify/Notifier.kt`, `engine/Guardian.kt` |
| EVD: complaint pack | `core/.../evidence/`, `app/.../evidence/PackPdf.kt`, `ui/PaidScreen.kt` |
| ALY, UPD, retention | `app/.../intervene/AllyNotifier.kt`, `app/.../work/Workers.kt` |
| UI, ONB, SET | `app/.../ui/` |

## Build and test

You need JDK 17 and an Android SDK; `local.properties` points at the SDK.

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :core:test :app:testDebugUnitTest
```

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:assembleDebug
```

After changing the generator sources, regenerate the pack and the scenarios:

```bash
python3 tools/scriptpack/build_pack.py && python3 tools/scenarios/build_scenarios.py
```

## Running on a phone or emulator

Install from Android Studio or with `adb install`. Android blocks notification access for apps installed from a downloaded file, but not for adb installs. Onboarding asks for each permission. To grant them all on a test device in one go:

```bash
adb shell cmd notification allow_listener com.tripwire.app/com.tripwire.app.collect.NotificationCollector && adb shell appops set com.tripwire.app GET_USAGE_STATS allow && adb shell appops set com.tripwire.app SYSTEM_ALERT_WINDOW allow
```

Useful test inputs on an emulator:
- **Scam SMS:** `adb emu sms send 9000000066 "<text>"`
- **Stranger call:** `adb emu gsm call 9000000077`, then `adb emu gsm accept 9000000077`
- **App installed from a link:** `adb install -i com.android.chrome demoapp/build/outputs/apk/debug/demoapp-debug.apk`
- **UPI link:** `adb shell am start -a android.intent.action.VIEW -d "'upi://pay?pa=x.tripwiredemo@ybl&pn=Name&am=100'"`
- **Recorded scams, no real records touched:** Settings → Demo.

**On-device model.** The app runs on keyword rules until a `.litertlm` tactic model is present. To add one, copy it to `/sdcard/Android/data/com.tripwire.app/files/models/`, then tap "Look for the model file again" in Settings. Name it `tactic-full.litertlm` on 12 GB phones and `tactic-small.litertlm` on 8 GB phones. Phones under 8 GB stay on rules (TAC-09). To enable an over-the-air download instead, set `tripwire.modelUrl` and `tripwire.modelSha256` as Gradle properties.

## Known limits, found by testing

- **Android 15 notification redaction.** On the Android 15 emulator, the system notification assistant marked every SMS as sensitive. Third-party listeners then see only "Sensitive notification content hidden". Tripwire drops these instead of storing them, but it can't read those messages. The emulator tests disabled the assistant with `cmd notification disallow_assistant`. **Check this on the iQOO before relying on the SMS path.** This is PRD assumption 1.
- **Ally alerts need one tap.** Tripwire holds no SMS permission (PRD 15.2) and has no push backend yet, so an ally alert opens a pre-filled text for the user to send. Push delivery through FCM (ALY-04) is still to do.
- **Warning before an install needs on-screen reading.** With the optional "See the install screen" permission on, the warning appears over Android's install screen and "Don't install" cancels it (SIG-11). Without it, the warning comes just after the install, with an offer to uninstall (INT-09). The screen was checked on Samsung's installer; other phone makers' installers may lay it out differently.
- **The model can't run on the Apple-silicon emulator.** Every LiteRT-LM backend hits an illegal CPU instruction there. The model runs in its own process, so these crashes never stop protection: Tripwire records the crashed backend, stops trying it and falls back to keyword rules. On-device inference must be verified on a real phone. The same `.litertlm` file runs correctly in LiteRT-LM 0.17 on the Mac.
- **No WhatsApp or Telegram on the emulator.** Those parsers are covered by unit tests only, not by device tests yet.

All detection numbers come from a small, partly synthetic test set. They are not real-world rates (PRD 16.5).
