package com.wheregoes.camping;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.Locale;

public class SettingsActivity extends Activity {

    private SharedPreferences prefs;
    private TextView unitCelsius;
    private TextView unitFahrenheit;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        prefs = getSharedPreferences(CampingService.PREF_NAME, MODE_PRIVATE);

        setupBackButton();
        setupBatteryThreshold();
        setupEnergyCost();
        setupTempUnit();
        setupBrightness();
    }

    private void setupBackButton() {
        findViewById(R.id.back_btn).setOnClickListener(v -> finish());
    }

    private void setupBatteryThreshold() {
        SeekBar slider = findViewById(R.id.battery_threshold_slider);
        TextView valueText = findViewById(R.id.battery_threshold_value);

        int current = prefs.getInt(CampingService.KEY_BATTERY_ALARM_THRESHOLD, 20);
        slider.setProgress(current);
        valueText.setText(current + "%");

        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                valueText.setText(progress + "%");
            }
            @Override
            public void onStartTrackingTouch(SeekBar sb) {}
            @Override
            public void onStopTrackingTouch(SeekBar sb) {
                prefs.edit().putInt(CampingService.KEY_BATTERY_ALARM_THRESHOLD, sb.getProgress()).apply();
            }
        });
    }

    private void setupEnergyCost() {
        EditText costInput = findViewById(R.id.energy_cost_input);
        float current = prefs.getFloat(CampingService.KEY_ENERGY_COST, 0f);
        if (current > 0) costInput.setText(String.format(Locale.US, "%.2f", current));

        costInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                try {
                    float val = Float.parseFloat(costInput.getText().toString());
                    prefs.edit().putFloat(CampingService.KEY_ENERGY_COST, val).apply();
                } catch (NumberFormatException ignored) {}
            }
        });
    }

    private void setupTempUnit() {
        unitCelsius = findViewById(R.id.unit_celsius);
        unitFahrenheit = findViewById(R.id.unit_fahrenheit);

        String current = prefs.getString(CampingService.KEY_TEMP_UNIT, getDefaultUnit());
        updateSegmentedState(CampingService.UNIT_FAHRENHEIT.equals(current));

        unitCelsius.setOnClickListener(v -> {
            prefs.edit().putString(CampingService.KEY_TEMP_UNIT, CampingService.UNIT_CELSIUS).apply();
            updateSegmentedState(false);
        });

        unitFahrenheit.setOnClickListener(v -> {
            prefs.edit().putString(CampingService.KEY_TEMP_UNIT, CampingService.UNIT_FAHRENHEIT).apply();
            updateSegmentedState(true);
        });
    }

    private void updateSegmentedState(boolean isFahrenheit) {
        if (isFahrenheit) {
            unitFahrenheit.setBackgroundResource(R.drawable.glass_segmented_active_dark);
            unitFahrenheit.setTextColor(0xFFFFFFFF);
            unitCelsius.setBackgroundResource(0);
            unitCelsius.setTextColor(0xB8FFFFFF);
        } else {
            unitCelsius.setBackgroundResource(R.drawable.glass_segmented_active_dark);
            unitCelsius.setTextColor(0xFFFFFFFF);
            unitFahrenheit.setBackgroundResource(0);
            unitFahrenheit.setTextColor(0xB8FFFFFF);
        }
    }

    private void setupBrightness() {
        SeekBar slider = findViewById(R.id.brightness_slider);
        int current = prefs.getInt(CampingService.KEY_SCREEN_BRIGHTNESS, 80);
        slider.setProgress(current);

        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser) {
                    prefs.edit().putInt(CampingService.KEY_SCREEN_BRIGHTNESS, progress).apply();
                    android.view.WindowManager.LayoutParams lp = getWindow().getAttributes();
                    lp.screenBrightness = progress / 255f;
                    getWindow().setAttributes(lp);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });
    }

    private String getDefaultUnit() {
        String country = Locale.getDefault().getCountry();
        return ("US".equals(country) || "LR".equals(country) || "MM".equals(country))
                ? CampingService.UNIT_FAHRENHEIT : CampingService.UNIT_CELSIUS;
    }
}
