package com.wheregoes.camping;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.bydauto.statistic.BYDAutoStatisticDevice;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

class DischargeMonitor {
    private static final String TAG = "DischargeMonitor";
    static final double DEFAULT_BATTERY_CAPACITY_WH = 44900.0;
    private static final int SAMPLE_INTERVAL_MS = 30000;
    private static final int MAX_SAMPLES = 20;
    private static final double EMA_ALPHA = 0.3;

    interface Listener {
        void onDischargeRateChanged(double watts);
        void onRemainingTimeChanged(long remainingMinutes);
        void onSocChanged(int socPercent);
    }

    private final Context context;
    private BYDAutoStatisticDevice statisticDevice;
    private Handler handler;
    private Listener listener;
    private double batteryCapacityWh = DEFAULT_BATTERY_CAPACITY_WH;
    private int alarmThresholdPercent = 20;

    private final long[] sampleTimes = new long[MAX_SAMPLES];
    private final double[] sampleSoc = new double[MAX_SAMPLES];
    private int sampleCount = 0;
    private int sampleHead = 0;

    private double smoothedWatts = 0;
    private double lastSocPercent = -1;
    private double totalKwhUsed = 0;
    private double startSoc = -1;

    DischargeMonitor(Context context) {
        this.context = context;
        handler = new Handler(Looper.getMainLooper());
    }

    void start(BYDAutoStatisticDevice device, Listener cb) {
        statisticDevice = device;
        listener = cb;

        SharedPreferences prefs = context.getSharedPreferences(CampingService.PREF_NAME, Context.MODE_PRIVATE);
        alarmThresholdPercent = prefs.getInt(CampingService.KEY_BATTERY_ALARM_THRESHOLD, 20);

        if (statisticDevice != null) {
            takeSample();
            scheduleSample();
        }
    }

    void stop() {
        if (handler != null) handler.removeCallbacksAndMessages(null);
    }

    void setAlarmThreshold(int percent) {
        alarmThresholdPercent = percent;
    }

    double getSmoothedWatts() { return smoothedWatts; }
    double getLastSocPercent() { return lastSocPercent; }
    double getTotalKwhUsed() { return totalKwhUsed; }
    double getStartSoc() { return startSoc; }

    private void scheduleSample() {
        handler.postDelayed(() -> {
            takeSample();
            scheduleSample();
        }, SAMPLE_INTERVAL_MS);
    }

    private void takeSample() {
        if (statisticDevice == null) return;
        new Thread(() -> {
            try {
                double soc = statisticDevice.getElecPercentageValue();
                if (soc < 0 || soc > 100) return;
                long now = System.currentTimeMillis();
                handler.post(() -> processSample(now, soc));
            } catch (Exception e) {
                Log.d(TAG, "SOC read failed: " + e.getMessage());
            }
        }).start();
    }

    private void processSample(long time, double soc) {
        if (startSoc < 0) startSoc = soc;

        if (lastSocPercent >= 0 && soc < lastSocPercent) {
            double deltaSoc = lastSocPercent - soc;
            totalKwhUsed += (deltaSoc / 100.0) * batteryCapacityWh / 1000.0;
        }

        lastSocPercent = soc;
        int idx = (sampleHead + sampleCount) % MAX_SAMPLES;
        if (sampleCount < MAX_SAMPLES) {
            sampleCount++;
        } else {
            sampleHead = (sampleHead + 1) % MAX_SAMPLES;
        }
        sampleTimes[idx] = time;
        sampleSoc[idx] = soc;

        if (listener != null) listener.onSocChanged((int) Math.round(soc));

        if (sampleCount >= 2) {
            double oldestSoc = sampleSoc[sampleHead];
            long oldestTime = sampleTimes[sampleHead];
            int newestIdx = (sampleHead + sampleCount - 1) % MAX_SAMPLES;
            double newestSoc = sampleSoc[newestIdx];
            long newestTime = sampleTimes[newestIdx];

            double timeDeltaHours = (newestTime - oldestTime) / 3600000.0;
            if (timeDeltaHours > 0) {
                double socDelta = oldestSoc - newestSoc;
                double instantWatts = (socDelta / 100.0) * batteryCapacityWh / timeDeltaHours;
                if (instantWatts < 0) instantWatts = 0;

                if (smoothedWatts <= 0) {
                    smoothedWatts = instantWatts;
                } else {
                    smoothedWatts = EMA_ALPHA * instantWatts + (1 - EMA_ALPHA) * smoothedWatts;
                }

                if (listener != null) listener.onDischargeRateChanged(smoothedWatts);

                if (smoothedWatts > 0) {
                    double usableSoc = soc - alarmThresholdPercent;
                    if (usableSoc < 0) usableSoc = 0;
                    double usableWh = (usableSoc / 100.0) * batteryCapacityWh;
                    long remainingMinutes = (long) (usableWh / smoothedWatts * 60);
                    if (listener != null) listener.onRemainingTimeChanged(remainingMinutes);
                }
            }
        }
    }
}
