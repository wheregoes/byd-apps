package android.hardware.bydauto.statistic;

import android.content.Context;
import android.hardware.bydauto.AbsBYDAutoDevice;

public class BYDAutoStatisticDevice extends AbsBYDAutoDevice {
    private static BYDAutoStatisticDevice mInstance;

    private BYDAutoStatisticDevice(Context context) { super(context); }

    public static synchronized BYDAutoStatisticDevice getInstance(Context context) {
        synchronized (BYDAutoStatisticDevice.class) {
            if (mInstance == null) mInstance = new BYDAutoStatisticDevice(context);
        }
        return mInstance;
    }

    public double getElecPercentageValue() { return 0.0; }
    public int getEVMileageValue() { return 0; }
    public double getTotalElecConValue() { return 0.0; }
    public double getTotalFuelConValue() { return 0.0; }
    public double getTotalMileageValue() { return 0.0; }
    public int getType() { return 0; }
}
