package com.tordroid.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TorUtils - Kelas utilitas untuk operasi Tor
 */
public class TorUtils {

    private static final String TAG = "TorUtils";

    /**
     * Cek apakah port SOCKS Tor sedang aktif / mendengarkan
     */
    public static boolean isSocksPortOpen() {
        return isPortOpen(TorConfig.TOR_HOST, TorConfig.SOCKS_PORT, 1000);
    }

    /**
     * Cek apakah port Tor Control aktif
     */
    public static boolean isControlPortOpen() {
        return isPortOpen(TorConfig.TOR_HOST, TorConfig.CONTROL_PORT, 1000);
    }

    /**
     * Cek apakah port tertentu aktif
     */
    public static boolean isPortOpen(String host, int port, int timeoutMs) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Salin file dari assets ke direktori data aplikasi
     */
    public static boolean copyAsset(Context context, String assetName, String destPath) {
        try {
            File destFile = new File(destPath);
            if (destFile.getParentFile() != null) {
                destFile.getParentFile().mkdirs();
            }
            try (
                InputStream in = context.getAssets().open(assetName);
                OutputStream out = new FileOutputStream(destFile)
            ) {
                byte[] buf = new byte[4096];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
            }
            destFile.setExecutable(true);
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Gagal menyalin asset: " + assetName, e);
            return false;
        }
    }

    /**
     * Tulis file teks (torrc config)
     */
    public static boolean writeTextFile(String path, String content) {
        try {
            File file = new File(path);
            if (file.getParentFile() != null) file.getParentFile().mkdirs();
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(content.getBytes("UTF-8"));
            }
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Gagal menulis file: " + path, e);
            return false;
        }
    }

    /**
     * Hapus direktori secara rekursif
     */
    public static void deleteRecursive(File dir) {
        if (dir.isDirectory()) {
            for (File child : dir.listFiles()) {
                deleteRecursive(child);
            }
        }
        dir.delete();
    }

    /**
     * Cek koneksi internet
     */
    public static boolean isNetworkAvailable(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkInfo ni = cm.getActiveNetworkInfo();
        return ni != null && ni.isConnected();
    }

    /**
     * Parse persentase bootstrap dari log Tor
     * Contoh: "Bootstrapped 25%: Loading networkstatus consensus"
     */
    public static int parseBootstrapPercent(String line) {
        Pattern p = Pattern.compile("Bootstrapped (\\d+)%");
        Matcher m = p.matcher(line);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException ignored) {}
        }
        return -1;
    }

    /**
     * Parse pesan status bootstrap dari log Tor
     */
    public static String parseBootstrapMessage(String line) {
        Pattern p = Pattern.compile("Bootstrapped \\d+%: (.+)");
        Matcher m = p.matcher(line);
        if (m.find()) return m.group(1);
        return null;
    }

    /**
     * Format bytes menjadi human-readable
     */
    public static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
