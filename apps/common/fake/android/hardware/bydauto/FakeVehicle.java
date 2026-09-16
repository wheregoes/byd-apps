package android.hardware.bydauto;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.bydauto.ac.AbsBYDAutoAcListener;
import android.hardware.bydauto.bodywork.AbsBYDAutoBodyworkListener;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.SparseIntArray;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * EMULATOR ONLY. The one piece of state behind every fake BYD device.
 *
 * Driven from the command line: tools/emu.sh vehicle lock=1 ac=0
 *
 * It answers instantly and never lies, which is exactly why it proves nothing
 * about CAN timing, readback or MCU behaviour -- verify those on a car.
 */
public final class FakeVehicle {
    public static final String ACTION = "com.wheregoes.byd.FAKE";

    private static final String TAG = "FakeVehicle";
    /** What the real HAL returns for a feature id it does not support. */
    private static final int UNSUPPORTED = -10011;

    private static FakeVehicle instance;

    public final CopyOnWriteArrayList<AbsBYDAutoAcListener> acListeners =
            new CopyOnWriteArrayList<>();
    public final CopyOnWriteArrayList<AbsBYDAutoBodyworkListener> bodyListeners =
            new CopyOnWriteArrayList<>();

    // A healthy parked car, so Cabin renders a normal screen immediately.
    public volatile int powerLevel = 2;
    public volatile int systemState = 0;
    public volatile int setTemp = 24;
    public volatile int outsideTemp = 27;
    public volatile boolean acOn = true;
    public volatile int soc = 70;
    /** Indexed by BYD door area: 1-4 doors, 5 hood, 6 trunk, 7 fuel/charge cap. */
    public final int[] doors = new int[8];

    private final SparseIntArray features = new SparseIntArray();

    private FakeVehicle(Context context) {
        features.put(0x48F00000, 2);   // engine sound simulator present
        features.put(0x48F00013, 1);   // a voice source exists
        features.put(0x48F0000A, 0);   // simulator off
        features.put(0x48F00010, 1);   // source type 1
        // Callbacks arrive off the main thread, as the real HAL's binder
        // threads do; onReceive runs here too so apply() dispatches directly.
        HandlerThread thread = new HandlerThread("byd-fake");
        thread.start();
        context.getApplicationContext().registerReceiver(
                new Receiver(this), new IntentFilter(ACTION), null,
                new Handler(thread.getLooper()));
        Log.i(TAG, "up: " + describe());
    }

    public static synchronized FakeVehicle get(Context context) {
        // A fake Cabin on a real car would report a fake AC state for a real
        // pet. Make that impossible rather than unlikely.
        if (!"ranchu".equals(Build.HARDWARE) && !"goldfish".equals(Build.HARDWARE)) {
            throw new IllegalStateException("fake BYD SDK on real hardware: " + Build.HARDWARE);
        }
        if (instance == null) {
            instance = new FakeVehicle(context);
        }
        return instance;
    }

    // ---------------------------------------------------------------- features

    public synchronized int getFeature(int fid) {
        int idx = features.indexOfKey(fid);
        return idx >= 0 ? features.valueAt(idx) : UNSUPPORTED;
    }

    /**
     * Writes land on the paired GET id, so an app's readback verification
     * behaves the way the MCU does.
     */
    public synchronized void setFeature(int fid, int val) {
        if (fid == 0x3E300038 && val == 0) {
            // Measured: the Dolphin MCU rejects source type 0 and stores every
            // other value (verified to 200).
            Log.w(TAG, "source type 0 rejected, as the car does");
            return;
        }
        features.put(pairedGetId(fid), val);
    }

    private static int pairedGetId(int setId) {
        if (setId == 0x3E300020) return 0x48F0000A;   // simulator on/off
        if (setId == 0x3E300038) return 0x48F00010;   // source type
        return setId;
    }

    // ------------------------------------------------------------------ events

    /** byd-fake thread. */
    void apply(Bundle extras) {
        if (extras == null) {
            Log.w(TAG, "broadcast with no extras");
            return;
        }
        for (String key : extras.keySet()) {
            String raw = extras.getString(key);
            if (raw == null) {
                Log.w(TAG, "not a string extra: " + key);
                continue;
            }
            try {
                applyKey(key, raw);
            } catch (NumberFormatException e) {
                Log.w(TAG, "bad value for " + key + ": " + raw);
            }
        }
        Log.i(TAG, describe());
    }

    private void applyKey(String key, String raw) {
        switch (key) {
            case "temp":
                setTemp = Integer.parseInt(raw.trim());
                break;
            case "outside":
                outsideTemp = Integer.parseInt(raw.trim());
                break;
            case "ac":
                setAc(Integer.parseInt(raw.trim()) != 0);
                break;
            case "door": {
                int area = first(raw);
                int state = second(raw);
                if (area < 1 || area > 7) {
                    Log.w(TAG, "door area out of range: " + area);
                    return;
                }
                doors[area] = state;
                for (AbsBYDAutoBodyworkListener l : bodyListeners) {
                    l.onDoorStateChanged(area, state);
                }
                break;
            }
            case "lock":
                systemState = Integer.parseInt(raw.trim());
                for (AbsBYDAutoBodyworkListener l : bodyListeners) {
                    l.onAutoSystemStateChanged(systemState);
                }
                break;
            case "power":
                powerLevel = Integer.parseInt(raw.trim());
                for (AbsBYDAutoBodyworkListener l : bodyListeners) {
                    l.onPowerLevelChanged(powerLevel);
                }
                break;
            // No dispatch: reproduces a car that reports the state but never
            // announces it, which is what the polling fallbacks exist for.
            case "lock_quiet":
                systemState = Integer.parseInt(raw.trim());
                break;
            case "power_quiet":
                powerLevel = Integer.parseInt(raw.trim());
                break;
            case "soc":
                soc = Integer.parseInt(raw.trim());
                break;
            case "voltage": {
                // Undocumented scale on the car; passed through unchanged.
                int level = Integer.parseInt(raw.trim());
                for (AbsBYDAutoBodyworkListener l : bodyListeners) {
                    l.onBatteryVoltageLevelChanged(level);
                }
                break;
            }
            case "alarm": {
                int state = Integer.parseInt(raw.trim());
                for (AbsBYDAutoBodyworkListener l : bodyListeners) {
                    l.onAlarmStateChanged(state);
                }
                break;
            }
            case "window": {
                int area = first(raw);
                int state = second(raw);
                for (AbsBYDAutoBodyworkListener l : bodyListeners) {
                    l.onWindowStateChanged(area, state);
                }
                break;
            }
            case "sim":
                setFeature(0x48F0000A, Integer.parseInt(raw.trim()) != 0 ? 1 : 0);
                break;
            case "preset":
                setFeature(0x48F00010, Integer.parseInt(raw.trim()));
                break;
            case "fid":
                setFeature(first(raw), second(raw));
                break;
            default:
                Log.w(TAG, "unknown key: " + key);
        }
    }

    private void setAc(boolean on) {
        acOn = on;
        // Both device listeners report AC on the car, so both do here.
        for (AbsBYDAutoAcListener l : acListeners) {
            if (on) l.onAcStarted(); else l.onAcStoped();
        }
        for (AbsBYDAutoBodyworkListener l : bodyListeners) {
            if (on) l.onAcStarted(); else l.onAcStoped();
        }
    }

    // ----------------------------------------------------------------- parsing

    private static int colon(String raw) {
        int i = raw.indexOf(':');
        if (i < 0) {
            throw new NumberFormatException("expected A:B, got " + raw);
        }
        return i;
    }

    private static int first(String raw) {
        return hex(raw.substring(0, colon(raw)));
    }

    private static int second(String raw) {
        return hex(raw.substring(colon(raw) + 1));
    }

    /** Accepts decimal and 0x-prefixed hex; Long, so 0xAA000148 fits. */
    private static int hex(String raw) {
        return (int) Long.decode(raw.trim()).longValue();
    }

    private String describe() {
        StringBuilder doorList = new StringBuilder();
        for (int i = 1; i <= 7; i++) {
            if (doors[i] != 0) doorList.append(' ').append(i).append('=').append(doors[i]);
        }
        return "power=" + powerLevel + " sys=" + systemState + " ac=" + acOn
                + " set=" + setTemp + "C outside=" + outsideTemp + "C soc=" + soc + "%"
                + " sim=" + getFeature(0x48F0000A) + " src=" + getFeature(0x48F00010)
                + " doorsOpen[" + doorList.toString().trim() + "]";
    }

    /** Static, not anonymous: d8 8.2.2 cannot dex anonymous inner classes. */
    static final class Receiver extends BroadcastReceiver {
        private final FakeVehicle vehicle;

        Receiver(FakeVehicle vehicle) {
            this.vehicle = vehicle;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            vehicle.apply(intent.getExtras());
        }
    }
}
