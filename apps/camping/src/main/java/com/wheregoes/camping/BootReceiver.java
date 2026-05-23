package com.wheregoes.camping;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences prefs = context.getSharedPreferences(CampingService.PREF_NAME, Context.MODE_PRIVATE);
        if (prefs.getBoolean(CampingService.KEY_ENABLED, false)) {
            Log.i("BootReceiver", "Restarting Camping service after boot");
            context.startForegroundService(new Intent(context, CampingService.class));
        }
    }
}
