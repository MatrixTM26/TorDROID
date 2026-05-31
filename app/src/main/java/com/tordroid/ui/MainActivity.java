package com.tordroid.ui;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.VpnService;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.tordroid.R;
import com.tordroid.service.TorProxyService;
import com.tordroid.service.TorVpnService;
import com.tordroid.util.TorConfig;
import com.tordroid.util.TorStatus;

// Main screen showing connection status, bootstrap progress, exit IP, and action buttons
public class MainActivity extends AppCompatActivity {

    private static final int VpnRequestCode = 100;
    private static final String Tag = "MainActivity";

    // Views
    private ImageView ShieldIcon;
    private TextView StatusText;
    private TextView SubStatusText;
    private ProgressBar BootstrapProgress;
    private TextView ProgressText;
    private TextView ExitIpText;
    private TextView UptimeText;
    private Button ConnectButton;
    private Button NewIdentityButton;
    private CardView IpCard;
    private View StatusDot;

    // State
    private TorStatus.State CurrentState = TorStatus.State.Stopped;
    private Handler UiHandler = new Handler(Looper.getMainLooper());
    private Runnable UptimeRunner;
    private long ConnectedAt = 0;
    private ObjectAnimator PulseAnimator;

    // Receives status broadcasts from TorProxyService and updates the UI
    private final BroadcastReceiver StatusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context AppContext, Intent ReceivedIntent) {
            if (!TorConfig.ActionStatus.equals(ReceivedIntent.getAction())) return;

            String StateString = ReceivedIntent.getStringExtra(TorConfig.ExtraStatus);
            String Message = ReceivedIntent.getStringExtra(TorConfig.ExtraMessage);
            int Progress = ReceivedIntent.getIntExtra(TorConfig.ExtraProgress, 0);
            String ExitIp = ReceivedIntent.getStringExtra(TorConfig.ExtraIp);

            if (StateString != null) {
                try {
                    CurrentState = TorStatus.State.valueOf(StateString);
                } catch (IllegalArgumentException Ignored) {}
            }

            UpdateUi(Message, Progress, ExitIp);
        }
    };

    @Override
    protected void onCreate(Bundle SavedInstanceState) {
        super.onCreate(SavedInstanceState);
        setContentView(R.layout.activity_main);
        BindViews();
        SetupClickListeners();
        SetupPulseAnimation();
        UpdateUi("Ready to connect", 0, null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter Filter = new IntentFilter(TorConfig.ActionStatus);
        registerReceiver(StatusReceiver, Filter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(StatusReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        StopUptimeTimer();
        if (PulseAnimator != null) PulseAnimator.cancel();
    }

    // Binds all view references from the layout
    private void BindViews() {
        ShieldIcon        = findViewById(R.id.iv_shield);
        StatusText        = findViewById(R.id.tv_status);
        SubStatusText     = findViewById(R.id.tv_substatus);
        BootstrapProgress = findViewById(R.id.pb_bootstrap);
        ProgressText      = findViewById(R.id.tv_progress);
        ExitIpText        = findViewById(R.id.tv_exit_ip);
        UptimeText        = findViewById(R.id.tv_uptime);
        ConnectButton     = findViewById(R.id.btn_connect);
        NewIdentityButton = findViewById(R.id.btn_new_identity);
        IpCard            = findViewById(R.id.card_ip);
        StatusDot         = findViewById(R.id.view_status_dot);
    }

    // Wires up button click listeners
    private void SetupClickListeners() {
        ConnectButton.setOnClickListener(V -> {
            if (CurrentState == TorStatus.State.Connected
                    || CurrentState == TorStatus.State.Bootstrapping
                    || CurrentState == TorStatus.State.Starting) {
                DisconnectTor();
            } else {
                ConnectTor();
            }
        });

        NewIdentityButton.setOnClickListener(V -> RequestNewIdentity());

        // Tap the IP card to copy the exit IP to clipboard
        IpCard.setOnClickListener(V -> {
            String Ip = ExitIpText.getText().toString();
            if (!Ip.isEmpty() && !Ip.equals("-")) {
                android.content.ClipboardManager Clipboard =
                    (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                Clipboard.setPrimaryClip(
                    android.content.ClipData.newPlainText("Exit IP", Ip));
                Toast.makeText(this, "IP copied: " + Ip, Toast.LENGTH_SHORT).show();
            }
        });
    }

    // Requests VPN permission then starts the Tor and VPN services
    private void ConnectTor() {
        Intent VpnIntent = VpnService.prepare(this);
        if (VpnIntent != null) {
            startActivityForResult(VpnIntent, VpnRequestCode);
        } else {
            OnVpnPermissionGranted();
        }
    }

    @Override
    protected void onActivityResult(int RequestCode, int ResultCode, Intent Data) {
        super.onActivityResult(RequestCode, ResultCode, Data);
        if (RequestCode == VpnRequestCode) {
            if (ResultCode == Activity.RESULT_OK) {
                OnVpnPermissionGranted();
            } else {
                Toast.makeText(this, "VPN permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // Starts both TorProxyService and TorVpnService after permission is granted
    private void OnVpnPermissionGranted() {
        Intent TorServiceIntent = new Intent(this, TorProxyService.class);
        TorServiceIntent.setAction(TorConfig.ActionStart);
        startForegroundService(TorServiceIntent);

        Intent VpnServiceIntent = new Intent(this, TorVpnService.class);
        VpnServiceIntent.setAction(TorConfig.ActionStart);
        startService(VpnServiceIntent);

        CurrentState = TorStatus.State.Starting;
        UpdateUi("Starting Tor connection...", 0, null);
    }

    // Sends stop actions to both services
    private void DisconnectTor() {
        Intent TorServiceIntent = new Intent(this, TorProxyService.class);
        TorServiceIntent.setAction(TorConfig.ActionStop);
        startService(TorServiceIntent);

        Intent VpnServiceIntent = new Intent(this, TorVpnService.class);
        VpnServiceIntent.setAction(TorConfig.ActionStop);
        startService(VpnServiceIntent);

        CurrentState = TorStatus.State.Stopped;
        StopUptimeTimer();
        UpdateUi("Disconnected from Tor", 0, null);
    }

    // Sends the new identity action to TorProxyService
    private void RequestNewIdentity() {
        Intent ServiceIntent = new Intent(this, TorProxyService.class);
        ServiceIntent.setAction(TorConfig.ActionNewIdentity);
        startService(ServiceIntent);
        Toast.makeText(this, "Requesting new identity...", Toast.LENGTH_SHORT).show();
    }

    // Updates all UI elements to reflect the current connection state
    private void UpdateUi(String Message, int Progress, String ExitIp) {
        runOnUiThread(() -> {
            switch (CurrentState) {
                case Stopped:
                    SetShieldState(false, false);
                    StatusText.setText("Not Protected");
                    StatusText.setTextColor(getColor(R.color.color_disconnected));
                    SubStatusText.setText("Your connection is not encrypted");
                    ConnectButton.setText("Connect to Tor");
                    ConnectButton.setBackgroundTintList(
                        getColorStateList(R.color.color_accent_green));
                    BootstrapProgress.setVisibility(View.GONE);
                    ProgressText.setVisibility(View.GONE);
                    NewIdentityButton.setEnabled(false);
                    IpCard.setVisibility(View.INVISIBLE);
                    StopUptimeTimer();
                    break;

                case Starting:
                case Bootstrapping:
                    SetShieldState(false, true);
                    StatusText.setText("Connecting...");
                    StatusText.setTextColor(getColor(R.color.color_connecting));
                    SubStatusText.setText(Message != null ? Message : "Building Tor circuits");
                    ConnectButton.setText("Cancel");
                    ConnectButton.setBackgroundTintList(
                        getColorStateList(R.color.color_disconnected));
                    BootstrapProgress.setVisibility(View.VISIBLE);
                    ProgressText.setVisibility(View.VISIBLE);
                    if (Progress >= 0) {
                        AnimateProgress(Progress);
                        ProgressText.setText(Progress + "%");
                    }
                    NewIdentityButton.setEnabled(false);
                    break;

                case Connected:
                    SetShieldState(true, false);
                    StatusText.setText("Protected");
                    StatusText.setTextColor(getColor(R.color.color_connected));
                    SubStatusText.setText("Your traffic is routed through Tor");
                    ConnectButton.setText("Disconnect");
                    ConnectButton.setBackgroundTintList(
                        getColorStateList(R.color.color_disconnected));
                    BootstrapProgress.setVisibility(View.GONE);
                    ProgressText.setVisibility(View.GONE);
                    NewIdentityButton.setEnabled(true);
                    IpCard.setVisibility(View.VISIBLE);
                    if (ConnectedAt == 0) {
                        ConnectedAt = System.currentTimeMillis();
                        StartUptimeTimer();
                    }
                    break;

                case Error:
                    SetShieldState(false, false);
                    StatusText.setText("Error");
                    StatusText.setTextColor(getColor(R.color.color_disconnected));
                    SubStatusText.setText(Message != null ? Message : "An error occurred");
                    ConnectButton.setText("Retry");
                    BootstrapProgress.setVisibility(View.GONE);
                    NewIdentityButton.setEnabled(false);
                    StopUptimeTimer();
                    break;
            }

            if (ExitIp != null && !ExitIp.isEmpty()) {
                ExitIpText.setText(ExitIp);
            }
        });
    }

    // Sets the shield icon and status dot to reflect connected, loading, or disconnected
    private void SetShieldState(boolean IsConnected, boolean IsLoading) {
        if (IsConnected) {
            ShieldIcon.setImageResource(R.drawable.ic_shield_on);
            ShieldIcon.setColorFilter(getColor(R.color.color_connected));
            StatusDot.setBackgroundResource(R.drawable.bg_dot_connected);
            StartPulseAnimation();
        } else if (IsLoading) {
            ShieldIcon.setImageResource(R.drawable.ic_shield_loading);
            ShieldIcon.setColorFilter(getColor(R.color.color_connecting));
            StatusDot.setBackgroundResource(R.drawable.bg_dot_connecting);
        } else {
            ShieldIcon.setImageResource(R.drawable.ic_shield_off);
            ShieldIcon.setColorFilter(getColor(R.color.color_disconnected));
            StatusDot.setBackgroundResource(R.drawable.bg_dot_disconnected);
            StopPulseAnimation();
        }
    }

    // Smoothly animates the progress bar to the target value
    private void AnimateProgress(int Target) {
        ObjectAnimator Animator = ObjectAnimator.ofInt(
            BootstrapProgress, "progress",
            BootstrapProgress.getProgress(), Target);
        Animator.setDuration(500);
        Animator.setInterpolator(new DecelerateInterpolator());
        Animator.start();
    }

    // Sets up the alpha pulse animation for the shield icon
    private void SetupPulseAnimation() {
        PulseAnimator = ObjectAnimator.ofFloat(ShieldIcon, "alpha", 1f, 0.5f);
        PulseAnimator.setDuration(800);
        PulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
        PulseAnimator.setRepeatMode(ValueAnimator.REVERSE);
    }

    private void StartPulseAnimation() {
        if (PulseAnimator != null && !PulseAnimator.isRunning()) {
            ShieldIcon.setAlpha(1f);
        }
    }

    private void StopPulseAnimation() {
        if (PulseAnimator != null && PulseAnimator.isRunning()) {
            PulseAnimator.cancel();
        }
        ShieldIcon.setAlpha(1f);
    }

    // Starts a 1-second tick that updates the uptime display
    private void StartUptimeTimer() {
        UptimeRunner = new Runnable() {
            @Override
            public void run() {
                if (ConnectedAt > 0) {
                    long Diff = System.currentTimeMillis() - ConnectedAt;
                    long Hours   = (Diff / 3600000) % 24;
                    long Minutes = (Diff / 60000) % 60;
                    long Seconds = (Diff / 1000) % 60;
                    UptimeText.setText(String.format("%02d:%02d:%02d", Hours, Minutes, Seconds));
                    UiHandler.postDelayed(this, 1000);
                }
            }
        };
        UiHandler.post(UptimeRunner);
    }

    // Stops the uptime timer and resets the display
    private void StopUptimeTimer() {
        if (UptimeRunner != null) UiHandler.removeCallbacks(UptimeRunner);
        ConnectedAt = 0;
        if (UptimeText != null) UptimeText.setText("00:00:00");
    }
}
