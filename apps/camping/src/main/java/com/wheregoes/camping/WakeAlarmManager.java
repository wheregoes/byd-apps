package com.wheregoes.camping;

import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.Calendar;

class WakeAlarmManager {
    private static final String TAG = "WakeAlarm";

    interface Listener {
        void onWakeAlarmTriggered();
        void onWakeAlarmDismissed();
        void onWakeAlarmSet(int hour, int minute);
        void onWakeAlarmCleared();
    }

    private final Handler handler;
    private Listener listener;
    private int alarmHour = -1;
    private int alarmMinute = -1;
    private boolean enabled = false;
    private boolean ringing = false;
    private ToneGenerator toneGenerator;

    WakeAlarmManager() {
        this.handler = new Handler(Looper.getMainLooper());
    }

    void setListener(Listener listener) { this.listener = listener; }

    boolean isEnabled() { return enabled; }
    boolean isRinging() { return ringing; }
    int getAlarmHour() { return alarmHour; }
    int getAlarmMinute() { return alarmMinute; }

    void setAlarm(int hour, int minute) {
        alarmHour = hour;
        alarmMinute = minute;
        enabled = true;
        ringing = false;
        scheduleCheck();
        Log.i(TAG, "Wake alarm set: " + String.format("%02d:%02d", hour, minute));
        if (listener != null) listener.onWakeAlarmSet(hour, minute);
    }

    void clearAlarm() {
        enabled = false;
        ringing = false;
        alarmHour = -1;
        alarmMinute = -1;
        stopAlarmSound();
        handler.removeCallbacksAndMessages(null);
        Log.i(TAG, "Wake alarm cleared");
        if (listener != null) listener.onWakeAlarmCleared();
    }

    void dismiss() {
        ringing = false;
        enabled = false;
        stopAlarmSound();
        handler.removeCallbacksAndMessages(null);
        Log.i(TAG, "Wake alarm dismissed");
        if (listener != null) listener.onWakeAlarmDismissed();
    }

    private void scheduleCheck() {
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(() -> {
            checkAlarm();
            if (enabled && !ringing) scheduleCheck();
        }, 15000);
    }

    private void checkAlarm() {
        if (!enabled || ringing) return;
        Calendar now = Calendar.getInstance();
        int nowHour = now.get(Calendar.HOUR_OF_DAY);
        int nowMinute = now.get(Calendar.MINUTE);
        if (nowHour == alarmHour && nowMinute == alarmMinute) {
            Log.i(TAG, "WAKE ALARM TRIGGERED!");
            ringing = true;
            playAlarmSound();
            if (listener != null) listener.onWakeAlarmTriggered();
        }
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
        if (!ringing || toneGenerator == null) return;
        toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_AUTOREDIAL_LITE, 500);
        handler.postDelayed(this::playAlarmLoop, 1000);
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
        ringing = false;
        enabled = false;
        stopAlarmSound();
        handler.removeCallbacksAndMessages(null);
    }
}
