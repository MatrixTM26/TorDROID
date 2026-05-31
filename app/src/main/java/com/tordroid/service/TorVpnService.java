package com.tordroid.service;

import android.content.Intent;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import com.tordroid.util.TorConfig;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

// Creates and manages the Android VPN interface that routes all traffic through Tor
// Flow: App -> VPN interface (tun0) -> TorVpnService -> Tor SOCKS5 -> Internet
public class TorVpnService extends VpnService {

    private static final String Tag = "TorVpnService";

    private ParcelFileDescriptor VpnInterface;
    private ExecutorService Executor;
    private AtomicBoolean Running = new AtomicBoolean(false);

    @Override
    public int onStartCommand(Intent IntentParam, int Flags, int StartId) {
        if (IntentParam == null) return START_NOT_STICKY;

        String Action = IntentParam.getAction();
        if (TorConfig.ActionStart.equals(Action))    StartVpn();
        else if (TorConfig.ActionStop.equals(Action)) StopVpn();

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        StopVpn();
    }

    // Builds the VPN interface and starts packet routing
    private void StartVpn() {
        if (Running.get()) return;

        try {
            Builder VpnBuilder = new Builder()
                .setMtu(TorConfig.VpnMtu)
                .addAddress(TorConfig.VpnAddress, TorConfig.VpnPrefix)
                .addRoute(TorConfig.VpnRoute, 0)
                .addDnsServer(TorConfig.VpnDns)
                .setSession("TorDROID")
                .setBlocking(true);

            // Exclude TorDROID itself so it can reach the Tor daemon directly
            try {
                VpnBuilder.addDisallowedApplication(getPackageName());
            } catch (Exception Ignored) {}

            VpnInterface = VpnBuilder.establish();
            if (VpnInterface == null) {
                Log.e(Tag, "Failed to establish VPN interface");
                return;
            }

            Running.set(true);
            Log.d(Tag, "VPN interface established, starting packet routing");

            Executor = Executors.newFixedThreadPool(2);
            StartPacketRouting();

        } catch (Exception e) {
            Log.e(Tag, "Error starting VPN", e);
        }
    }

    // Tears down the VPN interface and stops all related threads
    private void StopVpn() {
        Running.set(false);

        if (Executor != null) Executor.shutdownNow();

        if (VpnInterface != null) {
            try {
                VpnInterface.close();
            } catch (IOException e) {
                Log.e(Tag, "Error closing VPN interface", e);
            }
            VpnInterface = null;
        }

        stopSelf();
        Log.d(Tag, "VPN stopped");
    }

    // Launches the tun2socks process to bridge the VPN tun device with Tor SOCKS5
    private void StartPacketRouting() {
        Executor.execute(() -> {
            try {
                StartTun2Socks();
            } catch (Exception e) {
                Log.e(Tag, "Packet routing error", e);
            }
        });
    }

    // Runs the tun2socks binary which transparently forwards packets to Tor SOCKS5
    private void StartTun2Socks() {
        try {
            String Tun2SocksBin = getFilesDir() + "/tun2socks";
            String SocksAddr = TorConfig.TorHost + ":" + TorConfig.SocksPort;
            String TunFd = String.valueOf(VpnInterface.getFd());

            String[] Command = {
                Tun2SocksBin,
                "--netif-ipaddr",        TorConfig.VpnDns,
                "--netif-netmask",       "255.255.255.0",
                "--socks-server-addr",   SocksAddr,
                "--tunfd",               TunFd,
                "--tunmtu",              String.valueOf(TorConfig.VpnMtu),
                "--sock-path",           getFilesDir() + "/tun2socks.sock",
                "--enable-udp",
                "--udpgw-remote-server-addr", SocksAddr,
                "--loglevel",            "3"
            };

            ProcessBuilder Builder = new ProcessBuilder(Command);
            Builder.environment().put("HOME", getFilesDir().getAbsolutePath());

            Process Tun2SocksProcess = Builder.start();
            Log.d(Tag, "tun2socks started, PID: " + Tun2SocksProcess.pid());

            while (Running.get()) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    break;
                }
            }

            Tun2SocksProcess.destroy();

        } catch (IOException e) {
            Log.e(Tag, "Failed to run tun2socks: " + e.getMessage());
        }
    }
}
