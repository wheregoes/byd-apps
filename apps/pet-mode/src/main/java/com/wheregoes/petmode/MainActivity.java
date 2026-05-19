package com.wheregoes.petmode;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.PorterDuff;
import android.graphics.Shader;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.ScaleAnimation;
import android.view.animation.TranslateAnimation;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;
import java.util.Random;

public class MainActivity extends Activity implements PetModeService.StateCallback {

    private PetModeService service;
    private boolean bound = false;
    private Handler handler;
    private Random random = new Random();

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
    private View settingsBtn;
    private ImageView settingsIcon;
    private View exitBtn;
    private View exitScrim;
    private View exitSheet;
    private int originalBrightnessMode = -1;
    private boolean isDarkMode = false;

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
    protected void onDestroy() {
        if (bound) unbindService(connection);
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

    private void showExitDialog() {
        exitScrim.setVisibility(View.VISIBLE);
        exitSheet.setBackgroundResource(isDarkMode
                ? R.drawable.glass_sheet_dark : R.drawable.glass_sheet);
        TextView sheetTitle = findViewById(R.id.sheet_title);
        TextView sheetBody = findViewById(R.id.sheet_body);
        int fgP = isDarkMode ? Color.WHITE : 0xFF1A1A2E;
        int fgS = isDarkMode ? 0xB8FFFFFF : 0xFF636E7B;
        sheetTitle.setTextColor(fgP);
        sheetBody.setTextColor(fgS);
        TextView stayBtn = findViewById(R.id.sheet_btn_stay);
        stayBtn.setTextColor(fgP);
        if (isDarkMode) stayBtn.setBackgroundResource(R.drawable.btn_ghost);
    }

    private void doExit() {
        stopService(new Intent(this, PetModeService.class));
        getSharedPreferences(PetModeService.PREF_NAME, MODE_PRIVATE)
                .edit().putBoolean(PetModeService.KEY_ENABLED, false).apply();
        finish();
    }

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
        settingsBtn = findViewById(R.id.settings_btn);
        settingsIcon = findViewById(R.id.settings_icon);
        exitBtn = findViewById(R.id.exit_btn);
        exitScrim = findViewById(R.id.exit_scrim);
        exitSheet = findViewById(R.id.exit_sheet);

        settingsBtn.setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));

        exitBtn.setOnClickListener(v -> showExitDialog());

        findViewById(R.id.sheet_btn_stay).setOnClickListener(v ->
                exitScrim.setVisibility(View.GONE));
        findViewById(R.id.sheet_btn_exit).setOnClickListener(v -> doExit());
        exitScrim.setOnClickListener(v -> exitScrim.setVisibility(View.GONE));

        avatarView.setImageResource(getAvatarDrawable());
    }

    private void loadTheme() {
        isDarkMode = getSharedPreferences(PetModeService.PREF_NAME, MODE_PRIVATE)
                .getBoolean(PetModeService.KEY_DARK_MODE, false);
    }

    private void applyTheme() {
        int fgPrimary;
        int fgSecondary;
        int iconTint;

        if (isDarkMode) {
            auroraBg.setBackgroundResource(R.drawable.aurora_dark);
            fgPrimary = Color.WHITE;
            fgSecondary = 0xB8FFFFFF;
            iconTint = Color.WHITE;

            settingsBtn.setBackgroundResource(R.drawable.glass_chrome_btn);
            statusCapsule.setBackgroundResource(R.drawable.glass_status_capsule);

            statusItemAc.setBackgroundResource(R.drawable.glass_status_item_active);

            exitBtn.setBackgroundResource(R.drawable.glass_exit_btn);
        } else {
            auroraBg.setBackgroundResource(R.drawable.aurora_light);
            fgPrimary = 0xFF1A1A2E;
            fgSecondary = 0xFF636E7B;
            iconTint = 0xFF232830;

            settingsBtn.setBackgroundResource(R.drawable.glass_chrome_btn);
            statusCapsule.setBackgroundResource(R.drawable.glass_status_capsule);
            statusItemAc.setBackgroundResource(R.drawable.glass_status_item_active);
            exitBtn.setBackgroundResource(R.drawable.glass_exit_btn);
        }

        tempText.setTextColor(fgPrimary);
        tempText.post(() -> {
            int h = tempText.getHeight();
            if (h > 0) {
                int gradTop = isDarkMode ? 0xFFFFFFFF : 0xFF1A1A2E;
                int gradBot = isDarkMode ? 0xFFB7E8DC : 0xFF232830;
                Shader grad = new LinearGradient(0, 0, 0, h, gradTop, gradBot, Shader.TileMode.CLAMP);
                tempText.getPaint().setShader(grad);
                tempText.invalidate();
            }
        });
        tempUnit.setTextColor(fgSecondary);
        acInfoText.setTextColor(fgSecondary);
        messageText.setTextColor(fgPrimary);
        subMessageText.setTextColor(fgSecondary);
        statusAc.setTextColor(fgPrimary);
        statusDoors.setTextColor(fgPrimary);
        statusBattery.setTextColor(fgPrimary);
        statusTimer.setTextColor(fgPrimary);
        outsideTempText.setTextColor(fgPrimary);
        ((TextView) exitBtn).setTextColor(fgPrimary);

        settingsIcon.setColorFilter(iconTint, PorterDuff.Mode.SRC_IN);
        outsideTempIcon.setColorFilter(iconTint, PorterDuff.Mode.SRC_IN);
        statusIconAc.setColorFilter(iconTint, PorterDuff.Mode.SRC_IN);
        statusIconDoors.setColorFilter(iconTint, PorterDuff.Mode.SRC_IN);
        statusIconBattery.setColorFilter(iconTint, PorterDuff.Mode.SRC_IN);
        statusIconTimer.setColorFilter(iconTint, PorterDuff.Mode.SRC_IN);

        avatarDisc.setBackgroundResource(isDarkMode
                ? R.drawable.glass_avatar_disc_dark : R.drawable.glass_avatar_disc);

        findViewById(R.id.aurora_orb1).setBackgroundResource(isDarkMode
                ? R.drawable.aurora_orb_1_dark : R.drawable.aurora_orb_1);
        findViewById(R.id.aurora_orb2).setBackgroundResource(isDarkMode
                ? R.drawable.aurora_orb_2_dark : R.drawable.aurora_orb_2);
        findViewById(R.id.aurora_orb3).setBackgroundResource(isDarkMode
                ? R.drawable.aurora_orb_3_dark : R.drawable.aurora_orb_3);

        avatarView.setImageResource(getAvatarDrawable());
    }

    private void updateDisplay() {
        if (service == null) return;

        SharedPreferences prefs = getSharedPreferences(PetModeService.PREF_NAME, MODE_PRIVATE);
        String petName = prefs.getString(PetModeService.KEY_PET_NAME, "");
        boolean hasPetName = !petName.isEmpty();
        boolean useFahrenheit = PetModeService.UNIT_FAHRENHEIT.equals(
                prefs.getString(PetModeService.KEY_TEMP_UNIT, getDefaultUnit()));

        String unitLabel = useFahrenheit ? "°F" : "°C";
        int acSetTempC = service.getAcSetTemp();
        if (acSetTempC != Integer.MIN_VALUE) {
            int displayTemp = useFahrenheit ? (acSetTempC * 9 / 5) + 32 : acSetTempC;
            String oldText = tempText.getText().toString();
            String newText = String.valueOf(displayTemp);
            tempText.setText(newText);
            tempUnit.setText(unitLabel);
            if (!oldText.equals(newText) && !oldText.equals("--")) animateTempPulse();
        } else {
            tempText.setText("--");
            tempUnit.setText(unitLabel);
        }

        int outsideTempC = service.getOutsideTemp();
        if (outsideTempC != Integer.MIN_VALUE) {
            int displayOutside = useFahrenheit ? (outsideTempC * 9 / 5) + 32 : outsideTempC;
            outsideTempText.setText(getString(R.string.outside_temp_pill, displayOutside, unitLabel));
        } else {
            outsideTempText.setText(getString(R.string.outside_temp_pill_unknown));
        }

        if (hasPetName) {
            messageText.setText(getString(R.string.msg_safe, petName));
        } else {
            messageText.setText(R.string.msg_driver_back);
        }

        if (service.isClimateAvailable() && service.isAcOn()) {
            subMessageText.setText(R.string.msg_ac_on);
        } else if (service.isClimateAvailable()) {
            subMessageText.setText(R.string.msg_ac_off);
        } else {
            subMessageText.setText(R.string.msg_monitoring);
        }

        int power = service.getPowerLevel();
        boolean acOn = service.isAcOn();
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

        boolean acAlert = service.isClimateAvailable() && !acOn;
        safetyAlert.setVisibility(acAlert ? View.VISIBLE : View.GONE);

        if (service.isAnyDoorOpen()) {
            statusDoors.setText(R.string.status_doors_open);
            statusIconDoors.setImageResource(R.drawable.ic_unlock);
        } else if (service.isLocked()) {
            statusDoors.setText(R.string.status_doors_locked);
            statusIconDoors.setImageResource(R.drawable.ic_lock);
        } else {
            statusDoors.setText(R.string.status_doors_unlocked);
            statusIconDoors.setImageResource(R.drawable.ic_unlock);
        }

        int iconTint = isDarkMode ? Color.WHITE : 0xFF232830;
        statusIconDoors.setColorFilter(iconTint, PorterDuff.Mode.SRC_IN);

        int battery = service.getBatteryLevel();
        if (battery >= 0) {
            statusBattery.setText(getString(R.string.status_battery, battery));
        } else {
            statusBattery.setText("--");
        }

        long millis = service.getActiveMillis();
        long mins = (millis / 60000) % 60;
        long hours = millis / 3600000;
        statusTimer.setText(getString(R.string.status_active,
                String.format(Locale.US, "%d:%02d", hours, mins)));
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

    private void enforceMaxBrightness() {
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = 1.0f;
        getWindow().setAttributes(lp);
        try {
            originalBrightnessMode = Settings.System.getInt(
                    getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE);
            Settings.System.putInt(getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS_MODE, 0);
            Settings.System.putInt(getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS, 255);
        } catch (Exception ignored) {}
    }

    private void restoreBrightness() {
        if (originalBrightnessMode >= 0) {
            try {
                Settings.System.putInt(getContentResolver(),
                        Settings.System.SCREEN_BRIGHTNESS_MODE, originalBrightnessMode);
            } catch (Exception ignored) {}
        }
    }

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

    private void startPawAnimation() {
        schedulePawSpawn(1000);
    }

    private void schedulePawSpawn(long delay) {
        handler.postDelayed(() -> {
            if (isFinishing()) return;
            spawnFloatingPaw();
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
        int tint = isDarkMode ? 0x0FFFFFFF : 0x1A008264;
        paw.setColorFilter(tint);
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
            if (isFinishing()) return;
            if (service != null) {
                long millis = service.getActiveMillis();
                long mins = (millis / 60000) % 60;
                long hours = millis / 3600000;
                statusTimer.setText(getString(R.string.status_active,
                        String.format(Locale.US, "%d:%02d", hours, mins)));
            }
            scheduleTimerTick();
        }, 30000);
    }
}
