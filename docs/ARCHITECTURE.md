# YPVM — Architecture

YPVM turns one Android phone into two data-isolated environments — **Host**
(the real, unrestricted device) and **Guest** (a whitelisted, sandboxed
mode) — switched between with two different PINs on one lock screen.

Every mechanism below is built on Android's own documented Device
Management APIs (`DevicePolicyManager` / `UserManager`), the same surface
Android Enterprise MDMs use for BYOD work profiles. There is no private
API use and no undocumented behavior anywhere in this project.

## 1. Prerequisite: Device Owner

None of this works until YPVM is activated as the device's **Device
Owner** — a provisioning class Android only grants to an app on a device
with no Google account added yet:

```
adb shell dpm set-device-owner \
    com.mnmyounus.ypvm/.admin.YpvmDeviceAdminReceiver
```

On a device already in use, the equivalent path is Android's own QR-code
managed-provisioning flow, run once during setup. Either way, this is
Android's constraint, not YPVM's: it exists specifically so a device
owner can't be installed silently onto a phone someone is already using.

## 2. Host and Guest, concretely

- **Host** — the real primary Android user. Unchanged.
- **Guest** — a **Work Profile**: a second, separately-encrypted app
  container living alongside the primary user (not a second OS to boot
  into). Work profiles have been part of Android since 5.0, are
  implemented as secondary users with their own UID range, and store
  their data at `/data/user/<profile-id>/` — the same storage model the
  primary user already uses.

This is deliberately *not* a silent switch of the whole foreground OS
user the way Settings' multi-user picker works — that needs
system-signature permissions no downloadable APK can hold. A Work Profile
gets the same real, OS-enforced isolation without needing that.

## 3. The lock screen

`LockScreenActivity` is registered as the device's HOME app and pinned
via **Lock Task Mode** (`setLockTaskPackages`), the same mechanism retail
and hospital kiosk devices use. On top of that, `setKeyguardDisabled(true)`
replaces Android's native keyguard with this screen.

**Hard limit nothing routes around:** after a full reboot, Android's disk
encryption requires the real device credential once before *any* profile's
data is readable. That first unlock after a restart is always Android's
own prompt — YPVM takes over for every unlock after that.

Two PINs, one field:

| PIN entered | Result |
|---|---|
| Primary | Host launcher, full primary-user access |
| Secondary | `GuestLauncherActivity`, whitelisted Work Profile apps only |
| Wrong, 5+ times | Exponential backoff (30 s → capped at 15 min) |

## 4. Guest isolation

| Requirement | Mechanism |
|---|---|
| App whitelisting | Non-listed Work Profile apps: `setApplicationHidden(true)`. Host apps: `setPersonalAppsSuspended(true)` |
| Cryptographic file isolation | Native to the Work Profile model — Android's File-Based Encryption keys the profile to its own unlock challenge. No custom crypto written here. |
| USB/MTP blocking | `DISALLOW_USB_FILE_TRANSFER` + `DISALLOW_MOUNT_PHYSICAL_MEDIA`, applied to the Guest profile only |
| Find My Device coexistence | Untouched by design — nothing here reaches Play services, location, or account layers |

## 5. Fail-secure crash handling

Disabling the real keyguard (step 3) and "never lock anyone out
permanently" pull against each other unless handled deliberately:

1. `WatchdogService` runs continuously and checks every ~800 ms that
   `LockScreenActivity` is the foreground activity.
2. If not, it relaunches it immediately.
3. If relaunching fails 5 times in a row, the watchdog stops trying and
   calls `ProfileManager.restoreNativeKeyguard()`, which re-arms Android's
   real keyguard. **That's the actual fail-secure path** — not just "a
   keyguard exists somewhere underneath."
4. Worst case, the user sees Android's own PIN/biometric prompt. Never an
   unlocked phone, never a stuck half-state.

## 6. Emergency Unlock

A plainly labeled control on both the lock screen and the Guest launcher.
Tapping it prompts for the **Primary PIN**; success re-arms the native
keyguard and returns to Host, exactly like the automatic fail-secure path.

### Design decisions

This project's first draft specified an *invisible* recovery mechanism: a
transparent, unlabeled tap target with a secret 5-tap gesture that
revealed a hidden PIN prompt. That was deliberately not built, and it's
worth writing down why, since it's the one meaningful architectural
change from the original spec:

- A real recovery path should be discoverable by whoever is holding the
  device, even if they can't use it. Visibility doesn't weaken the
  security — the PIN gate is identical either way — it only changes
  whether the Guest-mode user knows a privileged mode exists on their own
  phone.
- Making that path invisible is the specific, defining feature of
  stalkerware-style tooling: hidden elevated access that the visible
  device user doesn't know to look for. That property doesn't change
  based on who's deploying it or why.
- Practically, Android already has a true last-resort escape hatch that
  needs no design work at all: **Safe Mode** (long-press Power →
  long-press "Power off"), which disables all third-party apps, including
  YPVM, until reboot. It's been part of Android since 4.1 (2012), so it's
  available on every realistic deployment target.

For the same reason, `DISALLOW_DEBUGGING_FEATURES` (§4) is scoped to the
Guest work profile only. It stops a Guest session from reaching Host
data — the same protection a BYOD employer policy gives corporate data —
but it does not, and isn't meant to, resist a Host-authenticated ADB
session once the device owner has deliberately turned on their own
Developer Options.

## 7. Repository layout

```
app/src/main/java/com/mnmyounus/ypvm/
├── admin/      DeviceAdminReceiver + all DevicePolicyManager/UserManager calls
├── watchdog/   Foreground watchdog service + boot receiver
├── security/   Keystore-backed PIN hashing, verification, lockout
└── ui/         Lock screen, Guest launcher, Emergency Unlock dialog
```

## 8. Local setup

1. Factory-reset a test device (or use an emulator image with Google APIs
   but no signed-in account).
2. `./gradlew installDebug`
3. `adb shell dpm set-device-owner com.mnmyounus.ypvm.debug/com.mnmyounus.ypvm.admin.YpvmDeviceAdminReceiver`
   (note the `.debug` application-id suffix on debug builds)
4. Launch YPVM once to set the two PINs via `PinVault`, then trigger Work
   Profile provisioning through `ProfileManager.guestProfileProvisioningIntent()`.

See the root `README.md` for the CI/CD build pipeline.
