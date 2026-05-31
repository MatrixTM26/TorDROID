#!/bin/bash
# Build script for TorDROID - runs on Termux (Android) or Linux

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}"
echo "╔══════════════════════════════════════╗"
echo "║         TorDROID Builder             ║"
echo "║     Compile on Termux / Android      ║"
echo "╚══════════════════════════════════════╝"
echo -e "${NC}"

# Detect whether we are running inside Termux
DetectEnvironment() {
    if [ -d "/data/data/com.termux" ] || [ -n "$TERMUX_VERSION" ]; then
        echo -e "${GREEN}✓ Environment: Termux${NC}"
        IS_TERMUX=true
    else
        echo -e "${GREEN}✓ Environment: Linux${NC}"
        IS_TERMUX=false
    fi
}

# Install all required Termux packages
InstallDepsTermux() {
    echo -e "\n${YELLOW}[1/5] Installing Termux packages...${NC}"

    # zipalign is bundled inside the aapt package, NOT a separate package
    # apksigner is bundled inside apksigner package (part of build-tools)
    pkg update -y 2>/dev/null || true

    local Packages="openjdk-17 gradle aapt apksigner wget unzip git"
    for Pkg in $Packages; do
        if pkg list-installed 2>/dev/null | grep -q "^$Pkg"; then
            echo -e "  ${GREEN}✓ $Pkg already installed${NC}"
        else
            echo "  Installing $Pkg..."
            pkg install -y "$Pkg" || echo -e "  ${YELLOW}⚠ $Pkg skipped (may not exist)${NC}"
        fi
    done

    echo -e "${GREEN}✓ Dependencies ready${NC}"
}

# Check required tools on generic Linux
InstallDepsLinux() {
    echo -e "\n${YELLOW}[1/5] Checking Linux dependencies...${NC}"
    for Cmd in java gradle wget unzip; do
        if command -v "$Cmd" &>/dev/null; then
            echo -e "  ${GREEN}✓ $Cmd found${NC}"
        else
            echo -e "  ${RED}✗ $Cmd not found. Install: sudo apt install $Cmd${NC}"
            exit 1
        fi
    done
}

# Download Android SDK command-line tools if ANDROID_HOME is not set
SetupAndroidSdk() {
    echo -e "\n${YELLOW}[2/5] Setting up Android SDK...${NC}"

    if [ -n "$ANDROID_HOME" ] && [ -d "$ANDROID_HOME" ]; then
        echo -e "${GREEN}✓ ANDROID_HOME already set: $ANDROID_HOME${NC}"
        export PATH="$PATH:$ANDROID_HOME/platform-tools:$ANDROID_HOME/build-tools/34.0.0"
        return
    fi

    local SdkDir="$HOME/android-sdk"

    if [ ! -d "$SdkDir/cmdline-tools/latest" ]; then
        echo "  Downloading Android SDK command-line tools..."
        mkdir -p "$SdkDir"
        local SdkUrl="https://dl.google.com/android/repository/commandlinetools-linux-10406996_latest.zip"
        wget -q --show-progress -O /tmp/sdk-tools.zip "$SdkUrl"
        unzip -q /tmp/sdk-tools.zip -d /tmp/sdk-unzip/
        mkdir -p "$SdkDir/cmdline-tools/latest"
        mv /tmp/sdk-unzip/cmdline-tools/* "$SdkDir/cmdline-tools/latest/"
        rm -rf /tmp/sdk-tools.zip /tmp/sdk-unzip

        echo "  Accepting SDK licenses..."
        yes | "$SdkDir/cmdline-tools/latest/bin/sdkmanager" --licenses > /dev/null 2>&1 || true

        echo "  Installing platform + build-tools..."
        "$SdkDir/cmdline-tools/latest/bin/sdkmanager" \
            "platforms;android-34" \
            "build-tools;34.0.0" \
            "platform-tools" 2>/dev/null

        echo -e "${GREEN}✓ Android SDK installed${NC}"
    else
        echo -e "${GREEN}✓ Android SDK already present: $SdkDir${NC}"
    fi

    export ANDROID_HOME="$SdkDir"
    export PATH="$PATH:$ANDROID_HOME/platform-tools:$ANDROID_HOME/build-tools/34.0.0"
}

# Copy or download Tor binaries into assets
SetupTorBinaries() {
    echo -e "\n${YELLOW}[3/5] Setting up Tor binaries...${NC}"

    local Assets="app/src/main/assets"
    mkdir -p "$Assets"

    if [ -f "$Assets/tor-arm64" ] && [ -s "$Assets/tor-arm64" ]; then
        echo -e "${GREEN}✓ Tor binaries already present${NC}"
        return
    fi

    echo "  Running download_tor_binaries.sh..."
    if bash download_tor_binaries.sh; then
        echo -e "${GREEN}✓ Tor binaries downloaded${NC}"
    else
        echo -e "${YELLOW}⚠ Auto-download failed, creating placeholders${NC}"
        for Arch in arm64 armeabi x86 x86_64; do
            echo "#!/system/bin/sh" > "$Assets/tor-$Arch"
            echo "# Replace with real Tor binary from: https://github.com/guardianproject/tor-android" >> "$Assets/tor-$Arch"
        done
        echo "#!/system/bin/sh" > "$Assets/tun2socks"
        echo "# Replace with real tun2socks binary" >> "$Assets/tun2socks"
    fi
}

# Run gradle to produce the debug APK
BuildApk() {
    echo -e "\n${YELLOW}[4/5] Compiling APK...${NC}"

    # Set JAVA_HOME if missing (common in Termux)
    if [ -z "$JAVA_HOME" ]; then
        export JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(which java)")")")"
        echo "  JAVA_HOME set to: $JAVA_HOME"
    fi

    # Reduce memory usage on low-RAM devices
    export GRADLE_OPTS="${GRADLE_OPTS} -Xmx512m -Xms256m"

    local GradleCmd="gradle"
    if [ -f "./gradlew" ]; then
        chmod +x ./gradlew
        GradleCmd="./gradlew"
    fi

    echo "  Running: $GradleCmd assembleDebug"
    $GradleCmd assembleDebug --no-daemon --stacktrace 2>&1 | tail -30

    if [ $? -ne 0 ]; then
        echo -e "${RED}✗ Build failed${NC}"
        exit 1
    fi
}

# Print the path to the output APK
ShowOutput() {
    echo -e "\n${YELLOW}[5/5] Build output:${NC}"

    local ApkPath="app/build/outputs/apk/debug/app-debug.apk"

    if [ -f "$ApkPath" ]; then
        local Size
        Size=$(du -sh "$ApkPath" | cut -f1)
        echo -e "${GREEN}"
        echo "╔══════════════════════════════════════════════╗"
        echo "║  ✓ APK built successfully!                   ║"
        echo "║  📦 $ApkPath"
        echo "║  📏 Size: $Size"
        echo "╚══════════════════════════════════════════════╝"
        echo -e "${NC}"
        echo "  Install via ADB:"
        echo "    adb install $ApkPath"
        echo ""
        echo "  Or copy to device storage:"
        echo "    cp $ApkPath /sdcard/TorDROID.apk"
    else
        echo -e "${RED}✗ APK not found at $ApkPath${NC}"
        echo "  Check build output above for errors"
    fi
}

# Entry point
Main() {
    cd "$(dirname "$0")"
    DetectEnvironment

    if [ "$IS_TERMUX" = true ]; then
        InstallDepsTermux
    else
        InstallDepsLinux
    fi

    SetupAndroidSdk
    SetupTorBinaries
    BuildApk
    ShowOutput
}

Main "$@"
