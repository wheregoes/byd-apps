package com.wheregoes.camping;

import android.hardware.bydauto.BYDAutoEventValue;
import android.hardware.bydauto.ac.AbsBYDAutoAcListener;
import android.util.Log;

public class AcListenerHandler extends AbsBYDAutoAcListener {
    private static final String TAG = "AcListener";

    private final ClimateMonitor monitor;

    AcListenerHandler(ClimateMonitor monitor) {
        this.monitor = monitor;
    }

    @Override
    public void onDataEventChanged(int featureId, BYDAutoEventValue value) {
        if (value != null) {
            Log.d(TAG, "onDataEvent 0x" + Integer.toHexString(featureId) + " = " + value.intValue);
        }
    }

    @Override
    public void onAcStarted() {
        Log.i(TAG, "AC started");
        monitor.onAcStartedEvent();
    }

    @Override
    public void onAcStoped() {
        Log.i(TAG, "AC stopped");
        monitor.onAcStoppedEvent();
    }
}
