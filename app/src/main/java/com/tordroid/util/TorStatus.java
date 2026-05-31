package com.tordroid.util;

// Represents the current connection state of TorDROID
public class TorStatus {

    public enum State {
        Stopped,
        Starting,
        Bootstrapping,
        Connected,
        Stopping,
        Error
    }

    private State State;
    private int BootstrapPercent;
    private String BootstrapMessage;
    private String ExitIp;
    private long ConnectedAt;
    private String ErrorMessage;

    public TorStatus() {
        State = com.tordroid.util.TorStatus.State.Stopped;
        BootstrapPercent = 0;
    }

    public com.tordroid.util.TorStatus.State GetState() {
        return State;
    }

    public void SetState(com.tordroid.util.TorStatus.State NewState) {
        State = NewState;
        if (NewState == com.tordroid.util.TorStatus.State.Connected) {
            ConnectedAt = System.currentTimeMillis();
        }
    }

    public int GetBootstrapPercent() {
        return BootstrapPercent;
    }

    public void SetBootstrapPercent(int Percent) {
        BootstrapPercent = Percent;
    }

    public String GetBootstrapMessage() {
        return BootstrapMessage;
    }

    public void SetBootstrapMessage(String Message) {
        BootstrapMessage = Message;
    }

    public String GetExitIp() {
        return ExitIp;
    }

    public void SetExitIp(String Ip) {
        ExitIp = Ip;
    }

    public long GetConnectedAt() {
        return ConnectedAt;
    }

    public String GetErrorMessage() {
        return ErrorMessage;
    }

    public void SetErrorMessage(String Message) {
        ErrorMessage = Message;
    }

    public boolean IsActive() {
        return State == com.tordroid.util.TorStatus.State.Connected
            || State == com.tordroid.util.TorStatus.State.Bootstrapping;
    }

    public boolean IsConnected() {
        return State == com.tordroid.util.TorStatus.State.Connected;
    }

    // Returns formatted uptime string HH:MM:SS
    public String GetUptime() {
        if (ConnectedAt == 0) return "00:00:00";
        long Diff = System.currentTimeMillis() - ConnectedAt;
        long Hours = (Diff / 3600000) % 24;
        long Minutes = (Diff / 60000) % 60;
        long Seconds = (Diff / 1000) % 60;
        return String.format("%02d:%02d:%02d", Hours, Minutes, Seconds);
    }

    @Override
    public String toString() {
        return "TorStatus { State=" + State
            + ", Bootstrap=" + BootstrapPercent + "%"
            + ", ExitIp=" + ExitIp + " }";
    }
}
