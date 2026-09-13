package com.wheregoes.petmode;

import android.content.Context;
import android.hardware.bydauto.bodywork.BYDAutoBodyworkDevice;
import android.hardware.bydauto.statistic.BYDAutoStatisticDevice;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

public class VehicleStateMonitor {
    private static final String TAG = "VehicleState";

    private static final long POLL_INTERVAL_MS = 30_000L;

    interface Listener {
        void onDoorStateChanged(int area, boolean open);
        void onLockStateChanged(boolean locked);
        void onPowerLevelChanged(int level);
        void onBatteryChanged(int level);
        void onVoltageLevelChanged(int level);
    }

    private BYDAutoBodyworkDevice device;
    private BYDAutoStatisticDevice statisticDevice;
    private BodyworkHandler handler;
    private Listener callback;
    private HandlerThread ioThread;
    private Handler io;

    final boolean[] doorOpen = new boolean[8];
    volatile boolean locked = false;
    volatile int powerLevel = -1;
    /** Traction battery state of charge, 0-100, from the statistic device. */
    volatile int batteryLevel = -1;
    /**
     * 12V system level from onBatteryVoltageLevelChanged. An undocumented scale
     * and a different quantity from the SOC percentage -- they used to share one
     * field, so a voltage event silently overwrote the battery percentage.
     */
    volatile int voltageLevel = -1;

    private final Runnable pollTick = this::pollAndReschedule;

    void start(Context context, Listener cb) {
        callback = cb;
        // Three Binder/CAN reads per tick used to run on the main looper.
        ioThread = new HandlerThread("vehicle-io");
        ioThread.start();
        io = new Handler(ioThread.getLooper());
        io.post(() -> init(context));
    }

    private void init(Context context) {
        try {
            device = BYDAutoBodyworkDevice.getInstance(new BydPermissionContext(context));
            powerLevel = device.getPowerLevel();
            int sysState = device.getAutoSystemState();
            locked = (sysState >= BYDAutoBodyworkDevice.BODYWORK_AUTO_SYSTEM_STATE_SET_SECURE);
            Log.i(TAG, "Initial: power=" + powerLevel + " sysState=" + sysState
                    + " locked=" + locked);

            handler = new BodyworkHandler(this);
            device.registerListener(handler);

            try {
                statisticDevice = BYDAutoStatisticDevice.getInstance(
                        new BydPermissionContext(context));
                double soc = statisticDevice.getElecPercentageValue();
                batteryLevel = (int) Math.round(soc);
                Log.i(TAG, "Initial SOC: " + soc + " -> " + batteryLevel + "%");
            } catch (Throwable t) {
                Log.e(TAG, "StatisticDevice init failed: " + t.getMessage());
            }

            if (callback != null) {
                final boolean l = locked;
                final int p = powerLevel;
                final int b = batteryLevel;
                callback.onLockStateChanged(l);
                callback.onPowerLevelChanged(p);
                callback.onBatteryChanged(b);
            }
            io.postDelayed(pollTick, POLL_INTERVAL_MS);
        } catch (Throwable e) {
            Log.e(TAG, "Failed to register: " + e.getMessage());
        }
    }

    void stop() {
        if (io != null) {
            io.removeCallbacksAndMessages(null);
        }
        if (device != null && handler != null) {
            try {
                device.unregisterListener(handler);
            } catch (Exception ignored) {}
        }
        if (ioThread != null) {
            ioThread.quitSafely();
        }
    }

    private void pollAndReschedule() {
        pollState();
        io.postDelayed(pollTick, POLL_INTERVAL_MS);
    }

    private void pollState() {
        if (device == null) {
            return;
        }
        try {
            int sysState = device.getAutoSystemState();
            boolean newLocked =
                    (sysState >= BYDAutoBodyworkDevice.BODYWORK_AUTO_SYSTEM_STATE_SET_SECURE);
            if (newLocked != locked) {
                locked = newLocked;
                Log.i(TAG, "Poll: lock=" + (locked ? "LOCKED" : "UNLOCKED")
                        + " sysState=" + sysState);
                if (callback != null) {
                    callback.onLockStateChanged(locked);
                }
            }
            int newPower = device.getPowerLevel();
            if (newPower != powerLevel) {
                powerLevel = newPower;
                Log.i(TAG, "Poll: power=" + powerLevel);
                if (callback != null) {
                    callback.onPowerLevelChanged(powerLevel);
                }
            }
            if (statisticDevice != null) {
                double soc = statisticDevice.getElecPercentageValue();
                int newBattery = (int) Math.round(soc);
                if (newBattery != batteryLevel) {
                    batteryLevel = newBattery;
                    Log.i(TAG, "Poll: SOC=" + soc + " -> " + batteryLevel + "%");
                    if (callback != null) {
                        callback.onBatteryChanged(batteryLevel);
                    }
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "Poll failed: " + e.getMessage());
        }
    }

    boolean isAnyDoorOpen() {
        for (int i = 1; i <= 6; i++) {
            if (doorOpen[i]) {
                return true;
            }
        }
        return false;
    }

    boolean allDoorsClosed() {
        return !isAnyDoorOpen();
    }

    boolean isLocked() {
        return locked;
    }

    int getPowerLevel() {
        return powerLevel;
    }

    boolean isCarOn() {
        return powerLevel >= BYDAutoBodyworkDevice.BODYWORK_POWER_LEVEL_ON;
    }

    int getBatteryLevel() {
        return batteryLevel;
    }

    Listener getCallback() {
        return callback;
    }
}
