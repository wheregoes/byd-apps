package com.wheregoes.camping;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

class SessionLogger {
    private static final String TAG = "SessionLogger";
    private static final int LOG_INTERVAL_MS = 60000;
    private static final String KEY_SESSION_LOG = "session_log";

    private final Context context;
    private final Handler handler;
    private final DischargeMonitor dischargeMonitor;
    private final ClimateMonitor climateMonitor;

    private long sessionStartTime;
    private double startSoc = -1;
    private double minTemp = Double.MAX_VALUE;
    private double maxTemp = Double.MIN_VALUE;
    private double totalDischargeW = 0;
    private int dischargeCount = 0;
    private JSONArray logEntries;

    SessionLogger(Context context, DischargeMonitor dm, ClimateMonitor cm) {
        this.context = context;
        this.dischargeMonitor = dm;
        this.climateMonitor = cm;
        this.handler = new Handler(Looper.getMainLooper());
        this.logEntries = new JSONArray();
    }

    void start() {
        sessionStartTime = System.currentTimeMillis();
        scheduleLog();
    }

    void stop() {
        handler.removeCallbacksAndMessages(null);
    }

    private void scheduleLog() {
        handler.postDelayed(() -> {
            logSnapshot();
            scheduleLog();
        }, LOG_INTERVAL_MS);
    }

    private void logSnapshot() {
        try {
            double soc = dischargeMonitor.getLastSocPercent();
            double watts = dischargeMonitor.getSmoothedWatts();
            int insideTemp = climateMonitor.getLastSetTemp();
            int outsideTemp = climateMonitor.getLastOutsideTemp();

            if (startSoc < 0 && soc >= 0) startSoc = soc;

            if (insideTemp != Integer.MIN_VALUE) {
                if (insideTemp < minTemp) minTemp = insideTemp;
                if (insideTemp > maxTemp) maxTemp = insideTemp;
            }

            if (watts > 0) {
                totalDischargeW += watts;
                dischargeCount++;
            }

            JSONObject entry = new JSONObject();
            entry.put("t", System.currentTimeMillis());
            entry.put("soc", soc);
            entry.put("w", Math.round(watts));
            entry.put("ti", insideTemp);
            entry.put("to", outsideTemp);
            logEntries.put(entry);

            Log.d(TAG, "Logged: soc=" + soc + " w=" + Math.round(watts));
        } catch (Exception e) {
            Log.e(TAG, "Log failed: " + e.getMessage());
        }
    }

    SessionSummary getSummary() {
        long durationMs = System.currentTimeMillis() - sessionStartTime;
        double endSoc = dischargeMonitor.getLastSocPercent();
        double kwhUsed = dischargeMonitor.getTotalKwhUsed();
        double avgDischarge = dischargeCount > 0 ? totalDischargeW / dischargeCount : 0;

        return new SessionSummary(
                durationMs,
                startSoc >= 0 ? startSoc : 0,
                endSoc >= 0 ? endSoc : 0,
                kwhUsed,
                avgDischarge,
                minTemp == Double.MAX_VALUE ? 0 : minTemp,
                maxTemp == Double.MIN_VALUE ? 0 : maxTemp
        );
    }

    void saveSummary() {
        try {
            SessionSummary summary = getSummary();
            SharedPreferences prefs = context.getSharedPreferences(CampingService.PREF_NAME, Context.MODE_PRIVATE);
            JSONObject json = new JSONObject();
            json.put("durationMs", summary.durationMs);
            json.put("startSoc", summary.startSoc);
            json.put("endSoc", summary.endSoc);
            json.put("kwhUsed", summary.kwhUsed);
            json.put("avgDischargeW", summary.avgDischargeW);
            json.put("minTemp", summary.minTemp);
            json.put("maxTemp", summary.maxTemp);
            json.put("timestamp", System.currentTimeMillis());
            prefs.edit().putString("last_session_summary", json.toString()).apply();
            Log.i(TAG, "Session summary saved: " + json);
        } catch (Exception e) {
            Log.e(TAG, "Save summary failed: " + e.getMessage());
        }
    }

    static class SessionSummary {
        final long durationMs;
        final double startSoc;
        final double endSoc;
        final double kwhUsed;
        final double avgDischargeW;
        final double minTemp;
        final double maxTemp;

        SessionSummary(long durationMs, double startSoc, double endSoc,
                       double kwhUsed, double avgDischargeW, double minTemp, double maxTemp) {
            this.durationMs = durationMs;
            this.startSoc = startSoc;
            this.endSoc = endSoc;
            this.kwhUsed = kwhUsed;
            this.avgDischargeW = avgDischargeW;
            this.minTemp = minTemp;
            this.maxTemp = maxTemp;
        }
    }
}
