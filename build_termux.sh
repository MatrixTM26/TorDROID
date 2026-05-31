#!/bin/bash
# ============================================================
#  build_termux.sh
#  Script untuk kompilasi TorDROID langsung di Termux (Android)
#
#  Prasyarat:
#    pkg install aapt apksigner dx ecj zip
#    atau
#    pkg install gradle openjdk-17
# ============================================================

set -e

# Warna output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}"
echo "╔══════════════════════════════════════╗"
echo "║         TorDROID Builder                    ║"
echo "║     Compile di Termux / Android             ║"
echo "╚══════════════════════════════════════╝"
echo -e "${NC}"

# ── Deteksi lingkungan ────────────────────────────────────────
detect_environment() {
    if [ -d "/data/data/com.termux" ] || [ -n "$TERMUX_VERSION" ]; then
        echo -e "${GREEN}✓ Terdeteksi: Termux${NC}"
        ENV="termux"
        PREFIX="$PREFIX"
    else
        echo -e "${GREEN}✓ Terdeteksi: Linux biasa${NC}"
        ENV="linux"
        PREFIX="/usr"
    fi
}

# ── Instalasi dependensi ──────────────────────────────────────
install_deps_termux() {
    echo -e "\n${YELLOW}[1/5] Instalasi dependensi Termux...${NC}"
    pkg update -y
    pkg install -y \
        openjdk-17 \
        gradle \
        aapt \
        zipalign \
        apksigner \
        wget \
        unzip \
        git \
        || { echo -e "${RED}Error instalasi dependensi${NC}"; exit 1; }
    echo -e "${GREEN}✓ Dependensi terinstal${NC}"
}

install_deps_linux() {
    echo -e "\n${YELLOW}[1/5] Cek dependensi Linux...${NC}"
    for cmd in java gradle wget unzip; do
        if ! command -v "$cmd" &>/dev/null; then
            echo -e "${RED}✗ $cmd tidak ditemukan. Install: sudo apt install $cmd${NC}"
            exit 1
        fi
    done
    echo -e "${GREEN}✓ Semua dependensi tersedia${NC}"
}

# ── Setup Android SDK ─────────────────────────────────────────
setup_android_sdk() {
    echo -e "\n${YELLOW}[2/5] Setup Android SDK...${NC}"

    if [ -n "$ANDROID_HOME" ] && [ -d "$ANDROID_HOME" ]; then
        echo -e "${GREEN}✓ Android SDK ditemukan: $ANDROID_HOME${NC}"
        return
    fi

    # Di Termux, android-sdk bisa diinstall via termux-tools
    SDK_DIR="$HOME/android-sdk"

    if [ ! -d "$SDK_DIR" ]; then
        echo "  Mengunduh Android SDK Command Line Tools..."
        mkdir -p "$SDK_DIR"
        SDK_URL="https://dl.google.com/android/repository/commandlinetools-linux-10406996_latest.zip"
        wget -q -O /tmp/sdk-tools.zip "$SDK_URL"
        unzip -q /tmp/sdk-tools.zip -d /tmp/
        mkdir -p "$SDK_DIR/cmdline-tools/latest"
        mv /tmp/cmdline-tools/* "$SDK_DIR/cmdline-tools/latest/"

        # Accept licenses
        yes | "$SDK_DIR/cmdline-tools/latest/bin/sdkmanager" --licenses > /dev/null 2>&1

        # Install platform tools
        "$SDK_DIR/cmdline-tools/latest/bin/sdkmanager" \
            "platforms;android-34" \
            "build-tools;34.0.0" \
            "platform-tools" 2>/dev/null

        echo -e "${GREEN}✓ Android SDK terinstal${NC}"
    else
        echo -e "${GREEN}✓ Android SDK sudah ada: $SDK_DIR${NC}"
    fi

    export ANDROID_HOME="$SDK_DIR"
    export PATH="$PATH:$ANDROID_HOME/platform-tools:$ANDROID_HOME/build-tools/34.0.0"
}

# ── Download Tor binaries ─────────────────────────────────────
setup_tor_binaries() {
    echo -e "\n${YELLOW}[3/5] Setup binary Tor...${NC}"

    ASSETS="app/src/main/assets"
    mkdir -p "$ASSETS"

    # Cek apakah binary sudah ada
    if [ -f "$ASSETS/tor-arm64" ] && [ -s "$ASSETS/tor-arm64" ]; then
        echo -e "${GREEN}✓ Binary Tor sudah ada${NC}"
        return
    fi

    echo "  Mengunduh binary Tor dari Guardian Project..."
    bash download_tor_binaries.sh || {
        echo -e "${YELLOW}⚠ Auto-download gagal, membuat placeholder${NC}"
        echo "# Ganti dengan binary Tor asli" > "$ASSETS/tor-arm64"
        echo "# Ganti dengan binary Tor asli" > "$ASSETS/tor-armeabi"
        echo "# Ganti dengan binary Tor asli" > "$ASSETS/tor-x86"
        echo "# Ganti dengan binary Tor asli" > "$ASSETS/tor-x86_64"
        echo "# Ganti dengan binary tun2socks asli" > "$ASSETS/tun2socks"
    }
}

# ── Kompilasi ─────────────────────────────────────────────────
build_apk() {
    echo -e "\n${YELLOW}[4/5] Kompilasi APK...${NC}"

    # Set JAVA_HOME jika di Termux
    if [ "$ENV" = "termux" ]; then
        export JAVA_HOME="$(dirname $(dirname $(which java)))"
    fi

    # Jalankan gradle
    echo "  Menjalankan: ./gradlew assembleDebug"

    if [ -f "./gradlew" ]; then
        chmod +x ./gradlew
        ./gradlew assembleDebug --no-daemon 2>&1 | tail -20
    else
        gradle assembleDebug --no-daemon 2>&1 | tail -20
    fi

    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓ Kompilasi berhasil!${NC}"
    else
        echo -e "${RED}✗ Kompilasi gagal${NC}"
        exit 1
    fi
}

# ── Lokasi output ─────────────────────────────────────────────
show_output() {
    echo -e "\n${YELLOW}[5/5] Output:${NC}"

    APK_DEBUG="app/build/outputs/apk/debug/app-debug.apk"
    APK_RELEASE="app/build/outputs/apk/release/app-release-unsigned.apk"

    if [ -f "$APK_DEBUG" ]; then
        SIZE=$(du -sh "$APK_DEBUG" | cut -f1)
        echo -e "${GREEN}╔══════════════════════════════════════════╗${NC}"
        echo -e "${GREEN}║  ✓ APK berhasil dibuat!                  ║${NC}"
        echo -e "${GREEN}║  📦 $APK_DEBUG${NC}"
        echo -e "${GREEN}║  📏 Ukuran: $SIZE                        ║${NC}"
        echo -e "${GREEN}╚══════════════════════════════════════════╝${NC}"
        echo ""
        echo "  Install ke device:"
        echo "  adb install $APK_DEBUG"
        echo ""
        echo "  Atau copy ke device dan install manual:"
        echo "  cp $APK_DEBUG /sdcard/TorDROID.apk"
    else
        echo -e "${RED}APK tidak ditemukan di $APK_DEBUG${NC}"
    fi
}

# ── Main ──────────────────────────────────────────────────────
main() {
    cd "$(dirname "$0")"
    detect_environment

    if [ "$ENV" = "termux" ]; then
        install_deps_termux
    else
        install_deps_linux
    fi

    setup_android_sdk
    setup_tor_binaries
    build_apk
    show_output
}

main "$@"
