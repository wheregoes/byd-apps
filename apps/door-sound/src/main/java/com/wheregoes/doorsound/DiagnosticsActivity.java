package com.wheregoes.doorsound;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.hardware.bydauto.bodywork.BYDAutoBodyworkDevice;
import android.os.Build;
import android.os.Bundle;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Locale;

/**
 * Replaces adb for the people who need it most. Several reporters install APKs
 * from the head unit itself and own no PC at all, so every `adb logcat` and
 * `adb shell cat` we asked for was unusable; one photo of this screen carries
 * the same answers.
 *
 * Performs no vehicle I/O -- it reads only what {@link DoorSoundService} has
 * published, so the service stays the single writer of vehicle state.
 *
 * The dump body is English-only literals in Java on purpose: it is a support
 * artifact that must read identically whatever language the car is set to (one
 * reporter's car is in German, which this app does not translate). Only the two
 * chrome strings are localised.
 */
public class DiagnosticsActivity extends Activity {

    private static final int LOG_TAIL_LINES = 40;

    private TextView diagText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_diagnostics);
        getWindow().setStatusBarColor(getColor(R.color.aurora_light_base_start));
        getWindow().setNavigationBarColor(getColor(R.color.aurora_light_base_end));

        diagText = findViewById(R.id.diag_text);
        findViewById(R.id.btn_diag_refresh).setOnClickListener(v -> render());
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    private void render() {
        SharedPreferences prefs =
                getSharedPreferences(DoorSoundService.PREF_NAME, MODE_PRIVATE);

        StringBuilder sb = new StringBuilder();
        sb.append("Door Sound ").append(versionLine()).append('\n');
        sb.append("android: ").append(Build.VERSION.SDK_INT)
                .append("  model: ").append(Build.MODEL)
                .append("  hw: ").append(Build.HARDWARE).append('\n');
        sb.append("locale: ").append(Locale.getDefault()).append('\n');
        sb.append("service: ").append(DoorSoundService.isRunning()
                ? "RUNNING since " + clock(DoorSoundService.sStartedAtMs)
                : "STOPPED").append('\n');
        sb.append("listener: ")
                .append(DoorSoundService.sListenerOk ? "registered" : "NOT registered")
                .append('\n');
        sb.append("avas: ")
                .append(DoorSoundService.sAvasAvailable ? "available" : "unavailable")
                .append('\n');
        sb.append("lock raw: ").append(lockLine()).append('\n');
        sb.append("power: ").append(powerLine()).append('\n');
        sb.append("last poll: ").append(DoorSoundService.sLastPollMs == 0L
                ? "never" : clock(DoorSoundService.sLastPollMs)).append('\n');
        sb.append("master switch: ")
                .append(prefs.getBoolean(DoorSoundService.KEY_ENABLED, false) ? "on" : "off")
                .append('\n');
        sb.append("last event: ")
                .append(prefs.getString(DoorSoundService.KEY_LAST_EVENT, "none")).append('\n');
        sb.append("--- decision log, last ").append(LOG_TAIL_LINES).append(" lines ---\n");
        sb.append(logTail());

        diagText.setText(sb.toString());
    }

    /** Never hardcoded: a wrong version number in a bug report costs a whole round trip. */
    private String versionLine() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            return info.versionName + " (code " + info.getLongVersionCode() + ")";
        } catch (Exception e) {
            return "version unknown";
        }
    }

    private static String lockLine() {
        int raw = DoorSoundService.sLastLockRaw;
        if (raw < 0) {
            return "unknown";
        }
        boolean locked = raw >= BYDAutoBodyworkDevice.BODYWORK_AUTO_SYSTEM_STATE_SET_SECURE;
        return raw + (locked ? " (locked)" : " (unlocked)");
    }

    private static String powerLine() {
        int power = DoorSoundService.sLastPower;
        switch (power) {
            case BYDAutoBodyworkDevice.BODYWORK_POWER_LEVEL_OFF:
                return power + " (off)";
            case BYDAutoBodyworkDevice.BODYWORK_POWER_LEVEL_ACC:
                return power + " (acc)";
            case BYDAutoBodyworkDevice.BODYWORK_POWER_LEVEL_ON:
                return power + " (on)";
            case BYDAutoBodyworkDevice.BODYWORK_POWER_LEVEL_OK:
                return power + " (ok)";
            case BYDAutoBodyworkDevice.BODYWORK_POWER_LEVEL_FAKE_OK:
                return power + " (fake-ok)";
            default:
                return power < 0 ? "unknown" : power + " (unknown)";
        }
    }

    private static String clock(long millis) {
        return new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date(millis));
    }

    /**
     * Same file {@link DoorSoundService#log(String)} appends to, which is readable
     * without root on API 29 and reachable from the head unit's own file manager.
     */
    private String logTail() {
        File dir = getExternalFilesDir(null);
        if (dir == null) {
            return "(no log file yet)";
        }
        File logFile = new File(dir, "doorsound-log.txt");
        // ponytail: reads the whole file to keep the last 40 lines; it is capped at
        // 512 KB by the writer, so a seek-from-the-end reader would buy nothing.
        ArrayDeque<String> tail = new ArrayDeque<>();
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(logFile));
            String line;
            while ((line = reader.readLine()) != null) {
                tail.addLast(line);
                if (tail.size() > LOG_TAIL_LINES) {
                    tail.removeFirst();
                }
            }
        } catch (Exception e) {
            return "(no log file yet)";
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception ignored) {
                }
            }
        }
        if (tail.isEmpty()) {
            return "(no log file yet)";
        }
        StringBuilder sb = new StringBuilder();
        for (String line : tail) {
            sb.append(line).append('\n');
        }
        return sb.toString();
    }
}
