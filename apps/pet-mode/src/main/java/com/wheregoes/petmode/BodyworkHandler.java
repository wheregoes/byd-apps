package com.wheregoes.petmode;

import android.hardware.bydauto.bodywork.AbsBYDAutoBodyworkListener;
import android.util.Log;

public class BodyworkHandler extends AbsBYDAutoBodyworkListener {
    private static final String TAG = "VehicleState";
    private final VehicleStateMonitor monitor;

    BodyworkHandler(VehicleStateMonitor monitor) {
        this.monitor = monitor;
    }

    @Override
    public void onDoorStateChanged(int area, int state) {
        if (area >= 1 && area <= 6) {
            monitor.doorOpen[area] = (state == 1);
            Log.i(TAG, "Door " + area + " " + (state == 1 ? "OPEN" : "CLOSED"));
            VehicleStateMonitor.Listener cb = monitor.getCallback();
            if (cb != null) cb.onDoorStateChanged(area, state == 1);
        }
    }

    @Override
    public void onAutoSystemStateChanged(int state) {
        boolean locked = (state == 1);
        monitor.locked = locked;
        Log.i(TAG, "Car " + (locked ? "LOCKED" : "UNLOCKED"));
        VehicleStateMonitor.Listener cb = monitor.getCallback();
        if (cb != null) cb.onLockStateChanged(locked);
    }

    @Override
    public void onPowerLevelChanged(int level) {
        monitor.powerLevel = level;
        Log.i(TAG, "Power level: " + level);
        VehicleStateMonitor.Listener cb = monitor.getCallback();
        if (cb != null) cb.onPowerLevelChanged(level);
    }

    @Override
    public void onBatteryVoltageLevelChanged(int level) {
        monitor.batteryLevel = level;
        Log.i(TAG, "Battery level: " + level);
        VehicleStateMonitor.Listener cb = monitor.getCallback();
        if (cb != null) cb.onBatteryChanged(level);
    }
}
