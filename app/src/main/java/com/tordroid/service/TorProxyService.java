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

// Manages the Tor daemon process lifecycle and monitors its log output
public class TorProxyService extends Service {

    private static final String Tag = "TorProxyService";

    private final IBinder LocalBinder = new ServiceBinder();
    private ExecutorService Executor;
    private Process TorProcess;
    private AtomicBoolean Running = new AtomicBoolean(false);
    private TorStatus Status = new TorStatus();
    private TorControlClient ControlClient;

    public class ServiceBinder extends Binder {
        public TorProxyService GetService() {
            return TorProxyService.this;
        }
    }

    @Override
    public IBinder onBind(Intent IntentParam) {
        return LocalBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Executor = Executors.newCachedThreadPool();
        CreateNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent IntentParam, int Flags, int StartId) {
        if (IntentParam == null) return START_NOT_STICKY;

        String Action = IntentParam.getAction();
        if (Action == null) return START_NOT_STICKY;

        if (Action.equals(TorConfig.ActionStart)) StartTor();
        else if (Action.equals(TorConfig.ActionStop)) StopTor();
        else if (Action.equals(TorConfig.ActionNewIdentity)) RequestNewIdentity();

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        StopTor();
        if (Executor != null) Executor.shutdownNow();
    }

    // Starts the Tor daemon process
    public void StartTor() {
        if (Running.get()) {
            Log.d(Tag, "Tor is already running");
            return;
        }

        Status.SetState(TorStatus.State.Starting);
        BroadcastStatus("Preparing Tor...", 0);
        startForeground(TorConfig.NotificationId, BuildNotification("Starting Tor...", 0));

        Executor.execute(() -> {
            try {
                if (!PrepareTorBinary()) {
                    HandleError("Failed to prepare Tor binary");
                    return;
                }

                String DataDir = getFilesDir().getAbsolutePath() + "/tor_data";
                String TorrcPath = getFilesDir().getAbsolutePath() + "/torrc";
                new File(DataDir).mkdirs();

                TorUtils.WriteTextFile(TorrcPath, TorConfig.BuildTorrcConfig(DataDir));

                String TorBinary = getFilesDir().getAbsolutePath() + "/tor";
                ProcessBuilder Builder = new ProcessBuilder(TorBinary, "-f", TorrcPath);
                Builder.environment().put("HOME", getFilesDir().getAbsolutePath());
                Builder.redirectErrorStream(true);

                TorProcess = Builder.start();
                Running.set(true);

                Log.d(Tag, "Tor process started");
                MonitorTorLog();

            } catch (Exception e) {
                Log.e(Tag, "Error starting Tor", e);
                HandleError("Error: " + e.getMessage());
            }
        });
    }

    // Stops the Tor daemon and cleans up
    public void StopTor() {
        Status.SetState(TorStatus.State.Stopping);
        BroadcastStatus("Stopping Tor...", 0);

        if (ControlClient != null) {
            ControlClient.Disconnect();
            ControlClient = null;
        }

        if (TorProcess != null) {
            TorProcess.destroy();
            TorProcess = null;
        }

        Running.set(false);
        Status.SetState(TorStatus.State.Stopped);
        BroadcastStatus("Tor stopped", 0);
        stopForeground(true);
        stopSelf();
    }

    // Sends the NEWNYM signal to Tor to request a new identity
    public void RequestNewIdentity() {
        Executor.execute(() -> {
            if (ControlClient == null || !ControlClient.IsConnected()) {
                ControlClient = new TorControlClient();
                if (!ControlClient.Connect() || !ControlClient.Authenticate()) {
                    BroadcastStatus("Failed to connect to Tor Control", -1);
                    return;
                }
            }
            if (ControlClient.NewIdentity()) {
                BroadcastStatus("New identity requested successfully", -1);
                try { Thread.sleep(3000); } catch (InterruptedException Ignored) {}
                FetchAndBroadcastExitIp();
            } else {
                BroadcastStatus("Failed to request new identity", -1);
            }
        });
    }

    // Copies the correct Tor binary from assets based on device ABI
    private boolean PrepareTorBinary() {
        String DestPath = getFilesDir().getAbsolutePath() + "/tor";
        File TorBin = new File(DestPath);

        if (TorBin.exists() && TorBin.canExecute()) {
            Log.d(Tag, "Tor binary already exists");
            return true;
        }

        String Abi = Build.SUPPORTED_ABIS[0];
        String AssetName;

        if (Abi.contains("arm64"))       AssetName = "tor-arm64";
        else if (Abi.contains("armeabi")) AssetName = "tor-armeabi";
        else if (Abi.contains("x86_64")) AssetName = "tor-x86_64";
        else if (Abi.contains("x86"))    AssetName = "tor-x86";
        else                             AssetName = "tor-arm64";

        Log.d(Tag, "Copying Tor binary: " + AssetName);
        return TorUtils.CopyAsset(this, AssetName, DestPath);
    }

    // Reads Tor stdout log and parses bootstrap progress lines
    private void MonitorTorLog() {
        try (BufferedReader LogReader = new BufferedReader(
                new InputStreamReader(TorProcess.getInputStream()))) {

            String Line;
            while ((Line = LogReader.readLine()) != null && Running.get()) {
                Log.d(Tag, "[TOR] " + Line);

                int Percent = TorUtils.ParseBootstrapPercent(Line);
                if (Percent >= 0) {
                    String Message = TorUtils.ParseBootstrapMessage(Line);
                    Status.SetState(TorStatus.State.Bootstrapping);
                    Status.SetBootstrapPercent(Percent);
                    Status.SetBootstrapMessage(Message);
                    BroadcastStatus(Message != null ? Message : "Bootstrap " + Percent + "%", Percent);
                    UpdateNotification("Bootstrap " + Percent + "%", Percent);

                    if (Percent == 100) OnTorReady();
                }

                if (Line.contains("[err]") || Line.contains("[warn]")) {
                    Log.w(Tag, "Tor warning: " + Line);
                }
            }

            if (Running.get()) HandleError("Tor process stopped unexpectedly");

        } catch (IOException e) {
            if (Running.get()) HandleError("Error reading Tor log: " + e.getMessage());
        }
    }

    // Called when Tor finishes bootstrapping to 100%
    private void OnTorReady() {
        Status.SetState(TorStatus.State.Connected);
        Log.d(Tag, "Tor connected successfully");

        Executor.execute(() -> {
            try { Thread.sleep(500); } catch (InterruptedException Ignored) {}
            ControlClient = new TorControlClient();
            if (ControlClient.Connect() && ControlClient.Authenticate()) {
                Log.d(Tag, "Control port connected");
                FetchAndBroadcastExitIp();
            }
        });

        BroadcastStatus("Connected to Tor network", 100);
        UpdateNotification("Protected by Tor", 100);
    }

    // Fetches the current exit node IP through the Tor SOCKS proxy
    private void FetchAndBroadcastExitIp() {
        Executor.execute(() -> {
            try {
                java.net.Proxy Proxy = new java.net.Proxy(
                    java.net.Proxy.Type.SOCKS,
                    new java.net.InetSocketAddress(TorConfig.TorHost, TorConfig.SocksPort));

                java.net.URL Url = new java.net.URL("https://api.ipify.org");
                java.net.HttpURLConnection Connection =
                    (java.net.HttpURLConnection) Url.openConnection(Proxy);
                Connection.setConnectTimeout(10000);
                Connection.setReadTimeout(10000);

                BufferedReader IpReader = new BufferedReader(
                    new InputStreamReader(Connection.getInputStream()));
                String Ip = IpReader.readLine();
                IpReader.close();

                if (Ip != null && !Ip.isEmpty()) {
                    Status.SetExitIp(Ip.trim());
                    Intent IpIntent = new Intent(TorConfig.ActionStatus);
                    IpIntent.putExtra(TorConfig.ExtraIp, Ip.trim());
                    sendBroadcast(IpIntent);
                    Log.d(Tag, "Exit IP: " + Ip);
                }
            } catch (Exception e) {
                Log.w(Tag, "Failed to fetch exit IP: " + e.getMessage());
            }
        });
    }

    // Handles a fatal error by updating state and broadcasting the message
    private void HandleError(String Message) {
        Log.e(Tag, "Error: " + Message);
        Running.set(false);
        Status.SetState(TorStatus.State.Error);
        Status.SetErrorMessage(Message);
        BroadcastStatus(Message, -1);
        UpdateNotification("Error: " + Message, 0);
    }

    // Sends a STATUS broadcast to any registered UI receivers
    private void BroadcastStatus(String Message, int Progress) {
        Intent StatusIntent = new Intent(TorConfig.ActionStatus);
        StatusIntent.putExtra(TorConfig.ExtraStatus, Status.GetState().name());
        StatusIntent.putExtra(TorConfig.ExtraMessage, Message);
        StatusIntent.putExtra(TorConfig.ExtraProgress, Progress);
        sendBroadcast(StatusIntent);
    }

    // Creates the notification channel required on Android O and above
    private void CreateNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel Channel = new NotificationChannel(
                TorConfig.NotificationChannelId,
                TorConfig.NotificationChannelName,
                NotificationManager.IMPORTANCE_LOW);
            Channel.setDescription("TorDROID VPN connection status");
            NotificationManager Manager = getSystemService(NotificationManager.class);
            if (Manager != null) Manager.createNotificationChannel(Channel);
        }
    }

    // Builds a foreground notification with optional progress bar
    private Notification BuildNotification(String Message, int Progress) {
        Intent MainIntent = new Intent(this, MainActivity.class);
        PendingIntent PendingIntentAction = PendingIntent.getActivity(this, 0, MainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder Builder = new NotificationCompat.Builder(
            this, TorConfig.NotificationChannelId)
            .setSmallIcon(R.drawable.ic_tor_shield)
            .setContentTitle("TorDROID")
            .setContentText(Message)
            .setOngoing(true)
            .setContentIntent(PendingIntentAction)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE);

        if (Progress > 0 && Progress < 100) {
            Builder.setProgress(100, Progress, false);
        }

        return Builder.build();
    }

    // Posts an updated notification to the system tray
    private void UpdateNotification(String Message, int Progress) {
        NotificationManager Manager = (NotificationManager)
            getSystemService(Context.NOTIFICATION_SERVICE);
        if (Manager != null) {
            Manager.notify(TorConfig.NotificationId, BuildNotification(Message, Progress));
        }
    }

    public TorStatus GetTorStatus() {
        return Status;
    }

    public boolean IsRunning() {
        return Running.get();
    }
}
