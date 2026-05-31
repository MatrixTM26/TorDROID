package com.tordroid.util;

import android.util.Log;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;

/**
 * TorControlClient - Komunikasi dengan Tor via Control Port (TCP)
 * Implementasi protokol Tor Control Protocol v1
 */
public class TorControlClient {

    private static final String TAG = "TorControlClient";

    private Socket mSocket;
    private BufferedReader mReader;
    private BufferedWriter mWriter;
    private boolean mConnected = false;

    /**
     * Sambungkan ke Tor Control Port
     */
    public boolean connect() {
        try {
            mSocket = new Socket(TorConfig.TOR_HOST, TorConfig.CONTROL_PORT);
            mSocket.setSoTimeout(5000);
            mReader = new BufferedReader(new InputStreamReader(mSocket.getInputStream()));
            mWriter = new BufferedWriter(new OutputStreamWriter(mSocket.getOutputStream()));
            mConnected = true;
            Log.d(TAG, "Terhubung ke Tor Control Port");
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Gagal koneksi ke Control Port: " + e.getMessage());
            mConnected = false;
            return false;
        }
    }

    /**
     * Autentikasi ke Tor Control Port (null-auth / cookie)
     */
    public boolean authenticate() {
        return authenticate("");
    }

    public boolean authenticate(String password) {
        try {
            String cmd = password.isEmpty() ? "AUTHENTICATE\r\n" : "AUTHENTICATE \"" + password + "\"\r\n";
            sendCommand(cmd);
            String resp = mReader.readLine();
            Log.d(TAG, "Auth response: " + resp);
            return resp != null && resp.startsWith("250");
        } catch (IOException e) {
            Log.e(TAG, "Autentikasi gagal: " + e.getMessage());
            return false;
        }
    }

    /**
     * Minta identitas baru (IP exit node baru)
     * Setara dengan "New Circuit" di Tor Browser
     */
    public boolean newIdentity() {
        try {
            sendCommand("SIGNAL NEWNYM\r\n");
            String resp = mReader.readLine();
            Log.d(TAG, "NEWNYM response: " + resp);
            return resp != null && resp.startsWith("250");
        } catch (IOException e) {
            Log.e(TAG, "NewIdentity gagal: " + e.getMessage());
            return false;
        }
    }

    /**
     * Bersihkan semua circuit Tor
     */
    public boolean clearDnsCache() {
        try {
            sendCommand("SIGNAL CLEARDNSCACHE\r\n");
            String resp = mReader.readLine();
            return resp != null && resp.startsWith("250");
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Ambil IP exit node saat ini via checkip.torproject.org
     */
    public String getExitNodeIP() {
        try {
            sendCommand("GETINFO address\r\n");
            String resp = mReader.readLine();
            if (resp != null && resp.startsWith("250-address=")) {
                return resp.substring("250-address=".length()).trim();
            }
        } catch (IOException e) {
            Log.e(TAG, "Gagal ambil IP: " + e.getMessage());
        }
        return null;
    }

    /**
     * Ambil info circuit aktif
     */
    public String getCircuitStatus() {
        try {
            sendCommand("GETINFO circuit-status\r\n");
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = mReader.readLine()) != null) {
                if (line.startsWith("250 ")) break;
                sb.append(line).append("\n");
            }
            return sb.toString();
        } catch (IOException e) {
            return "Error: " + e.getMessage();
        }
    }

    /**
     * Cek status bootstrap Tor
     */
    public String getBootstrapStatus() {
        try {
            sendCommand("GETINFO status/bootstrap-phase\r\n");
            String resp = mReader.readLine();
            mReader.readLine(); // consume "250 OK"
            return resp;
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Kirim perintah raw ke Tor Control
     */
    private void sendCommand(String cmd) throws IOException {
        mWriter.write(cmd);
        mWriter.flush();
    }

    /**
     * Tutup koneksi Control Port
     */
    public void disconnect() {
        try {
            if (mSocket != null && !mSocket.isClosed()) {
                sendCommand("QUIT\r\n");
                mSocket.close();
            }
        } catch (IOException ignored) {}
        mConnected = false;
    }

    public boolean isConnected() {
        return mConnected && mSocket != null && !mSocket.isClosed();
    }
}
