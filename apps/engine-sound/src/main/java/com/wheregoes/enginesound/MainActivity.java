package com.wheregoes.enginesound;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import java.lang.reflect.Method;

/**
 * Engine Voice Simulator control.
 *
 * All vehicle I/O runs on a single {@link HandlerThread}. That is load bearing:
 * the previous version spawned a thread per tap, and each thread wrote its own
 * readback into the label, so with several taps in flight the label showed
 * whichever thread finished last rather than the newest value. Serialising the
 * I/O makes out-of-order readbacks impossible.
 */
public class MainActivity extends Activity {

    private static final String TAG = "EngineSound";

    private static final String PREFS = "engine_sound_prefs";
    private static final String KEY_NAME_PREFIX = "preset_name_";

    private static final int D = 1002;

    private static final int FID_STATE_GET    = 0x48F0000A;
    private static final int FID_STATE_SET    = 0x3E300020;
    private static final int FID_SRC_TYPE_GET = 0x48F00010;
    private static final int FID_SRC_TYPE_SET = 0x3E300038;
    private static final int FID_HAS_SIM      = 0x48F00000;
    private static final int FID_HAS_SRC      = 0x48F00013;
    private static final int FID_PA_CONTROL   = 0xAA000148;
    private static final int FID_MCU_SPEAK    = 0xAA000142;
    private static final int FID_FM_SPEAK     = 0xAA00011A;
    private static final int FID_AVAS_CFG     = 0xAA000171;

    /** Feature ids written, in order, to bring the simulator up. */
    private static final int[] SIM_ENABLE_FIDS = {
            FID_PA_CONTROL, FID_MCU_SPEAK, FID_FM_SPEAK, FID_AVAS_CFG, FID_STATE_SET
    };

    /**
     * How many presets to offer.
     *
     * This is deliberately a fixed number, not a probed one. The MCU stores and
     * echoes back *any* SRC_TYPE we write -- measured to 200 on a Dolphin, with
     * only 0 rejected -- so a set/read-back probe cannot discover a bound; it
     * just walks to whatever ceiling the probe uses. No register reports the
     * count either: 0x48F00013 returns 1, meaning "a voice source exists".
     *
     * So the real number of distinct sounds is only knowable by ear, which is
     * what the long-press naming is for. 20 is a generous browsing range around
     * the 10 that live testing confirmed.
     */
    private static final int PRESET_RANGE = 20;
    /** Taps inside this window collapse into a single CAN write. */
    private static final long APPLY_DELAY_MS = 150;
    private static final long CYCLE_DWELL_MS = 2500;
    /** Pause between a write and its readback; the MCU does not answer instantly. */
    private static final long SETTLE_MS = 120;

    private Object mgr;
    private Method setIntMethod;
    private Method getIntMethod;

    private TextView typeLabel;
    private TextView presetName;
    private TextView statusLabel;
    private TextView simCaption;
    private View statusDot;
    private Switch simToggle;
    private Button prevBtn;
    private Button nextBtn;
    private Button cycleBtn;
    private LinearLayout chipsRow;

    private SharedPreferences prefs;
    private HandlerThread ioThread;
    private Handler io;

    /** Written on the main thread, read on the io thread. */
    private volatile int pendingType = 1;
    private volatile boolean cycling = false;
    private volatile boolean ready = false;
    private boolean simOn = false;
    private int cycleIndex = 1;

    private final CompoundButton.OnCheckedChangeListener simListener =
            (btn, checked) -> {
                if (checked) {
                    enableSim();
                } else {
                    disableSim();
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        typeLabel = findViewById(R.id.type_label);
        presetName = findViewById(R.id.preset_name);
        statusLabel = findViewById(R.id.status_label);
        simCaption = findViewById(R.id.sim_caption);
        statusDot = findViewById(R.id.status_dot);
        simToggle = findViewById(R.id.sim_toggle);
        prevBtn = findViewById(R.id.prev_btn);
        nextBtn = findViewById(R.id.next_btn);
        cycleBtn = findViewById(R.id.cycle_btn);
        chipsRow = findViewById(R.id.chips_row);

        prevBtn.setOnClickListener(v -> setPending(pendingType - 1));
        nextBtn.setOnClickListener(v -> setPending(pendingType + 1));
        cycleBtn.setOnClickListener(v -> toggleCycle());
        simToggle.setOnCheckedChangeListener(simListener);

        buildChips(PRESET_RANGE);
        setControlsEnabled(false);

        ioThread = new HandlerThread("avas-io");
        ioThread.start();
        io = new Handler(ioThread.getLooper());
        io.post(this::initVehicle);
    }

    // ---------------------------------------------------------------- lifecycle

    @Override
    protected void onResume() {
        super.onResume();
        // First launch: initVehicle() does its own refresh once it is connected.
        if (ready) {
            io.post(this::readVehicleState);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopCycle();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (ioThread != null) {
            ioThread.quitSafely();
        }
    }

    // ------------------------------------------------------------- vehicle init

    /** Runs on the io thread. */
    private void initVehicle() {
        BydPermissionContext ctx = new BydPermissionContext(this);
        try {
            mgr = ctx.getSystemService("auto");
            if (mgr == null) {
                fail(getString(R.string.err_service_null));
                return;
            }
            setIntMethod = mgr.getClass().getMethod("setInt", int.class, int.class, int.class);
            getIntMethod = mgr.getClass().getMethod("getInt", int.class, int.class);
        } catch (Throwable t) {
            Log.e(TAG, "auto service unavailable", t);
            fail(getString(R.string.err_service, String.valueOf(t.getMessage())));
            return;
        }

        Integer hasSim = getInt(FID_HAS_SIM);
        Integer hasSrc = getInt(FID_HAS_SRC);
        // 0x48F00013 reports *whether* a source exists, not how many. It cannot
        // supply the preset count, which is why the range has to be probed.
        Log.i(TAG, "FID_HAS_SIM=" + hasSim + " FID_HAS_SRC=" + hasSrc);

        if (hasSim == null) {
            fail(getString(R.string.sim_unreadable));
            return;
        }
        if (hasSim != 2) {
            fail(getString(R.string.sim_unsupported, hasSim));
            return;
        }

        runOnUiThread(() -> setControlsEnabled(true));
        ready = true;
        readVehicleState();
    }

    /** The MCU needs a moment before a readback reflects the write. */
    private void settle() {
        try {
            Thread.sleep(SETTLE_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Runs on the io thread. */
    private void readVehicleState() {
        Integer state = getInt(FID_STATE_GET);
        Integer src = getInt(FID_SRC_TYPE_GET);
        runOnUiThread(() -> {
            if (src != null && src > 0) {
                pendingType = clamp(src);
            }
            renderPreset(pendingType);
            syncToggle(state != null && state == 1);
            if (state == null) {
                setStatus(getString(R.string.read_failed), R.color.warning);
            } else {
                setStatus(getString(simOn ? R.string.sim_on : R.string.sim_off, pendingType),
                        simOn ? R.color.success : R.color.fg_secondary);
            }
        });
    }

    private void fail(String message) {
        runOnUiThread(() -> {
            setStatus(message, R.color.danger);
            setControlsEnabled(false);
            simToggle.setEnabled(false);
        });
    }

    // ------------------------------------------------------------ preset select

    private int clamp(int type) {
        return Math.max(1, Math.min(type, PRESET_RANGE));
    }

    /** Main thread. Updates the UI immediately, then coalesces the CAN write. */
    private void setPending(int type) {
        int want = clamp(type);
        pendingType = want;
        renderPreset(want);
        io.removeCallbacks(applyPending);
        io.postDelayed(applyPending, APPLY_DELAY_MS);
    }

    /**
     * Runs on the io thread: one write per burst of taps, always verified.
     *
     * A method reference, not an anonymous class: the pinned d8 8.2.2-dev
     * crashes dexing the null inner_name of an anonymous InnerClasses entry,
     * and every other app in this repo uses lambdas for the same reason.
     */
    private final Runnable applyPending = this::applyPendingNow;

    private void applyPendingNow() {
        final int want = pendingType;
        if (!setInt(FID_SRC_TYPE_SET, want)) {
            runOnUiThread(() ->
                    setStatus(getString(R.string.write_failed, want), R.color.danger));
            return;
        }
        settle();
        Integer back = getInt(FID_SRC_TYPE_GET);
        if (back == null) {
            runOnUiThread(() ->
                    setStatus(getString(R.string.read_failed), R.color.warning));
            return;
        }
        final int got = back;
        runOnUiThread(() -> {
            if (got != want) {
                // setInt returns success even for values the MCU ignores, so
                // the readback is the only truth. Show what the car actually did.
                pendingType = got;
                renderPreset(got);
                setStatus(getString(R.string.preset_rejected, want, got), R.color.warning);
            } else {
                setStatus(getString(simOn ? R.string.sim_on : R.string.sim_off, got),
                        simOn ? R.color.success : R.color.fg_secondary);
            }
        });
    }

    // -------------------------------------------------------------- simulator

    private void enableSim() {
        io.post(() -> {
            for (int fid : SIM_ENABLE_FIDS) {
                if (!setInt(fid, 1)) {
                    final String hex = String.format("0x%08X", fid);
                    runOnUiThread(() -> {
                        setStatus(getString(R.string.sim_write_failed, hex), R.color.danger);
                        syncToggle(false);
                    });
                    return;
                }
            }
            setInt(FID_SRC_TYPE_SET, pendingType);
            settle();
            Integer state = getInt(FID_STATE_GET);
            final boolean on = state != null && state == 1;
            runOnUiThread(() -> {
                syncToggle(on);
                if (on) {
                    setStatus(getString(R.string.sim_on, pendingType), R.color.success);
                } else {
                    setStatus(getString(R.string.sim_verify_failed), R.color.danger);
                }
            });
        });
    }

    private void disableSim() {
        stopCycle();
        io.post(() -> {
            setInt(FID_STATE_SET, 0);
            Integer state = getInt(FID_STATE_GET);
            final boolean on = state != null && state == 1;
            runOnUiThread(() -> {
                syncToggle(on);
                setStatus(on ? getString(R.string.sim_verify_failed)
                             : getString(R.string.sim_turning_off),
                        on ? R.color.danger : R.color.fg_secondary);
            });
        });
    }

    // ------------------------------------------------------------------ cycling

    private void toggleCycle() {
        if (cycling) {
            stopCycle();
            setStatus(getString(R.string.cycle_stopped, pendingType), R.color.fg_secondary);
            return;
        }
        // The old version silently flipped the toggle on here, which raced the
        // cycle loop for FID_SRC_TYPE_SET. Ask instead.
        if (!simOn) {
            setStatus(getString(R.string.cycle_needs_sim), R.color.warning);
            return;
        }
        cycling = true;
        cycleIndex = 1;
        cycleBtn.setText(R.string.cycle_stop);
        io.post(cycleStep);
    }

    private void stopCycle() {
        cycling = false;
        if (io != null) {
            io.removeCallbacks(cycleStep);
        }
        cycleBtn.setText(R.string.cycle_all);
    }

    /**
     * One preset per pass, rescheduled rather than sleeping, so the io thread
     * stays free for taps while a cycle is running.
     */
    private final Runnable cycleStep = this::cycleOnce;

    private void cycleOnce() {
        if (!cycling) {
            return;
        }
        final int t = cycleIndex;
        if (t > PRESET_RANGE) {
            runOnUiThread(() -> {
                stopCycle();
                setStatus(getString(R.string.cycle_stopped, PRESET_RANGE),
                        R.color.fg_secondary);
            });
            return;
        }
        boolean ok = setInt(FID_SRC_TYPE_SET, t);
        settle();
        final Integer back = getInt(FID_SRC_TYPE_GET);
        if (!ok || back == null || back != t) {
            runOnUiThread(() -> {
                stopCycle();
                setStatus(getString(R.string.preset_rejected, t,
                        back == null ? t : back), R.color.warning);
            });
            return;
        }
        pendingType = t;
        runOnUiThread(() -> {
            renderPreset(t);
            setStatus(getString(R.string.cycling_now, t), R.color.info);
        });
        cycleIndex = t + 1;
        io.postDelayed(cycleStep, CYCLE_DWELL_MS);
    }

    // ----------------------------------------------------------------- chips/UI

    private void buildChips(int max) {
        chipsRow.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int t = 1; t <= max; t++) {
            final int type = t;
            TextView chip = (TextView) inflater.inflate(R.layout.chip_preset, chipsRow, false);
            chip.setText(String.valueOf(type));
            chip.setOnClickListener(v -> setPending(type));
            chip.setOnLongClickListener(v -> {
                promptRename(type);
                return true;
            });
            chipsRow.addView(chip);
        }
        highlightChip(pendingType);
    }

    private void highlightChip(int type) {
        for (int i = 0; i < chipsRow.getChildCount(); i++) {
            TextView chip = (TextView) chipsRow.getChildAt(i);
            boolean active = (i + 1) == type;
            chip.setBackgroundResource(
                    active ? R.drawable.glass_segmented_active : R.drawable.glass_pill);
            chip.setTextColor(getColor(active ? R.color.mint_700 : R.color.fg_primary));
        }
    }

    private void renderPreset(int type) {
        typeLabel.setText(String.valueOf(type));
        presetName.setText(presetLabel(type));
        highlightChip(type);
    }

    /**
     * BYD publishes no names for these presets -- they are opaque MCU flash
     * sounds, identifiable only by ear. So the owner names them, and the label
     * falls back to the number until they do.
     */
    private String presetLabel(int type) {
        String saved = prefs.getString(KEY_NAME_PREFIX + type, null);
        if (saved != null && !saved.trim().isEmpty()) {
            return saved;
        }
        return getString(R.string.preset_fallback, type);
    }

    private void promptRename(int type) {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        input.setSingleLine(true);
        String current = prefs.getString(KEY_NAME_PREFIX + type, "");
        input.setText(current);
        input.setSelection(current.length());

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.rename_title, type))
                .setView(input)
                .setPositiveButton(R.string.rename_save, (d, w) -> {
                    prefs.edit().putString(KEY_NAME_PREFIX + type,
                            input.getText().toString().trim()).apply();
                    renderPreset(pendingType);
                })
                .setNeutralButton(R.string.rename_reset, (d, w) -> {
                    prefs.edit().remove(KEY_NAME_PREFIX + type).apply();
                    renderPreset(pendingType);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void setStatus(String text, int colorRes) {
        statusLabel.setText(text);
        statusLabel.setTextColor(getColor(colorRes));
        statusDot.getBackground().setTint(getColor(colorRes));
    }

    /** Single place the toggle is driven, so the listener cannot re-enter. */
    private void syncToggle(boolean on) {
        simOn = on;
        simToggle.setOnCheckedChangeListener(null);
        simToggle.setChecked(on);
        simToggle.setTrackResource(on
                ? R.drawable.glass_switch_track_on
                : R.drawable.glass_switch_track_off);
        simToggle.setOnCheckedChangeListener(simListener);
        simCaption.setTextColor(getColor(on ? R.color.mint_700 : R.color.fg_secondary));
    }

    private void setControlsEnabled(boolean enabled) {
        prevBtn.setEnabled(enabled);
        nextBtn.setEnabled(enabled);
        cycleBtn.setEnabled(enabled);
        float alpha = enabled ? 1f : 0.4f;
        prevBtn.setAlpha(alpha);
        nextBtn.setAlpha(alpha);
        cycleBtn.setAlpha(alpha);
    }

    // ------------------------------------------------------------- vehicle I/O

    /** @return false if the write threw; a true return does NOT mean accepted. */
    private boolean setInt(int fid, int val) {
        try {
            setIntMethod.invoke(mgr, D, fid, val);
            return true;
        } catch (Throwable t) {
            Log.w(TAG, String.format("setInt(0x%08X, %d) failed", fid, val), t);
            return false;
        }
    }

    /** @return null when the read failed, so callers cannot mistake it for data. */
    private Integer getInt(int fid) {
        try {
            return (Integer) getIntMethod.invoke(mgr, D, fid);
        } catch (Throwable t) {
            Log.w(TAG, String.format("getInt(0x%08X) failed", fid), t);
            return null;
        }
    }
}
