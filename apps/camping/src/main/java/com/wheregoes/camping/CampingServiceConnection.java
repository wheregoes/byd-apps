package com.wheregoes.camping;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.IBinder;

class CampingServiceConnection implements ServiceConnection {
    private final MainActivity activity;

    CampingServiceConnection(MainActivity activity) {
        this.activity = activity;
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        CampingBinder campingBinder = (CampingBinder) binder;
        activity.onServiceBound(campingBinder.getService());
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        activity.onServiceUnbound();
    }
}
