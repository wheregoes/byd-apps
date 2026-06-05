package com.wheregoes.camping;

import android.content.Context;
import android.hardware.bydauto.bodywork.BYDAutoBodyworkDevice;
import android.hardware.bydauto.statistic.BYDAutoStatisticDevice;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

public class VehicleStateMonitor {
    private static final String TAG = "VehicleState";

    interface Listener {
        void onDoorStateChanged(int area, boolean open);
        void onLockStateChanged(boolean locked);
        void onAcStateChanged(boolean running);
        void onPowerLevelChanged(int level);
        void onBatteryChanged(int level);
    }

    private BYDAutoBodyworkDevice device;
    private BYDAutoStatisticDevice statisticDevice;
    private BodyworkHandler handler;
    private Listener callback;
    private Handler pollHandler;

    final boolean[] doorOpen = new boolean[7];
    boolean locked = false;
    boolean acRunning = false;
    int powerLevel = -1;
    int batteryLevel = -1;

    BYDAutoBodyworkDevice getDevice() { return device; }

    void start(Context context, Listener cb) {
        callback = cb;
        pollHandler = new Handler(Looper.getMainLooper());
        try {
            device = BYDAutoBodyworkDevice.getInstance(new BydPermissionContext(context));
            powerLevel = device.getPowerLevel();
            int sysState = device.getAutoSystemState();
            locked = (sysState >= 1);
            Log.i(TAG, "Initial: power=" + powerLevel + " sysState=" + sysState + " locked=" + locked);

            handler = new BodyworkHandler(this);
            device.registerListener(handler);

            try {
                statisticDevice = BYDAutoStatisticDevice.getInstance(new BydPermissionContext(context));
                double soc = statisticDevice.getElecPercentageValue();
                batteryLevel = (int) Math.round(soc);
                Log.i(TAG, "Initial SOC: " + soc + " -> " + batteryLevel + "%");
            } catch (Throwable t) {
                Log.e(TAG, "StatisticDevice init failed: " + t.getMessage());
            }

            if (cb != null) {
                cb.onLockStateChanged(locked);
                cb.onPowerLevelChanged(powerLevel);
                cb.onBatteryChanged(batteryLevel);
            }

            schedulePoll();
        } catch (Throwable e) {
            Log.e(TAG, "Failed to register: " + e.getMessage());
        }
    }

    void stop() {
        if (device != null && handler != null) {
            try { device.unregisterListener(handler); } catch (Exception ignored) {}
        }
        if (pollHandler != null) pollHandler.removeCallbacksAndMessages(null);
    }

    BYDAutoStatisticDevice getStatisticDevice() { return statisticDevice; }

    private void schedulePoll() {
        pollHandler.postDelayed(() -> {
            pollState();
            schedulePoll();
        }, 30000);
    }

    private void pollState() {
        if (device == null) return;
        try {
            int sysState = device.getAutoSystemState();
            boolean newLocked = (sysState >= 1);
            if (newLocked != locked) {
                locked = newLocked;
                Log.i(TAG, "Poll: lock=" + (locked ? "LOCKED" : "UNLOCKED") + " sysState=" + sysState);
                if (callback != null) callback.onLockStateChanged(locked);
            }
            int newPower = device.getPowerLevel();
            if (newPower != powerLevel) {
                powerLevel = newPower;
                Log.i(TAG, "Poll: power=" + powerLevel);
                if (callback != null) callback.onPowerLevelChanged(powerLevel);
            }
            if (statisticDevice != null) {
                double soc = statisticDevice.getElecPercentageValue();
                int newBattery = (int) Math.round(soc);
                if (newBattery != batteryLevel) {
                    batteryLevel = newBattery;
                    Log.i(TAG, "Poll: SOC=" + soc + " -> " + batteryLevel + "%");
                    if (callback != null) callback.onBatteryChanged(batteryLevel);
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "Poll failed: " + e.getMessage());
        }
    }

    boolean isAnyDoorOpen() {
        for (int i = 1; i <= 6; i++) if (doorOpen[i]) return true;
        return false;
    }

    boolean isLocked() { return locked; }

    int getPowerLevel() { return powerLevel; }

    boolean isCarOn() { return powerLevel >= 2; }

    int getBatteryLevel() { return batteryLevel; }

    Listener getCallback() { return callback; }
}
