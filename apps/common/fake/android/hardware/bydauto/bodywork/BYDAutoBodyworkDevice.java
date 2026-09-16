package android.hardware.bydauto.bodywork;

import android.content.Context;
import android.hardware.bydauto.AbsBYDAutoDevice;
import android.hardware.bydauto.FakeVehicle;

/**
 * EMULATOR ONLY. Same constants and public surface as apps/common/stubs,
 * backed by {@link FakeVehicle}.
 */
public class BYDAutoBodyworkDevice extends AbsBYDAutoDevice {
    public static final int BODYWORK_STATE_CLOSED = 0;
    public static final int BODYWORK_STATE_OPEN = 1;
    public static final int BODYWORK_STATE_UNDEFINED = 255;
    public static final int BODYWORK_AUTO_SYSTEM_STATE_NORMAL = 0;
    public static final int BODYWORK_AUTO_SYSTEM_STATE_SET_SECURE = 1;
    public static final int BODYWORK_AUTO_SYSTEM_STATE_START_SECURE = 2;
    public static final int BODYWORK_AUTO_SYSTEM_STATE_UNDEFINED = 255;
    public static final int BODYWORK_POWER_LEVEL_OFF = 0;
    public static final int BODYWORK_POWER_LEVEL_ACC = 1;
    public static final int BODYWORK_POWER_LEVEL_ON = 2;
    public static final int BODYWORK_POWER_LEVEL_OK = 3;
    public static final int BODYWORK_POWER_LEVEL_FAKE_OK = 4;
    public static final int BODYWORK_CMD_DOOR_LEFT_FRONT = 1;
    public static final int BODYWORK_CMD_DOOR_RIGHT_FRONT = 2;
    public static final int BODYWORK_CMD_DOOR_LEFT_REAR = 3;
    public static final int BODYWORK_CMD_DOOR_RIGHT_REAR = 4;
    public static final int BODYWORK_CMD_DOOR_HOOD = 5;
    public static final int BODYWORK_CMD_DOOR_LUGGAGE_DOOR = 6;
    public static final int BODYWORK_CMD_DOOR_FUEL_TANK_CAP = 7;

    private static BYDAutoBodyworkDevice mInstance;

    private final FakeVehicle vehicle;

    private BYDAutoBodyworkDevice(Context context) {
        super(context);
        vehicle = FakeVehicle.get(context);
    }

    public static synchronized BYDAutoBodyworkDevice getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new BYDAutoBodyworkDevice(context);
        }
        return mInstance;
    }

    public void registerListener(AbsBYDAutoBodyworkListener listener) {
        vehicle.bodyListeners.addIfAbsent(listener);
    }

    public void unregisterListener(AbsBYDAutoBodyworkListener listener) {
        vehicle.bodyListeners.remove(listener);
    }

    public int getAutoSystemState() { return vehicle.systemState; }
    public int getPowerLevel() { return vehicle.powerLevel; }
    public String getAutoVIN() { return "FAKE00000000000000"; }
}
