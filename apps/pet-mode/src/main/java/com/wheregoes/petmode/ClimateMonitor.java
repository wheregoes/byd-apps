package com.wheregoes.petmode;

import android.content.Context;
import android.hardware.bydauto.ac.BYDAutoAcDevice;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;

import java.lang.reflect.Method;

/**
 * The only authority on AC state and cabin temperatures.
 *
 * Previously {@link VehicleStateMonitor} also reported AC state, from the
 * bodywork listener, while this class's 30-second poll pushed its own reading
 * unconditionally -- so the two writers fought over PetModeService.acOn and the
 * flag flapped. AC events now arrive here only, via AcListenerHandler.
 */
class ClimateMonitor {
    private static final String TAG = "ClimateMonitor";

    private static final long POLL_INTERVAL_MS = 30_000L;
    /**
     * How long a successful read stays trustworthy. Events are deduplicated on
     * value change, so an unchanging temperature produces no events at all --
     * only the poll timestamp can tell a live feed from a dead one.
     */
    private static final long STALE_AFTER_MS = 120_000L;

    interface Listener {
        void onSetTempChanged(int tempCelsius);
        void onOutsideTempChanged(int tempCelsius);
        void onAcStatusChanged(boolean acOn);
        void onClimateUnavailable();
    }

    private final Context context;
    private Listener listener;
    private final Handler handler;
    private HandlerThread ioThread;
    private Handler io;
    private BYDAutoAcDevice acDevice;
    private AcListenerHandler acListenerHandler;
    /** Written on the io thread, read on the main thread. */
    private volatile boolean listenerRegistered = false;
    private int lastSetTemp = Integer.MIN_VALUE;
    private int lastOutsideTemp = Integer.MIN_VALUE;
    private Boolean lastAcOn = null;
    private boolean setTempAvailable = false;
    private long lastClimateOkMs = 0L;

    private final Runnable pollTick = this::pollAndReschedule;
    private final Runnable staleCheck = this::checkStale;

    ClimateMonitor(Context context) {
        this.context = context;
        this.handler = new Handler(Looper.getMainLooper());
    }

    void start(Listener cb) {
        listener = cb;
        ioThread = new HandlerThread("climate-io");
        ioThread.start();
        io = new Handler(ioThread.getLooper());
        io.post(() -> {
            initAcDevice();
            if (acDevice == null) {
                handler.post(() -> {
                    if (listener != null) {
                        listener.onClimateUnavailable();
                    }
                });
                return;
            }
            registerAcListener();
            readClimate(true);
            io.postDelayed(pollTick, POLL_INTERVAL_MS);
            handler.postDelayed(staleCheck, STALE_AFTER_MS);
        });
    }

    void stop() {
        handler.removeCallbacks(staleCheck);
        if (io != null) {
            io.removeCallbacksAndMessages(null);
            io.post(this::unregisterAcListener);
        }
        if (ioThread != null) {
            ioThread.quitSafely();
        }
    }

    // ------------------------------------------------------------------ device

    /** io thread. */
    private void initAcDevice() {
        try {
            acDevice = BYDAutoAcDevice.getInstance(new BydPermissionContext(context));
            Log.i(TAG, "BYDAutoAcDevice initialized");
        } catch (Throwable t) {
            Log.w(TAG, "BYDAutoAcDevice init failed: " + t.getMessage());
        }
    }

    /** io thread. */
    private void registerAcListener() {
        try {
            acListenerHandler = new AcListenerHandler(this);
            acDevice.registerListener(acListenerHandler, new int[0]);
            listenerRegistered = true;
            Log.i(TAG, "AC listener registered");
        } catch (Throwable t) {
            Log.w(TAG, "AC listener registration failed: " + t.getMessage());
            listenerRegistered = false;
        }
    }

    /** io thread. */
    private void unregisterAcListener() {
        if (listenerRegistered && acDevice != null && acListenerHandler != null) {
            try {
                acDevice.unregisterListener(acListenerHandler);
            } catch (Exception ignored) {
            }
            listenerRegistered = false;
        }
    }

    // -------------------------------------------------------------------- poll

    private void pollAndReschedule() {
        readClimate(true);
        io.postDelayed(pollTick, POLL_INTERVAL_MS);
    }

    /**
     * One reader for both the initial read and the poll; they differed only by
     * the extra AC-state call.
     *
     * io thread.
     */
    private void readClimate(boolean includeAcState) {
        if (acDevice == null) {
            return;
        }
        try {
            Method m = acDevice.getClass().getMethod("getTemprature", int.class);
            m.setAccessible(true);
            int setTemp = (int) m.invoke(acDevice, 1);
            int outside = (int) m.invoke(acDevice, 4);
            Integer acState = includeAcState ? acDevice.getAcStartState() : null;

            boolean gotSomething = false;
            if (isValidSetTemp(setTemp)) {
                gotSomething = true;
                handler.post(() -> onSetTempEvent(setTemp));
            }
            if (isValidTemp(outside)) {
                gotSomething = true;
                handler.post(() -> onOutsideTempEvent(outside));
            }
            if (acState != null) {
                final boolean on = acState == 1;
                handler.post(() -> publishAcState(on));
            }
            if (gotSomething) {
                lastClimateOkMs = System.currentTimeMillis();
            }
        } catch (Throwable t) {
            Log.d(TAG, "Climate read failed: " + t.getMessage());
        }
    }

    /**
     * A mid-session CAN dropout used to leave the last temperature on screen for
     * as long as the app ran. For a pet-safety display that is the worst
     * possible failure mode, so stale data is withdrawn explicitly.
     *
     * main thread.
     */
    private void checkStale() {
        long age = System.currentTimeMillis() - lastClimateOkMs;
        if (lastClimateOkMs > 0 && age > STALE_AFTER_MS && setTempAvailable) {
            Log.w(TAG, "Climate signal stale (" + (age / 1000) + "s), withdrawing");
            setTempAvailable = false;
            lastSetTemp = Integer.MIN_VALUE;
            if (listener != null) {
                listener.onClimateUnavailable();
            }
        }
        handler.postDelayed(staleCheck, STALE_AFTER_MS / 4);
    }

    // ------------------------------------------------------------------ events

    void onSetTempEvent(int tempCelsius) {
        handler.post(() -> {
            if (!isValidSetTemp(tempCelsius)) {
                return;
            }
            lastClimateOkMs = System.currentTimeMillis();
            boolean wasUnavailable = !setTempAvailable;
            setTempAvailable = true;
            if (tempCelsius != lastSetTemp || wasUnavailable) {
                lastSetTemp = tempCelsius;
                Log.i(TAG, "AC set temp: " + tempCelsius + "\u00B0C");
                if (listener != null) {
                    listener.onSetTempChanged(tempCelsius);
                }
            }
        });
    }

    void onOutsideTempEvent(int tempCelsius) {
        handler.post(() -> {
            if (tempCelsius != lastOutsideTemp && isValidTemp(tempCelsius)) {
                lastOutsideTemp = tempCelsius;
                lastClimateOkMs = System.currentTimeMillis();
                Log.i(TAG, "Outside temp: " + tempCelsius + "\u00B0C");
                if (listener != null) {
                    listener.onOutsideTempChanged(tempCelsius);
                }
            }
        });
    }

    void onAcStartedEvent() {
        handler.post(() -> publishAcState(true));
    }

    void onAcStoppedEvent() {
        handler.post(() -> publishAcState(false));
    }

    /** main thread. Deduplicated so the 30s poll cannot spam the UI. */
    private void publishAcState(boolean on) {
        if (lastAcOn != null && lastAcOn == on) {
            return;
        }
        lastAcOn = on;
        Log.i(TAG, "AC " + (on ? "ON" : "OFF"));
        if (listener != null) {
            listener.onAcStatusChanged(on);
        }
    }

    private static boolean isValidTemp(int val) {
        return val > -50 && val < 80;
    }

    private static boolean isValidSetTemp(int val) {
        return val >= 16 && val <= 32;
    }

    int getLastSetTemp() {
        return lastSetTemp;
    }

    int getLastOutsideTemp() {
        return lastOutsideTemp;
    }

    boolean isSetTempAvailable() {
        return setTempAvailable;
    }
}
