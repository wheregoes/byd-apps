package com.wheregoes.petmode;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;

import java.util.Locale;

public class SettingsActivity extends Activity {

    private SharedPreferences prefs;
    private TextView unitCelsius;
    private TextView unitFahrenheit;
    private TextView modeAuto;
    private TextView modeBeacon;
    private TextView modeInterior;
    private View auroraBg;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        prefs = getSharedPreferences(PetModeService.PREF_NAME, MODE_PRIVATE);
        auroraBg = findViewById(R.id.aurora_bg);
        setupPetName();
        setupAvatarSelection();
        setupTempUnit();
        setupDisplayMode();
        setupThemeToggle();
        setupBackButton();
        applyTheme();
    }

    private void setupPetName() {
        EditText nameInput = findViewById(R.id.pet_name_input);
        String name = prefs.getString(PetModeService.KEY_PET_NAME, "");
        nameInput.setText(name);
        nameInput.setHint(R.string.default_pet_name);
        nameInput.addTextChangedListener(new PetNameWatcher(prefs));
    }

    private void setupAvatarSelection() {
        String current = prefs.getString(PetModeService.KEY_AVATAR, "paw");

        View pawOption = findViewById(R.id.avatar_paw);
        View dogOption = findViewById(R.id.avatar_dog);
        View catOption = findViewById(R.id.avatar_cat);

        highlightAvatar(current);

        pawOption.setOnClickListener(v -> selectAvatar("paw"));
        dogOption.setOnClickListener(v -> selectAvatar("dog"));
        catOption.setOnClickListener(v -> selectAvatar("cat"));
    }

    private void selectAvatar(String avatar) {
        prefs.edit().putString(PetModeService.KEY_AVATAR, avatar).apply();
        highlightAvatar(avatar);
    }

    private void highlightAvatar(String selected) {
        View pawBorder = findViewById(R.id.avatar_paw_border);
        View dogBorder = findViewById(R.id.avatar_dog_border);
        View catBorder = findViewById(R.id.avatar_cat_border);

        pawBorder.setBackgroundResource("paw".equals(selected)
                ? R.drawable.glass_avatar_tile_selected : R.drawable.glass_avatar_tile);
        dogBorder.setBackgroundResource("dog".equals(selected)
                ? R.drawable.glass_avatar_tile_selected : R.drawable.glass_avatar_tile);
        catBorder.setBackgroundResource("cat".equals(selected)
                ? R.drawable.glass_avatar_tile_selected : R.drawable.glass_avatar_tile);
    }

    private void setupTempUnit() {
        unitCelsius = findViewById(R.id.unit_celsius);
        unitFahrenheit = findViewById(R.id.unit_fahrenheit);

        String current = prefs.getString(PetModeService.KEY_TEMP_UNIT, getDefaultUnit());
        updateSegmentedState(PetModeService.UNIT_FAHRENHEIT.equals(current));

        unitCelsius.setOnClickListener(v -> {
            prefs.edit().putString(PetModeService.KEY_TEMP_UNIT, PetModeService.UNIT_CELSIUS).apply();
            updateSegmentedState(false);
        });

        unitFahrenheit.setOnClickListener(v -> {
            prefs.edit().putString(PetModeService.KEY_TEMP_UNIT, PetModeService.UNIT_FAHRENHEIT).apply();
            updateSegmentedState(true);
        });
    }

    private void updateSegmentedState(boolean isFahrenheit) {
        selectSegment(isFahrenheit ? unitFahrenheit : unitCelsius,
                isFahrenheit ? unitCelsius : unitFahrenheit);
    }

    /**
     * Beacon mode: the same readings at maximum contrast so they can be read
     * from outside the car through tinted glass.
     */
    private void setupDisplayMode() {
        modeAuto = findViewById(R.id.mode_auto);
        modeBeacon = findViewById(R.id.mode_beacon);
        modeInterior = findViewById(R.id.mode_interior);

        updateDisplayModeState(currentDisplayMode());

        modeAuto.setOnClickListener(v -> selectDisplayMode(PetModeService.DISPLAY_AUTO));
        modeBeacon.setOnClickListener(v -> selectDisplayMode(PetModeService.DISPLAY_BEACON));
        modeInterior.setOnClickListener(v -> selectDisplayMode(PetModeService.DISPLAY_INTERIOR));
    }

    private String currentDisplayMode() {
        return prefs.getString(PetModeService.KEY_DISPLAY_MODE, PetModeService.DISPLAY_AUTO);
    }

    private void selectDisplayMode(String mode) {
        prefs.edit().putString(PetModeService.KEY_DISPLAY_MODE, mode).apply();
        updateDisplayModeState(mode);
    }

    private void updateDisplayModeState(String mode) {
        if (PetModeService.DISPLAY_BEACON.equals(mode)) {
            selectSegment(modeBeacon, modeAuto, modeInterior);
        } else if (PetModeService.DISPLAY_INTERIOR.equals(mode)) {
            selectSegment(modeInterior, modeAuto, modeBeacon);
        } else {
            selectSegment(modeAuto, modeBeacon, modeInterior);
        }
    }

    /** One place segmented controls are styled, using named colours not hex. */
    private void selectSegment(TextView active, TextView... inactive) {
        boolean isDark = prefs.getBoolean(PetModeService.KEY_DARK_MODE, false);
        int activeBg = isDark
                ? R.drawable.glass_segmented_active_dark : R.drawable.glass_segmented_active;
        int activeColor = getColor(isDark ? R.color.fg_dark_primary : R.color.fg_primary);
        int inactiveColor = getColor(isDark ? R.color.fg_dark_secondary : R.color.fg_secondary);

        active.setBackgroundResource(activeBg);
        active.setTextColor(activeColor);
        for (TextView tv : inactive) {
            tv.setBackgroundResource(0);
            tv.setTextColor(inactiveColor);
        }
    }

    private void setupThemeToggle() {
        Switch themeSwitch = findViewById(R.id.theme_toggle);
        themeSwitch.setChecked(prefs.getBoolean(PetModeService.KEY_DARK_MODE, false));
        themeSwitch.setOnCheckedChangeListener((btn, checked) -> {
            prefs.edit().putBoolean(PetModeService.KEY_DARK_MODE, checked).apply();
            applyTheme();
        });
    }

    private void setupBackButton() {
        findViewById(R.id.back_btn).setOnClickListener(v -> finish());
    }

    private void applyTheme() {
        boolean isDark = prefs.getBoolean(PetModeService.KEY_DARK_MODE, false);
        auroraBg.setBackgroundResource(isDark ? R.drawable.aurora_dark : R.drawable.aurora_light);

        int fgPrimary = getColor(isDark ? R.color.fg_dark_primary : R.color.fg_primary);
        int fgSecondary = getColor(isDark ? R.color.fg_dark_secondary : R.color.fg_secondary);
        int fgTertiary = getColor(isDark ? R.color.fg_dark_tertiary : R.color.fg_tertiary);
        int iconTint = getColor(isDark ? R.color.fg_dark_primary : R.color.slate_700);
        int rowBg = isDark ? R.drawable.glass_setting_row_dark : R.drawable.glass_setting_row;
        int inputBg = isDark ? R.drawable.glass_input_dark : R.drawable.glass_input;
        int segBg = isDark ? R.drawable.glass_segmented_bg_dark : R.drawable.glass_segmented_bg;
        int chromeBg = isDark ? R.drawable.glass_chrome_btn_dark : R.drawable.glass_chrome_btn;

        ((TextView) findViewById(R.id.settings_title)).setTextColor(fgPrimary);
        ((TextView) findViewById(R.id.label_pet_name)).setTextColor(fgSecondary);
        ((TextView) findViewById(R.id.label_avatar)).setTextColor(fgSecondary);
        ((TextView) findViewById(R.id.label_paw)).setTextColor(fgSecondary);
        ((TextView) findViewById(R.id.label_dog)).setTextColor(fgSecondary);
        ((TextView) findViewById(R.id.label_cat)).setTextColor(fgSecondary);
        ((TextView) findViewById(R.id.label_temp_unit)).setTextColor(fgPrimary);
        ((TextView) findViewById(R.id.label_temp_unit_desc)).setTextColor(fgSecondary);
        ((TextView) findViewById(R.id.label_display_mode)).setTextColor(fgPrimary);
        ((TextView) findViewById(R.id.label_display_mode_desc)).setTextColor(fgSecondary);
        ((TextView) findViewById(R.id.label_dark_mode)).setTextColor(fgPrimary);
        ((TextView) findViewById(R.id.label_dark_mode_desc)).setTextColor(fgSecondary);
        ((TextView) findViewById(R.id.label_version)).setTextColor(fgTertiary);

        EditText nameInput = findViewById(R.id.pet_name_input);
        nameInput.setTextColor(fgPrimary);
        nameInput.setHintTextColor(fgSecondary);
        nameInput.setBackgroundResource(inputBg);

        findViewById(R.id.row_pet_name).setBackgroundResource(rowBg);
        findViewById(R.id.row_avatar).setBackgroundResource(rowBg);
        findViewById(R.id.row_temp_unit).setBackgroundResource(rowBg);
        findViewById(R.id.row_display_mode).setBackgroundResource(rowBg);
        findViewById(R.id.row_dark_mode).setBackgroundResource(rowBg);

        findViewById(R.id.temp_unit_group).setBackgroundResource(segBg);
        findViewById(R.id.display_mode_group).setBackgroundResource(segBg);
        View backBtn = findViewById(R.id.back_btn);
        backBtn.setBackgroundResource(chromeBg);
        View backIcon = ((android.view.ViewGroup) backBtn).getChildAt(0);
        if (backIcon instanceof ImageView) {
            ((ImageView) backIcon).setColorFilter(iconTint, PorterDuff.Mode.SRC_IN);
        }

        String current = prefs.getString(PetModeService.KEY_TEMP_UNIT, getDefaultUnit());
        updateSegmentedState(PetModeService.UNIT_FAHRENHEIT.equals(current));
        updateDisplayModeState(currentDisplayMode());
    }

    private String getDefaultUnit() {
        String country = Locale.getDefault().getCountry();
        return ("US".equals(country) || "LR".equals(country) || "MM".equals(country))
                ? PetModeService.UNIT_FAHRENHEIT : PetModeService.UNIT_CELSIUS;
    }
}
