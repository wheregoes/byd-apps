package com.wheregoes.petmode;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Color;
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
    private FrameLayout pawContainer;
    private ImageView avatarView;
    private TextView tempText;
    private TextView tempUnit;
    private TextView acInfoText;
    private TextView messageText;
    private TextView subMessageText;
    private TextView statusAc;
    private TextView statusDoors;
    private TextView statusTimer;
    private View settingsBtn;
    private View statusDot;
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
        startStatusDotAnimation();
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
        new AlertDialog.Builder(this)
                .setTitle(R.string.exit_confirm_title)
                .setMessage(R.string.exit_confirm)
                .setPositiveButton(android.R.string.yes, (d, w) -> {
                    stopService(new Intent(this, PetModeService.class));
                    getSharedPreferences(PetModeService.PREF_NAME, MODE_PRIVATE)
                            .edit().putBoolean(PetModeService.KEY_ENABLED, false).apply();
                    finish();
                })
                .setNegativeButton(android.R.string.no, null)
                .show();
    }

    @Override
    public void onStateUpdated() {
        handler.post(this::updateDisplay);
    }

    private void bindViews() {
        rootLayout = findViewById(R.id.root_layout);
        pawContainer = findViewById(R.id.paw_container);
        avatarView = findViewById(R.id.avatar);
        tempText = findViewById(R.id.temperature);
        tempUnit = findViewById(R.id.temp_unit);
        messageText = findViewById(R.id.message);
        subMessageText = findViewById(R.id.sub_message);
        statusAc = findViewById(R.id.status_ac);
        statusDoors = findViewById(R.id.status_doors);
        statusTimer = findViewById(R.id.status_timer);
        acInfoText = findViewById(R.id.ac_info);
        settingsBtn = findViewById(R.id.settings_btn);
        statusDot = findViewById(R.id.status_dot);

        settingsBtn.setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));

        avatarView.setImageResource(getAvatarDrawable());
    }

    private void loadTheme() {
        isDarkMode = getSharedPreferences(PetModeService.PREF_NAME, MODE_PRIVATE)
                .getBoolean(PetModeService.KEY_DARK_MODE, false);
    }

    private void applyTheme() {
        if (isDarkMode) {
            rootLayout.setBackgroundColor(0xFF0A0E1A);
            tempText.setTextColor(Color.WHITE);
            tempUnit.setTextColor(0xFFB0B8C8);
            acInfoText.setTextColor(0xFFB0B8C8);
            messageText.setTextColor(Color.WHITE);
            subMessageText.setTextColor(0xFFB0B8C8);
            statusAc.setTextColor(0xFFB0B8C8);
            statusDoors.setTextColor(0xFFB0B8C8);
            statusTimer.setTextColor(0xFFB0B8C8);
        } else {
            rootLayout.setBackgroundColor(0xFFF5F7FA);
            tempText.setTextColor(0xFF1A1A2E);
            tempUnit.setTextColor(0xFF636E7B);
            acInfoText.setTextColor(0xFF636E7B);
            messageText.setTextColor(0xFF1A1A2E);
            subMessageText.setTextColor(0xFF636E7B);
            statusAc.setTextColor(0xFF636E7B);
            statusDoors.setTextColor(0xFF636E7B);
            statusTimer.setTextColor(0xFF636E7B);
        }
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
        int temp = service.getCurrentTemp();
        if (temp != Integer.MIN_VALUE) {
            int displayTemp = useFahrenheit ? (temp * 9 / 5) + 32 : temp;
            String oldText = tempText.getText().toString();
            String newText = String.valueOf(displayTemp);
            tempText.setText(newText);
            tempUnit.setText(unitLabel);
            if (!oldText.equals(newText) && !oldText.equals("--")) animateTempPulse();
        } else {
            tempText.setText("--");
            tempUnit.setText(unitLabel);
        }

        int setTempC = service.getSetTemp();
        if (setTempC != Integer.MIN_VALUE && service.isAcOn()) {
            int displaySetTemp = useFahrenheit ? (setTempC * 9 / 5) + 32 : setTempC;
            acInfoText.setText(getString(R.string.ac_set_to, displaySetTemp, unitLabel));
            acInfoText.setVisibility(View.VISIBLE);
        } else {
            acInfoText.setVisibility(View.GONE);
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

        statusAc.setText(service.isAcOn() ? getString(R.string.status_ac_on) :
                getString(R.string.status_ac_off));
        statusAc.setTextColor(service.isAcOn() ? 0xFF27AE60 : 0xFF636E7B);

        if (service.isAnyDoorOpen()) {
            statusDoors.setText(R.string.status_doors_open);
            statusDoors.setTextColor(0xFFE74C3C);
        } else if (service.isLocked()) {
            statusDoors.setText(R.string.status_doors_locked);
            statusDoors.setTextColor(0xFF27AE60);
        } else {
            statusDoors.setText(R.string.status_doors_unlocked);
            statusDoors.setTextColor(0xFFF39C12);
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

    private void startStatusDotAnimation() {
        AlphaAnimation breathe = new AlphaAnimation(0.4f, 1.0f);
        breathe.setDuration(1500);
        breathe.setRepeatMode(Animation.REVERSE);
        breathe.setRepeatCount(Animation.INFINITE);
        statusDot.startAnimation(breathe);
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
        int tint = isDarkMode ? 0x3300D4AA : 0x2200B894;
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
