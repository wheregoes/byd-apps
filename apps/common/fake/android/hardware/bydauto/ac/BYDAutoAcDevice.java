package android.hardware.bydauto.ac;

import android.content.Context;
import android.hardware.IBYDAutoListener;
import android.hardware.bydauto.AbsBYDAutoDevice;
import android.hardware.bydauto.BYDAutoEventValue;
import android.hardware.bydauto.FakeVehicle;

/**
 * EMULATOR ONLY. Same public surface as apps/common/stubs, backed by
 * {@link FakeVehicle}.
 *
 * Unlike the stub, getInstance returns an instance: Cabin's ClimateMonitor
 * degrades to "--" forever when it gets null.
 */
public class BYDAutoAcDevice extends AbsBYDAutoDevice {
    private static BYDAutoAcDevice mInstance;

    private final FakeVehicle vehicle;

    public BYDAutoAcDevice(Context context) {
        super(context);
        vehicle = FakeVehicle.get(context);
    }

    public static synchronized BYDAutoAcDevice getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new BYDAutoAcDevice(context);
        }
        return mInstance;
    }

    public int getAcStartState() { return vehicle.acOn ? 1 : 0; }

    /**
     * Reached by reflection from Cabin, and not declared in the stub.
     *
     * @param zone 1 = AC set temperature, 4 = outside temperature.
     */
    public int getTemprature(int zone) {
        if (zone == 1) return vehicle.setTemp;
        if (zone == 4) return vehicle.outsideTemp;
        return 0;
    }

    public int getAcCycleMode() { return 0; }
    public int getAcOnlineState() { return 0; }
    public int get3daOnlineState() { return 0; }
    public int getQuickCleanAirState() { return 0; }
    public BYDAutoEventValue get(int[] featureIds, Class<?> type) { return null; }
    public int set(int[] featureIds, BYDAutoEventValue value) { return 0; }

    public void registerListener(AbsBYDAutoAcListener listener, int[] featureIds) {
        vehicle.acListeners.addIfAbsent(listener);
    }

    public void unregisterListener(AbsBYDAutoAcListener listener) {
        vehicle.acListeners.remove(listener);
    }

    public void registerListener(IBYDAutoListener listener, int[] featureIds) {}
    public void unregisterListener(IBYDAutoListener listener) {}
    public int setQuickCleanAirState(int state) { return 0; }
}
