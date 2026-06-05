package com.wheregoes.camping;

import android.hardware.bydauto.bodywork.BYDAutoBodyworkDevice;
import android.util.Log;

import java.lang.reflect.Method;

class WindowController {
    private static final String TAG = "WindowController";
    private final BYDAutoBodyworkDevice device;

    static final int WINDOW_OPEN = 1;
    static final int WINDOW_CLOSE = 0;

    WindowController(BYDAutoBodyworkDevice device) {
        this.device = device;
    }

    void openAllWindows() {
        setAllWindows(WINDOW_OPEN);
    }

    void closeAllWindows() {
        setAllWindows(WINDOW_CLOSE);
    }

    private void setAllWindows(int state) {
        new Thread(() -> {
            try {
                Method m = device.getClass().getMethod("setAllWindowState", int.class);
                Object result = m.invoke(device, state);
                Log.i(TAG, "setAllWindowState(" + state + ") = " + result);
            } catch (Exception e) {
                Log.e(TAG, "setAllWindowState failed, trying individual: " + e.getMessage());
                setIndividualWindow(1, state);
                setIndividualWindow(2, state);
                setIndividualWindow(3, state);
                setIndividualWindow(4, state);
            }
        }).start();
    }

    void setIndividualWindow(int area, int state) {
        try {
            Method m = device.getClass().getMethod("setBodyWindowCtrlState", int.class, int.class);
            Object result = m.invoke(device, area, state);
            Log.i(TAG, "setBodyWindowCtrlState(" + area + ", " + state + ") = " + result);
        } catch (Exception e) {
            Log.e(TAG, "setBodyWindowCtrlState failed: " + e.getMessage());
        }
    }
}
