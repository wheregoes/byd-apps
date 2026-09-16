package com.wheregoes.byd;

import android.content.Context;

/**
 * Emulator-only replacement for the real seam: a stock AVD has no "auto" system
 * service, so the fake manager is handed out directly.
 */
public final class Vehicle {
    private Vehicle() {}

    public static Object autoService(Context context) {
        return android.hardware.BYDAutoManager.get(context);
    }
}
