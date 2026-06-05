package com.wheregoes.camping;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

public class CampingService extends Service implements
        VehicleStateMonitor.Listener, ClimateMonitor.Listener, DischargeMonitor.Listener {

    private static final String TAG = "CampingService";
    private static final String CHANNEL_ID = "camping_service";
    private static final int NOTIFICATION_ID = 3;
    static final String PREF_NAME = "camping_prefs";
    static final String KEY_ENABLED = "enabled";
    static final String KEY_TEMP_UNIT = "temp_unit";
    static final String KEY_DARK_MODE = "dark_mode";
    static final String KEY_BATTERY_ALARM_THRESHOLD = "battery_alarm_threshold";
    static final String KEY_ENERGY_COST = "energy_cost";
    static final String KEY_SCREEN_BRIGHTNESS = "screen_brightness";
    static final String UNIT_CELSIUS = "C";
    static final String UNIT_FAHRENHEIT = "F";

    private static volatile boolean sRunning = false;

    private PowerManager.WakeLock wakeLock;
    private VehicleStateMonitor vehicleMonitor;
    private ClimateMonitor climateMonitor;
    private DischargeMonitor dischargeMonitor;
    private DoorAlarmManager doorAlarmManager;
    private BatteryAlarmManager batteryAlarmManager;
    private WakeAlarmManager wakeAlarmManager;
    private SessionLogger sessionLogger;
    private AcController acController;
    private WindowController windowController;
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
    private double dischargeWatts = 0;
    private long remainingMinutes = -1;

    private StateCallback stateCallback;

    interface StateCallback {
        void onStateUpdated();
    }

    static boolean isRunning() { return sRunning; }

    private final IBinder binder = new CampingBinder(this);

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

        dischargeMonitor = new DischargeMonitor(this);
        dischargeMonitor.start(vehicleMonitor.getStatisticDevice(), this);

        doorAlarmManager = new DoorAlarmManager(this);
        batteryAlarmManager = new BatteryAlarmManager();
        wakeAlarmManager = new WakeAlarmManager();

        int threshold = getSharedPreferences(PREF_NAME, MODE_PRIVATE)
                .getInt(KEY_BATTERY_ALARM_THRESHOLD, 20);
        batteryAlarmManager.setThreshold(threshold);

        sessionLogger = new SessionLogger(this, dischargeMonitor, climateMonitor);
        sessionLogger.start();

        if (climateMonitor.getAcDevice() != null) {
            acController = new AcController(climateMonitor.getAcDevice());
        }
        if (vehicleMonitor.getDevice() != null) {
            windowController = new WindowController(vehicleMonitor.getDevice());
        }

        scheduleRestarter();
        sRunning = true;
        Log.i(TAG, "Camping service started");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        sRunning = false;
        if (sessionLogger != null) {
            sessionLogger.saveSummary();
            sessionLogger.stop();
        }
        if (vehicleMonitor != null) vehicleMonitor.stop();
        if (climateMonitor != null) climateMonitor.stop();
        if (dischargeMonitor != null) dischargeMonitor.stop();
        if (doorAlarmManager != null) doorAlarmManager.destroy();
        if (batteryAlarmManager != null) batteryAlarmManager.destroy();
        if (wakeAlarmManager != null) wakeAlarmManager.destroy();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
        Log.i(TAG, "Camping service stopped");
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
    double getDischargeWatts() { return dischargeWatts; }
    long getRemainingMinutes() { return remainingMinutes; }
    long getActiveMillis() { return System.currentTimeMillis() - startTime; }
    DoorAlarmManager getDoorAlarmManager() { return doorAlarmManager; }
    BatteryAlarmManager getBatteryAlarmManager() { return batteryAlarmManager; }
    WakeAlarmManager getWakeAlarmManager() { return wakeAlarmManager; }
    AcController getAcController() { return acController; }
    WindowController getWindowController() { return windowController; }
    DischargeMonitor getDischargeMonitor() { return dischargeMonitor; }
    SessionLogger getSessionLogger() { return sessionLogger; }

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
        notifyStateChanged();
    }

    @Override
    public void onDoorStateChanged(int area, boolean open) {
        anyDoorOpen = vehicleMonitor.isAnyDoorOpen();
        if (open && doorAlarmManager != null) {
            doorAlarmManager.onDoorOpened(area);
        }
        notifyStateChanged();
    }

    @Override
    public void onLockStateChanged(boolean isLocked) {
        locked = isLocked;
        notifyStateChanged();
    }

    @Override
    public void onAcStateChanged(boolean running) {
        acOn = running;
        notifyStateChanged();
    }

    @Override
    public void onPowerLevelChanged(int level) {
        powerLevel = level;
        notifyStateChanged();
    }

    @Override
    public void onBatteryChanged(int level) {
        batteryLevel = level;
        if (batteryAlarmManager != null) batteryAlarmManager.checkSoc(level);
        notifyStateChanged();
    }

    @Override
    public void onDischargeRateChanged(double watts) {
        dischargeWatts = watts;
        notifyStateChanged();
    }

    @Override
    public void onRemainingTimeChanged(long minutes) {
        remainingMinutes = minutes;
        notifyStateChanged();
    }

    @Override
    public void onSocChanged(int socPercent) {
        batteryLevel = socPercent;
        if (batteryAlarmManager != null) batteryAlarmManager.checkSoc(socPercent);
        notifyStateChanged();
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
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "camping:service");
        wakeLock.acquire();
    }

    private void scheduleRestarter() {
        try {
            JobScheduler js = (JobScheduler) getSystemService(JOB_SCHEDULER_SERVICE);
            JobInfo job = new JobInfo.Builder(RestarterJobService.JOB_ID,
                    new ComponentName(this, RestarterJobService.class))
                    .setOverrideDeadline(60000)
                    .setPersisted(true)
                    .build();
            js.schedule(job);
        } catch (Exception ignored) {}
    }
}
