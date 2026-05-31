package com.tordroid.util;

/**
 * TorStatus - Representasi status koneksi TorDROID
 */
public class TorStatus {

    public enum State {
        STOPPED, // Tor tidak berjalan
        STARTING, // Sedang memulai Tor
        BOOTSTRAPPING, // Terhubung ke jaringan Tor, melakukan bootstrap
        CONNECTED, // Siap digunakan (bootstrap 100%)
        STOPPING, // Sedang menghentikan Tor
        ERROR, // Terjadi error
    }

    private State mState;
    private int mBootstrapPercent;
    private String mBootstrapMessage;
    private String mExitIp;
    private long mConnectedAt;
    private String mErrorMessage;

    public TorStatus() {
        mState = State.STOPPED;
        mBootstrapPercent = 0;
    }

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public State getState() {
        return mState;
    }

    public void setState(State state) {
        mState = state;
        if (state == State.CONNECTED) {
            mConnectedAt = System.currentTimeMillis();
        }
    }

    public int getBootstrapPercent() {
        return mBootstrapPercent;
    }

    public void setBootstrapPercent(int percent) {
        mBootstrapPercent = percent;
    }

    public String getBootstrapMessage() {
        return mBootstrapMessage;
    }

    public void setBootstrapMessage(String msg) {
        mBootstrapMessage = msg;
    }

    public String getExitIp() {
        return mExitIp;
    }

    public void setExitIp(String ip) {
        mExitIp = ip;
    }

    public long getConnectedAt() {
        return mConnectedAt;
    }

    public String getErrorMessage() {
        return mErrorMessage;
    }

    public void setErrorMessage(String msg) {
        mErrorMessage = msg;
    }

    public boolean isActive() {
        return mState == State.CONNECTED || mState == State.BOOTSTRAPPING;
    }

    public boolean isConnected() {
        return mState == State.CONNECTED;
    }

    /**
     * Hitung durasi koneksi
     */
    public String getUptime() {
        if (mConnectedAt == 0) return "00:00:00";
        long diff = System.currentTimeMillis() - mConnectedAt;
        long h = (diff / 3600000) % 24;
        long m = (diff / 60000) % 60;
        long s = (diff / 1000) % 60;
        return String.format("%02d:%02d:%02d", h, m, s);
    }

    @Override
    public String toString() {
        return "TorStatus{state=" + mState + ", bootstrap=" + mBootstrapPercent + "%" + ", exitIp=" + mExitIp + "}";
    }
}
