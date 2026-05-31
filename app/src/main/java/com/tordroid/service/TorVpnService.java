package com.tordroid.service;

import android.content.Intent;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import com.tordroid.util.TorConfig;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * TorVpnService - Android VPN Service
 *
 * Membuat interface VPN virtual yang menangkap semua traffic
 * dan meneruskannya melalui Tor SOCKS5 proxy.
 *
 * Alur:
 * Device App → VPN Interface (tun0) → TorVpnService → Tor SOCKS5 → Internet
 */
public class TorVpnService extends VpnService {

    private static final String TAG = "TorVpnService";

    private ParcelFileDescriptor mVpnInterface;
    private ExecutorService mExecutor;
    private AtomicBoolean mRunning = new AtomicBoolean(false);
    private Thread mVpnThread;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;

        String action = intent.getAction();
        if (TorConfig.ACTION_START.equals(action)) {
            startVpn();
        } else if (TorConfig.ACTION_STOP.equals(action)) {
            stopVpn();
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopVpn();
    }

    // ── VPN Control ───────────────────────────────────────────────────────────

    /**
     * Bangun dan mulai VPN interface
     */
    private void startVpn() {
        if (mRunning.get()) return;

        try {
            // Bangun VPN interface
            Builder builder = new Builder()
                .setMtu(TorConfig.VPN_MTU)
                .addAddress(TorConfig.VPN_ADDRESS, TorConfig.VPN_PREFIX)
                .addRoute(TorConfig.VPN_ROUTE, 0)
                .addDnsServer(TorConfig.VPN_DNS)
                .setSession("TorDROID")
                .setBlocking(true);

            // Kecualikan aplikasi TorDROID sendiri dari VPN
            // agar bisa koneksi langsung ke Tor
            try {
                builder.addDisallowedApplication(getPackageName());
            } catch (Exception ignored) {}

            mVpnInterface = builder.establish();
            if (mVpnInterface == null) {
                Log.e(TAG, "Gagal membuat VPN interface");
                return;
            }

            mRunning.set(true);
            Log.d(TAG, "VPN interface dibuat, memulai packet routing...");

            // Mulai thread routing
            mExecutor = Executors.newFixedThreadPool(2);
            startPacketRouting();
        } catch (Exception e) {
            Log.e(TAG, "Error memulai VPN", e);
        }
    }

    /**
     * Hentikan VPN interface
     */
    private void stopVpn() {
        mRunning.set(false);

        if (mVpnThread != null) {
            mVpnThread.interrupt();
        }

        if (mExecutor != null) {
            mExecutor.shutdownNow();
        }

        if (mVpnInterface != null) {
            try {
                mVpnInterface.close();
            } catch (IOException e) {
                Log.e(TAG, "Error menutup VPN interface", e);
            }
            mVpnInterface = null;
        }

        stopSelf();
        Log.d(TAG, "VPN dihentikan");
    }

    /**
     * Mulai routing paket antara VPN interface dan Tor SOCKS proxy
     *
     * Catatan: Implementasi packet-level routing di Android membutuhkan
     * manipulasi IP/TCP headers. Untuk fungsionalitas penuh, gunakan
     * library tun2socks (tersedia di assets).
     */
    private void startPacketRouting() {
        mExecutor.execute(() -> {
            try {
                // Gunakan tun2socks untuk forward semua traffic ke SOCKS5
                startTun2Socks();
            } catch (Exception e) {
                Log.e(TAG, "Error routing paket", e);
            }
        });
    }

    /**
     * Jalankan tun2socks binary untuk bridging VPN <-> SOCKS5
     *
     * tun2socks menangkap paket dari tun interface dan
     * meneruskannya ke Tor SOCKS5 proxy secara transparan.
     */
    private void startTun2Socks() {
        try {
            String tun2socksBin = getFilesDir() + "/tun2socks";
            String socksAddr = TorConfig.TOR_HOST + ":" + TorConfig.SOCKS_PORT;
            String tunFd = String.valueOf(mVpnInterface.getFd());

            // Perintah tun2socks
            String[] cmd = {
                tun2socksBin,
                "--netif-ipaddr",
                TorConfig.VPN_DNS, // Gateway VPN
                "--netif-netmask",
                "255.255.255.0",
                "--socks-server-addr",
                socksAddr, // Tor SOCKS5
                "--tunfd",
                tunFd,
                "--tunmtu",
                String.valueOf(TorConfig.VPN_MTU),
                "--sock-path",
                getFilesDir() + "/tun2socks.sock",
                "--enable-udp",
                "--udpgw-remote-server-addr",
                socksAddr,
                "--loglevel",
                "3",
            };

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.environment().put("HOME", getFilesDir().getAbsolutePath());

            Process process = pb.start();
            Log.d(TAG, "tun2socks dimulai, PID: " + process.pid());

            // Monitor sampai dihentikan
            while (mRunning.get()) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    break;
                }
            }

            process.destroy();
        } catch (IOException e) {
            Log.e(TAG, "Gagal menjalankan tun2socks: " + e.getMessage());
            // Fallback: mode proxy manual
            Log.d(TAG, "Fallback ke mode proxy manual");
        }
    }
}
