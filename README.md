# TorDROID 🧅

**Aplikasi Android VPN Tor — ditulis dalam Java, bisa dikompilasi di Termux.**

TorDROID menghubungkan perangkat Android Anda ke jaringan **Tor** dengan membuat tunnel VPN transparan yang meneruskan seluruh traffic melalui Tor SOCKS5 proxy.

---

## Fitur

- 🔒 Koneksi VPN penuh via jaringan Tor
- 📊 Monitor progress bootstrap secara real-time
- 🌍 Tampil IP exit node saat ini
- 🔄 Minta identitas baru (New Identity) dengan satu tap
- 📱 Notifikasi foreground service
- 🚀 Auto-start saat boot (opsional)
- 🎨 UI dark mode modern

---

## Arsitektur

```
┌─────────────────────────────────────────┐
│               Android App               │
│  ┌──────────┐    ┌──────────────────┐   │
│  │MainActivity│  │ TorProxyService  │   │
│  │   (UI)    │◄──│  (Tor daemon)    │   │
│  └──────────┘    └────────┬─────────┘   │
│                           │             │
│  ┌────────────────────────▼──────────┐  │
│  │         TorVpnService             │  │
│  │  (VPN Interface + tun2socks)      │  │
│  └────────────────────────┬──────────┘  │
└───────────────────────────┼─────────────┘
                            │ SOCKS5 :9050
                    ┌───────▼────────┐
                    │  Tor Network   │
                    │  (3 hops)      │
                    └───────────────┘
```

### Komponen Utama

| File | Deskripsi |
|------|-----------|
| `TorProxyService.java` | Service utama yang menjalankan & monitor Tor daemon |
| `TorVpnService.java` | Android VPN Service yang redirect traffic ke Tor |
| `TorControlClient.java` | Komunikasi dengan Tor via Control Protocol |
| `TorConfig.java` | Konstanta konfigurasi (port, path, dll) |
| `TorUtils.java` | Helper utilities |
| `TorStatus.java` | Model status koneksi |
| `MainActivity.java` | UI utama |

---

## Cara Build

### Opsi 1: Build di Termux (Android) ⭐ Rekomendasi

```bash
# 1. Clone atau copy project ke Termux
git clone https://github.com/yourusername/TorDROID
cd TorDROID

# 2. Jalankan script build otomatis
chmod +x build_termux.sh
./build_termux.sh
```

Script akan otomatis:
- Install dependensi (Java, Gradle, dll)
- Download Android SDK
- Download binary Tor
- Kompilasi APK

### Opsi 2: Build Manual di Termux

```bash
# Install dependensi
pkg update && pkg install -y openjdk-17 gradle wget unzip

# Setup Android SDK
export ANDROID_HOME="$HOME/android-sdk"
# (download SDK seperti di build_termux.sh)

# Download binary Tor
chmod +x download_tor_binaries.sh
./download_tor_binaries.sh

# Build
./gradlew assembleDebug

# APK output
ls app/build/outputs/apk/debug/
```

### Opsi 3: Build di PC (Linux/Mac/Windows)

```bash
# Prasyarat: Android Studio atau SDK + JDK 17

# Clone project
git clone https://github.com/yourusername/TorDROID
cd TorDROID

# Download binary Tor
./download_tor_binaries.sh

# Build debug
./gradlew assembleDebug

# Install ke device via ADB
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## Binary Tor

TorDROID membutuhkan binary Tor yang dikompilasi untuk Android. Ada beberapa cara mendapatkannya:

### 1. Dari Guardian Project (Mudah)
```bash
./download_tor_binaries.sh
```

### 2. Extract dari Orbot APK
```bash
# Download Orbot APK dari F-Droid
wget https://f-droid.org/repo/org.torproject.android_16110006.apk

# Extract binary
apktool d org.torproject.android_16110006.apk -o orbot_extracted
find orbot_extracted -name "libtor.so" -o -name "tor"

# Copy ke assets
cp orbot_extracted/lib/arm64-v8a/libtor.so app/src/main/assets/tor-arm64
```

### 3. Compile dari Source
```bash
# Clone tor-android dari Guardian Project
git clone https://github.com/guardianproject/tor-android
cd tor-android

# Butuh Android NDK
export ANDROID_NDK_HOME=/path/to/ndk
./tor-droid-make.sh

# Output di external/bin/
```

### Binary yang dibutuhkan di `app/src/main/assets/`:
```
tor-arm64      ← untuk perangkat 64-bit ARM (kebanyakan HP modern)
tor-armeabi    ← untuk perangkat 32-bit ARM
tor-x86        ← untuk emulator x86
tor-x86_64     ← untuk emulator x86_64
tun2socks      ← untuk VPN routing (opsional, tapi direkomendasikan)
```

---

## tun2socks

`tun2socks` adalah komponen yang menjembatani VPN interface dengan Tor SOCKS5 proxy.

```bash
# Download tun2socks untuk Android ARM64
wget https://github.com/xjasonlyu/tun2socks/releases/latest/download/tun2socks-android-arm64.zip
unzip tun2socks-android-arm64.zip
cp tun2socks app/src/main/assets/tun2socks
```

---

## Port yang Digunakan

| Port | Protokol | Fungsi |
|------|----------|--------|
| 9050 | SOCKS5   | Tor proxy (digunakan oleh VPN) |
| 9051 | TCP      | Tor Control Port |
| 8118 | HTTP     | HTTP tunnel proxy |
| 5400 | UDP      | DNS-through-Tor |
| 9040 | TCP      | Transparent proxy |

---

## Konfigurasi Tor (torrc)

File `torrc` dibuat otomatis di direktori data aplikasi:
```
/data/data/com.tordroid/files/torrc
/data/data/com.tordroid/files/tor_data/
```

Isi default torrc:
```
SocksPort 127.0.0.1:9050
ControlPort 127.0.0.1:9051
DNSPort 127.0.0.1:5400
ClientOnly 1
SafeSocks 1
```

---

## Izin Aplikasi

| Izin | Alasan |
|------|--------|
| `INTERNET` | Koneksi ke jaringan Tor |
| `BIND_VPN_SERVICE` | Membuat VPN tunnel |
| `FOREGROUND_SERVICE` | Agar service tetap berjalan |
| `RECEIVE_BOOT_COMPLETED` | Auto-start saat boot |
| `ACCESS_NETWORK_STATE` | Cek status jaringan |

---

## Struktur Project

```
TorDROID/
├── app/
│   ├── src/main/
│   │   ├── java/com/tordroid/
│   │   │   ├── service/
│   │   │   │   ├── TorProxyService.java  ← Tor daemon manager
│   │   │   │   ├── TorVpnService.java    ← VPN interface
│   │   │   │   └── BootReceiver.java     ← Auto-start
│   │   │   ├── ui/
│   │   │   │   └── MainActivity.java     ← UI utama
│   │   │   └── util/
│   │   │       ├── TorConfig.java        ← Konfigurasi
│   │   │       ├── TorControlClient.java ← Control protocol
│   │   │       ├── TorStatus.java        ← Status model
│   │   │       └── TorUtils.java         ← Utilities
│   │   ├── res/
│   │   │   ├── layout/activity_main.xml
│   │   │   ├── values/{colors,strings,themes}.xml
│   │   │   └── drawable/*.xml
│   │   ├── assets/
│   │   │   ├── tor-arm64      ← Binary Tor (diisi manual)
│   │   │   ├── tor-armeabi
│   │   │   └── tun2socks
│   │   └── AndroidManifest.xml
│   └── build.gradle
├── build_termux.sh         ← Script build di Termux
├── download_tor_binaries.sh ← Download binary Tor
├── build.gradle
└── README.md
```

---

## Troubleshooting

### "Binary Tor tidak ditemukan"
Jalankan `./download_tor_binaries.sh` atau download manual dari link di atas.

### "VPN permission denied"
Pastikan tidak ada VPN lain yang aktif. Buka Pengaturan → VPN → Hapus konfigurasi lama.

### "Bootstrap stuck di 0%"
Cek koneksi internet. Jika menggunakan WiFi hotspot, pastikan port tidak diblokir.

### "Tor tidak bisa terhubung"
Coba aktifkan jembatan (bridge) dengan mengedit `TorConfig.java`:
```java
"UseBridges 1\n" +
"Bridge obfs4 ..."
```

### Error saat build di Termux
```bash
# Pastikan JAVA_HOME benar
export JAVA_HOME=$(dirname $(dirname $(which java)))
# Beri memori lebih ke JVM
export GRADLE_OPTS="-Xmx1024m"
```

---

## Disclaimer

TorDROID dibuat untuk tujuan edukasi dan privasi yang sah. Pengguna bertanggung jawab atas penggunaan aplikasi sesuai hukum yang berlaku di wilayah masing-masing.

---

*TorDROID menggunakan jaringan Tor yang dikembangkan oleh The Tor Project (https://www.torproject.org)*
