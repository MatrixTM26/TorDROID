# TorDROID 🧅

An Android VPN application that routes all device traffic through the **Tor anonymity network**.
Written entirely in Java. Designed to be compiled directly on Android via **Termux** without a PC.

---

## How It Works

```
┌─────────────────────────────────────────────────────┐
│                  Android Device                     │
│                                                     │
│   Any App  ──►  VPN Interface (tun0)                │
│                      │                              │
│               TorVpnService                         │
│               (tun2socks bridge)                    │
│                      │                              │
│               TorProxyService                       │
│               (Tor daemon, SOCKS5 :9050)            │
└──────────────────────┼──────────────────────────────┘
                       │
              [ Tor Network ]
              Guard ► Middle ► Exit
                       │
                  Public Internet
```

All traffic from every app on the device is captured by the VPN interface and forwarded
through the local Tor SOCKS5 proxy. The Tor daemon builds an encrypted three-hop circuit
before any data leaves the device.

---

## Features

- Full-device VPN tunnel via Tor (all apps protected automatically)
- Real-time bootstrap progress display (0% → 100%)
- Shows current Tor exit node IP address
- One-tap New Identity (requests fresh circuit + new exit IP)
- Foreground service with persistent notification
- Auto-start on device boot (optional, toggle in settings)
- Dark-mode UI with animated shield icon and uptime counter
- Copies exit IP to clipboard on tap

---

## Project Structure

```
TorDROID/
├── app/
│   ├── build.gradle
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/                        ← Tor + tun2socks binaries go here
│       │   ├── tor-arm64
│       │   ├── tor-armeabi
│       │   ├── tor-x86
│       │   ├── tor-x86_64
│       │   └── tun2socks
│       ├── java/com/tordroid/
│       │   ├── service/
│       │   │   ├── TorProxyService.java   ← Tor daemon manager + log monitor
│       │   │   ├── TorVpnService.java     ← Android VPN interface + tun2socks
│       │   │   └── BootReceiver.java      ← Auto-start on boot
│       │   ├── ui/
│       │   │   └── MainActivity.java      ← Main screen UI
│       │   └── util/
│       │       ├── TorConfig.java         ← All constants (ports, paths, actions)
│       │       ├── TorControlClient.java  ← Tor Control Protocol (TCP)
│       │       ├── TorStatus.java         ← Connection state model
│       │       └── TorUtils.java          ← File, network, and parsing helpers
│       └── res/
│           ├── drawable/                  ← Shield icons, status dot shapes
│           ├── layout/activity_main.xml   ← Main screen layout
│           ├── mipmap-*/                  ← Launcher icons
│           ├── values/colors.xml
│           ├── values/strings.xml
│           ├── values/themes.xml
│           └── xml/network_security_config.xml
├── gradle/wrapper/
│   └── gradle-wrapper.properties
├── build.gradle
├── settings.gradle
├── gradle.properties
├── gradlew
├── build_termux.sh                        ← Automated build script for Termux
├── download_tor_binaries.sh               ← Downloads Tor binaries into assets/
└── README.md
```

---

## Source File Reference

### `TorConfig.java`
Central configuration. All port numbers, VPN address settings, Intent action strings,
and broadcast extra keys are defined here as PascalCase constants. The `BuildTorrcConfig()`
method generates the torrc file content written to disk before Tor starts.

### `TorProxyService.java`
Foreground service that manages the entire Tor daemon lifecycle:
1. Selects the correct Tor binary for the device ABI and copies it from assets
2. Writes the torrc configuration file
3. Spawns the Tor process via `ProcessBuilder`
4. Reads stdout line by line and parses bootstrap percentage with regex
5. Broadcasts status updates to `MainActivity` via `LocalBroadcastManager`
6. Connects to the Control port after bootstrap completes
7. Fetches the exit IP through the Tor SOCKS5 proxy via `api.ipify.org`

### `TorVpnService.java`
Android `VpnService` subclass that:
1. Builds a tun VPN interface with `VpnService.Builder`
2. Excludes the TorDROID package itself so it can reach the Tor daemon directly
3. Launches `tun2socks` binary to transparently bridge the tun device to Tor SOCKS5
4. Keeps the process running until a stop action is received

### `TorControlClient.java`
Minimal Tor Control Protocol v1 client over raw TCP.
Supports: `AUTHENTICATE`, `SIGNAL NEWNYM`, `SIGNAL CLEARDNSCACHE`,
`GETINFO address`, `GETINFO circuit-status`, `GETINFO status/bootstrap-phase`.

### `TorStatus.java`
State model holding the current `State` enum value, bootstrap percent and message,
exit IP, connection timestamp, and error message. `GetUptime()` computes `HH:MM:SS`
from the stored `ConnectedAt` timestamp.

### `TorUtils.java`
Static helpers for: copying assets to executable paths, writing text files,
checking if a TCP port is open, parsing Tor log lines with regex, formatting
byte counts, and checking network availability.

### `MainActivity.java`
Single-activity UI. Registers a `BroadcastReceiver` for `TorConfig.ActionStatus`
intents and calls `UpdateUi()` on every state change. Manages the VPN permission
request flow (`VpnService.prepare()`), uptime timer, clipboard copy, and
shield/dot animations.

### `BootReceiver.java`
`BroadcastReceiver` for `ACTION_BOOT_COMPLETED`. Reads the `autostart` preference
and starts `TorProxyService` if enabled.

---

## Ports Used

| Port | Protocol | Purpose                          |
|------|----------|----------------------------------|
| 9050 | SOCKS5   | Main Tor proxy (used by the VPN) |
| 9051 | TCP      | Tor Control Port                 |
| 8118 | HTTP     | HTTP tunnel proxy                |
| 5400 | UDP/TCP  | DNS-through-Tor                  |
| 9040 | TCP      | Transparent proxy port           |

---

## Build Instructions

### Requirements

| Tool | Version |
|------|---------|
| Java | 17 or 21 |
| Gradle | 7.6.x or system gradle |
| Android SDK | platform 33, build-tools 34.0.0 |
| Termux | Any recent version |

### Step 1 — Install packages

```bash
pkg update
pkg install openjdk-17 gradle aapt apksigner wget unzip git
```

### Step 2 — Clone or extract the project

```bash
cd ~
unzip TorDROID.zip   # or: git clone <repo>
cd TorDROID
```

### Step 3 — Point Gradle at the Android SDK

```bash
echo "sdk.dir=$HOME/android-sdk" > local.properties
```

If `~/android-sdk` does not exist yet, download it:

```bash
wget -O ~/sdk.zip https://dl.google.com/android/repository/commandlinetools-linux-10406996_latest.zip
unzip ~/sdk.zip -d ~/sdk-tmp
mkdir -p ~/android-sdk/cmdline-tools/latest
mv ~/sdk-tmp/cmdline-tools/* ~/android-sdk/cmdline-tools/latest/
rm -rf ~/sdk-tmp ~/sdk.zip

yes | ~/android-sdk/cmdline-tools/latest/bin/sdkmanager --licenses
~/android-sdk/cmdline-tools/latest/bin/sdkmanager "platforms;android-33" "build-tools;34.0.0"
```

### Step 4 — Download Tor binaries

```bash
chmod +x download_tor_binaries.sh
./download_tor_binaries.sh
```

### Step 5 — Build

```bash
# Keep memory low for devices with limited RAM
echo 'org.gradle.jvmargs=-Xmx512m -Xms128m
org.gradle.daemon=false
android.useAndroidX=true
android.enableJetifier=true
android.enableAapt2=false' > gradle.properties

gradle assembleDebug --no-daemon
```

### Step 6 — Install

```bash
# Copy APK to shared storage
cp app/build/outputs/apk/debug/app-debug.apk /sdcard/TorDROID.apk

# Or install via ADB from a PC
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Automated build (does all steps above)

```bash
chmod +x build_termux.sh
./build_termux.sh
```

---

## Getting Tor Binaries

TorDROID requires pre-compiled Tor binaries for Android placed in `app/src/main/assets/`.

### Option A — Guardian Project (recommended)

```bash
./download_tor_binaries.sh
```

Source: https://github.com/guardianproject/tor-android

### Option B — Extract from Orbot APK

```bash
# Download Orbot from F-Droid
wget https://f-droid.org/repo/org.torproject.android_16110006.apk -O orbot.apk

# Decode the APK
apktool d orbot.apk -o orbot_out

# Copy the binary
cp orbot_out/lib/arm64-v8a/libtor.so app/src/main/assets/tor-arm64
chmod +x app/src/main/assets/tor-arm64
```

### Option C — Compile from source

```bash
git clone https://github.com/guardianproject/tor-android
cd tor-android
export ANDROID_NDK_HOME=/path/to/ndk
./tor-droid-make.sh
# Output: external/bin/<abi>/tor
```

### Required files in `app/src/main/assets/`

| Filename      | ABI            | Devices                        |
|---------------|----------------|-------------------------------|
| `tor-arm64`   | arm64-v8a      | Most modern Android phones    |
| `tor-armeabi` | armeabi-v7a    | Older 32-bit ARM phones       |
| `tor-x86`     | x86            | x86 emulators                 |
| `tor-x86_64`  | x86_64         | x86_64 emulators              |
| `tun2socks`   | matches device | VPN bridge (all architectures)|

---

## Getting tun2socks

`tun2socks` bridges the VPN tun interface to the Tor SOCKS5 proxy.

```bash
# ARM64 (most phones)
wget https://github.com/xjasonlyu/tun2socks/releases/latest/download/tun2socks-android-arm64.zip
unzip tun2socks-android-arm64.zip
cp tun2socks app/src/main/assets/tun2socks
chmod +x app/src/main/assets/tun2socks
```

---

## Permissions

| Permission | Reason |
|---|---|
| `INTERNET` | Connect to the Tor network |
| `BIND_VPN_SERVICE` | Create the VPN tunnel interface |
| `FOREGROUND_SERVICE` | Keep the Tor daemon running in background |
| `RECEIVE_BOOT_COMPLETED` | Auto-start when device boots |
| `ACCESS_NETWORK_STATE` | Check connectivity before connecting |
| `CHANGE_NETWORK_STATE` | Required by VPN subsystem |
| `VIBRATE` | Optional notification vibration |

---

## Troubleshooting

### `AAPT2 is not supported on 32-bit Linux`
Your device runs a 32-bit kernel. Add this to `gradle.properties`:
```
android.enableAapt2=false
```
And use AGP 7.4.2 in `build.gradle` (already configured).

### `Could not reserve enough space for object heap`
Gradle is requesting too much memory. Set in `gradle.properties`:
```
org.gradle.jvmargs=-Xmx512m -Xms128m
```

### `SDK location not found`
Create `local.properties` in the project root:
```bash
echo "sdk.dir=$HOME/android-sdk" > local.properties
```

### `GradleWrapperMain ClassNotFoundException`
The `gradle-wrapper.jar` is missing. Either download it:
```bash
mkdir -p gradle/wrapper
wget -O gradle/wrapper/gradle-wrapper.jar \
  https://github.com/gradle/gradle/raw/v7.6.4/gradle/wrapper/gradle-wrapper.jar
```
Or skip the wrapper and use system gradle directly:
```bash
gradle assembleDebug --no-daemon
```

### Bootstrap stuck at 0%
- Check your internet connection
- If on a network that blocks Tor, enable bridges in `TorConfig.java`:
```java
+ "UseBridges 1\n"
+ "Bridge obfs4 <bridge_address>\n"
```
Get bridges at: https://bridges.torproject.org

### Tor process exits immediately
Check that the binary in `assets/` matches your device ABI:
```bash
file app/src/main/assets/tor-arm64
# Should output: ELF 64-bit LSB executable, ARM aarch64
```

---

## Architecture Notes

### Why a separate Tor binary instead of a Java library?
The official Tor daemon is written in C. Running it as a subprocess is the same
approach used by Orbot, the official Tor app for Android. It is more stable and
receives security updates directly from The Tor Project.

### Why tun2socks?
Android's `VpnService` gives you a raw tun file descriptor. To forward packets
from that interface to a SOCKS5 proxy without root, you need a userspace TCP/IP
stack. `tun2socks` implements this stack and handles all protocol translation.

### Control Port
After bootstrap, TorDROID connects to the Tor Control port on `127.0.0.1:9051`
using the `TorControlClient` class. This enables features like New Identity
(`SIGNAL NEWNYM`) and real-time status queries without restarting the daemon.

---

## Security Notes

- TorDROID excludes itself from the VPN tunnel so the Tor daemon can reach the
  network directly without looping through its own proxy.
- DNS queries are routed through Tor via the `DNSPort` to prevent DNS leaks.
- `SafeSocks 1` and `TestSocks 1` are enabled in torrc to reject non-anonymous
  SOCKS connections.
- The exit IP check uses `api.ipify.org` routed through the Tor proxy itself,
  confirming the traffic is actually exiting through Tor.

---

## License

This project is released under the MIT License.

TorDROID uses the Tor network developed by [The Tor Project](https://www.torproject.org).
Tor is free software under the BSD license.
