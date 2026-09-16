package android.hardware;

import android.content.Context;
import android.hardware.bydauto.FakeVehicle;

/**
 * EMULATOR ONLY. Stand-in for the platform class the "auto" system service
 * hands out on a head unit.
 *
 * Every method signature here is looked up by reflection from app code, so the
 * names and parameter lists must match the real class exactly.
 */
public final class BYDAutoManager {
    private static BYDAutoManager instance;

    private final FakeVehicle vehicle;

    private BYDAutoManager(FakeVehicle vehicle) {
        this.vehicle = vehicle;
    }

    public static synchronized BYDAutoManager get(Context context) {
        if (instance == null) {
            instance = new BYDAutoManager(FakeVehicle.get(context));
        }
        return instance;
    }

    /** @param dev device id (1002 for the sound features); ignored here. */
    public int setInt(int dev, int fid, int val) {
        vehicle.setFeature(fid, val);
        return 0;
    }

    /** @return the feature value, or -10011 for an unsupported id. */
    public int getInt(int dev, int fid) {
        return vehicle.getFeature(fid);
    }
}
