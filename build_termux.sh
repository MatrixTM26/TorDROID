#!/bin/bash
# Build script for TorDROID on Termux (Android) or Linux

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

DetectEnvironment() {
    if [ -d "/data/data/com.termux" ] || [ -n "$TERMUX_VERSION" ]; then
        echo -e "${GREEN}✓ Environment: Termux${NC}"
        IS_TERMUX=true
    else
        echo -e "${GREEN}✓ Environment: Linux${NC}"
        IS_TERMUX=false
    fi
}

InstallDepsTermux() {
    echo -e "\n${YELLOW}[1/6] Installing Termux packages...${NC}"
    pkg update -y 2>/dev/null || true
    local Packages="openjdk-17 gradle aapt apksigner wget unzip git"
    for Pkg in $Packages; do
        if pkg list-installed 2>/dev/null | grep -q "^$Pkg"; then
            echo -e "  ${GREEN}✓ $Pkg${NC}"
        else
            echo "  Installing $Pkg..."
            pkg install -y "$Pkg" || echo -e "  ${YELLOW}⚠ $Pkg skipped${NC}"
        fi
    done
    echo -e "${GREEN}✓ Dependencies ready${NC}"
}

InstallDepsLinux() {
    echo -e "\n${YELLOW}[1/6] Checking Linux dependencies...${NC}"
    for Cmd in java gradle wget unzip; do
        command -v "$Cmd" &>/dev/null \
            && echo -e "  ${GREEN}✓ $Cmd${NC}" \
            || { echo -e "  ${RED}✗ $Cmd missing${NC}"; exit 1; }
    done
}

WriteGradleProperties() {
    echo -e "\n${YELLOW}[2/6] Writing gradle.properties...${NC}"

    # Locate the 32-bit aapt binary installed by Termux
    local AaptPath=""
    if [ "$IS_TERMUX" = true ]; then
        AaptPath=$(which aapt 2>/dev/null || true)
    fi

    {
        echo "android.useAndroidX=true"
        echo "android.enableJetifier=true"
        echo "android.enableAapt2=false"
        if [ -n "$AaptPath" ]; then
            echo "android.aapt2FromMavenOverride=$AaptPath"
            echo -e "  ${GREEN}✓ Using system aapt: $AaptPath${NC}" >&2
        fi
        echo "org.gradle.jvmargs=-Xmx512m -Xms128m -Dfile.encoding=UTF-8"
        echo "org.gradle.daemon=false"
        echo "org.gradle.warning.mode=none"
    } > gradle.properties

    echo -e "${GREEN}✓ gradle.properties written${NC}"
}

SetupAndroidSdk() {
    echo -e "\n${YELLOW}[3/6] Setting up Android SDK...${NC}"

    if [ -n "$ANDROID_HOME" ] && [ -d "$ANDROID_HOME" ]; then
        echo -e "${GREEN}✓ ANDROID_HOME: $ANDROID_HOME${NC}"
        echo "sdk.dir=$ANDROID_HOME" > local.properties
        return
    fi

    local SdkDir="$HOME/android-sdk"

    if [ ! -d "$SdkDir/platforms/android-34" ]; then
        echo "  Downloading Android SDK..."
        local TmpDir="${TMPDIR:-$HOME/tmp}"
        mkdir -p "$TmpDir" "$SdkDir"

        wget -q --show-progress \
            -O "$TmpDir/sdk-tools.zip" \
            "https://dl.google.com/android/repository/commandlinetools-linux-10406996_latest.zip"

        unzip -q "$TmpDir/sdk-tools.zip" -d "$TmpDir/sdk-unzip/"
        mkdir -p "$SdkDir/cmdline-tools/latest"
        mv "$TmpDir/sdk-unzip/cmdline-tools/"* "$SdkDir/cmdline-tools/latest/"
        rm -rf "$TmpDir/sdk-tools.zip" "$TmpDir/sdk-unzip"

        echo "  Accepting licenses..."
        yes | "$SdkDir/cmdline-tools/latest/bin/sdkmanager" --licenses > /dev/null 2>&1 || true

        echo "  Installing platform + build-tools..."
        "$SdkDir/cmdline-tools/latest/bin/sdkmanager" \
            "platforms;android-34" \
            "build-tools;34.0.0" \
            "platform-tools" 2>/dev/null

        echo -e "${GREEN}✓ Android SDK installed${NC}"
    else
        echo -e "${GREEN}✓ Android SDK found: $SdkDir${NC}"
    fi

    export ANDROID_HOME="$SdkDir"
    echo "sdk.dir=$SdkDir" > local.properties
    echo -e "${GREEN}✓ local.properties written${NC}"
}

SetupTorBinaries() {
    echo -e "\n${YELLOW}[4/6] Setting up Tor binaries...${NC}"
    local Assets="app/src/main/assets"
    mkdir -p "$Assets"

    if [ -f "$Assets/tor-arm64" ] && [ -s "$Assets/tor-arm64" ]; then
        echo -e "${GREEN}✓ Tor binaries already present${NC}"
        return
    fi

    bash download_tor_binaries.sh || {
        echo -e "${YELLOW}⚠ Download failed, creating placeholders${NC}"
        for Arch in arm64 armeabi x86 x86_64; do
            echo "#!/system/bin/sh" > "$Assets/tor-$Arch"
        done
        echo "#!/system/bin/sh" > "$Assets/tun2socks"
    }
}

BuildApk() {
    echo -e "\n${YELLOW}[5/6] Compiling APK...${NC}"

    if [ -z "$JAVA_HOME" ]; then
        export JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(which java)")")")"
        echo "  JAVA_HOME: $JAVA_HOME"
    fi

    # Download gradle-wrapper.jar if not present
    local WrapperJar="gradle/wrapper/gradle-wrapper.jar"
    if [ ! -f "$WrapperJar" ]; then
        echo "  Downloading gradle-wrapper.jar..."
        local TmpDir="${TMPDIR:-$HOME/tmp}"
        mkdir -p "$TmpDir" "gradle/wrapper"
        wget -q -O "$WrapperJar" \
            "https://github.com/gradle/gradle/raw/v9.5.1/gradle/wrapper/gradle-wrapper.jar" \
            || rm -f "$WrapperJar"
    fi

    # Prefer system gradle (already at 9.5.1 in Termux, matches our config)
    echo "  Running: gradle assembleDebug --no-daemon"
    gradle assembleDebug --no-daemon 2>&1 | tail -40

    if [ ${PIPESTATUS[0]} -ne 0 ]; then
        echo -e "${RED}✗ Build failed${NC}"
        exit 1
    fi
}

ShowOutput() {
    echo -e "\n${YELLOW}[6/6] Build output:${NC}"
    local ApkPath="app/build/outputs/apk/debug/app-debug.apk"

    if [ -f "$ApkPath" ]; then
        local Size
        Size=$(du -sh "$ApkPath" | cut -f1)
        echo -e "${GREEN}"
        echo "╔══════════════════════════════════════════╗"
        echo "║  ✓ APK built successfully!               ║"
        echo "║  📦 $ApkPath"
        echo "║  📏 Size: $Size"
        echo "╚══════════════════════════════════════════╝"
        echo -e "${NC}"
        echo "  Copy to storage:  cp $ApkPath /sdcard/TorDROID.apk"
        echo "  Install via ADB:  adb install $ApkPath"
    else
        echo -e "${RED}✗ APK not found at $ApkPath${NC}"
    fi
}

Main() {
    cd "$(dirname "$0")"
    DetectEnvironment

    if [ "$IS_TERMUX" = true ]; then
        InstallDepsTermux
    else
        InstallDepsLinux
    fi

    WriteGradleProperties
    SetupAndroidSdk
    SetupTorBinaries
    BuildApk
    ShowOutput
}

Main "$@"
