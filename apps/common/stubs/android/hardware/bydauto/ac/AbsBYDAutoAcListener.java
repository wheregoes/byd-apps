package android.hardware.bydauto.ac;

import android.hardware.IBYDAutoListener;
import android.hardware.bydauto.BYDAutoEventValue;

public class AbsBYDAutoAcListener implements IBYDAutoListener {
    public void onAcStarted() {}
    public void onAcStoped() {}
    public void onAcOnlineStateChanged(int state) {}
    public void on3daOnlineStateChanged(int state) {}
    public void onQuickCleanAirStateChanged(int state) {}
    public void onDataEventChanged(int featureId, BYDAutoEventValue value) {}
}
