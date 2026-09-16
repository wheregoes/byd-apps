package com.wheregoes.byd;

import android.content.Context;

/**
 * The BYD "auto" HAL bridge.
 *
 * On a head unit this is the platform's android.hardware.BYDAutoManager, reached
 * through the system service registry. The seam exists so the emulator-only fake
 * in apps/common/fake/ can substitute a stand-in without the apps knowing;
 * see tools/emu.sh.
 */
public final class Vehicle {
    private Vehicle() {}

    /** @return the "auto" system service, or null when the platform has none. */
    public static Object autoService(Context context) {
        return context.getSystemService("auto");
    }
}
