package com.wheregoes.doorsound;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.bydauto.bodywork.BYDAutoBodyworkDevice;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DoorSoundService extends Service {
    static final String TAG = "DoorSoundService";
    private static final String CHANNEL_ID = "door_sound_service";
    private static final int NOTIFICATION_ID = 1;

    static final String PREF_NAME = "door_sound_prefs";
    static final String KEY_ENABLED = "enabled";
    static final String KEY_LAST_EVENT = "last_event";
    static final int DEFAULT_VOLUME = 10;

    /**
     * Held only long enough to outlive a sound. The old code acquired with no
     * timeout and released only in onDestroy, which a kill skips -- so a killed
     * service left the CPU pinned awake until the next reboot.
     */
    private static final long WAKE_LOCK_TIMEOUT_MS = 10 * 60 * 1000L;

    /** API 29 clamps periodic jobs to 15 minutes; ask for exactly that. */
    private static final long RESTART_PERIOD_MS = 15 * 60 * 1000L;

    /**
     * Fallback for vehicles that never push onAutoSystemStateChanged. Reporters
     * had door events working and lock/unlock silent, which is exactly what an
     * event-only service does on a car that reports the lock state but never
     * announces it. Cabin has always polled; this app did not. The pushed path
     * still fires instantly, so polling only adds a floor.
     */
    private static final long POLL_INTERVAL_MS = 10_000L;

    /** How long after the last event or power change the poll and the wake lock stay active. */
    private static final long WATCH_WINDOW_MS = 10 * 60 * 1000L;

    /** Backoff for a listener registration that races BYD's own `auto` service. */
    private static final long[] RETRY_DELAYS_MS = {2_000L, 5_000L, 10_000L, 30_000L};
    private static final long RETRY_DELAY_STEADY_MS = 60_000L;

    /** Debug log is self-limiting; no setting to forget to turn off. */
    private static final long LOG_MAX_BYTES = 512 * 1024L;

    private static volatile boolean sRunning = false;

    /**
     * Diagnostic snapshot for {@link DiagnosticsActivity}. Process scoped on
     * purpose: after a kill these read back as zero/false, which is itself the
     * answer to "was the service alive when I locked the car".
     */
    static volatile long sStartedAtMs = 0L;
    static volatile boolean sListenerOk = false;
    static volatile boolean sAvasAvailable = false;
    static volatile int sLastLockRaw = -1;
    static volatile int sLastPower = -1;
    static volatile long sLastPollMs = 0L;

    private PowerManager.WakeLock wakeLock;
    private BYDAutoBodyworkDevice bodyworkDevice;
    private BodyworkHandler bodyworkListener;
    private MediaPlayer activePlayer;
    private Handler mainHandler;
    private AvasPlayer avasPlayer;
    private int maxStreamVolume = 15;
    private int retryAttempt = 0;

    private HandlerThread ioThread;
    private Handler io;
    /** Deadline for the poll and the wake lock, ms since epoch; extended by armWatch(). */
    private volatile long watchUntilMs;
    private int lastPolledState = -1;
    private int lastPolledPower = -1;
    private int lastDispatchedPower = -1;

    private final Runnable registerRetry = this::registerBodyworkListener;
    /** Method reference, not an anonymous class: d8 8.2.2 cannot dex those. */
    private final Runnable pollTick = this::pollAndReschedule;

    public static boolean isRunning() {
        return sRunning;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        log("=== Service onCreate START ===");
        mainHandler = new Handler(Looper.getMainLooper());
        maxStreamVolume = ((AudioManager) getSystemService(AUDIO_SERVICE))
                .getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());
        acquireWakeLock();
        avasPlayer = new AvasPlayer(new BydPermissionContext(this));
        sAvasAvailable = avasPlayer.isAvailable();
        log("AVAS player available: " + sAvasAvailable);
        // One serialised thread for every vehicle read; getPowerLevel() used to
        // run on the main looper.
        ioThread = new HandlerThread("doorsound-io");
        ioThread.start();
        io = new Handler(ioThread.getLooper());
        io.post(this::registerBodyworkListener);
        scheduleRestarter();
        sStartedAtMs = System.currentTimeMillis();
        sRunning = true;
        log("=== Service onCreate COMPLETE ===");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        sRunning = false;
        if (io != null) {
            io.removeCallbacksAndMessages(null);
        }
        unregisterBodyworkListener();
        releasePlayer();
        if (avasPlayer != null) {
            avasPlayer.stop();
        }
        releaseWakeLock();
        if (ioThread != null) {
            ioThread.quitSafely();
        }
        super.onDestroy();
    }

    // ------------------------------------------------------------ notification

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, getString(R.string.service_channel),
                NotificationManager.IMPORTANCE_LOW);
        channel.setShowBadge(false);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    private Notification buildNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        return new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.service_running))
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    // -------------------------------------------------------------- wake lock

    private synchronized void acquireWakeLock() {
        try {
            if (wakeLock == null) {
                PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "doorsound:service");
                wakeLock.setReferenceCounted(false);
            }
            wakeLock.acquire(WAKE_LOCK_TIMEOUT_MS);
        } catch (Exception e) {
            Log.w(TAG, "wake lock unavailable", e);
        }
    }

    private synchronized void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    /**
     * Called from {@link BodyworkHandler#onPowerLevelChanged(int)} and from the
     * poll. Parking re-arms the window instead of ending it: the old code released
     * the wake lock on BODYWORK_POWER_LEVEL_OFF, which is the exact moment before
     * someone locks the car and walks away -- the one event they wanted a sound for.
     */
    void onPowerLevel(int level) {
        log("Power level: " + level);
        armWatch();
    }

    /**
     * Extends the poll-and-wake-lock window. The lock stays bounded:
     * acquire(WAKE_LOCK_TIMEOUT_MS) plus an explicit release when the window
     * closes, so a killed service cannot pin the CPU.
     */
    private void armWatch() {
        watchUntilMs = System.currentTimeMillis() + WATCH_WINDOW_MS;
        acquireWakeLock();
        if (io != null) {
            io.removeCallbacks(pollTick);
            io.postDelayed(pollTick, POLL_INTERVAL_MS);
        }
    }

    /** io thread. */
    private void pollAndReschedule() {
        if (bodyworkDevice == null || bodyworkListener == null) {
            return;
        }
        if (System.currentTimeMillis() > watchUntilMs) {
            log("poll: watch window closed, pushed events only");
            releaseWakeLock();
            return;
        }
        try {
            int state = bodyworkDevice.getAutoSystemState();
            int power = bodyworkDevice.getPowerLevel();
            sLastLockRaw = state;
            sLastPower = power;
            sLastPollMs = System.currentTimeMillis();
            if (state != lastPolledState || power != lastPolledPower) {
                log("poll: lock raw=" + state + " power=" + power);
                lastPolledState = state;
                lastPolledPower = power;
            }
            // Unconditional: onAutoSystemStateChanged already drops equal values,
            // so a car that does push the event cannot fire twice.
            bodyworkListener.onAutoSystemStateChanged(state);
            if (power != lastDispatchedPower) {
                lastDispatchedPower = power;
                onPowerLevel(power);
            }
        } catch (Throwable t) {
            log("poll failed: " + t.getClass().getName() + ": " + t.getMessage());
        }
        // removeCallbacks first: fire() and onPowerLevel() also re-arm the tick,
        // and two pending ticks would double the poll rate for good.
        io.removeCallbacks(pollTick);
        io.postDelayed(pollTick, POLL_INTERVAL_MS);
    }

    // --------------------------------------------------------------- listener

    /** io thread. */
    private void registerBodyworkListener() {
        try {
            bodyworkDevice = BYDAutoBodyworkDevice.getInstance(new BydPermissionContext(this));
            log("Got bodywork device, power=" + bodyworkDevice.getPowerLevel());
            bodyworkListener = new BodyworkHandler(this);
            bodyworkDevice.registerListener(bodyworkListener);
            bodyworkListener.primeLockState(bodyworkDevice.getAutoSystemState());
            log("Listener registered OK (attempt " + (retryAttempt + 1) + ")");
            retryAttempt = 0;
            sListenerOk = true;
            armWatch();
        } catch (Throwable e) {
            // Giving up here left the app permanently deaf whenever a boot-start
            // beat BYD's own service into existence. Retry instead.
            sListenerOk = false;
            long delay = retryAttempt < RETRY_DELAYS_MS.length
                    ? RETRY_DELAYS_MS[retryAttempt]
                    : RETRY_DELAY_STEADY_MS;
            retryAttempt++;
            Log.w(TAG, "registerListener failed, retry #" + retryAttempt + " in " + delay + "ms", e);
            log("FAILED to register (attempt " + retryAttempt + "): "
                    + e.getClass().getName() + ": " + e.getMessage()
                    + " -- retrying in " + delay + "ms");
            io.removeCallbacks(registerRetry);
            io.postDelayed(registerRetry, delay);
        }
    }

    private void unregisterBodyworkListener() {
        if (bodyworkDevice != null && bodyworkListener != null) {
            try {
                bodyworkDevice.unregisterListener(bodyworkListener);
            } catch (Exception ignored) {
            }
        }
    }

    // ------------------------------------------------------------------ events

    /** Single entry point for every event; description is already localised. */
    void fire(SoundEvent event, String description) {
        armWatch();
        log("EVENT: " + event.name + " - " + description);
        setLastEvent(description);
        playSoundIfEnabled(event);
        playPatternIfEnabled(event);
    }

    String doorName(int area) {
        switch (area) {
            case BYDAutoBodyworkDevice.BODYWORK_CMD_DOOR_LEFT_FRONT:
                return getString(R.string.door_left_front);
            case BYDAutoBodyworkDevice.BODYWORK_CMD_DOOR_RIGHT_FRONT:
                return getString(R.string.door_right_front);
            case BYDAutoBodyworkDevice.BODYWORK_CMD_DOOR_LEFT_REAR:
                return getString(R.string.door_left_rear);
            case BYDAutoBodyworkDevice.BODYWORK_CMD_DOOR_RIGHT_REAR:
                return getString(R.string.door_right_rear);
            case BYDAutoBodyworkDevice.BODYWORK_CMD_DOOR_HOOD:
                return getString(R.string.event_hood);
            case BYDAutoBodyworkDevice.BODYWORK_CMD_DOOR_LUGGAGE_DOOR:
                return getString(R.string.event_trunk);
            default:
                return getString(R.string.door_unknown, area);
        }
    }

    private void playSoundIfEnabled(SoundEvent event) {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        if (!prefs.getBoolean(KEY_ENABLED, false)) {
            log("  master switch off, no interior sound");
            return;
        }
        if (!prefs.getBoolean(event.enabledKey, true)) {
            log("  " + event.enabledKey + " off, no interior sound");
            return;
        }
        String path = prefs.getString(event.pathKey, null);
        if (path == null || path.isEmpty()) {
            log("  no file selected for " + event.name);
            return;
        }
        int volume = prefs.getInt(event.volumeKey, DEFAULT_VOLUME);
        log("  playing " + new File(path).getName() + " at volume " + volume
                + "/" + maxStreamVolume);
        acquireWakeLock();
        mainHandler.post(new SoundPlayer(this, path, volume, maxStreamVolume));
    }

    private void playPatternIfEnabled(SoundEvent event) {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        if (!prefs.getBoolean(KEY_ENABLED, false)) {
            return;
        }
        if (!prefs.getBoolean(event.outsideEnabledKey, false)) {
            log("  " + event.outsideEnabledKey + " off, no AVAS pattern");
            return;
        }
        int pattern = prefs.getInt(event.outsidePatternKey, AvasPlayer.PATTERN_NONE);
        if (pattern >= 0 && avasPlayer != null && avasPlayer.isAvailable()) {
            log("  playing AVAS pattern " + pattern);
            acquireWakeLock();
            avasPlayer.play(pattern);
        }
    }

    private void setLastEvent(String event) {
        String msg = event + " @ " + android.text.format.DateFormat
                .format("HH:mm:ss", System.currentTimeMillis());
        getSharedPreferences(PREF_NAME, MODE_PRIVATE)
                .edit().putString(KEY_LAST_EVENT, msg).apply();
    }

    // ----------------------------------------------------------------- players

    void releasePlayer() {
        if (activePlayer != null) {
            try {
                activePlayer.release();
            } catch (Exception ignored) {
            }
            activePlayer = null;
        }
    }

    void setActivePlayer(MediaPlayer mp) {
        activePlayer = mp;
    }

    MediaPlayer getActivePlayer() {
        return activePlayer;
    }

    // ----------------------------------------------------------------- logging

    /**
     * Goes to logcat, which is what `adb logcat -d | grep DoorSound` needs, plus
     * an app-private external file for users reporting issues. The old version
     * wrote only to /sdcard/Download, where WRITE_EXTERNAL_STORAGE is denied on
     * API 29 -- so the file never existed and none of the 21 call sites produced
     * any diagnostics at all.
     */
    void log(String msg) {
        Log.d(TAG, msg);
        try {
            File dir = getExternalFilesDir(null);
            if (dir == null) {
                return;
            }
            File logFile = new File(dir, "doorsound-log.txt");
            // ponytail: truncate rather than rotate; upgrade to a rolling file
            // only if someone actually needs the older half.
            if (logFile.length() > LOG_MAX_BYTES) {
                logFile.delete();
            }
            String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
                    .format(new Date());
            FileWriter fw = new FileWriter(logFile, true);
            fw.write(ts + " " + msg + "\n");
            fw.close();
        } catch (Exception ignored) {
        }
    }

    // ---------------------------------------------------------------- restarter

    private void scheduleRestarter() {
        try {
            JobScheduler js = (JobScheduler) getSystemService(JOB_SCHEDULER_SERVICE);
            JobInfo job = new JobInfo.Builder(RestarterJobService.JOB_ID,
                    new ComponentName(this, RestarterJobService.class))
                    .setPeriodic(RESTART_PERIOD_MS)
                    .setPersisted(true)
                    .build();
            js.schedule(job);
            log("Restarter scheduled every " + (RESTART_PERIOD_MS / 60000) + " min");
        } catch (Exception e) {
            Log.e(TAG, "Failed to schedule restarter", e);
        }
    }
}
