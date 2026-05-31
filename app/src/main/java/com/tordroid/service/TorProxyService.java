package com.tordroid.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import com.tordroid.R;
import com.tordroid.ui.MainActivity;
import com.tordroid.util.TorConfig;
import com.tordroid.util.TorControlClient;
import com.tordroid.util.TorStatus;
import com.tordroid.util.TorUtils;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * TorProxyService - Service utama yang mengelola proses Tor daemon
 *
 * Alur kerja:
 * 1. Salin binary Tor dari assets ke direktori data
 * 2. Tulis konfigurasi torrc
 * 3. Jalankan proses Tor
 * 4. Monitor log output untuk progress bootstrap
 * 5. Kirim broadcast status ke UI
 */
public class TorProxyService extends Service {

    private static final String TAG = "TorProxyService";

    private final IBinder mBinder = new LocalBinder();
    private ExecutorService mExecutor;
    private Process mTorProcess;
    private AtomicBoolean mRunning = new AtomicBoolean(false);
    private TorStatus mStatus = new TorStatus();
    private TorControlClient mControl;

    // ── Binder ────────────────────────────────────────────────────────────────

    public class LocalBinder extends Binder {

        public TorProxyService getService() {
            return TorProxyService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void onCreate() {
        super.onCreate();
        mExecutor = Executors.newCachedThreadPool();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;

        String action = intent.getAction();
        if (action == null) return START_NOT_STICKY;

        switch (action) {
            case TorConfig.ACTION_START:
                startTor();
                break;
            case TorConfig.ACTION_STOP:
                stopTor();
                break;
            case TorConfig.ACTION_NEWID:
                requestNewIdentity();
                break;
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopTor();
        if (mExecutor != null) mExecutor.shutdownNow();
    }

    // ── Tor Control ───────────────────────────────────────────────────────────

    /**
     * Mulai Tor daemon
     */
    public void startTor() {
        if (mRunning.get()) {
            Log.d(TAG, "Tor sudah berjalan");
            return;
        }

        mStatus.setState(TorStatus.State.STARTING);
        broadcastStatus("Mempersiapkan Tor...", 0);
        startForeground(TorConfig.NOTIF_ID, buildNotification("Memulai Tor...", 0));

        mExecutor.execute(() -> {
            try {
                // 1. Siapkan binary dan konfigurasi
                if (!prepareTorBinary()) {
                    handleError("Gagal menyiapkan binary Tor");
                    return;
                }

                // 2. Tulis torrc
                String dataDir = getFilesDir().getAbsolutePath() + "/tor_data";
                String torrcPath = getFilesDir().getAbsolutePath() + "/torrc";
                new File(dataDir).mkdirs();

                String torrcContent = TorConfig.buildTorrcConfig(dataDir);
                TorUtils.writeTextFile(torrcPath, torrcContent);

                // 3. Jalankan Tor
                String torBinary = getFilesDir().getAbsolutePath() + "/tor";
                ProcessBuilder pb = new ProcessBuilder(torBinary, "-f", torrcPath);
                pb.environment().put("HOME", getFilesDir().getAbsolutePath());
                pb.redirectErrorStream(true);

                mTorProcess = pb.start();
                mRunning.set(true);

                Log.d(TAG, "Proses Tor dimulai");

                // 4. Monitor log
                monitorTorLog();
            } catch (Exception e) {
                Log.e(TAG, "Error menjalankan Tor", e);
                handleError("Error: " + e.getMessage());
            }
        });
    }

    /**
     * Hentikan Tor daemon
     */
    public void stopTor() {
        mStatus.setState(TorStatus.State.STOPPING);
        broadcastStatus("Menghentikan Tor...", 0);

        // Disconnect control
        if (mControl != null) {
            mControl.disconnect();
            mControl = null;
        }

        // Kill process
        if (mTorProcess != null) {
            mTorProcess.destroy();
            mTorProcess = null;
        }

        mRunning.set(false);
        mStatus.setState(TorStatus.State.STOPPED);
        broadcastStatus("Tor dihentikan", 0);
        stopForeground(true);
        stopSelf();
    }

    /**
     * Minta identitas / IP baru
     */
    public void requestNewIdentity() {
        mExecutor.execute(() -> {
            if (mControl == null || !mControl.isConnected()) {
                mControl = new TorControlClient();
                if (!mControl.connect() || !mControl.authenticate()) {
                    broadcastStatus("Gagal koneksi ke Tor Control", -1);
                    return;
                }
            }
            if (mControl.newIdentity()) {
                broadcastStatus("Identitas baru berhasil diminta!", -1);
                // Ambil IP baru setelah delay singkat
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException ignored) {}
                fetchAndBroadcastExitIP();
            } else {
                broadcastStatus("Gagal meminta identitas baru", -1);
            }
        });
    }

    // ── Private Helpers ───────────────────────────────────────────────────────

    /**
     * Salin binary Tor dari assets ke direktori yang bisa dieksekusi
     */
    private boolean prepareTorBinary() {
        String destPath = getFilesDir().getAbsolutePath() + "/tor";
        File torBin = new File(destPath);

        // Cek apakah sudah ada
        if (torBin.exists() && torBin.canExecute()) {
            Log.d(TAG, "Binary Tor sudah ada");
            return true;
        }

        // Tentukan ABI
        String abi = Build.SUPPORTED_ABIS[0];
        String assetName;

        if (abi.contains("arm64")) {
            assetName = "tor-arm64";
        } else if (abi.contains("armeabi")) {
            assetName = "tor-armeabi";
        } else if (abi.contains("x86_64")) {
            assetName = "tor-x86_64";
        } else if (abi.contains("x86")) {
            assetName = "tor-x86";
        } else {
            assetName = "tor-arm64"; // default
        }

        Log.d(TAG, "Menyalin binary Tor: " + assetName + " -> " + destPath);
        return TorUtils.copyAsset(this, assetName, destPath);
    }

    /**
     * Monitor output log Tor dan parse bootstrap progress
     */
    private void monitorTorLog() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(mTorProcess.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null && mRunning.get()) {
                Log.d(TAG, "[TOR] " + line);

                // Parse bootstrap
                int percent = TorUtils.parseBootstrapPercent(line);
                if (percent >= 0) {
                    String msg = TorUtils.parseBootstrapMessage(line);
                    mStatus.setState(TorStatus.State.BOOTSTRAPPING);
                    mStatus.setBootstrapPercent(percent);
                    mStatus.setBootstrapMessage(msg);
                    broadcastStatus(msg != null ? msg : "Bootstrap " + percent + "%", percent);
                    updateNotification("Bootstrap " + percent + "%", percent);

                    // Bootstrap selesai
                    if (percent == 100) {
                        onTorReady();
                    }
                }

                // Deteksi error
                if (line.contains("[err]") || line.contains("[warn]")) {
                    Log.w(TAG, "Tor warning/error: " + line);
                }
            }

            // Process selesai
            if (mRunning.get()) {
                handleError("Proses Tor berhenti tak terduga");
            }
        } catch (IOException e) {
            if (mRunning.get()) {
                handleError("Error membaca log Tor: " + e.getMessage());
            }
        }
    }

    /**
     * Dipanggil saat Tor selesai bootstrap (100%)
     */
    private void onTorReady() {
        mStatus.setState(TorStatus.State.CONNECTED);
        Log.d(TAG, "Tor berhasil terhubung!");

        // Hubungkan ke control port
        mExecutor.execute(() -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException ignored) {}
            mControl = new TorControlClient();
            if (mControl.connect() && mControl.authenticate()) {
                Log.d(TAG, "Control port terhubung");
                fetchAndBroadcastExitIP();
            }
        });

        broadcastStatus("Terhubung ke jaringan Tor!", 100);
        updateNotification("Terlindungi oleh Tor", 100);
    }

    /**
     * Ambil dan kirim info IP exit node
     */
    private void fetchAndBroadcastExitIP() {
        mExecutor.execute(() -> {
            try {
                // Gunakan okhttp via SOCKS proxy untuk cek IP
                java.net.Proxy proxy = new java.net.Proxy(
                    java.net.Proxy.Type.SOCKS,
                    new java.net.InetSocketAddress(TorConfig.TOR_HOST, TorConfig.SOCKS_PORT)
                );

                java.net.URL url = new java.net.URL("https://api.ipify.org");
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection(proxy);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String ip = reader.readLine();
                reader.close();

                if (ip != null && !ip.isEmpty()) {
                    mStatus.setExitIp(ip.trim());
                    Intent intent = new Intent(TorConfig.ACTION_STATUS);
                    intent.putExtra(TorConfig.EXTRA_IP, ip.trim());
                    sendBroadcast(intent);
                    Log.d(TAG, "Exit IP: " + ip);
                }
            } catch (Exception e) {
                Log.w(TAG, "Gagal mengambil exit IP: " + e.getMessage());
            }
        });
    }

    /**
     * Handle error
     */
    private void handleError(String message) {
        Log.e(TAG, "Error: " + message);
        mRunning.set(false);
        mStatus.setState(TorStatus.State.ERROR);
        mStatus.setErrorMessage(message);
        broadcastStatus(message, -1);
        updateNotification("Error: " + message, 0);
    }

    /**
     * Kirim broadcast status ke UI
     */
    private void broadcastStatus(String message, int progress) {
        Intent intent = new Intent(TorConfig.ACTION_STATUS);
        intent.putExtra(TorConfig.EXTRA_STATUS, mStatus.getState().name());
        intent.putExtra(TorConfig.EXTRA_MESSAGE, message);
        intent.putExtra(TorConfig.EXTRA_PROGRESS, progress);
        sendBroadcast(intent);
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                TorConfig.NOTIF_CHANNEL_ID,
                TorConfig.NOTIF_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Status koneksi TorDROID VPN");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String message, int progress) {
        Intent mainIntent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(
            this,
            0,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, TorConfig.NOTIF_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tor_shield)
            .setContentTitle("TorDROID")
            .setContentText(message)
            .setOngoing(true)
            .setContentIntent(pi)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE);

        if (progress > 0 && progress < 100) {
            builder.setProgress(100, progress, false);
        }

        return builder.build();
    }

    private void updateNotification(String message, int progress) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(TorConfig.NOTIF_ID, buildNotification(message, progress));
        }
    }

    // ── Public Accessors ──────────────────────────────────────────────────────

    public TorStatus getTorStatus() {
        return mStatus;
    }

    public boolean isRunning() {
        return mRunning.get();
    }
}
