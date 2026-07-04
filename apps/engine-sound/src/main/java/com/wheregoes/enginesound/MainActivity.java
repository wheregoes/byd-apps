package com.wheregoes.enginesound;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.lang.reflect.Method;

public class MainActivity extends Activity {

    private static final int D = 1002;

    private static final int FID_STATE_GET        = 0x48F0000A;
    private static final int FID_STATE_SET        = 0x3E300020;
    private static final int FID_SRC_TYPE_GET     = 0x48F00010;
    private static final int FID_SRC_TYPE_SET     = 0x3E300038;
    private static final int FID_HAS_SIM          = 0x48F00000;
    private static final int FID_HAS_SRC          = 0x48F00013;
    private static final int FID_AVAS_PITCH       = 0xAA000104;
    private static final int FID_PA_CONTROL       = 0xAA000148;
    private static final int FID_MCU_SPEAK        = 0xAA000142;
    private static final int FID_FM_SPEAK         = 0xAA00011A;
    private static final int FID_AVAS_CFG         = 0xAA000171;

    private Object mgr;
    private Method setIntMethod;
    private Method getIntMethod;

    private TextView typeLabel;
    private TextView statusLabel;
    private ToggleButton powerToggle;
    private Button prevBtn;
    private Button nextBtn;
    private Button cycleBtn;

    private int currentType = 1;
    private boolean cycling = false;
    private Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        BydPermissionContext ctx = new BydPermissionContext(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(60, 80, 60, 60);
        root.setBackgroundColor(Color.parseColor("#1a1a2e"));

        TextView title = new TextView(this);
        title.setText("ENGINE SOUND SELECTOR");
        title.setTextSize(28);
        title.setTextColor(Color.parseColor("#00d4ff"));
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, 30);
        root.addView(title);

        typeLabel = new TextView(this);
        typeLabel.setText("1");
        typeLabel.setTextSize(120);
        typeLabel.setTextColor(Color.WHITE);
        typeLabel.setTypeface(Typeface.DEFAULT_BOLD);
        typeLabel.setGravity(Gravity.CENTER);
        typeLabel.setPadding(0, 20, 0, 20);
        root.addView(typeLabel);

        statusLabel = new TextView(this);
        statusLabel.setText("Connecting...");
        statusLabel.setTextSize(16);
        statusLabel.setTextColor(Color.parseColor("#888888"));
        statusLabel.setGravity(Gravity.CENTER);
        statusLabel.setPadding(0, 0, 0, 30);
        root.addView(statusLabel);

        LinearLayout toggleRow = new LinearLayout(this);
        toggleRow.setOrientation(LinearLayout.HORIZONTAL);
        toggleRow.setGravity(Gravity.CENTER);
        toggleRow.setPadding(0, 0, 0, 20);

        powerToggle = new ToggleButton(this);
        powerToggle.setTextOff("ENGINE SOUND: OFF");
        powerToggle.setTextOn("ENGINE SOUND: ON");
        powerToggle.setTextSize(18);
        powerToggle.setTextColor(Color.WHITE);
        powerToggle.setPadding(40, 20, 40, 20);
        LinearLayout.LayoutParams toggleParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        toggleRow.addView(powerToggle, toggleParams);
        root.addView(toggleRow);

        LinearLayout navRow = new LinearLayout(this);
        navRow.setOrientation(LinearLayout.HORIZONTAL);
        navRow.setGravity(Gravity.CENTER);
        navRow.setPadding(0, 10, 0, 10);

        int navBtnSize = 160;

        prevBtn = new Button(this);
        prevBtn.setText("< PREV");
        prevBtn.setTextSize(22);
        prevBtn.setTextColor(Color.WHITE);
        prevBtn.setBackgroundColor(Color.parseColor("#16213e"));
        LinearLayout.LayoutParams prevParams = new LinearLayout.LayoutParams(navBtnSize, navBtnSize);
        prevParams.setMargins(20, 0, 20, 0);
        navRow.addView(prevBtn, prevParams);

        nextBtn = new Button(this);
        nextBtn.setText("NEXT >");
        nextBtn.setTextSize(22);
        nextBtn.setTextColor(Color.WHITE);
        nextBtn.setBackgroundColor(Color.parseColor("#16213e"));
        LinearLayout.LayoutParams nextParams = new LinearLayout.LayoutParams(navBtnSize, navBtnSize);
        nextParams.setMargins(20, 0, 20, 0);
        navRow.addView(nextBtn, nextParams);

        root.addView(navRow);

        cycleBtn = new Button(this);
        cycleBtn.setText("CYCLE ALL SOUNDS");
        cycleBtn.setTextSize(18);
        cycleBtn.setTextColor(Color.parseColor("#00d4ff"));
        cycleBtn.setBackgroundColor(Color.parseColor("#16213e"));
        cycleBtn.setPadding(40, 30, 40, 30);
        LinearLayout.LayoutParams cycleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        cycleParams.setMargins(40, 10, 40, 10);
        root.addView(cycleBtn, cycleParams);

        setContentView(root);

        try {
            mgr = ctx.getSystemService("auto");
            if (mgr == null) {
                statusLabel.setText("ERROR: auto service null");
                statusLabel.setTextColor(Color.RED);
                return;
            }
            setIntMethod = mgr.getClass().getMethod("setInt", int.class, int.class, int.class);
            getIntMethod = mgr.getClass().getMethod("getInt", int.class, int.class);
        } catch (Exception e) {
            statusLabel.setText("ERROR: " + e.getMessage());
            statusLabel.setTextColor(Color.RED);
            return;
        }

        int hasSim = getInt(FID_HAS_SIM);
        if (hasSim != 2) {
            statusLabel.setText("Engine simulator not supported (" + hasSim + ")");
            statusLabel.setTextColor(Color.RED);
            disableControls();
            return;
        }

        powerToggle.setOnCheckedChangeListener((btn, checked) -> {
            if (checked) {
                enableSim();
            } else {
                setInt(FID_STATE_SET, 0);
                statusLabel.setText("Simulator OFF");
            }
        });

        prevBtn.setOnClickListener(v -> {
            if (currentType > 1) {
                currentType--;
                setType(currentType);
            }
        });

        nextBtn.setOnClickListener(v -> {
            currentType++;
            setType(currentType);
        });

        cycleBtn.setOnClickListener(v -> {
            if (cycling) {
                cycling = false;
                cycleBtn.setText("CYCLE ALL SOUNDS");
            } else {
                cycling = true;
                cycleBtn.setText("STOP CYCLE");
                startCycle();
            }
        });

        refreshStatus();
    }

    private void enableSim() {
        new Thread(() -> {
            setInt(FID_PA_CONTROL, 1);
            setInt(FID_MCU_SPEAK, 1);
            setInt(FID_FM_SPEAK, 1);
            setInt(FID_AVAS_CFG, 1);
            setInt(FID_STATE_SET, 1);
            setInt(FID_SRC_TYPE_SET, currentType);
            runOnUiThread(() -> {
                statusLabel.setText("Sound " + currentType + " | ON");
                statusLabel.setTextColor(Color.parseColor("#00ff88"));
            });
        }).start();
    }

    private void setType(int type) {
        typeLabel.setText(String.valueOf(type));
        new Thread(() -> {
            setInt(FID_SRC_TYPE_SET, type);
            int readback = getInt(FID_SRC_TYPE_GET);
            runOnUiThread(() -> {
                typeLabel.setText(String.valueOf(readback));
                if (powerToggle.isChecked()) {
                    statusLabel.setText("Sound " + readback + " | ON");
                } else {
                    statusLabel.setText("Sound " + readback + " | OFF (toggle ON)");
                }
                toast("Sound " + readback);
            });
        }).start();
    }

    private void startCycle() {
        if (!powerToggle.isChecked()) {
            powerToggle.setChecked(true);
        }
        new Thread(() -> {
            for (int t = 1; t <= 50 && cycling; t++) {
                final int ft = t;
                setInt(FID_SRC_TYPE_SET, t);
                int readback = getInt(FID_SRC_TYPE_GET);
                if (readback != t) {
                    break;
                }
                runOnUiThread(() -> {
                    typeLabel.setText(String.valueOf(ft));
                    statusLabel.setText("Sound " + ft + " | CYCLING...");
                });
                try { Thread.sleep(2500); } catch (Exception e) { break; }
            }
            cycling = false;
            runOnUiThread(() -> cycleBtn.setText("CYCLE ALL SOUNDS"));
        }).start();
    }

    private void refreshStatus() {
        new Thread(() -> {
            int state = getInt(FID_STATE_GET);
            int srcType = getInt(FID_SRC_TYPE_GET);
            runOnUiThread(() -> {
                currentType = srcType > 0 ? srcType : 1;
                typeLabel.setText(String.valueOf(currentType));
                powerToggle.setOnCheckedChangeListener(null);
                powerToggle.setChecked(state == 1);
                powerToggle.setOnCheckedChangeListener((btn, checked) -> {
                    if (checked) {
                        enableSim();
                    } else {
                        setInt(FID_STATE_SET, 0);
                        statusLabel.setText("Simulator OFF");
                    }
                });
                if (state == 1) {
                    statusLabel.setText("Sound " + currentType + " | ON");
                    statusLabel.setTextColor(Color.parseColor("#00ff88"));
                } else {
                    statusLabel.setText("Sound " + currentType + " | OFF");
                    statusLabel.setTextColor(Color.parseColor("#888888"));
                }
            });
        }).start();
    }

    private void disableControls() {
        powerToggle.setEnabled(false);
        prevBtn.setEnabled(false);
        nextBtn.setEnabled(false);
        cycleBtn.setEnabled(false);
    }

    private void setInt(int fid, int val) {
        try {
            setIntMethod.invoke(mgr, D, fid, val);
        } catch (Exception e) { }
    }

    private int getInt(int fid) {
        try {
            return (int) (Integer) getIntMethod.invoke(mgr, D, fid);
        } catch (Exception e) { return -999; }
    }

    private void toast(String msg) {
        runOnUiThread(() -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show());
    }

    @Override
    protected void onPause() {
        super.onPause();
        cycling = false;
    }
}
