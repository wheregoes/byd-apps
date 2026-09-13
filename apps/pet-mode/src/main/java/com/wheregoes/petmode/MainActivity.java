package com.wheregoes.petmode;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.app.Activity;
import android.graphics.LinearGradient;
import android.graphics.PorterDuff;
import android.graphics.Shader;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.transition.Fade;
import android.transition.TransitionManager;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.ScaleAnimation;
import android.view.animation.TranslateAnimation;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;
import java.util.Random;

public class MainActivity extends Activity implements PetModeService.StateCallback {

    private static final String TAG = "CabinUI";

    /** How long a touch on the beacon returns to the interior view. */
    private static final long BEACON_SNOOZE_MS = 60_000L;

    private PetModeService service;
    private boolean bound = false;
    private Handler handler;
    private final Random random = new Random();

    private FrameLayout rootLayout;
    private View auroraBg;
    private FrameLayout pawContainer;
    private FrameLayout avatarDisc;
    private ImageView avatarView;
    private TextView tempText;
    private TextView tempUnit;
    private TextView acInfoText;
    private TextView outsideTempText;
    private ImageView outsideTempIcon;
    private TextView messageText;
    private TextView subMessageText;
    private View statusCapsule;
    private View statusItemAc;
    private TextView statusAc;
    private TextView statusDoors;
    private TextView statusTimer;
    private TextView statusBattery;
    private ImageView statusIconAc;
    private ImageView statusIconDoors;
    private ImageView statusIconBattery;
    private ImageView statusIconTimer;
    private View safetyAlert;
    private TextView safetyAlertText;
    private View doorAlert;
    private View settingsBtn;
    private ImageView settingsIcon;
    private View exitBtn;
    private View exitScrim;
    private View exitSheet;
    private View orb1;
    private View orb2;
    private View orb3;

    private LinearLayout beaconRoot;
    private TextView beaconTemp;
    private TextView beaconUnit;
    private TextView beaconMessage;
    private View beaconStatus;
    private TextView beaconAc;
    private TextView beaconDoors;
    private TextView beaconOutside;
    private ValueAnimator beaconPulse;

    private int originalBrightnessMode = -1;
    private int originalBrightness = -1;
    private boolean isDarkMode = false;
    private boolean beaconActive = false;
    /** Set while a touch has temporarily forced the interior view. */
    private boolean beaconSnoozed = false;

    private final Runnable endBeaconSnooze = this::clearBeaconSnooze;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handler = new Handler(Looper.getMainLooper());
        loadTheme();
        setContentView(R.layout.activity_main);
        setupImmersiveMode();
        enforceMaxBrightness();
        bindViews();
        applyTheme();
        startPawAnimation();
        startAvatarAnimation();
        startAuroraAnimation();
        startTimerUpdate();
        startService();
    }

    @Override
    protected void onResume() {
        super.onResume();
        setupImmersiveMode();
        if (bound && service != null) {
            loadTheme();
            applyTheme();
            updateDisplay();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopBeaconPulse();
        handler.removeCallbacks(endBeaconSnooze);
    }

    @Override
    protected void onDestroy() {
        if (bound) {
            unbindService(connection);
        }
        restoreBrightness();
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

    // ------------------------------------------------------------------- colors

    private int fgPrimary() {
        return getColor(isDarkMode ? R.color.fg_dark_primary : R.color.fg_primary);
    }

    private int fgSecondary() {
        return getColor(isDarkMode ? R.color.fg_dark_secondary : R.color.fg_secondary);
    }

    private int iconTint() {
        return getColor(isDarkMode ? R.color.fg_dark_primary : R.color.slate_700);
    }

    // -------------------------------------------------------------------- views

    private void bindViews() {
        rootLayout = findViewById(R.id.root_layout);
        auroraBg = findViewById(R.id.aurora_bg);
        pawContainer = findViewById(R.id.paw_container);
        avatarDisc = findViewById(R.id.avatar_disc);
        avatarView = findViewById(R.id.avatar);
        tempText = findViewById(R.id.temperature);
        tempUnit = findViewById(R.id.temp_unit);
        acInfoText = findViewById(R.id.ac_info);
        outsideTempText = findViewById(R.id.outside_temp_text);
        outsideTempIcon = findViewById(R.id.outside_temp_icon);
        messageText = findViewById(R.id.message);
        subMessageText = findViewById(R.id.sub_message);
        statusCapsule = findViewById(R.id.status_capsule);
        statusItemAc = findViewById(R.id.status_item_ac);
        statusAc = findViewById(R.id.status_ac);
        statusDoors = findViewById(R.id.status_doors);
        statusTimer = findViewById(R.id.status_timer);
        statusBattery = findViewById(R.id.status_battery);
        statusIconAc = findViewById(R.id.status_icon_ac);
        statusIconDoors = findViewById(R.id.status_icon_doors);
        statusIconBattery = findViewById(R.id.status_icon_battery);
        statusIconTimer = findViewById(R.id.status_icon_timer);
        safetyAlert = findViewById(R.id.safety_alert);
        safetyAlertText = findViewById(R.id.safety_alert_text);
        doorAlert = findViewById(R.id.door_alert);
        settingsBtn = findViewById(R.id.settings_btn);
        settingsIcon = findViewById(R.id.settings_icon);
        exitBtn = findViewById(R.id.exit_btn);
        exitScrim = findViewById(R.id.exit_scrim);
        exitSheet = findViewById(R.id.exit_sheet);
        orb1 = findViewById(R.id.aurora_orb1);
        orb2 = findViewById(R.id.aurora_orb2);
        orb3 = findViewById(R.id.aurora_orb3);

        beaconRoot = findViewById(R.id.beacon_root);
        beaconTemp = findViewById(R.id.beacon_temp);
        beaconUnit = findViewById(R.id.beacon_unit);
        beaconMessage = findViewById(R.id.beacon_message);
        beaconStatus = findViewById(R.id.beacon_status);
        beaconAc = findViewById(R.id.beacon_ac);
        beaconDoors = findViewById(R.id.beacon_doors);
        beaconOutside = findViewById(R.id.beacon_outside);

        beaconRoot.setOnClickListener(v -> snoozeBeacon());

        settingsBtn.setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));
        exitBtn.setOnClickListener(v -> showExitDialog());
        findViewById(R.id.sheet_btn_stay).setOnClickListener(v ->
                exitScrim.setVisibility(View.GONE));
        findViewById(R.id.sheet_btn_exit).setOnClickListener(v -> doExit());
        exitScrim.setOnClickListener(v -> exitScrim.setVisibility(View.GONE));

        avatarView.setImageResource(getAvatarDrawable());
    }

    private void showExitDialog() {
        exitScrim.setVisibility(View.VISIBLE);
        exitSheet.setBackgroundResource(isDarkMode
                ? R.drawable.glass_sheet_dark : R.drawable.glass_sheet);
        TextView sheetTitle = findViewById(R.id.sheet_title);
        TextView sheetBody = findViewById(R.id.sheet_body);
        sheetTitle.setTextColor(fgPrimary());
        sheetBody.setTextColor(fgSecondary());
        TextView stayBtn = findViewById(R.id.sheet_btn_stay);
        stayBtn.setTextColor(fgPrimary());
        if (isDarkMode) {
            stayBtn.setBackgroundResource(R.drawable.btn_ghost);
        }
    }

    private void doExit() {
        stopService(new Intent(this, PetModeService.class));
        getSharedPreferences(PetModeService.PREF_NAME, MODE_PRIVATE)
                .edit().putBoolean(PetModeService.KEY_ENABLED, false).apply();
        finish();
    }

    private void loadTheme() {
        isDarkMode = getSharedPreferences(PetModeService.PREF_NAME, MODE_PRIVATE)
                .getBoolean(PetModeService.KEY_DARK_MODE, false);
    }

    private void applyTheme() {
        int fgP = fgPrimary();
        int fgS = fgSecondary();
        int tint = iconTint();

        auroraBg.setBackgroundResource(isDarkMode
                ? R.drawable.aurora_dark : R.drawable.aurora_light);
        settingsBtn.setBackgroundResource(isDarkMode
                ? R.drawable.glass_chrome_btn_dark : R.drawable.glass_chrome_btn);
        statusCapsule.setBackgroundResource(R.drawable.glass_status_capsule);
        statusItemAc.setBackgroundResource(R.drawable.glass_status_item_active);
        exitBtn.setBackgroundResource(R.drawable.glass_exit_btn);

        tempText.setTextColor(fgP);
        tempText.post(() -> {
            int h = tempText.getHeight();
            if (h > 0) {
                int gradTop = getColor(isDarkMode
                        ? R.color.fg_dark_primary : R.color.fg_primary);
                int gradBot = getColor(isDarkMode
                        ? R.color.mint_100 : R.color.slate_700);
                Shader grad = new LinearGradient(0, 0, 0, h, gradTop, gradBot,
                        Shader.TileMode.CLAMP);
                tempText.getPaint().setShader(grad);
                tempText.invalidate();
            }
        });
        tempUnit.setTextColor(fgS);
        acInfoText.setTextColor(fgS);
        messageText.setTextColor(fgP);
        subMessageText.setTextColor(fgS);
        statusAc.setTextColor(fgP);
        statusDoors.setTextColor(fgP);
        statusBattery.setTextColor(fgP);
        statusTimer.setTextColor(fgP);
        outsideTempText.setTextColor(fgP);
        ((TextView) exitBtn).setTextColor(fgP);

        settingsIcon.setColorFilter(tint, PorterDuff.Mode.SRC_IN);
        outsideTempIcon.setColorFilter(tint, PorterDuff.Mode.SRC_IN);
        statusIconAc.setColorFilter(tint, PorterDuff.Mode.SRC_IN);
        statusIconDoors.setColorFilter(tint, PorterDuff.Mode.SRC_IN);
        statusIconBattery.setColorFilter(tint, PorterDuff.Mode.SRC_IN);
        statusIconTimer.setColorFilter(tint, PorterDuff.Mode.SRC_IN);

        orb1.setBackgroundResource(isDarkMode
                ? R.drawable.aurora_orb_1_dark : R.drawable.aurora_orb_1);
        orb2.setBackgroundResource(isDarkMode
                ? R.drawable.aurora_orb_2_dark : R.drawable.aurora_orb_2);
        orb3.setBackgroundResource(isDarkMode
                ? R.drawable.aurora_orb_3_dark : R.drawable.aurora_orb_3);

        avatarView.setImageResource(getAvatarDrawable());
    }

    // ------------------------------------------------------------ beacon mode

    private String displayMode() {
        return getSharedPreferences(PetModeService.PREF_NAME, MODE_PRIVATE)
                .getString(PetModeService.KEY_DISPLAY_MODE, PetModeService.DISPLAY_AUTO);
    }

    /** Whether the beacon should be up right now, given mode + vehicle state. */
    private boolean wantBeacon() {
        String mode = displayMode();
        if (PetModeService.DISPLAY_INTERIOR.equals(mode)) {
            return false;
        }
        if (PetModeService.DISPLAY_BEACON.equals(mode)) {
            return !beaconSnoozed;
        }
        // auto: the car being locked is the signal that nobody is inside to read
        // the pretty view.
        return !beaconSnoozed && service != null && service.isLocked();
    }

    private void snoozeBeacon() {
        beaconSnoozed = true;
        handler.removeCallbacks(endBeaconSnooze);
        handler.postDelayed(endBeaconSnooze, BEACON_SNOOZE_MS);
        applyDisplayMode(false);
    }

    private void clearBeaconSnooze() {
        beaconSnoozed = false;
        applyDisplayMode(wantBeacon());
    }

    private void applyDisplayMode(boolean beacon) {
        if (beacon == beaconActive) {
            return;
        }
        beaconActive = beacon;
        TransitionManager.beginDelayedTransition(rootLayout, new Fade());
        beaconRoot.setVisibility(beacon ? View.VISIBLE : View.GONE);
        if (beacon) {
            // Nothing behind an opaque overlay needs to keep animating.
            suspendInteriorAnimations();
        } else {
            stopBeaconPulse();
            resumeInteriorAnimations();
        }
        Log.i(TAG, "display mode: " + (beacon ? "beacon" : "interior"));
    }

    private void suspendInteriorAnimations() {
        orb1.clearAnimation();
        orb2.clearAnimation();
        orb3.clearAnimation();
        avatarView.clearAnimation();
        pawContainer.removeAllViews();
    }

    private void resumeInteriorAnimations() {
        startAuroraAnimation();
        startAvatarAnimation();
    }

    private void startBeaconPulse() {
        if (beaconPulse != null) {
            return;
        }
        // Only the status strip pulses: the temperature and the message have to
        // stay steadily readable.
        beaconPulse = ValueAnimator.ofFloat(0.45f, 1f);
        beaconPulse.setDuration(1000);
        beaconPulse.setRepeatCount(ValueAnimator.INFINITE);
        beaconPulse.setRepeatMode(ValueAnimator.REVERSE);
        beaconPulse.addUpdateListener(a -> beaconStatus.setAlpha((float) a.getAnimatedValue()));
        beaconPulse.start();
    }

    private void stopBeaconPulse() {
        if (beaconPulse != null) {
            beaconPulse.cancel();
            beaconPulse = null;
            beaconStatus.setAlpha(1f);
        }
    }

    private void updateBeacon(String tempLabel, String unitLabel, String message,
                              boolean acAlert, boolean acOn, boolean anyDoorOpen,
                              String outsideLabel) {
        beaconTemp.setText(tempLabel);
        beaconUnit.setText(unitLabel);
        beaconMessage.setText(message);

        int power = service.getPowerLevel();
        if (power == 0) {
            beaconAc.setText(R.string.beacon_car_off);
        } else {
            beaconAc.setText(acOn ? R.string.beacon_ac_on : R.string.beacon_ac_off);
        }
        beaconDoors.setText(anyDoorOpen
                ? R.string.beacon_doors_open : R.string.beacon_doors_closed);
        beaconOutside.setText(outsideLabel);

        beaconRoot.setBackgroundColor(getColor(acAlert ? R.color.danger : R.color.beacon_bg));
        if (acAlert) {
            startBeaconPulse();
        } else {
            stopBeaconPulse();
        }
    }

    // ------------------------------------------------------------------ display

    private void updateDisplay() {
        if (service == null) {
            return;
        }

        SharedPreferences prefs = getSharedPreferences(PetModeService.PREF_NAME, MODE_PRIVATE);
        String petName = prefs.getString(PetModeService.KEY_PET_NAME, "");
        boolean hasPetName = !petName.isEmpty();
        boolean useFahrenheit = PetModeService.UNIT_FAHRENHEIT.equals(
                prefs.getString(PetModeService.KEY_TEMP_UNIT, getDefaultUnit()));
        String unitLabel = getString(useFahrenheit
                ? R.string.unit_fahrenheit : R.string.unit_celsius);

        int acSetTempC = service.getAcSetTemp();
        String tempLabel;
        if (acSetTempC != Integer.MIN_VALUE) {
            int displayTemp = useFahrenheit ? (acSetTempC * 9 / 5) + 32 : acSetTempC;
            tempLabel = String.valueOf(displayTemp);
            String oldText = tempText.getText().toString();
            tempText.setText(tempLabel);
            tempUnit.setText(unitLabel);
            if (!oldText.equals(tempLabel)
                    && !oldText.equals(getString(R.string.beacon_dash))
                    && !beaconActive) {
                animateTempPulse();
            }
        } else {
            tempLabel = getString(R.string.beacon_dash);
            tempText.setText(tempLabel);
            tempUnit.setText(unitLabel);
        }

        int outsideTempC = service.getOutsideTemp();
        String outsideBeaconLabel;
        if (outsideTempC != Integer.MIN_VALUE) {
            int displayOutside = useFahrenheit ? (outsideTempC * 9 / 5) + 32 : outsideTempC;
            outsideTempText.setText(
                    getString(R.string.outside_temp_pill, displayOutside, unitLabel));
            outsideBeaconLabel = getString(R.string.beacon_outside, displayOutside, unitLabel);
        } else {
            outsideTempText.setText(getString(R.string.outside_temp_pill_unknown));
            outsideBeaconLabel = getString(R.string.beacon_outside_unknown);
        }

        // Autosize handles fitting now; the old code measured the string and
        // shrank it against a hardcoded 400dp cap.
        String message = hasPetName
                ? getString(R.string.msg_safe, petName)
                : getString(R.string.msg_driver_back);
        messageText.setText(message);

        boolean climateOk = service.isClimateAvailable();
        boolean acOn = service.isAcOn();
        if (!climateOk) {
            subMessageText.setText(R.string.climate_signal_lost);
        } else if (acOn) {
            subMessageText.setText(R.string.msg_ac_on);
        } else {
            subMessageText.setText(R.string.msg_ac_off);
        }

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

        boolean acAlert = climateOk && !acOn;
        boolean voltageLow = service.isVoltageLow();
        if (voltageLow) {
            safetyAlertText.setText(R.string.battery_low_warning);
            safetyAlert.setVisibility(View.VISIBLE);
        } else if (acAlert) {
            safetyAlertText.setText(R.string.alert_ac_off);
            safetyAlert.setVisibility(View.VISIBLE);
        } else {
            safetyAlert.setVisibility(View.GONE);
        }

        boolean anyDoorOpen = service.isAnyDoorOpen();
        doorAlert.setVisibility(anyDoorOpen ? View.VISIBLE : View.GONE);
        if (anyDoorOpen) {
            statusDoors.setText(R.string.status_doors_open);
            statusIconDoors.setImageResource(R.drawable.ic_unlock);
        } else {
            statusDoors.setText(R.string.status_doors_closed);
            statusIconDoors.setImageResource(R.drawable.ic_lock);
        }
        statusIconDoors.setColorFilter(iconTint(), PorterDuff.Mode.SRC_IN);

        int battery = service.getBatteryLevel();
        statusBattery.setText(battery >= 0
                ? getString(R.string.status_battery, battery)
                : getString(R.string.beacon_dash));

        statusTimer.setText(getString(R.string.status_active, activeDuration()));

        String beaconMsg = climateOk ? message : getString(R.string.climate_signal_lost);
        if (acAlert) {
            beaconMsg = getString(R.string.msg_ac_off);
        }
        updateBeacon(tempLabel, unitLabel, beaconMsg, acAlert || voltageLow, acOn,
                anyDoorOpen, outsideBeaconLabel);
        applyDisplayMode(wantBeacon());
    }

    private String activeDuration() {
        long millis = service.getActiveMillis();
        long mins = (millis / 60000) % 60;
        long hours = millis / 3600000;
        return String.format(Locale.US, "%02dh %02dmin", hours, mins);
    }

    private String getDefaultUnit() {
        String country = Locale.getDefault().getCountry();
        return ("US".equals(country) || "LR".equals(country) || "MM".equals(country))
                ? PetModeService.UNIT_FAHRENHEIT : PetModeService.UNIT_CELSIUS;
    }

    private int getAvatarDrawable() {
        String avatar = getSharedPreferences(PetModeService.PREF_NAME, MODE_PRIVATE)
                .getString(PetModeService.KEY_AVATAR, "paw");
        switch (avatar) {
            case "dog": return R.drawable.avatar_dog;
            case "cat": return R.drawable.avatar_cat;
            default: return R.drawable.avatar_paw;
        }
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

    // -------------------------------------------------------------- brightness

    private void enforceMaxBrightness() {
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = 1.0f;
        getWindow().setAttributes(lp);

        if (!Settings.System.canWrite(this)) {
            // Never prompt: an unexpected settings screen on a car display is
            // worse than a slightly dimmer window. The window attribute above
            // still gives this window maximum brightness.
            Log.w(TAG, "WRITE_SETTINGS not granted; window brightness only");
            return;
        }
        try {
            originalBrightnessMode = Settings.System.getInt(
                    getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE);
            // Capturing the level is what the old code missed, so Cabin left the
            // car pinned at 255 permanently.
            originalBrightness = Settings.System.getInt(
                    getContentResolver(), Settings.System.SCREEN_BRIGHTNESS);
            Settings.System.putInt(getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS_MODE, 0);
            Settings.System.putInt(getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS, 255);
            Log.i(TAG, "brightness forced to 255 (was " + originalBrightness
                    + ", mode " + originalBrightnessMode + ")");
        } catch (Settings.SettingNotFoundException e) {
            Log.w(TAG, "brightness settings unreadable", e);
        }
    }

    private void restoreBrightness() {
        if (!Settings.System.canWrite(this)) {
            return;
        }
        try {
            if (originalBrightness >= 0) {
                Settings.System.putInt(getContentResolver(),
                        Settings.System.SCREEN_BRIGHTNESS, originalBrightness);
            }
            if (originalBrightnessMode >= 0) {
                Settings.System.putInt(getContentResolver(),
                        Settings.System.SCREEN_BRIGHTNESS_MODE, originalBrightnessMode);
            }
            Log.i(TAG, "brightness restored to " + originalBrightness);
        } catch (Exception e) {
            Log.w(TAG, "brightness restore failed", e);
        }
    }

    // ----------------------------------------------------------------- service

    private void startService() {
        SharedPreferences prefs = getSharedPreferences(PetModeService.PREF_NAME, MODE_PRIVATE);
        prefs.edit().putBoolean(PetModeService.KEY_ENABLED, true).apply();
        Intent svc = new Intent(this, PetModeService.class);
        startForegroundService(svc);
        bindService(svc, connection, Context.BIND_AUTO_CREATE);
    }

    private final ServiceConnection connection = new PetModeServiceConnection(this);

    void onServiceBound(PetModeService svc) {
        service = svc;
        service.setStateCallback(this);
        bound = true;
        updateDisplay();
    }

    void onServiceUnbound() {
        bound = false;
        service = null;
    }

    // -------------------------------------------------------------- animations

    private void animateTempPulse() {
        ScaleAnimation pulse = new ScaleAnimation(1f, 1.05f, 1f, 1.05f,
                Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        pulse.setDuration(150);
        pulse.setRepeatMode(Animation.REVERSE);
        pulse.setRepeatCount(1);
        tempText.startAnimation(pulse);
    }

    private void startAvatarAnimation() {
        TranslateAnimation bob = new TranslateAnimation(0, 0, 0, -15);
        bob.setDuration(2500);
        bob.setRepeatMode(Animation.REVERSE);
        bob.setRepeatCount(Animation.INFINITE);
        bob.setInterpolator(new AccelerateInterpolator(0.5f));
        avatarView.startAnimation(bob);
    }

    private void startAuroraAnimation() {
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

    private void startPawAnimation() {
        schedulePawSpawn(1000);
    }

    private void schedulePawSpawn(long delay) {
        handler.postDelayed(() -> {
            if (isFinishing()) {
                return;
            }
            if (!beaconActive) {
                spawnFloatingPaw();
            }
            schedulePawSpawn(2000 + random.nextInt(3000));
        }, delay);
    }

    private void spawnFloatingPaw() {
        ImageView paw = new ImageView(this);
        paw.setImageResource(R.drawable.paw_print);
        int size = 40 + random.nextInt(30);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(size, size);
        lp.leftMargin = random.nextInt(Math.max(1, pawContainer.getWidth() - size));
        lp.topMargin = pawContainer.getHeight();
        lp.gravity = Gravity.TOP | Gravity.START;
        paw.setAlpha(0f);
        paw.setRotation(random.nextInt(40) - 20);
        paw.setColorFilter(getColor(isDarkMode
                ? R.color.paw_tint_dark : R.color.paw_tint_light));
        pawContainer.addView(paw, lp);

        int duration = 4000 + random.nextInt(3000);
        AnimationSet set = new AnimationSet(false);
        TranslateAnimation rise = new TranslateAnimation(0, random.nextInt(60) - 30,
                0, -pawContainer.getHeight() - size);
        rise.setDuration(duration);
        AlphaAnimation fade = new AlphaAnimation(0.6f, 0f);
        fade.setDuration(duration);
        set.addAnimation(rise);
        set.addAnimation(fade);
        set.setAnimationListener(new PawRemovalListener(handler, pawContainer, paw));
        paw.startAnimation(set);
    }

    private void startTimerUpdate() {
        scheduleTimerTick();
    }

    private void scheduleTimerTick() {
        handler.postDelayed(() -> {
            if (isFinishing()) {
                return;
            }
            if (service != null) {
                statusTimer.setText(getString(R.string.status_active, activeDuration()));
            }
            scheduleTimerTick();
        }, 30000);
    }
}
