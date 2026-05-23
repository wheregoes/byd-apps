package com.wheregoes.camping;

import android.app.Activity;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.view.animation.TranslateAnimation;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Calendar;
import java.util.Locale;

public class MainActivity extends Activity implements CampingService.StateCallback {

    private CampingService service;
    private boolean bound = false;
    private Handler handler;

    private TextView clockText;
    private TextView outsideTempText;
    private TextView dischargeValue;
    private TextView dischargeUnit;
    private TextView dischargeLabel;
    private TextView insideTempText;
    private TextView batteryPercent;
    private TextView remainingText;
    private View batteryBarFill;
    private TextView alarmThresholdText;
    private View statusItemAc;
    private TextView statusAc;
    private TextView statusDoors;
    private TextView statusBattery;
    private TextView statusTimer;
    private TextView statusEnergy;
    private ImageView statusIconDoors;
    private View btnAcToggle;
    private ImageView btnAcIcon;
    private TextView btnAcText;
    private TextView btnTempDown;
    private TextView btnTempUp;
    private TextView ctrlTempDisplay;
    private View btnWindows;
    private View btnDoorAlarm;
    private ImageView btnAlarmIcon;
    private TextView btnAlarmText;
    private View btnWakeAlarm;
    private TextView btnWakeText;
    private View alarmOverlay;
    private ImageView alarmOverlayIcon;
    private TextView alarmOverlayText;
    private View alarmBtnSnooze;
    private View alarmBtnDismiss;
    private View exitBtn;
    private View exitScrim;
    private View settingsBtn;
    private int currentAlarmType = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handler = new Handler(Looper.getMainLooper());
        setContentView(R.layout.activity_main);
        setupImmersiveMode();
        applyBrightness();
        bindViews();
        startAuroraAnimation();
        startClockUpdate();
        startService();
    }

    @Override
    protected void onResume() {
        super.onResume();
        setupImmersiveMode();
        applyBrightness();
        if (bound && service != null) updateDisplay();
    }

    @Override
    protected void onDestroy() {
        if (bound) unbindService(connection);
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        showExitDialog();
    }

    @Override
    public void onStateUpdated() {
        handler.post(this::updateDisplay);
    }

    private void bindViews() {
        clockText = findViewById(R.id.clock_text);
        outsideTempText = findViewById(R.id.outside_temp_text);
        dischargeValue = findViewById(R.id.discharge_value);
        dischargeUnit = findViewById(R.id.discharge_unit);
        dischargeLabel = findViewById(R.id.discharge_label);
        insideTempText = findViewById(R.id.inside_temp_text);
        batteryPercent = findViewById(R.id.battery_percent);
        remainingText = findViewById(R.id.remaining_text);
        batteryBarFill = findViewById(R.id.battery_bar_fill);
        alarmThresholdText = findViewById(R.id.alarm_threshold_text);
        statusItemAc = findViewById(R.id.status_item_ac);
        statusAc = findViewById(R.id.status_ac);
        statusDoors = findViewById(R.id.status_doors);
        statusBattery = findViewById(R.id.status_battery);
        statusTimer = findViewById(R.id.status_timer);
        statusEnergy = findViewById(R.id.status_energy);
        statusIconDoors = findViewById(R.id.status_icon_doors);
        btnAcToggle = findViewById(R.id.btn_ac_toggle);
        btnAcIcon = findViewById(R.id.btn_ac_icon);
        btnAcText = findViewById(R.id.btn_ac_text);
        btnTempDown = findViewById(R.id.btn_temp_down);
        btnTempUp = findViewById(R.id.btn_temp_up);
        ctrlTempDisplay = findViewById(R.id.ctrl_temp_display);
        btnWindows = findViewById(R.id.btn_windows);
        btnDoorAlarm = findViewById(R.id.btn_door_alarm);
        btnAlarmIcon = findViewById(R.id.btn_alarm_icon);
        btnAlarmText = findViewById(R.id.btn_alarm_text);
        btnWakeAlarm = findViewById(R.id.btn_wake_alarm);
        btnWakeText = findViewById(R.id.btn_wake_text);
        alarmOverlay = findViewById(R.id.alarm_overlay);
        alarmOverlayIcon = findViewById(R.id.alarm_overlay_icon);
        alarmOverlayText = findViewById(R.id.alarm_overlay_text);
        alarmBtnSnooze = findViewById(R.id.alarm_btn_snooze);
        alarmBtnDismiss = findViewById(R.id.alarm_btn_dismiss);
        exitBtn = findViewById(R.id.exit_btn);
        exitScrim = findViewById(R.id.exit_scrim);
        settingsBtn = findViewById(R.id.settings_btn);

        settingsBtn.setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));

        exitBtn.setOnClickListener(v -> showExitDialog());

        findViewById(R.id.sheet_btn_stay).setOnClickListener(v ->
                exitScrim.setVisibility(View.GONE));
        findViewById(R.id.sheet_btn_exit).setOnClickListener(v -> doExit());
        exitScrim.setOnClickListener(v -> exitScrim.setVisibility(View.GONE));

        btnAcToggle.setOnClickListener(v -> toggleAc());
        btnTempDown.setOnClickListener(v -> adjustTemp(-1));
        btnTempUp.setOnClickListener(v -> adjustTemp(1));
        btnWindows.setOnClickListener(v -> showWindowDialog());
        btnDoorAlarm.setOnClickListener(v -> toggleDoorAlarm());
        btnWakeAlarm.setOnClickListener(v -> showWakeAlarmDialog());

        alarmBtnSnooze.setOnClickListener(v -> snoozeAlarm());
        alarmBtnDismiss.setOnClickListener(v -> dismissAlarm());

        int threshold = getSharedPreferences(CampingService.PREF_NAME, MODE_PRIVATE)
                .getInt(CampingService.KEY_BATTERY_ALARM_THRESHOLD, 20);
        alarmThresholdText.setText(getString(R.string.battery_alarm_at, threshold));
    }

    private void updateDisplay() {
        if (service == null) return;

        SharedPreferences prefs = getSharedPreferences(CampingService.PREF_NAME, MODE_PRIVATE);
        boolean useFahrenheit = CampingService.UNIT_FAHRENHEIT.equals(
                prefs.getString(CampingService.KEY_TEMP_UNIT, getDefaultUnit()));
        String unitLabel = useFahrenheit ? "°F" : "°C";

        int outsideTempC = service.getOutsideTemp();
        if (outsideTempC != Integer.MIN_VALUE) {
            int displayOutside = useFahrenheit ? (outsideTempC * 9 / 5) + 32 : outsideTempC;
            outsideTempText.setText(getString(R.string.outside_temp_pill, displayOutside, unitLabel));
        } else {
            outsideTempText.setText(getString(R.string.outside_temp_pill_unknown));
        }

        int acSetTempC = service.getAcSetTemp();
        if (acSetTempC != Integer.MIN_VALUE) {
            int displayTemp = useFahrenheit ? (acSetTempC * 9 / 5) + 32 : acSetTempC;
            insideTempText.setText(displayTemp + unitLabel + " inside");
            ctrlTempDisplay.setText(displayTemp + unitLabel);
        } else {
            insideTempText.setText("--" + unitLabel + " inside");
            ctrlTempDisplay.setText("--" + unitLabel);
        }

        double watts = service.getDischargeWatts();
        if (watts > 0) {
            String oldText = dischargeValue.getText().toString();
            String newText = String.valueOf((int) Math.round(watts));
            dischargeValue.setText(newText);
            if (!oldText.equals(newText) && !oldText.equals("--")) animateValuePulse(dischargeValue);
        } else {
            dischargeValue.setText("--");
        }

        int battery = service.getBatteryLevel();
        if (battery >= 0) {
            batteryPercent.setText(battery + "%");
            statusBattery.setText(getString(R.string.status_battery, battery));
            updateBatteryBar(battery);
        } else {
            batteryPercent.setText("--%");
            statusBattery.setText("--");
        }

        long remainingMin = service.getRemainingMinutes();
        if (remainingMin > 0) {
            long hours = remainingMin / 60;
            long mins = remainingMin % 60;
            remainingText.setText(getString(R.string.battery_remaining, (int) hours, (int) mins));
        } else if (watts > 0) {
            remainingText.setText(getString(R.string.battery_remaining_calculating));
        } else {
            remainingText.setText(getString(R.string.discharge_waiting));
        }

        boolean acOn = service.isAcOn();
        int power = service.getPowerLevel();
        if (power == 0) {
            statusAc.setText(R.string.status_car_off);
            statusItemAc.setBackgroundResource(0);
        } else if (acOn) {
            statusAc.setText(R.string.status_ac_on);
            statusItemAc.setBackgroundResource(R.drawable.glass_status_item_active);
        } else {
            statusAc.setText(R.string.status_ac_off);
            statusItemAc.setBackgroundResource(0);
        }

        btnAcToggle.setBackgroundResource(acOn
                ? R.drawable.glass_control_btn_active : R.drawable.glass_control_btn);
        btnAcText.setText(acOn ? R.string.ctrl_ac_on : R.string.ctrl_ac_off);

        boolean anyDoorOpen = service.isAnyDoorOpen();
        if (anyDoorOpen) {
            statusDoors.setText(R.string.status_doors_open);
            statusIconDoors.setImageResource(R.drawable.ic_unlock);
        } else {
            statusDoors.setText(R.string.status_doors_closed);
            statusIconDoors.setImageResource(R.drawable.ic_lock);
        }

        long millis = service.getActiveMillis();
        long sMin = (millis / 60000) % 60;
        long sHours = millis / 3600000;
        statusTimer.setText(getString(R.string.status_active,
                String.format(Locale.US, "%02dh %02dm", sHours, sMin)));

        DischargeMonitor dm = service.getDischargeMonitor();
        if (dm != null) {
            double kwh = dm.getTotalKwhUsed();
            float cost = prefs.getFloat(CampingService.KEY_ENERGY_COST, 0f);
            if (cost > 0 && kwh > 0) {
                String costStr = String.format(Locale.getDefault(), "R$ %.2f", kwh * cost);
                statusEnergy.setText(getString(R.string.status_energy_cost, kwh, costStr));
            } else {
                statusEnergy.setText(getString(R.string.status_energy, kwh));
            }
        }

        DoorAlarmManager dam = service.getDoorAlarmManager();
        if (dam != null) {
            if (dam.isArmed()) {
                btnDoorAlarm.setBackgroundResource(R.drawable.glass_control_btn_active);
                btnAlarmText.setText(R.string.ctrl_alarm_armed);
            } else {
                btnDoorAlarm.setBackgroundResource(R.drawable.glass_control_btn);
                btnAlarmText.setText(R.string.ctrl_alarm_arm);
            }
            if (dam.isAlarmActive()) showAlarmOverlay(0);
        }

        BatteryAlarmManager bam = service.getBatteryAlarmManager();
        if (bam != null && bam.isAlarmActive()) {
            showAlarmOverlay(1);
        }

        WakeAlarmManager wam = service.getWakeAlarmManager();
        if (wam != null) {
            if (wam.isRinging()) {
                showAlarmOverlay(2);
            } else if (wam.isEnabled()) {
                btnWakeText.setText(String.format(Locale.US, getString(R.string.ctrl_wake_set),
                        wam.getAlarmHour(), wam.getAlarmMinute()));
                btnWakeAlarm.setBackgroundResource(R.drawable.glass_control_btn_active);
            } else {
                btnWakeText.setText(R.string.ctrl_wake);
                btnWakeAlarm.setBackgroundResource(R.drawable.glass_control_btn);
            }
        }
    }

    private void updateBatteryBar(int percent) {
        View parent = (View) batteryBarFill.getParent();
        int parentWidth = parent.getWidth();
        if (parentWidth <= 0) return;
        int fillWidth = (int) (parentWidth * percent / 100.0);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) batteryBarFill.getLayoutParams();
        lp.width = fillWidth;
        batteryBarFill.setLayoutParams(lp);

        int threshold = getSharedPreferences(CampingService.PREF_NAME, MODE_PRIVATE)
                .getInt(CampingService.KEY_BATTERY_ALARM_THRESHOLD, 20);
        batteryBarFill.setBackgroundResource(percent <= threshold
                ? R.drawable.battery_bar_fill_low : R.drawable.battery_bar_fill);
    }

    private void toggleAc() {
        if (service == null || service.getAcController() == null) return;
        if (service.isAcOn()) {
            service.getAcController().turnOff();
        } else {
            service.getAcController().turnOn();
        }
    }

    private void adjustTemp(int delta) {
        if (service == null || service.getAcController() == null) return;
        int current = service.getAcSetTemp();
        if (current == Integer.MIN_VALUE) current = 24;
        int newTemp = Math.max(17, Math.min(33, current + delta));
        service.getAcController().setTemperature(newTemp);
    }

    private void showWindowDialog() {
        if (service == null || service.getWindowController() == null) return;
        WindowController wc = service.getWindowController();

        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle(R.string.window_title);
        builder.setItems(new CharSequence[]{
                getString(R.string.window_open_all),
                getString(R.string.window_close_all),
                getString(R.string.window_cancel)
        }, (dialog, which) -> {
            if (which == 0) wc.openAllWindows();
            else if (which == 1) wc.closeAllWindows();
        });
        builder.show();
    }

    private void toggleDoorAlarm() {
        if (service == null || service.getDoorAlarmManager() == null) return;
        DoorAlarmManager dam = service.getDoorAlarmManager();
        if (dam.isArmed()) {
            dam.disarm();
        } else {
            dam.arm();
        }
        updateDisplay();
    }

    private void showWakeAlarmDialog() {
        if (service == null) return;
        WakeAlarmManager wam = service.getWakeAlarmManager();
        if (wam == null) return;

        if (wam.isEnabled()) {
            wam.clearAlarm();
            updateDisplay();
            return;
        }

        Calendar now = Calendar.getInstance();
        TimePickerDialog tpd = new TimePickerDialog(this,
                android.R.style.Theme_Material_Dialog,
                (view, hourOfDay, minute) -> {
                    wam.setAlarm(hourOfDay, minute);
                    updateDisplay();
                },
                now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), true);
        tpd.show();
    }

    private void showAlarmOverlay(int type) {
        currentAlarmType = type;
        alarmOverlay.setVisibility(View.VISIBLE);
        switch (type) {
            case 0:
                alarmOverlayText.setText(R.string.alert_door_open);
                alarmOverlayIcon.setImageResource(R.drawable.ic_unlock);
                alarmBtnSnooze.setVisibility(View.VISIBLE);
                break;
            case 1:
                int soc = service != null ? service.getBatteryLevel() : 0;
                alarmOverlayText.setText(getString(R.string.alert_low_battery, soc));
                alarmOverlayIcon.setImageResource(R.drawable.ic_battery);
                alarmBtnSnooze.setVisibility(View.GONE);
                break;
            case 2:
                alarmOverlayText.setText(R.string.alert_wake_alarm);
                alarmOverlayIcon.setImageResource(R.drawable.ic_alarm);
                alarmBtnSnooze.setVisibility(View.GONE);
                break;
        }
    }

    private void snoozeAlarm() {
        if (service == null) return;
        if (currentAlarmType == 0 && service.getDoorAlarmManager() != null) {
            service.getDoorAlarmManager().snooze();
        }
        alarmOverlay.setVisibility(View.GONE);
    }

    private void dismissAlarm() {
        if (service == null) return;
        switch (currentAlarmType) {
            case 0:
                if (service.getDoorAlarmManager() != null) service.getDoorAlarmManager().dismiss();
                break;
            case 1:
                if (service.getBatteryAlarmManager() != null) service.getBatteryAlarmManager().dismiss();
                break;
            case 2:
                if (service.getWakeAlarmManager() != null) service.getWakeAlarmManager().dismiss();
                break;
        }
        alarmOverlay.setVisibility(View.GONE);
    }

    private void showExitDialog() {
        exitScrim.setVisibility(View.VISIBLE);
    }

    private void doExit() {
        if (service != null && service.getSessionLogger() != null) {
            service.getSessionLogger().saveSummary();
        }
        stopService(new Intent(this, CampingService.class));
        getSharedPreferences(CampingService.PREF_NAME, MODE_PRIVATE)
                .edit().putBoolean(CampingService.KEY_ENABLED, false).apply();
        finish();
    }

    private String getDefaultUnit() {
        String country = Locale.getDefault().getCountry();
        return ("US".equals(country) || "LR".equals(country) || "MM".equals(country))
                ? CampingService.UNIT_FAHRENHEIT : CampingService.UNIT_CELSIUS;
    }

    private void setupImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void applyBrightness() {
        SharedPreferences prefs = getSharedPreferences(CampingService.PREF_NAME, MODE_PRIVATE);
        int brightness = prefs.getInt(CampingService.KEY_SCREEN_BRIGHTNESS, 80);
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = brightness / 255f;
        getWindow().setAttributes(lp);
        try {
            Settings.System.putInt(getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS_MODE, 0);
            Settings.System.putInt(getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS, brightness);
        } catch (Exception ignored) {}
    }

    private void startAuroraAnimation() {
        View orb1 = findViewById(R.id.aurora_orb1);
        View orb2 = findViewById(R.id.aurora_orb2);
        View orb3 = findViewById(R.id.aurora_orb3);
        animateOrb(orb1, 40f, 27f, 6000);
        animateOrb(orb2, -40f, 30f, 8000);
        animateOrb(orb3, 30f, -25f, 10000);
    }

    private void animateOrb(View orb, float dx, float dy, int dur) {
        TranslateAnimation drift = new TranslateAnimation(0, dx, 0, dy);
        drift.setDuration(dur);
        drift.setRepeatMode(Animation.REVERSE);
        drift.setRepeatCount(Animation.INFINITE);
        drift.setInterpolator(new AccelerateInterpolator(0.3f));
        orb.startAnimation(drift);
    }

    private void animateValuePulse(View v) {
        ScaleAnimation pulse = new ScaleAnimation(1f, 1.05f, 1f, 1.05f,
                Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        pulse.setDuration(150);
        pulse.setRepeatMode(Animation.REVERSE);
        pulse.setRepeatCount(1);
        v.startAnimation(pulse);
    }

    private void startClockUpdate() {
        scheduleClockTick();
    }

    private void scheduleClockTick() {
        handler.postDelayed(() -> {
            if (isFinishing()) return;
            Calendar now = Calendar.getInstance();
            clockText.setText(String.format(Locale.US, "%02d:%02d",
                    now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE)));

            if (service != null) {
                long millis = service.getActiveMillis();
                long sMin = (millis / 60000) % 60;
                long sHours = millis / 3600000;
                statusTimer.setText(getString(R.string.status_active,
                        String.format(Locale.US, "%02dh %02dm", sHours, sMin)));
            }
            scheduleClockTick();
        }, 15000);

        Calendar now = Calendar.getInstance();
        clockText.setText(String.format(Locale.US, "%02d:%02d",
                now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE)));
    }

    private void startService() {
        SharedPreferences prefs = getSharedPreferences(CampingService.PREF_NAME, MODE_PRIVATE);
        prefs.edit().putBoolean(CampingService.KEY_ENABLED, true).apply();
        Intent svc = new Intent(this, CampingService.class);
        startForegroundService(svc);
        bindService(svc, connection, Context.BIND_AUTO_CREATE);
    }

    private final ServiceConnection connection = new CampingServiceConnection(this);

    void onServiceBound(CampingService svc) {
        service = svc;
        service.setStateCallback(this);
        bound = true;

        service.getDoorAlarmManager().setListener(new DoorAlarmManager.Listener() {
            @Override public void onAlarmTriggered() { handler.post(() -> showAlarmOverlay(0)); }
            @Override public void onAlarmDismissed() { handler.post(() -> alarmOverlay.setVisibility(View.GONE)); }
            @Override public void onArmedChanged(boolean armed) { handler.post(() -> updateDisplay()); }
        });

        service.getBatteryAlarmManager().setListener(new BatteryAlarmManager.Listener() {
            @Override public void onLowBatteryAlarm(int soc, int threshold) { handler.post(() -> showAlarmOverlay(1)); }
            @Override public void onLowBatteryDismissed() { handler.post(() -> alarmOverlay.setVisibility(View.GONE)); }
        });

        service.getWakeAlarmManager().setListener(new WakeAlarmManager.Listener() {
            @Override public void onWakeAlarmTriggered() { handler.post(() -> showAlarmOverlay(2)); }
            @Override public void onWakeAlarmDismissed() { handler.post(() -> alarmOverlay.setVisibility(View.GONE)); }
            @Override public void onWakeAlarmSet(int h, int m) { handler.post(() -> updateDisplay()); }
            @Override public void onWakeAlarmCleared() { handler.post(() -> updateDisplay()); }
        });

        updateDisplay();
    }

    void onServiceUnbound() {
        bound = false;
        service = null;
    }
}
