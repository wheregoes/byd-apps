package com.wheregoes.doorsound;

import android.hardware.bydauto.bodywork.AbsBYDAutoBodyworkListener;
import android.hardware.bydauto.bodywork.BYDAutoBodyworkDevice;

public class BodyworkHandler extends AbsBYDAutoBodyworkListener {
    private final DoorSoundService service;

    /** null until the first state is seen, so we never fire on the initial read. */
    private Boolean locked;
    private boolean alarmActive;
    /** Bit per window area that is currently not closed. */
    private int openWindows;

    BodyworkHandler(DoorSoundService service) {
        this.service = service;
    }

    /** Seeds the lock state at registration so the first real change is an edge. */
    void primeLockState(int state) {
        if (state != BYDAutoBodyworkDevice.BODYWORK_AUTO_SYSTEM_STATE_UNDEFINED) {
            locked = isLocked(state);
            service.log("Primed lock state: " + locked + " (raw " + state + ")");
        }
    }

    /**
     * NORMAL=0, SET_SECURE=1, START_SECURE=2, UNDEFINED=255. The old code tested
     * `state == 1` only, so any lock that settled on START_SECURE never fired.
     * Cabin already had this right (VehicleStateMonitor: `sysState >= 1`).
     */
    private static boolean isLocked(int state) {
        return state >= BYDAutoBodyworkDevice.BODYWORK_AUTO_SYSTEM_STATE_SET_SECURE;
    }

    /**
     * Synchronized: pushed callbacks arrive on a binder thread and the service's
     * poll calls this from its io thread, while the `locked` edge check is a
     * read-modify-write. Without it both paths can pass the check and fire twice.
     */
    @Override
    public synchronized void onAutoSystemStateChanged(int state) {
        if (state == BYDAutoBodyworkDevice.BODYWORK_AUTO_SYSTEM_STATE_UNDEFINED) {
            return;
        }
        boolean now = isLocked(state);
        // Edge only: NORMAL -> SET_SECURE -> START_SECURE is one lock, not two.
        if (locked != null && locked == now) {
            return;
        }
        locked = now;
        if (now) {
            service.fire(SoundEvent.LOCK, service.getString(R.string.log_locked));
            warnIfWindowsOpen();
        } else {
            service.fire(SoundEvent.UNLOCK, service.getString(R.string.log_unlocked));
        }
    }

    @Override
    public void onDoorStateChanged(int area, int state) {
        boolean open = state == BYDAutoBodyworkDevice.BODYWORK_STATE_OPEN;
        boolean closed = state == BYDAutoBodyworkDevice.BODYWORK_STATE_CLOSED;
        if (!open && !closed) {
            return;
        }
        String name = service.doorName(area);
        switch (area) {
            case BYDAutoBodyworkDevice.BODYWORK_CMD_DOOR_LEFT_FRONT:
            case BYDAutoBodyworkDevice.BODYWORK_CMD_DOOR_RIGHT_FRONT:
            case BYDAutoBodyworkDevice.BODYWORK_CMD_DOOR_LEFT_REAR:
            case BYDAutoBodyworkDevice.BODYWORK_CMD_DOOR_RIGHT_REAR:
                service.fire(open ? SoundEvent.DOOR_OPEN : SoundEvent.DOOR_CLOSE,
                        service.getString(open ? R.string.log_opened : R.string.log_closed, name));
                break;
            case BYDAutoBodyworkDevice.BODYWORK_CMD_DOOR_HOOD:
                if (open) {
                    service.fire(SoundEvent.HOOD, service.getString(R.string.log_opened, name));
                }
                break;
            case BYDAutoBodyworkDevice.BODYWORK_CMD_DOOR_LUGGAGE_DOOR:
                if (open) {
                    service.fire(SoundEvent.TRUNK, service.getString(R.string.log_opened, name));
                }
                break;
            default:
                // Area 7 is the fuel/charge cap; not an event worth a sound.
                break;
        }
    }

    @Override
    public void onAlarmStateChanged(int state) {
        boolean active = state != 0;
        if (active == alarmActive) {
            return;
        }
        alarmActive = active;
        if (active) {
            service.fire(SoundEvent.ALARM, service.getString(R.string.log_alarm));
        }
    }

    @Override
    public void onWindowStateChanged(int area, int state) {
        int bit = 1 << Math.min(Math.max(area, 0), 30);
        boolean open = state == BYDAutoBodyworkDevice.BODYWORK_STATE_OPEN;
        if (open) {
            openWindows |= bit;
        } else if (state == BYDAutoBodyworkDevice.BODYWORK_STATE_CLOSED) {
            openWindows &= ~bit;
        }
        // A window chime is only useful once the car has been left: opening a
        // window while driving is deliberate, leaving one open is not.
        if (open && locked != null && locked) {
            service.fire(SoundEvent.WINDOW, service.getString(R.string.log_window_open));
        }
    }

    private void warnIfWindowsOpen() {
        if (openWindows != 0) {
            service.fire(SoundEvent.WINDOW, service.getString(R.string.log_window_open));
        }
    }

    @Override
    public void onPowerLevelChanged(int level) {
        service.onPowerLevel(level);
    }
}
