package com.tordroid.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Utility helpers for Tor operations
public class TorUtils {

    private static final String Tag = "TorUtils";

    // Returns true if the Tor SOCKS port is accepting connections
    public static boolean IsSocksPortOpen() {
        return IsPortOpen(TorConfig.TorHost, TorConfig.SocksPort, 1000);
    }

    // Returns true if the Tor Control port is accepting connections
    public static boolean IsControlPortOpen() {
        return IsPortOpen(TorConfig.TorHost, TorConfig.ControlPort, 1000);
    }

    // Returns true if the given host:port is reachable within the timeout
    public static boolean IsPortOpen(String Host, int Port, int TimeoutMs) {
        try (Socket Socket = new Socket()) {
            Socket.connect(new InetSocketAddress(Host, Port), TimeoutMs);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    // Copies an asset file to the given destination path and marks it executable
    public static boolean CopyAsset(Context AppContext, String AssetName, String DestPath) {
        try {
            File DestFile = new File(DestPath);
            if (DestFile.getParentFile() != null) {
                DestFile.getParentFile().mkdirs();
            }
            try (InputStream In = AppContext.getAssets().open(AssetName);
                 OutputStream Out = new FileOutputStream(DestFile)) {
                byte[] Buffer = new byte[4096];
                int Length;
                while ((Length = In.read(Buffer)) > 0) {
                    Out.write(Buffer, 0, Length);
                }
            }
            DestFile.setExecutable(true);
            return true;
        } catch (IOException e) {
            Log.e(Tag, "Failed to copy asset: " + AssetName, e);
            return false;
        }
    }

    // Writes a plain text string to the given file path
    public static boolean WriteTextFile(String Path, String Content) {
        try {
            File File = new File(Path);
            if (File.getParentFile() != null) File.getParentFile().mkdirs();
            try (FileOutputStream Stream = new FileOutputStream(File)) {
                Stream.write(Content.getBytes("UTF-8"));
            }
            return true;
        } catch (IOException e) {
            Log.e(Tag, "Failed to write file: " + Path, e);
            return false;
        }
    }

    // Recursively deletes a directory and all its contents
    public static void DeleteRecursive(File Dir) {
        if (Dir.isDirectory()) {
            for (File Child : Dir.listFiles()) {
                DeleteRecursive(Child);
            }
        }
        Dir.delete();
    }

    // Returns true if the device has an active network connection
    public static boolean IsNetworkAvailable(Context AppContext) {
        ConnectivityManager Manager = (ConnectivityManager)
            AppContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (Manager == null) return false;
        NetworkInfo Info = Manager.getActiveNetworkInfo();
        return Info != null && Info.isConnected();
    }

    // Parses the bootstrap percentage from a Tor log line
    // Example input: "Bootstrapped 25%: Loading networkstatus consensus"
    public static int ParseBootstrapPercent(String Line) {
        Pattern P = Pattern.compile("Bootstrapped (\\d+)%");
        Matcher M = P.matcher(Line);
        if (M.find()) {
            try {
                return Integer.parseInt(M.group(1));
            } catch (NumberFormatException Ignored) {}
        }
        return -1;
    }

    // Parses the status message from a Tor bootstrap log line
    public static String ParseBootstrapMessage(String Line) {
        Pattern P = Pattern.compile("Bootstrapped \\d+%: (.+)");
        Matcher M = P.matcher(Line);
        if (M.find()) return M.group(1);
        return null;
    }

    // Formats a byte count into a human-readable string
    public static String FormatBytes(long Bytes) {
        if (Bytes < 1024) return Bytes + " B";
        if (Bytes < 1024 * 1024) return String.format("%.1f KB", Bytes / 1024.0);
        if (Bytes < 1024L * 1024 * 1024) return String.format("%.1f MB", Bytes / (1024.0 * 1024));
        return String.format("%.1f GB", Bytes / (1024.0 * 1024 * 1024));
    }
}
