# TorDROID Assets

Folder ini harus berisi binary Tor untuk Android:

  tor-arm64      <- ARM 64-bit  (Xiaomi, Samsung, Pixel modern)
  tor-armeabi    <- ARM 32-bit  (perangkat lama)
  tor-x86        <- x86 32-bit  (emulator)
  tor-x86_64     <- x86 64-bit  (emulator 64-bit)
  tun2socks      <- bridge VPN interface ke SOCKS5

Cara mendapatkan binary:
  1. Jalankan: ../../../download_tor_binaries.sh
  2. Atau extract dari Orbot APK (https://f-droid.org)
  3. Atau compile dari: https://github.com/guardianproject/tor-android
