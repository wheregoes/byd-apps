package android.hardware.bydauto.ac;

import android.content.Context;
import android.hardware.IBYDAutoListener;
import android.hardware.bydauto.AbsBYDAutoDevice;
import android.hardware.bydauto.BYDAutoEventValue;

public class BYDAutoAcDevice extends AbsBYDAutoDevice {
    public BYDAutoAcDevice(Context context) { super(context); }
    public static BYDAutoAcDevice getInstance(Context context) { return null; }
    public int getAcStartState() { return 0; }
    public int getAcCycleMode() { return 0; }
    public int getAcOnlineState() { return 0; }
    public int get3daOnlineState() { return 0; }
    public int getQuickCleanAirState() { return 0; }
    public BYDAutoEventValue get(int[] featureIds, Class<?> type) { return null; }
    public int set(int[] featureIds, BYDAutoEventValue value) { return 0; }
    public void registerListener(AbsBYDAutoAcListener listener, int[] featureIds) {}
    public void registerListener(IBYDAutoListener listener, int[] featureIds) {}
    public void unregisterListener(AbsBYDAutoAcListener listener) {}
    public void unregisterListener(IBYDAutoListener listener) {}
    public int setQuickCleanAirState(int state) { return 0; }
}
