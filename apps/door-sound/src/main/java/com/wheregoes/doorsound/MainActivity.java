package com.wheregoes.doorsound;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class MainActivity extends Activity {
    private static final int REQ_FILE_BASE = 100;
    private static final int REQ_PERMISSION = 1;

    /** Cards per row in the event grid; eight events over two rows. */
    private static final int COLUMNS = 4;

    private View contentInside;
    private View contentOutside;
    private TextView tabInside;
    private TextView tabOutside;
    private TextView tabHint;
    private TextView textServiceStatus;
    private TextView textLastEvent;
    private View statusDot;
    private Switch switchEnabled;
    private Handler refreshHandler;
    private Runnable refreshRunnable;
    private AvasPlayer avasPlayer;
    private SharedPreferences prefs;

    private final View[] insideCards = new View[SoundEvent.VALUES.length];
    private final View[] outsideCards = new View[SoundEvent.VALUES.length];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().setStatusBarColor(getColor(R.color.aurora_light_base_start));
        getWindow().setNavigationBarColor(getColor(R.color.aurora_light_base_end));

        prefs = getSharedPreferences(DoorSoundService.PREF_NAME, MODE_PRIVATE);
        int maxVolume = ((AudioManager) getSystemService(AUDIO_SERVICE))
                .getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        avasPlayer = new AvasPlayer(new BydPermissionContext(this));

        tabInside = findViewById(R.id.tab_inside);
        tabOutside = findViewById(R.id.tab_outside);
        tabHint = findViewById(R.id.tab_hint);
        contentInside = findViewById(R.id.content_inside);
        contentOutside = findViewById(R.id.content_outside);
        textServiceStatus = findViewById(R.id.text_service_status);
        textLastEvent = findViewById(R.id.text_last_event);
        statusDot = findViewById(R.id.status_dot);
        switchEnabled = findViewById(R.id.switch_enabled);

        tabInside.setOnClickListener(v -> switchTab(true));
        tabOutside.setOnClickListener(v -> switchTab(false));

        switchEnabled.setChecked(prefs.getBoolean(DoorSoundService.KEY_ENABLED, false));
        switchEnabled.setOnCheckedChangeListener((view, checked) -> {
            prefs.edit().putBoolean(DoorSoundService.KEY_ENABLED, checked).apply();
            if (checked) {
                startForegroundService(new Intent(this, DoorSoundService.class));
            } else {
                stopService(new Intent(this, DoorSoundService.class));
            }
            syncMasterSwitch();
            refreshAllWarnings();
            updateStatus();
        });

        findViewById(R.id.btn_diagnostics).setOnClickListener(v ->
                startActivity(new Intent(this, DiagnosticsActivity.class)));

        buildGrid((LinearLayout) contentInside, true, maxVolume);
        buildGrid((LinearLayout) contentOutside, false, maxVolume);

        switchTab(true);
        syncMasterSwitch();
        requestStoragePermission();
        updateFileLabels();
        refreshAllWarnings();
        updateStatus();

        refreshHandler = new Handler(Looper.getMainLooper());
        refreshRunnable = new StatusRefresher(this, refreshHandler);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // The switch reflects a stored preference, so "Enabled" with a stopped
        // service was a reachable and self-contradictory state -- e.g. after the
        // system killed the service. Opening the app repairs it.
        if (prefs.getBoolean(DoorSoundService.KEY_ENABLED, false)
                && !DoorSoundService.isRunning()) {
            startForegroundService(new Intent(this, DoorSoundService.class));
        }
        updateFileLabels();
        refreshAllWarnings();
        updateStatus();
        refreshHandler.postDelayed(refreshRunnable, StatusRefresher.INTERVAL_MS);
    }

    @Override
    protected void onPause() {
        super.onPause();
        refreshHandler.removeCallbacks(refreshRunnable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (avasPlayer != null) {
            avasPlayer.stop();
        }
    }

    // -------------------------------------------------------------------- grid

    private void buildGrid(LinearLayout container, boolean inside, int maxVolume) {
        LayoutInflater inflater = LayoutInflater.from(this);
        SoundEvent[] events = SoundEvent.VALUES;
        int layout = inside ? R.layout.card_inside_event : R.layout.card_outside_event;
        LinearLayout row = null;

        for (int i = 0; i < events.length; i++) {
            if (i % COLUMNS == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
                if (i > 0) {
                    rp.topMargin = getResources().getDimensionPixelSize(R.dimen.space_3);
                }
                container.addView(row, rp);
            }

            View card = inflater.inflate(layout, row, false);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
            if (i % COLUMNS > 0) {
                cp.leftMargin = getResources().getDimensionPixelSize(R.dimen.space_3);
            }
            row.addView(card, cp);

            if (inside) {
                insideCards[i] = card;
                setupInsideCard(card, events[i], REQ_FILE_BASE + i, maxVolume);
            } else {
                outsideCards[i] = card;
                setupOutsideCard(card, events[i]);
            }
        }
    }

    private void setupInsideCard(View card, SoundEvent event, int reqCode, int maxVolume) {
        ((TextView) card.findViewById(R.id.card_title)).setText(event.titleRes);

        Switch sw = card.findViewById(R.id.card_switch);
        sw.setChecked(prefs.getBoolean(event.enabledKey, true));
        styleSwitch(sw, sw.isChecked());
        sw.setOnCheckedChangeListener((v, checked) -> {
            prefs.edit().putBoolean(event.enabledKey, checked).apply();
            styleSwitch(sw, checked);
            refreshAllWarnings();
        });

        SeekBar seekBar = card.findViewById(R.id.card_seekbar);
        TextView volLabel = card.findViewById(R.id.card_volume_label);
        seekBar.setMax(maxVolume);
        int current = Math.min(prefs.getInt(event.volumeKey, DoorSoundService.DEFAULT_VOLUME),
                maxVolume);
        seekBar.setProgress(current);
        volLabel.setText(String.valueOf(current));
        seekBar.setOnSeekBarChangeListener(
                new VolumeChangeListener(volLabel, event.volumeKey, prefs));

        card.findViewById(R.id.card_btn_select).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("audio/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(
                    Intent.createChooser(intent, getString(R.string.select_audio_file)), reqCode);
        });

        TextView fileLabel = card.findViewById(R.id.card_file_label);
        card.findViewById(R.id.card_btn_clear).setOnClickListener(v -> {
            String old = prefs.getString(event.pathKey, null);
            if (old != null) {
                new File(old).delete();
            }
            prefs.edit().remove(event.pathKey).apply();
            fileLabel.setText(R.string.no_file_selected);
            refreshAllWarnings();
        });
    }

    private void setupOutsideCard(View card, SoundEvent event) {
        ((TextView) card.findViewById(R.id.card_title)).setText(event.titleRes);

        Switch sw = card.findViewById(R.id.card_switch);
        sw.setChecked(prefs.getBoolean(event.outsideEnabledKey, false));
        styleSwitch(sw, sw.isChecked());
        sw.setOnCheckedChangeListener((v, checked) -> {
            prefs.edit().putBoolean(event.outsideEnabledKey, checked).apply();
            styleSwitch(sw, checked);
            refreshAllWarnings();
        });

        Spinner spinner = card.findViewById(R.id.card_spinner);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this, R.array.pattern_names, R.layout.spinner_item);
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(prefs.getInt(event.outsidePatternKey, AvasPlayer.PATTERN_NONE) + 1);
        // Attached after setSelection so restoring the saved value does not look
        // like a user choice and silently self-enable every event on launch.
        spinner.post(() -> spinner.setOnItemSelectedListener(
                new PatternSelectListener(prefs, event, sw, this)));

        Button preview = card.findViewById(R.id.card_btn_preview);
        preview.setOnClickListener(v -> {
            int pattern = spinner.getSelectedItemPosition() - 1;
            if (pattern >= 0 && avasPlayer != null) {
                avasPlayer.play(pattern);
            }
        });
    }

    // ---------------------------------------------------------------- warnings

    /**
     * The trap this closes: the event log updates on every event regardless of
     * settings, while playback needs the master switch AND the per-event switch
     * AND a file. Previewing worked, so the app looked configured when it was
     * not. Now each card says exactly why it will stay silent.
     */
    void refreshAllWarnings() {
        boolean master = prefs.getBoolean(DoorSoundService.KEY_ENABLED, false);
        SoundEvent[] events = SoundEvent.VALUES;
        for (int i = 0; i < events.length; i++) {
            refreshWarning(insideCards[i], events[i], true, master);
            refreshWarning(outsideCards[i], events[i], false, master);
        }
    }

    private void refreshWarning(View card, SoundEvent event, boolean inside, boolean master) {
        TextView warn = card.findViewById(R.id.card_warning);
        int msg = 0;
        if (!master) {
            msg = R.string.warn_master_off;
        } else if (!prefs.getBoolean(inside ? event.enabledKey : event.outsideEnabledKey, inside)) {
            msg = R.string.warn_event_off;
        } else if (inside) {
            String path = prefs.getString(event.pathKey, null);
            if (path == null || path.isEmpty()) {
                msg = R.string.warn_no_file;
            }
        } else if (prefs.getInt(event.outsidePatternKey, AvasPlayer.PATTERN_NONE) < 0) {
            msg = R.string.warn_no_pattern;
        }

        if (msg == 0) {
            warn.setVisibility(View.GONE);
        } else {
            warn.setText(msg);
            warn.setVisibility(View.VISIBLE);
        }
    }

    // -------------------------------------------------------------------- tabs

    private void switchTab(boolean inside) {
        contentInside.setVisibility(inside ? View.VISIBLE : View.GONE);
        contentOutside.setVisibility(inside ? View.GONE : View.VISIBLE);
        tabInside.setBackgroundResource(
                inside ? R.drawable.glass_segmented_active : android.R.color.transparent);
        tabOutside.setBackgroundResource(
                inside ? android.R.color.transparent : R.drawable.glass_segmented_active);
        tabInside.setTextColor(getColor(inside ? R.color.mint_700 : R.color.fg_secondary));
        tabOutside.setTextColor(getColor(inside ? R.color.fg_secondary : R.color.mint_700));
        tabHint.setText(inside ? R.string.hint_inside : R.string.hint_outside);
    }

    private void styleSwitch(Switch sw, boolean on) {
        sw.setTrackResource(on
                ? R.drawable.glass_switch_track_on
                : R.drawable.glass_switch_track_off);
    }

    private void syncMasterSwitch() {
        styleSwitch(switchEnabled, switchEnabled.isChecked());
    }

    // ------------------------------------------------------------ file picking

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) {
            return;
        }
        Uri uri = data.getData();
        int index = requestCode - REQ_FILE_BASE;
        if (uri == null || index < 0 || index >= SoundEvent.VALUES.length) {
            return;
        }
        SoundEvent event = SoundEvent.VALUES[index];

        String previous = prefs.getString(event.pathKey, null);
        String localPath = copyToLocal(uri, event.pathKey);
        if (localPath != null) {
            if (previous != null && !previous.equals(localPath)) {
                new File(previous).delete();
            }
            // Choosing a sound is an unambiguous request for that sound to play.
            prefs.edit()
                    .putString(event.pathKey, localPath)
                    .putBoolean(event.enabledKey, true)
                    .apply();
            Switch sw = insideCards[index].findViewById(R.id.card_switch);
            sw.setChecked(true);
            styleSwitch(sw, true);
        }
        updateFileLabels();
        refreshAllWarnings();
    }

    private String copyToLocal(Uri uri, String key) {
        File dir = new File(getFilesDir(), "sounds");
        dir.mkdirs();
        String filename = getFileName(uri);
        if (filename == null) {
            filename = key + ".audio";
        }
        File dest = new File(dir, filename);
        try (InputStream in = getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(dest)) {
            if (in == null) {
                return null;
            }
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            return dest.getAbsolutePath();
        } catch (Exception e) {
            return null;
        }
    }

    private String getFileName(Uri uri) {
        String name = null;
        if ("content".equals(uri.getScheme())) {
            try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
                if (c != null && c.moveToFirst()) {
                    int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (idx >= 0) {
                        name = c.getString(idx);
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return name != null ? name : uri.getLastPathSegment();
    }

    private void updateFileLabels() {
        SoundEvent[] events = SoundEvent.VALUES;
        for (int i = 0; i < events.length; i++) {
            TextView label = insideCards[i].findViewById(R.id.card_file_label);
            String path = prefs.getString(events[i].pathKey, null);
            if (path == null || path.isEmpty()) {
                label.setText(R.string.no_file_selected);
            } else {
                label.setText(new File(path).getName());
            }
        }
    }

    // ------------------------------------------------------------------ status

    void updateStatus() {
        boolean running = DoorSoundService.isRunning();
        textServiceStatus.setText(running ? R.string.status_running : R.string.status_stopped);
        int color = running ? R.color.success : R.color.danger;
        textServiceStatus.setTextColor(getColor(color));
        statusDot.getBackground().setTint(getColor(color));

        String lastEvent = prefs.getString(DoorSoundService.KEY_LAST_EVENT, null);
        if (lastEvent != null) {
            textLastEvent.setText(lastEvent);
        } else {
            textLastEvent.setText(R.string.no_events_yet);
        }
    }

    private void requestStoragePermission() {
        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_PERMISSION);
        }
    }
}
