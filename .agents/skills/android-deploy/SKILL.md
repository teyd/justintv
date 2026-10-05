---
name: android-deploy
description: "Use when installing or running this app on a physical phone over wireless ADB: pairing a device, reconnecting after a port change or reboot, choosing among attached devices, launching the app, and reading its logcat."
---

# Android deploy over wireless ADB

The phone is the truth for playback and proxy behavior; an emulator under-reports HLS,
audio focus and thermals. From WSL2 connect by IP: `adb mdns` discovery usually fails,
and a Windows-side adb server runs separately.

Once per machine:

```sh
mise install
mise run android:setup
```

`mise` owns `adb`, `sdkmanager` and `ANDROID_HOME`.

## Pair

Phone: **Developer options → Wireless debugging → Pair device with pairing code**.
The dialog shows `IP:PAIR_PORT` and a six-digit code.

```sh
adb pair <IP>:<PAIR_PORT> <CODE>
```

Done when it prints `Successfully paired`. Pairing is remembered until ADB data is wiped.
Disable and re-enable Wireless debugging when Pairing code disappears.

## Connect

On the Wireless debugging screen, read the port under the toggle — `IP:CONNECT_PORT`,
different from the pairing port.

```sh
adb connect <IP>:<CONNECT_PORT>
adb devices
```

Done when `adb devices` lists the phone as `device`. A new port appears after every toggle
and reboot; reconnect with the current one. On `offline`, disconnect and connect again.

## Install and launch

```sh
./gradlew :app:installDebug
```

Done at `BUILD SUCCESSFUL`, with the APK installed on the phone. (When the `gradle-run`
skill is active, run this Gradle command through its wrapper.) With more than one device
attached, pin the target:

```sh
ANDROID_SERIAL=<IP>:<CONNECT_PORT> ./gradlew :app:installDebug
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
