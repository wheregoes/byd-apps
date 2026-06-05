package com.wheregoes.camping;

import android.content.Context;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

class DoorAlarmManager {
    private static final String TAG = "DoorAlarm";
    private static final int SNOOZE_MS = 30000;

    interface Listener {
        void onAlarmTriggered();
        void onAlarmDismissed();
        void onArmedChanged(boolean armed);
    }

    private final Context context;
    private final Handler handler;
    private Listener listener;
    private boolean armed = false;
    private boolean alarmActive = false;
    private boolean snoozed = false;
    private ToneGenerator toneGenerator;

    DoorAlarmManager(Context context) {
        this.context = context;
        this.handler = new Handler(Looper.getMainLooper());
    }

    void setListener(Listener listener) { this.listener = listener; }

    boolean isArmed() { return armed; }
    boolean isAlarmActive() { return alarmActive; }

    void arm() {
        armed = true;
        snoozed = false;
        Log.i(TAG, "Door alarm ARMED");
        if (listener != null) listener.onArmedChanged(true);
    }

    void disarm() {
        armed = false;
        stopAlarm();
        Log.i(TAG, "Door alarm DISARMED");
        if (listener != null) listener.onArmedChanged(false);
    }

    void onDoorOpened(int area) {
        if (!armed || snoozed) return;
        Log.w(TAG, "DOOR " + area + " OPENED while armed!");
        triggerAlarm();
    }

    void snooze() {
        snoozed = true;
        stopAlarmSound();
        alarmActive = false;
        Log.i(TAG, "Door alarm snoozed for " + (SNOOZE_MS / 1000) + "s");
        handler.postDelayed(() -> {
            snoozed = false;
            Log.i(TAG, "Snooze ended");
        }, SNOOZE_MS);
        if (listener != null) listener.onAlarmDismissed();
    }

    void dismiss() {
        stopAlarm();
        if (listener != null) listener.onAlarmDismissed();
    }

    private void triggerAlarm() {
        alarmActive = true;
        playAlarmSound();
        if (listener != null) listener.onAlarmTriggered();
    }

    private void stopAlarm() {
        alarmActive = false;
        snoozed = false;
        stopAlarmSound();
        handler.removeCallbacksAndMessages(null);
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
        toneGenerator.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 1000);
        handler.postDelayed(this::playAlarmLoop, 1500);
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
        stopAlarm();
    }
}
