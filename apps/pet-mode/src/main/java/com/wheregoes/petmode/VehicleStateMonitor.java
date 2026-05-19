package com.wheregoes.petmode;

import android.content.Context;
import android.hardware.bydauto.bodywork.AbsBYDAutoBodyworkListener;
import android.hardware.bydauto.bodywork.BYDAutoBodyworkDevice;
import android.util.Log;

public class VehicleStateMonitor {
    private static final String TAG = "VehicleState";

    interface Listener {
        void onDoorStateChanged(int area, boolean open);
        void onLockStateChanged(boolean locked);
        void onPowerLevelChanged(int level);
        void onBatteryChanged(int level);
    }

    private BYDAutoBodyworkDevice device;
    private BodyworkHandler handler;
    private Listener callback;

    final boolean[] doorOpen = new boolean[7];
    boolean locked = false;
    int powerLevel = -1;
    int batteryLevel = -1;

    void start(Context context, Listener cb) {
        callback = cb;
        try {
            device = BYDAutoBodyworkDevice.getInstance(new BydPermissionContext(context));
            powerLevel = device.getPowerLevel();
            handler = new BodyworkHandler(this);
            device.registerListener(handler);
            Log.i(TAG, "Registered, power=" + powerLevel);
        } catch (Throwable e) {
            Log.e(TAG, "Failed to register: " + e.getMessage());
        }
    }

    void stop() {
        if (device != null && handler != null) {
            try { device.unregisterListener(handler); } catch (Exception ignored) {}
        }
    }

    boolean isAnyDoorOpen() {
        for (int i = 1; i <= 6; i++) if (doorOpen[i]) return true;
        return false;
    }

    boolean allDoorsClosed() { return !isAnyDoorOpen(); }

    boolean isLocked() { return locked; }

    int getPowerLevel() { return powerLevel; }

    boolean isCarOn() { return powerLevel >= 2; }

    int getBatteryLevel() { return batteryLevel; }

    Listener getCallback() { return callback; }
}
