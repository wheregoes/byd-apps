package com.wheregoes.camping;

import android.content.Context;
import android.hardware.bydauto.ac.BYDAutoAcDevice;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.lang.reflect.Method;

class ClimateMonitor {
    private static final String TAG = "ClimateMonitor";

    interface Listener {
        void onSetTempChanged(int tempCelsius);
        void onOutsideTempChanged(int tempCelsius);
        void onAcStatusChanged(boolean acOn);
        void onClimateUnavailable();
    }

    private final Context context;
    private Listener listener;
    private Handler handler;
    private BYDAutoAcDevice acDevice;
    private AcListenerHandler acListenerHandler;
    private boolean listenerRegistered = false;
    private int lastSetTemp = Integer.MIN_VALUE;
    private int lastOutsideTemp = Integer.MIN_VALUE;
    private boolean setTempAvailable = false;

    ClimateMonitor(Context context) {
        this.context = context;
        handler = new Handler(Looper.getMainLooper());
    }

    BYDAutoAcDevice getAcDevice() { return acDevice; }

    void start(Listener cb) {
        listener = cb;
        initAcDevice();
        if (acDevice != null) {
            checkInitialAcState();
            tryRegisterListener();
            readTemperatures();
            schedulePoll();
        } else {
            if (listener != null) listener.onClimateUnavailable();
        }
    }

    void stop() {
        unregisterAcListener();
        handler.removeCallbacksAndMessages(null);
    }

    private void schedulePoll() {
        handler.postDelayed(() -> {
            pollTemperature();
            schedulePoll();
        }, 30000);
    }

    private void pollTemperature() {
        if (acDevice == null) return;
        new Thread(() -> {
            try {
                Method m = acDevice.getClass().getMethod("getTemprature", int.class);
                m.setAccessible(true);
                int setTemp = (int) m.invoke(acDevice, 1);
                if (isValidSetTemp(setTemp)) handler.post(() -> onSetTempEvent(setTemp));
                int outside = (int) m.invoke(acDevice, 4);
                if (isValidTemp(outside)) handler.post(() -> onOutsideTempEvent(outside));
                int acState = acDevice.getAcStartState();
                handler.post(() -> { if (listener != null) listener.onAcStatusChanged(acState == 1); });
            } catch (Exception e) {
                Log.d(TAG, "Poll failed: " + e.getMessage());
            }
        }).start();
    }

    private void initAcDevice() {
        try {
            acDevice = BYDAutoAcDevice.getInstance(new BydPermissionContext(context));
            Log.i(TAG, "BYDAutoAcDevice initialized");
        } catch (Exception e) {
            Log.w(TAG, "BYDAutoAcDevice init failed: " + e.getMessage());
        }
    }

    private void checkInitialAcState() {
        try {
            int state = acDevice.getAcStartState();
            Log.i(TAG, "AC start state: " + state);
            if (listener != null) listener.onAcStatusChanged(state == 1);
        } catch (Exception e) {
            Log.w(TAG, "getAcStartState failed: " + e.getMessage());
        }
    }

    private void tryRegisterListener() {
        new Thread(() -> {
            try {
                acListenerHandler = new AcListenerHandler(this);
                acDevice.registerListener(acListenerHandler, new int[0]);
                listenerRegistered = true;
                Log.i(TAG, "AC listener registered");
            } catch (Exception e) {
                Log.w(TAG, "AC listener registration failed: " + e.getMessage());
                listenerRegistered = false;
            }
        }).start();
    }

    private void unregisterAcListener() {
        if (listenerRegistered && acDevice != null && acListenerHandler != null) {
            try {
                acDevice.unregisterListener(acListenerHandler);
                listenerRegistered = false;
            } catch (Exception ignored) {}
        }
    }

    private static boolean isValidTemp(int val) {
        return val > -50 && val < 80;
    }

    private static boolean isValidSetTemp(int val) {
        return val >= 16 && val <= 32;
    }

    private void readTemperatures() {
        new Thread(() -> {
            try {
                Method m = acDevice.getClass().getMethod("getTemprature", int.class);
                m.setAccessible(true);
                int setTemp = (int) m.invoke(acDevice, 1);
                if (isValidSetTemp(setTemp)) handler.post(() -> onSetTempEvent(setTemp));
                int outside = (int) m.invoke(acDevice, 4);
                if (isValidTemp(outside)) handler.post(() -> onOutsideTempEvent(outside));
            } catch (Exception e) {
                Log.w(TAG, "Temperature read failed: " + e.getMessage());
            }
        }).start();
    }

    void onSetTempEvent(int tempCelsius) {
        handler.post(() -> {
            if (tempCelsius != lastSetTemp && isValidSetTemp(tempCelsius)) {
                lastSetTemp = tempCelsius;
                setTempAvailable = true;
                Log.i(TAG, "AC set temp: " + tempCelsius + "°C");
                if (listener != null) listener.onSetTempChanged(tempCelsius);
            }
        });
    }

    void onOutsideTempEvent(int tempCelsius) {
        handler.post(() -> {
            if (tempCelsius != lastOutsideTemp && isValidTemp(tempCelsius)) {
                lastOutsideTemp = tempCelsius;
                Log.i(TAG, "Outside temp: " + tempCelsius + "°C");
                if (listener != null) listener.onOutsideTempChanged(tempCelsius);
            }
        });
    }

    void onAcStartedEvent() {
        handler.post(() -> { if (listener != null) listener.onAcStatusChanged(true); });
    }

    void onAcStoppedEvent() {
        handler.post(() -> { if (listener != null) listener.onAcStatusChanged(false); });
    }

    int getLastSetTemp() { return lastSetTemp; }
    int getLastOutsideTemp() { return lastOutsideTemp; }
    boolean isSetTempAvailable() { return setTempAvailable; }
}
