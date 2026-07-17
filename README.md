# YPVM — Your Private Virtual Machine

Dual-PIN Android lock screen that splits one device into an unrestricted
**Host** environment and a whitelisted, sandboxed **Guest** environment,
built entirely on Android's documented Device Owner / Work Profile APIs.

Full design write-up, including the security reasoning behind every
restriction: **[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)**.

## Requirements

- Android Studio (current stable) or a JDK 17 + Android SDK command-line
  toolchain
- A test device or emulator you can factory-reset, or provision fresh —
  Device Owner status requires no Google account be signed in yet
- `compileSdk` / `targetSdk` 35 (Android 15), `minSdk` 28 (Android 9,
  needed for DPC-initiated Lock Task Mode)

## Build

```bash
./gradlew assembleDebug
# APK lands in app/build/outputs/apk/debug/
```

This repo pins a known-stable toolchain (AGP 8.7.2, Kotlin 1.9.24, Gradle
8.9 via the wrapper included in `gradle/wrapper/`) so it builds correctly
out of the box. Android's tooling moves fast — AGP has since moved to a
9.x line with built-in Kotlin support — so Android Studio may offer an
upgrade when you open this project. That's safe to accept; nothing here
depends on deprecated APIs.

## Install as Device Owner

```bash
./gradlew installDebug
adb shell dpm set-device-owner \
    com.mnmyounus.ypvm.debug/com.mnmyounus.ypvm.admin.YpvmDeviceAdminReceiver
```

(Debug builds carry a `.debug` application-id suffix — see
`app/build.gradle.kts`.) Full first-run walkthrough — setting both PINs,
provisioning the Guest Work Profile, and choosing the app whitelist — is
in `docs/ARCHITECTURE.md §8`.

## CI/CD

`.github/workflows/release-debug-apk.yml` runs on every push or pull
request to `main` or `release`:

1. Builds `assembleDebug`
2. Signs with a consistent keystore decoded from GitHub Secrets, if
   configured (`YPVM_DEBUG_KEYSTORE_B64`, `YPVM_KEYSTORE_PASSWORD`,
   `YPVM_KEY_ALIAS`, `YPVM_KEY_PASSWORD`) — otherwise falls back to AGP's
   normal auto-generated debug key, so the build still succeeds without
   any secrets configured
3. Drafts a tagged GitHub Release and attaches the APK for OTA testing

## Project layout

```
app/src/main/java/com/mnmyounus/ypvm/
├── admin/      Device Owner + Work Profile policy logic
├── watchdog/   Crash-resilient relaunch service + boot receiver
├── security/   Keystore-backed PIN storage and lockout
└── ui/         Lock screen, Guest launcher, Emergency Unlock
```

## What's intentionally not in here

This project does not implement a hidden/invisible recovery gesture, and
does not attempt to defeat a Host-authenticated ADB session. Both are
explained in `docs/ARCHITECTURE.md → "Design decisions"` — short version:
the visible **Emergency Unlock** control does the same job with identical
security, without requiring the Guest-mode user to be kept unaware that a
privileged mode exists on their own phone.
