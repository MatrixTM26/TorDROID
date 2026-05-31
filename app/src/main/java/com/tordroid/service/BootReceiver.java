package com.tordroid.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import com.tordroid.util.TorConfig;

// Receives the BOOT_COMPLETED broadcast and auto-starts TorDROID if enabled
public class BootReceiver extends BroadcastReceiver {

    private static final String Tag = "BootReceiver";
    private static final String PrefFile = "tordroid_prefs";
    private static final String KeyAutoStart = "autostart";

    @Override
    public void onReceive(Context AppContext, Intent ReceivedIntent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(ReceivedIntent.getAction())) return;

        SharedPreferences Prefs = AppContext.getSharedPreferences(PrefFile, Context.MODE_PRIVATE);
        boolean AutoStart = Prefs.getBoolean(KeyAutoStart, false);

        if (AutoStart) {
            Log.d(Tag, "Boot completed, starting TorDROID...");
            Intent ServiceIntent = new Intent(AppContext, TorProxyService.class);
            ServiceIntent.setAction(TorConfig.ActionStart);
            AppContext.startForegroundService(ServiceIntent);
        }
    }
}
