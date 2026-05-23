package com.wheregoes.camping;

import android.hardware.bydauto.ac.BYDAutoAcDevice;
import android.util.Log;

import java.lang.reflect.Method;

class AcController {
    private static final String TAG = "AcController";
    private final BYDAutoAcDevice acDevice;

    AcController(BYDAutoAcDevice acDevice) {
        this.acDevice = acDevice;
    }

    void turnOn() {
        new Thread(() -> {
            try {
                Method start = acDevice.getClass().getMethod("start", int.class);
                Object result = start.invoke(acDevice, 0);
                Log.i(TAG, "AC start(0) = " + result);
            } catch (Exception e) {
                Log.e(TAG, "AC start failed: " + e.getMessage());
            }
        }).start();
    }

    void turnOff() {
        new Thread(() -> {
            try {
                Method stop = acDevice.getClass().getMethod("stop", int.class);
                Object result = stop.invoke(acDevice, 0);
                Log.i(TAG, "AC stop(0) = " + result);
            } catch (Exception e) {
                Log.e(TAG, "AC stop failed: " + e.getMessage());
            }
        }).start();
    }

    void setTemperature(int tempCelsius) {
        new Thread(() -> {
            try {
                Method setTemp = acDevice.getClass().getMethod("setAcTemperature",
                        int.class, int.class, int.class, int.class);
                Object result = setTemp.invoke(acDevice, 1, tempCelsius, 1, 1);
                Log.i(TAG, "setAcTemperature(1, " + tempCelsius + ", 1, 1) = " + result);
            } catch (Exception e) {
                Log.e(TAG, "Set temp failed: " + e.getMessage());
            }
        }).start();
    }

    void setFanLevel(int level) {
        new Thread(() -> {
            try {
                Method baseSet = acDevice.getClass().getSuperclass()
                        .getDeclaredMethod("set", int.class, int.class, int.class);
                baseSet.setAccessible(true);
                int r = (int) baseSet.invoke(acDevice, 1000, 0x1DE0000C, level);
                Log.i(TAG, "Fan set(" + level + ") = " + r);
            } catch (Exception e) {
                Log.e(TAG, "Set fan failed: " + e.getMessage());
            }
        }).start();
    }

    int getCurrentTemp() {
        try {
            Method getTemp = acDevice.getClass().getMethod("getTemprature", int.class);
            getTemp.setAccessible(true);
            return (int) getTemp.invoke(acDevice, 1);
        } catch (Exception e) {
            return Integer.MIN_VALUE;
        }
    }

    boolean isAcOn() {
        try {
            return acDevice.getAcStartState() == 1;
        } catch (Exception e) {
            return false;
        }
    }
}
