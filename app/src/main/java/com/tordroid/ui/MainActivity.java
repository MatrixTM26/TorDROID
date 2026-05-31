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

/**
 * MainActivity - Antarmuka utama TorDROID
 *
 * Menampilkan:
 * - Status koneksi Tor (shield icon)
 * - Bootstrap progress bar
 * - Exit IP address
 * - Tombol Connect/Disconnect
 * - Tombol New Identity
 */
public class MainActivity extends AppCompatActivity {

    private static final int VPN_REQUEST_CODE = 100;
    private static final String TAG = "MainActivity";

    // ── Views ─────────────────────────────────────────────────────────────────
    private ImageView mShieldIcon;
    private TextView mStatusText;
    private TextView mSubStatusText;
    private ProgressBar mBootstrapProgress;
    private TextView mProgressText;
    private TextView mExitIpText;
    private TextView mUptimeText;
    private Button mConnectButton;
    private Button mNewIdButton;
    private CardView mIpCard;
    private View mStatusDot;

    // ── State ─────────────────────────────────────────────────────────────────
    private TorStatus.State mCurrentState = TorStatus.State.STOPPED;
    private Handler mHandler = new Handler(Looper.getMainLooper());
    private Runnable mUptimeRunner;
    private long mConnectedAt = 0;
    private ObjectAnimator mPulseAnim;

    // ── Broadcast Receiver ────────────────────────────────────────────────────
    private final BroadcastReceiver mStatusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!TorConfig.ACTION_STATUS.equals(intent.getAction())) return;

            String stateStr = intent.getStringExtra(TorConfig.EXTRA_STATUS);
            String message = intent.getStringExtra(TorConfig.EXTRA_MESSAGE);
            int progress = intent.getIntExtra(TorConfig.EXTRA_PROGRESS, 0);
            String exitIp = intent.getStringExtra(TorConfig.EXTRA_IP);

            if (stateStr != null) {
                try {
                    mCurrentState = TorStatus.State.valueOf(stateStr);
                } catch (IllegalArgumentException ignored) {}
            }

            updateUI(message, progress, exitIp);
        }
    };

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bindViews();
        setupClickListeners();
        setupPulseAnimation();
        updateUI("Siap untuk terhubung", 0, null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter(TorConfig.ACTION_STATUS);
        registerReceiver(mStatusReceiver, filter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mStatusReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopUptimeTimer();
        if (mPulseAnim != null) mPulseAnim.cancel();
    }

    // ── View Binding ──────────────────────────────────────────────────────────

    private void bindViews() {
        mShieldIcon = findViewById(R.id.iv_shield);
        mStatusText = findViewById(R.id.tv_status);
        mSubStatusText = findViewById(R.id.tv_substatus);
        mBootstrapProgress = findViewById(R.id.pb_bootstrap);
        mProgressText = findViewById(R.id.tv_progress);
        mExitIpText = findViewById(R.id.tv_exit_ip);
        mUptimeText = findViewById(R.id.tv_uptime);
        mConnectButton = findViewById(R.id.btn_connect);
        mNewIdButton = findViewById(R.id.btn_new_identity);
        mIpCard = findViewById(R.id.card_ip);
        mStatusDot = findViewById(R.id.view_status_dot);
    }

    // ── Click Listeners ───────────────────────────────────────────────────────

    private void setupClickListeners() {
        mConnectButton.setOnClickListener(v -> {
            if (
                mCurrentState == TorStatus.State.CONNECTED ||
                mCurrentState == TorStatus.State.BOOTSTRAPPING ||
                mCurrentState == TorStatus.State.STARTING
            ) {
                disconnectTor();
            } else {
                connectTor();
            }
        });

        mNewIdButton.setOnClickListener(v -> requestNewIdentity());

        // IP Card: tap untuk copy
        mIpCard.setOnClickListener(v -> {
            String ip = mExitIpText.getText().toString();
            if (!ip.isEmpty() && !ip.equals("-")) {
                android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(
                    Context.CLIPBOARD_SERVICE
                );
                cm.setPrimaryClip(android.content.ClipData.newPlainText("Exit IP", ip));
                Toast.makeText(this, "IP disalin: " + ip, Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ── Tor Connect/Disconnect ─────────────────────────────────────────────────

    private void connectTor() {
        // Minta izin VPN
        Intent vpnIntent = VpnService.prepare(this);
        if (vpnIntent != null) {
            startActivityForResult(vpnIntent, VPN_REQUEST_CODE);
        } else {
            onVpnPermissionGranted();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VPN_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                onVpnPermissionGranted();
            } else {
                Toast.makeText(this, "Izin VPN ditolak", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void onVpnPermissionGranted() {
        // Mulai Tor service
        Intent torService = new Intent(this, TorProxyService.class);
        torService.setAction(TorConfig.ACTION_START);
        startForegroundService(torService);

        // Mulai VPN service
        Intent vpnService = new Intent(this, TorVpnService.class);
        vpnService.setAction(TorConfig.ACTION_START);
        startService(vpnService);

        mCurrentState = TorStatus.State.STARTING;
        updateUI("Memulai koneksi Tor...", 0, null);
    }

    private void disconnectTor() {
        // Stop Tor service
        Intent torService = new Intent(this, TorProxyService.class);
        torService.setAction(TorConfig.ACTION_STOP);
        startService(torService);

        // Stop VPN service
        Intent vpnService = new Intent(this, TorVpnService.class);
        vpnService.setAction(TorConfig.ACTION_STOP);
        startService(vpnService);

        mCurrentState = TorStatus.State.STOPPED;
        stopUptimeTimer();
        updateUI("Terputus dari Tor", 0, null);
    }

    private void requestNewIdentity() {
        Intent service = new Intent(this, TorProxyService.class);
        service.setAction(TorConfig.ACTION_NEWID);
        startService(service);
        Toast.makeText(this, "Meminta identitas baru...", Toast.LENGTH_SHORT).show();
    }

    // ── UI Update ─────────────────────────────────────────────────────────────

    private void updateUI(String message, int progress, String exitIp) {
        runOnUiThread(() -> {
            switch (mCurrentState) {
                case STOPPED:
                    setShieldState(false, false);
                    mStatusText.setText("Tidak Terlindungi");
                    mStatusText.setTextColor(getColor(R.color.color_disconnected));
                    mSubStatusText.setText("Koneksi Anda tidak terenkripsi");
                    mConnectButton.setText("Hubungkan Tor");
                    mConnectButton.setBackgroundTintList(getColorStateList(R.color.color_accent_green));
                    mBootstrapProgress.setVisibility(View.GONE);
                    mProgressText.setVisibility(View.GONE);
                    mNewIdButton.setEnabled(false);
                    mIpCard.setVisibility(View.INVISIBLE);
                    stopUptimeTimer();
                    break;
                case STARTING:
                case BOOTSTRAPPING:
                    setShieldState(false, true);
                    mStatusText.setText("Menghubungkan...");
                    mStatusText.setTextColor(getColor(R.color.color_connecting));
                    mSubStatusText.setText(message != null ? message : "Membangun sirkuit Tor");
                    mConnectButton.setText("Batalkan");
                    mConnectButton.setBackgroundTintList(getColorStateList(R.color.color_disconnected));
                    mBootstrapProgress.setVisibility(View.VISIBLE);
                    mProgressText.setVisibility(View.VISIBLE);
                    if (progress >= 0) {
                        animateProgress(progress);
                        mProgressText.setText(progress + "%");
                    }
                    mNewIdButton.setEnabled(false);
                    break;
                case CONNECTED:
                    setShieldState(true, false);
                    mStatusText.setText("Terlindungi");
                    mStatusText.setTextColor(getColor(R.color.color_connected));
                    mSubStatusText.setText("Traffic Anda dirutekan melalui Tor");
                    mConnectButton.setText("Putuskan");
                    mConnectButton.setBackgroundTintList(getColorStateList(R.color.color_disconnected));
                    mBootstrapProgress.setVisibility(View.GONE);
                    mProgressText.setVisibility(View.GONE);
                    mNewIdButton.setEnabled(true);
                    mIpCard.setVisibility(View.VISIBLE);
                    if (mConnectedAt == 0) {
                        mConnectedAt = System.currentTimeMillis();
                        startUptimeTimer();
                    }
                    break;
                case ERROR:
                    setShieldState(false, false);
                    mStatusText.setText("Error");
                    mStatusText.setTextColor(getColor(R.color.color_disconnected));
                    mSubStatusText.setText(message != null ? message : "Terjadi kesalahan");
                    mConnectButton.setText("Coba Lagi");
                    mBootstrapProgress.setVisibility(View.GONE);
                    mNewIdButton.setEnabled(false);
                    stopUptimeTimer();
                    break;
            }

            if (exitIp != null && !exitIp.isEmpty()) {
                mExitIpText.setText(exitIp);
            }
        });
    }

    private void setShieldState(boolean connected, boolean loading) {
        if (connected) {
            mShieldIcon.setImageResource(R.drawable.ic_shield_on);
            mShieldIcon.setColorFilter(getColor(R.color.color_connected));
            mStatusDot.setBackgroundResource(R.drawable.bg_dot_connected);
            startPulseAnimation();
        } else if (loading) {
            mShieldIcon.setImageResource(R.drawable.ic_shield_loading);
            mShieldIcon.setColorFilter(getColor(R.color.color_connecting));
            mStatusDot.setBackgroundResource(R.drawable.bg_dot_connecting);
        } else {
            mShieldIcon.setImageResource(R.drawable.ic_shield_off);
            mShieldIcon.setColorFilter(getColor(R.color.color_disconnected));
            mStatusDot.setBackgroundResource(R.drawable.bg_dot_disconnected);
            stopPulseAnimation();
        }
    }

    private void animateProgress(int target) {
        ObjectAnimator anim = ObjectAnimator.ofInt(
            mBootstrapProgress,
            "progress",
            mBootstrapProgress.getProgress(),
            target
        );
        anim.setDuration(500);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.start();
    }

    // ── Animations ────────────────────────────────────────────────────────────

    private void setupPulseAnimation() {
        mPulseAnim = ObjectAnimator.ofFloat(mShieldIcon, "alpha", 1f, 0.5f);
        mPulseAnim.setDuration(800);
        mPulseAnim.setRepeatCount(ValueAnimator.INFINITE);
        mPulseAnim.setRepeatMode(ValueAnimator.REVERSE);
    }

    private void startPulseAnimation() {
        if (mPulseAnim != null && !mPulseAnim.isRunning()) {
            mPulseAnim.cancel();
            mShieldIcon.setAlpha(1f);
        }
    }

    private void stopPulseAnimation() {
        if (mPulseAnim != null && mPulseAnim.isRunning()) {
            mPulseAnim.cancel();
        }
        mShieldIcon.setAlpha(1f);
    }

    // ── Uptime Timer ──────────────────────────────────────────────────────────

    private void startUptimeTimer() {
        mUptimeRunner = new Runnable() {
            @Override
            public void run() {
                if (mConnectedAt > 0) {
                    long diff = System.currentTimeMillis() - mConnectedAt;
                    long h = (diff / 3600000) % 24;
                    long m = (diff / 60000) % 60;
                    long s = (diff / 1000) % 60;
                    mUptimeText.setText(String.format("%02d:%02d:%02d", h, m, s));
                    mHandler.postDelayed(this, 1000);
                }
            }
        };
        mHandler.post(mUptimeRunner);
    }

    private void stopUptimeTimer() {
        if (mUptimeRunner != null) {
            mHandler.removeCallbacks(mUptimeRunner);
        }
        mConnectedAt = 0;
        if (mUptimeText != null) mUptimeText.setText("00:00:00");
    }
}
