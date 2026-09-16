package android.hardware.bydauto.statistic;

import android.content.Context;
import android.hardware.bydauto.AbsBYDAutoDevice;
import android.hardware.bydauto.FakeVehicle;

/**
 * EMULATOR ONLY. Same public surface as apps/common/stubs, backed by
 * {@link FakeVehicle}. Only the traction SOC is modelled; the rest read 0.
 */
public class BYDAutoStatisticDevice extends AbsBYDAutoDevice {
    private static BYDAutoStatisticDevice mInstance;

    private final FakeVehicle vehicle;

    private BYDAutoStatisticDevice(Context context) {
        super(context);
        vehicle = FakeVehicle.get(context);
    }

    public static synchronized BYDAutoStatisticDevice getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new BYDAutoStatisticDevice(context);
        }
        return mInstance;
    }

    public double getElecPercentageValue() { return vehicle.soc; }
    public int getEVMileageValue() { return 0; }
    public double getTotalElecConValue() { return 0.0; }
    public double getTotalFuelConValue() { return 0.0; }
    public double getTotalMileageValue() { return 0.0; }
    public int getType() { return 0; }
}
