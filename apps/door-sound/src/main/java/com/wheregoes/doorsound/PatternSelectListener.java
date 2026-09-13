package com.wheregoes.doorsound;

import android.content.SharedPreferences;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Switch;

class PatternSelectListener implements AdapterView.OnItemSelectedListener {
    private final SharedPreferences prefs;
    private final SoundEvent event;
    private final Switch enableSwitch;
    private final MainActivity activity;

    PatternSelectListener(SharedPreferences prefs, SoundEvent event,
                          Switch enableSwitch, MainActivity activity) {
        this.prefs = prefs;
        this.event = event;
        this.enableSwitch = enableSwitch;
        this.activity = activity;
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        int pattern = position - 1;
        SharedPreferences.Editor edit = prefs.edit().putInt(event.outsidePatternKey, pattern);
        if (pattern >= 0) {
            // Picking a pattern is an unambiguous request for it to play.
            edit.putBoolean(event.outsideEnabledKey, true);
        }
        edit.apply();
        if (pattern >= 0 && !enableSwitch.isChecked()) {
            enableSwitch.setChecked(true);
        }
        activity.refreshAllWarnings();
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {}
}
