package com.tordroid.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import com.tordroid.util.TorConfig;

/**
 * BootReceiver - Auto-start TorDROID saat device boot
 * (jika opsi autostart diaktifkan di pengaturan)
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";
    private static final String PREF = "tordroid_prefs";
    private static final String KEY_AUTOSTART = "autostart";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;

        SharedPreferences prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        boolean autostart = prefs.getBoolean(KEY_AUTOSTART, false);

        if (autostart) {
            Log.d(TAG, "Boot completed, memulai TorDROID...");
            Intent service = new Intent(context, TorProxyService.class);
            service.setAction(TorConfig.ACTION_START);
            context.startForegroundService(service);
        }
    }
}
