#!/bin/bash
# Manual build script for TorDROID using raw Android tools (no Gradle)
# Works on 32-bit Termux where AAPT2/AGP 8.x fails
# Tools used: aapt, javac, dx, apksigner (all available via Termux pkg)

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}"
echo "╔══════════════════════════════════════╗"
echo "║       TorDROID Manual Builder        ║"
echo "║   No Gradle — pure aapt/javac/dx     ║"
echo "╚══════════════════════════════════════╝"
echo -e "${NC}"

# ── Paths ─────────────────────────────────────────────────────
ProjectDir="$(cd "$(dirname "$0")" && pwd)"
BuildDir="$ProjectDir/build_manual"
SrcDir="$ProjectDir/app/src/main"
JavaSrc="$SrcDir/java"
ResDir="$SrcDir/res"
AssetsDir="$SrcDir/assets"
Manifest="$SrcDir/AndroidManifest.xml"
SdkDir="$HOME/android-sdk"
Platform="$SdkDir/platforms/android-34"
BuildTools="$SdkDir/build-tools/34.0.0"
OutApk="$ProjectDir/TorDROID-debug.apk"

# Termux tool paths
AAPT="$(which aapt)"
JAVAC="$(which javac)"
DX="$BuildTools/dx"
APKSIGNER="$(which apksigner)"
ZIPALIGN="$(which zipalign)"

# ── Check tools ───────────────────────────────────────────────
CheckTools() {
    echo -e "\n${YELLOW}[1/7] Checking tools...${NC}"
    local Missing=0

    for Tool in "$AAPT" "$JAVAC" "$APKSIGNER" "$ZIPALIGN"; do
        if [ -x "$Tool" ]; then
            echo -e "  ${GREEN}✓ $Tool${NC}"
        else
            echo -e "  ${RED}✗ $Tool not found${NC}"
            Missing=1
        fi
    done

    # dx may not exist, fallback to d8
    if [ -f "$DX" ]; then
        echo -e "  ${GREEN}✓ $DX${NC}"
    else
        DX="$BuildTools/d8"
        if [ -f "$DX" ]; then
            echo -e "  ${GREEN}✓ d8 (dx fallback): $DX${NC}"
            USE_D8=true
        else
            echo -e "  ${RED}✗ dx/d8 not found in $BuildTools${NC}"
            Missing=1
        fi
    fi

    if [ ! -f "$Platform/android.jar" ]; then
        echo -e "  ${RED}✗ android.jar not found at $Platform/android.jar${NC}"
        echo "  Run: ~/android-sdk/cmdline-tools/latest/bin/sdkmanager 'platforms;android-34'"
        Missing=1
    else
        echo -e "  ${GREEN}✓ android.jar${NC}"
    fi

    if [ $Missing -ne 0 ]; then
        echo -e "${RED}Missing tools. Run: pkg install aapt apksigner${NC}"
        exit 1
    fi
}

# ── Prepare build dirs ────────────────────────────────────────
PrepareDirectories() {
    echo -e "\n${YELLOW}[2/7] Preparing build directories...${NC}"
    rm -rf "$BuildDir"
    mkdir -p "$BuildDir/gen"
    mkdir -p "$BuildDir/classes"
    mkdir -p "$BuildDir/dex"
    mkdir -p "$BuildDir/apk"
    echo -e "${GREEN}✓ Build dir: $BuildDir${NC}"
}

# ── Collect dependencies ──────────────────────────────────────
CollectDeps() {
    echo -e "\n${YELLOW}[3/7] Collecting AAR/JAR dependencies...${NC}"
    mkdir -p "$BuildDir/libs"

    # Find all jars in gradle cache (already downloaded by previous gradle runs)
    local GradleCache="$HOME/.gradle/caches"
    local Found=0

    for Lib in appcompat material constraintlayout core netcipher okhttp okio; do
        local JarPath
        JarPath=$(find "$GradleCache" -name "*.jar" 2>/dev/null | grep "$Lib" | grep -v sources | grep -v javadoc | head -1)
        if [ -n "$JarPath" ]; then
            cp "$JarPath" "$BuildDir/libs/"
            echo -e "  ${GREEN}✓ $Lib${NC}"
            Found=$((Found + 1))
        else
            echo -e "  ${YELLOW}⚠ $Lib not in gradle cache${NC}"
        fi
    done

    # Also extract classes.jar from AARs
    for AarPath in $(find "$GradleCache" -name "*.aar" 2>/dev/null | grep -E "appcompat|material|constraintlayout|core" | head -20); do
        local LibName
        LibName=$(basename "$AarPath" .aar)
        local ExtractDir="$BuildDir/libs/aar_$LibName"
        mkdir -p "$ExtractDir"
        unzip -q "$AarPath" classes.jar -d "$ExtractDir" 2>/dev/null && \
            cp "$ExtractDir/classes.jar" "$BuildDir/libs/${LibName}.jar" && \
            echo -e "  ${GREEN}✓ Extracted: $LibName${NC}"
    done

    # Build classpath
    ClassPath="$Platform/android.jar"
    for Jar in "$BuildDir/libs/"*.jar; do
        [ -f "$Jar" ] && ClassPath="$ClassPath:$Jar"
    done

    echo -e "${GREEN}✓ Classpath built ($Found base libs found)${NC}"
}

# ── Generate R.java ───────────────────────────────────────────
GenerateR() {
    echo -e "\n${YELLOW}[4/7] Generating R.java with aapt...${NC}"

    "$AAPT" package -f -m \
        -J "$BuildDir/gen" \
        -M "$Manifest" \
        -S "$ResDir" \
        -A "$AssetsDir" \
        -I "$Platform/android.jar" \
        --auto-add-overlay \
        2>&1

    if [ -f "$BuildDir/gen/com/tordroid/R.java" ]; then
        echo -e "${GREEN}✓ R.java generated${NC}"
    else
        echo -e "${RED}✗ R.java not generated${NC}"
        exit 1
    fi
}

# ── Compile Java ──────────────────────────────────────────────
CompileJava() {
    echo -e "\n${YELLOW}[5/7] Compiling Java sources...${NC}"

    # Collect all .java files
    local JavaFiles
    JavaFiles=$(find "$JavaSrc" "$BuildDir/gen" -name "*.java" 2>/dev/null | tr '\n' ' ')

    "$JAVAC" \
        -source 1.8 \
        -target 1.8 \
        -bootclasspath "$Platform/android.jar" \
        -classpath "$ClassPath" \
        -d "$BuildDir/classes" \
        -encoding UTF-8 \
        $JavaFiles 2>&1

    echo -e "${GREEN}✓ Java compiled${NC}"
}

# ── Convert to DEX ────────────────────────────────────────────
ConvertDex() {
    echo -e "\n${YELLOW}[6/7] Converting to DEX...${NC}"

    if [ "$USE_D8" = true ]; then
        "$DX" \
            --release \
            --output "$BuildDir/dex" \
            "$BuildDir/classes" \
            $( [ -d "$BuildDir/libs" ] && find "$BuildDir/libs" -name "*.jar" | tr '\n' ' ') \
            2>&1
    else
        "$DX" \
            --dex \
            --output="$BuildDir/dex/classes.dex" \
            "$BuildDir/classes" \
            2>&1
    fi

    echo -e "${GREEN}✓ DEX created${NC}"
}

# ── Package APK ───────────────────────────────────────────────
PackageApk() {
    echo -e "\n${YELLOW}[7/7] Packaging and signing APK...${NC}"

    local UnsignedApk="$BuildDir/apk/unsigned.apk"
    local AlignedApk="$BuildDir/apk/aligned.apk"

    # Package with aapt
    "$AAPT" package -f \
        -M "$Manifest" \
        -S "$ResDir" \
        -A "$AssetsDir" \
        -I "$Platform/android.jar" \
        -F "$UnsignedApk" \
        --auto-add-overlay \
        2>&1

    # Add DEX
    cd "$BuildDir/dex"
    "$AAPT" add "$UnsignedApk" classes.dex 2>&1
    cd "$ProjectDir"

    # Zipalign
    "$ZIPALIGN" -f 4 "$UnsignedApk" "$AlignedApk"

    # Generate debug keystore if not present
    local Keystore="$HOME/.android/debug.keystore"
    if [ ! -f "$Keystore" ]; then
        mkdir -p "$HOME/.android"
        keytool -genkeypair \
            -keystore "$Keystore" \
            -alias androiddebugkey \
            -keypass android \
            -storepass android \
            -dname "CN=Android Debug,O=Android,C=US" \
            -keyalg RSA \
            -keysize 2048 \
            -validity 10000 \
            -storetype pkcs12 \
            2>/dev/null
        echo -e "  ${GREEN}✓ Debug keystore created${NC}"
    fi

    # Sign APK
    "$APKSIGNER" sign \
        --ks "$Keystore" \
        --ks-pass pass:android \
        --key-pass pass:android \
        --ks-key-alias androiddebugkey \
        --out "$OutApk" \
        "$AlignedApk" \
        2>&1

    echo -e "${GREEN}"
    echo "╔══════════════════════════════════════════╗"
    echo "║  ✓ APK built successfully!               ║"
    echo "║  📦 TorDROID-debug.apk"
    echo "║  📏 Size: $(du -sh "$OutApk" | cut -f1)"
    echo "╚══════════════════════════════════════════╝"
    echo -e "${NC}"
    echo "  Install:  cp $OutApk /sdcard/TorDROID.apk"
}

# ── Main ──────────────────────────────────────────────────────
cd "$ProjectDir"
CheckTools
PrepareDirectories
CollectDeps
GenerateR
CompileJava
ConvertDex
PackageApk
