#!/bin/bash
# Run this once to configure Android SDK path for Termux

# Cari lokasi Android SDK yang sudah diinstall oleh build_termux.sh
SDK_DIR="$HOME/android-sdk"

if [ ! -d "$SDK_DIR" ]; then
    echo "SDK tidak ditemukan di $SDK_DIR"
    echo "Jalankan ./build_termux.sh terlebih dahulu"
    exit 1
fi

# Tulis local.properties
echo "sdk.dir=$SDK_DIR" > local.properties
echo "✓ local.properties dibuat: sdk.dir=$SDK_DIR"

# Set environment variable juga
export ANDROID_HOME="$SDK_DIR"
export ANDROID_SDK_ROOT="$SDK_DIR"
export PATH="$PATH:$SDK_DIR/platform-tools:$SDK_DIR/build-tools/34.0.0"

echo "✓ ANDROID_HOME=$ANDROID_HOME"
echo ""
echo "Sekarang jalankan:"
echo "  gradle assembleDebug --no-daemon"
