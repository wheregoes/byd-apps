package com.wheregoes.petmode;

import android.hardware.bydauto.bodywork.AbsBYDAutoBodyworkListener;
import android.hardware.bydauto.bodywork.BYDAutoBodyworkDevice;
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
            boolean open = state == BYDAutoBodyworkDevice.BODYWORK_STATE_OPEN;
            monitor.doorOpen[area] = open;
            Log.i(TAG, "Door " + area + " " + (open ? "OPEN" : "CLOSED"));
            VehicleStateMonitor.Listener cb = monitor.getCallback();
            if (cb != null) {
                cb.onDoorStateChanged(area, open);
            }
        }
    }

    @Override
    public void onAutoSystemStateChanged(int state) {
        if (state == BYDAutoBodyworkDevice.BODYWORK_AUTO_SYSTEM_STATE_UNDEFINED) {
            return;
        }
        boolean locked = (state >= BYDAutoBodyworkDevice.BODYWORK_AUTO_SYSTEM_STATE_SET_SECURE);
        monitor.locked = locked;
        Log.i(TAG, "Car " + (locked ? "LOCKED" : "UNLOCKED"));
        VehicleStateMonitor.Listener cb = monitor.getCallback();
        if (cb != null) {
            cb.onLockStateChanged(locked);
        }
    }

    @Override
    public void onPowerLevelChanged(int level) {
        monitor.powerLevel = level;
        Log.i(TAG, "Power level: " + level);
        VehicleStateMonitor.Listener cb = monitor.getCallback();
        if (cb != null) {
            cb.onPowerLevelChanged(level);
        }
    }

    // onAcStarted/onAcStoped deliberately not overridden: ClimateMonitor is the
    // single authority on AC state and already receives those events through
    // AcListenerHandler. Handling them here too made two writers race over the
    // same flag.

    @Override
    public void onBatteryVoltageLevelChanged(int level) {
        monitor.voltageLevel = level;
        // Raw so the scale can be identified on a real car; it is NOT a percentage.
        Log.i(TAG, "12V battery voltage level (raw): " + level);
        VehicleStateMonitor.Listener cb = monitor.getCallback();
        if (cb != null) {
            cb.onVoltageLevelChanged(level);
        }
    }
}
