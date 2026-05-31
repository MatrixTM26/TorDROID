#!/bin/bash
# ============================================================
#  download_tor_binaries.sh
#  Script untuk mengunduh binary Tor yang sudah di-compile
#  untuk berbagai arsitektur Android (arm64, armeabi, x86, x86_64)
#
#  Sumber: Guardian Project (https://guardianproject.info)
#  atau compile sendiri dengan NDK
# ============================================================

set -e

ASSETS_DIR="app/src/main/assets"
mkdir -p "$ASSETS_DIR"

echo "============================================"
echo " TorDROID - Download Tor Binaries"
echo "============================================"
echo ""

# Versi Tor yang akan diunduh
TOR_VERSION="0.4.8.10"

# ── Opsi 1: Unduh dari Guardian Project ──────────────────────
# Guardian Project menyediakan binary Tor untuk Android
# https://github.com/guardianproject/tor-android

echo "[!] Memilih metode pengunduhan binary Tor..."
echo ""
echo "Pilihan:"
echo "  1. Unduh dari Guardian Project (Rekomendasi)"
echo "  2. Compile dari source (memerlukan NDK)"
echo "  3. Gunakan binary dari Orbot APK"
echo ""

# ── Download dari F-Droid/Guardian Project ───────────────────
BASE_URL="https://raw.githubusercontent.com/guardianproject/tor-android/master/external/bin"

download_binary() {
    local arch="$1"
    local filename="tor-${arch}"
    local url="${BASE_URL}/${arch}/tor"

    echo "→ Mengunduh tor untuk ${arch}..."
    if command -v curl &>/dev/null; then
        curl -L -o "${ASSETS_DIR}/${filename}" "${url}" 2>/dev/null && \
            echo "  ✓ ${filename} berhasil diunduh" || \
            echo "  ✗ ${filename} gagal, coba wget..."
    elif command -v wget &>/dev/null; then
        wget -q -O "${ASSETS_DIR}/${filename}" "${url}" && \
            echo "  ✓ ${filename} berhasil diunduh" || \
            echo "  ✗ ${filename} gagal"
    fi
}

# Coba unduh untuk semua arsitektur
download_binary "arm64-v8a"
download_binary "armeabi-v7a"
download_binary "x86"
download_binary "x86_64"

# Rename sesuai nama yang diharapkan kode Java
mv "${ASSETS_DIR}/tor-arm64-v8a"  "${ASSETS_DIR}/tor-arm64"  2>/dev/null || true
mv "${ASSETS_DIR}/tor-armeabi-v7a" "${ASSETS_DIR}/tor-armeabi" 2>/dev/null || true

echo ""
echo "============================================"
echo " Mengunduh tun2socks binary..."
echo "============================================"

# tun2socks untuk bridging VPN interface ke SOCKS proxy
TUN2SOCKS_URL="https://github.com/xjasonlyu/tun2socks/releases/latest/download"

download_tun2socks() {
    local arch="$1"
    local file="tun2socks-android-${arch}.zip"
    local url="${TUN2SOCKS_URL}/${file}"

    echo "→ tun2socks untuk ${arch}..."
    if command -v curl &>/dev/null; then
        curl -L -o "/tmp/${file}" "${url}" 2>/dev/null && \
        unzip -o -j "/tmp/${file}" "tun2socks" -d "${ASSETS_DIR}/" 2>/dev/null && \
        mv "${ASSETS_DIR}/tun2socks" "${ASSETS_DIR}/tun2socks-${arch}" && \
        echo "  ✓ tun2socks-${arch} berhasil" || \
        echo "  ✗ Gagal untuk ${arch}"
    fi
}

# Jika gagal, buat placeholder
create_placeholder() {
    local name="$1"
    echo "#!/system/bin/sh" > "${ASSETS_DIR}/${name}"
    echo "# Placeholder - ganti dengan binary asli" >> "${ASSETS_DIR}/${name}"
    echo "  ℹ Placeholder dibuat untuk ${name}"
}

# Cek apakah binary ada, jika tidak buat placeholder
for arch in arm64 armeabi x86 x86_64; do
    if [ ! -f "${ASSETS_DIR}/tor-${arch}" ]; then
        echo "⚠ Binary tor-${arch} tidak ditemukan, membuat placeholder"
        create_placeholder "tor-${arch}"
    fi
done

if [ ! -f "${ASSETS_DIR}/tun2socks" ]; then
    echo "⚠ Binary tun2socks tidak ditemukan, membuat placeholder"
    create_placeholder "tun2socks"
fi

echo ""
echo "============================================"
echo " Selesai! Isi direktori assets:"
ls -la "${ASSETS_DIR}/"
echo "============================================"
echo ""
echo "⚠ PENTING: Untuk produksi, ganti placeholder dengan"
echo "   binary Tor asli yang sudah dikompilasi untuk Android."
echo ""
echo "   Cara mendapatkan binary resmi:"
echo "   - Clone: https://github.com/guardianproject/tor-android"
echo "   - Extract dari Orbot APK (gunakan apktool)"
echo "   - Compile sendiri dengan NDK (lihat BUILD_NDK.md)"
