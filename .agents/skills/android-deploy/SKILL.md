---
name: android-deploy
description: "Use when installing or running this app on a physical phone over wireless ADB: pairing a device, reconnecting after a port change or reboot, pinning the port, choosing among attached devices, launching the app, and reading its logcat."
---

# Android deploy over wireless ADB

The phone is the truth for playback and proxy behavior; an emulator under-reports HLS,
audio focus and thermals. On the same network, connect to the phone's Wi-Fi `IP` (read it on
the Wireless debugging screen). `adb mdns` discovery usually fails from WSL2, and a
Windows-side adb server runs separately, so connect explicitly.

## LAN first, Tailscale only when away

The phone is also on the tailnet as `pixel-10`, but from WSL2 that path is unreliable for
anything but the TCP handshake: `adb connect pixel-10:5555` succeeds and `adb devices` says
`device`, then shell round trips stall for tens of seconds and `:app:installDebug` fails
with ddmlib `TimeoutException` fetching device properties ("Skipping device ... Unknown API
Level"). Measured 2026-10-05 on the same Wi-Fi: LAN shell round trips 0.08–0.14 s, deploy in
8.8 s; Tailscale stalled a shell for over 60 s and failed three installs in a row.

Use the Wi-Fi `IP` while the phone is reachable on the LAN. Use `pixel-10` only when it is
not (for example the phone is off-site), and expect a slow or stalled install.

Before any install, prove the transport is healthy. This must return in well under a second:

```sh
adb shell echo ok
```

## Once per machine

```sh
mise install
mise run android:setup
```

`mise` owns `adb`, `sdkmanager` and `ANDROID_HOME`.

## Pair

Phone: **Developer options → Wireless debugging → Pair device with pairing code**.
The dialog shows `IP:PAIR_PORT` and a six-digit code. Only the port is needed.

```sh
adb pair <IP>:<PAIR_PORT> <CODE>
```

Done when it prints `Successfully paired`. Pairing is remembered until ADB data is wiped.
Disable and re-enable Wireless debugging when the pairing code disappears.

## Connect

On the Wireless debugging screen, read the port under the toggle — `IP:CONNECT_PORT`,
different from the pairing port.

```sh
adb connect <IP>:<CONNECT_PORT>
adb devices
```

Done when `adb devices` lists the phone as `device`. A new port appears after every toggle
and reboot; reconnect with the current one. On `offline`, disconnect and connect again.
With two transports attached (for example LAN and a stale Tailscale address), disconnect the
one you do not mean to use so Gradle cannot pick it:

```sh
adb disconnect <the-other-IP>:<PORT>
```

### Pin the port

Right after connecting, move adbd to the fixed legacy port 5555 so the rest of the session
has one stable address. Requires Android 12+ and platform-tools ≥ 35 (the mise SDK ships
37.x):

```sh
adb -s <IP>:<CONNECT_PORT> tcpip 5555
adb disconnect <IP>:<CONNECT_PORT>
adb connect <IP>:5555
```

Done when `adb devices` lists `<IP>:5555 device`. The address holds until the phone reboots;
after a reboot, connect with the fresh dynamic port and repeat. If it fails or the reconnect
stays `offline`, approve any prompt on the phone and fall back to `<IP>:<CONNECT_PORT>` for
the session.

## Install and launch

```sh
./gradlew :app:installDebug
```

Done at `BUILD SUCCESSFUL`, with the APK installed on the phone. (When the `gradle-run`
skill is active, run this Gradle command through its wrapper.) With more than one device
attached, pin the target:

```sh
ANDROID_SERIAL=<IP>:5555 ./gradlew :app:installDebug
```

Then restart into the new build:

```sh
adb shell am force-stop dev.teyd.justintv
adb shell am start -n dev.teyd.justintv/.MainActivity
adb shell pidof dev.teyd.justintv
```

Done when `pidof` prints a PID.

## Logs

```sh
adb logcat --pid="$(adb shell pidof dev.teyd.justintv)"
```

After a crash, dump the crash buffer: `adb logcat -b crash -d`.
