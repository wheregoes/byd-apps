package com.wheregoes.camping;

import android.content.Context;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

class BatteryAlarmManager {
    private static final String TAG = "BatteryAlarm";

    interface Listener {
        void onLowBatteryAlarm(int currentSoc, int threshold);
        void onLowBatteryDismissed();
    }

    private final Handler handler;
    private Listener listener;
    private int thresholdPercent = 20;
    private boolean alarmActive = false;
    private boolean alarmTriggered = false;
    private ToneGenerator toneGenerator;

    BatteryAlarmManager() {
        this.handler = new Handler(Looper.getMainLooper());
    }

    void setListener(Listener listener) { this.listener = listener; }

    void setThreshold(int percent) {
        thresholdPercent = percent;
        if (alarmTriggered) {
            alarmTriggered = false;
        }
    }

    int getThreshold() { return thresholdPercent; }
    boolean isAlarmActive() { return alarmActive; }

    void checkSoc(int socPercent) {
        if (alarmTriggered) return;
        if (socPercent <= thresholdPercent && socPercent >= 0) {
            Log.w(TAG, "LOW BATTERY: " + socPercent + "% <= " + thresholdPercent + "%");
            alarmTriggered = true;
            alarmActive = true;
            playAlarmSound();
            if (listener != null) listener.onLowBatteryAlarm(socPercent, thresholdPercent);
        }
    }

    void dismiss() {
        alarmActive = false;
        stopAlarmSound();
        if (listener != null) listener.onLowBatteryDismissed();
    }

    private void playAlarmSound() {
        stopAlarmSound();
        try {
            toneGenerator = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
            playAlarmLoop();
        } catch (Exception e) {
            Log.e(TAG, "Failed to play alarm: " + e.getMessage());
        }
    }

    private void playAlarmLoop() {
        if (!alarmActive || toneGenerator == null) return;
        toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 800);
        handler.postDelayed(this::playAlarmLoop, 1200);
    }

    private void stopAlarmSound() {
        if (toneGenerator != null) {
            try {
                toneGenerator.stopTone();
                toneGenerator.release();
            } catch (Exception ignored) {}
            toneGenerator = null;
        }
    }

    void destroy() {
        alarmActive = false;
        stopAlarmSound();
        handler.removeCallbacksAndMessages(null);
    }
}
