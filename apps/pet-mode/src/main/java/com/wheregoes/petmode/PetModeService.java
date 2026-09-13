package com.wheregoes.petmode;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Intent;
import android.hardware.bydauto.bodywork.BYDAutoBodyworkDevice;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

public class PetModeService extends Service implements
        VehicleStateMonitor.Listener, ClimateMonitor.Listener {

    private static final String TAG = "PetModeService";
    private static final String CHANNEL_ID = "pet_mode_service";
    private static final int NOTIFICATION_ID = 2;
    static final String PREF_NAME = "pet_mode_prefs";
    static final String KEY_ENABLED = "enabled";
    static final String KEY_PET_NAME = "pet_name";
    static final String KEY_AVATAR = "avatar";
    static final String KEY_TEMP_UNIT = "temp_unit";
    static final String KEY_DARK_MODE = "dark_mode";
    static final String KEY_DISPLAY_MODE = "display_mode";
    static final String UNIT_CELSIUS = "C";
    static final String UNIT_FAHRENHEIT = "F";
    static final String DISPLAY_AUTO = "auto";
    static final String DISPLAY_BEACON = "beacon";
    static final String DISPLAY_INTERIOR = "interior";

    /** Released on power-off and refreshed while active; never held forever. */
    private static final long WAKE_LOCK_TIMEOUT_MS = 30 * 60 * 1000L;
    /** API 29 clamps periodic jobs to 15 minutes. */
    private static final long RESTART_PERIOD_MS = 15 * 60 * 1000L;

    /**
     * Below this the 12V system is reported low. The scale is undocumented, so
     * only small integers are treated as a level; anything that looks like a
     * percentage is ignored rather than guessed at.
     */
    private static final int VOLTAGE_LOW_MAX = 1;

    private static volatile boolean sRunning = false;

    private PowerManager.WakeLock wakeLock;
    private VehicleStateMonitor vehicleMonitor;
    private ClimateMonitor climateMonitor;
    private Handler handler;
    private long startTime;

    private int acSetTemp = Integer.MIN_VALUE;
    private int outsideTemp = Integer.MIN_VALUE;
    private boolean acOn = false;
    private boolean climateAvailable = false;
    private boolean locked = false;
    private boolean anyDoorOpen = false;
    private int powerLevel = -1;
    private int batteryLevel = -1;
    private boolean voltageLow = false;

    private StateCallback stateCallback;

    interface StateCallback {
        void onStateUpdated();
    }

    static boolean isRunning() { return sRunning; }

    private final IBinder binder = new PetModeBinder(this);

    @Override
    public IBinder onBind(Intent intent) { return binder; }

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        startTime = System.currentTimeMillis();

        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());
        acquireWakeLock();

        vehicleMonitor = new VehicleStateMonitor();
        vehicleMonitor.start(this, this);

        climateMonitor = new ClimateMonitor(this);
        climateMonitor.start(this);

        scheduleRestarter();
        sRunning = true;
        Log.i(TAG, "Pet Mode service started");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        sRunning = false;
        if (vehicleMonitor != null) vehicleMonitor.stop();
        if (climateMonitor != null) climateMonitor.stop();
        releaseWakeLock();
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
        Log.i(TAG, "Pet Mode service stopped");
    }

    void setStateCallback(StateCallback cb) { stateCallback = cb; }

    int getAcSetTemp() { return acSetTemp; }
    int getOutsideTemp() { return outsideTemp; }
    boolean isAcOn() { return acOn; }
    boolean isClimateAvailable() { return climateAvailable; }
    boolean isLocked() { return locked; }
    boolean isAnyDoorOpen() { return anyDoorOpen; }
    int getPowerLevel() { return powerLevel; }
    int getBatteryLevel() { return batteryLevel; }
    boolean isVoltageLow() { return voltageLow; }
    long getActiveMillis() { return System.currentTimeMillis() - startTime; }
    ClimateMonitor getClimateMonitor() { return climateMonitor; }

    @Override
    public void onSetTempChanged(int tempCelsius) {
        acSetTemp = tempCelsius;
        climateAvailable = true;
        notifyStateChanged();
    }

    @Override
    public void onOutsideTempChanged(int tempCelsius) {
        outsideTemp = tempCelsius;
        notifyStateChanged();
    }

    @Override
    public void onAcStatusChanged(boolean on) {
        acOn = on;
        notifyStateChanged();
    }

    @Override
    public void onClimateUnavailable() {
        climateAvailable = false;
        acSetTemp = Integer.MIN_VALUE;
        notifyStateChanged();
    }

    @Override
    public void onDoorStateChanged(int area, boolean open) {
        anyDoorOpen = vehicleMonitor.isAnyDoorOpen();
        notifyStateChanged();
    }

    @Override
    public void onLockStateChanged(boolean isLocked) {
        locked = isLocked;
        notifyStateChanged();
    }

    @Override
    public void onPowerLevelChanged(int level) {
        powerLevel = level;
        if (level == BYDAutoBodyworkDevice.BODYWORK_POWER_LEVEL_OFF) {
            releaseWakeLock();
        } else {
            acquireWakeLock();
        }
        notifyStateChanged();
    }

    @Override
    public void onBatteryChanged(int level) {
        batteryLevel = level;
        notifyStateChanged();
    }

    @Override
    public void onVoltageLevelChanged(int level) {
        // Treated as a discrete level, not written into batteryLevel: that field
        // holds the traction battery percentage and the two are different things.
        boolean low = level >= 0 && level <= VOLTAGE_LOW_MAX;
        if (low != voltageLow) {
            voltageLow = low;
            Log.i(TAG, "12V low=" + low + " (raw " + level + ")");
            notifyStateChanged();
        }
    }

    private void notifyStateChanged() {
        handler.post(() -> { if (stateCallback != null) stateCallback.onStateUpdated(); });
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, getString(R.string.app_name), NotificationManager.IMPORTANCE_LOW);
        channel.setShowBadge(false);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    private Notification buildNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_text))
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    private void acquireWakeLock() {
        try {
            if (wakeLock == null) {
                PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "petmode:service");
                wakeLock.setReferenceCounted(false);
            }
            wakeLock.acquire(WAKE_LOCK_TIMEOUT_MS);
        } catch (Exception e) {
            Log.w(TAG, "wake lock unavailable", e);
        }
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    private void scheduleRestarter() {
        try {
            JobScheduler js = (JobScheduler) getSystemService(JOB_SCHEDULER_SERVICE);
            JobInfo job = new JobInfo.Builder(RestarterJobService.JOB_ID,
                    new ComponentName(this, RestarterJobService.class))
                    .setPeriodic(RESTART_PERIOD_MS)
                    .setPersisted(true)
                    .build();
            js.schedule(job);
            Log.i(TAG, "Restarter scheduled every " + (RESTART_PERIOD_MS / 60000) + " min");
        } catch (Exception e) {
            Log.e(TAG, "Failed to schedule restarter", e);
        }
    }
}
