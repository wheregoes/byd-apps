package com.wheregoes.bydprobe;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.Button;
import android.view.Gravity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.util.Log;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.util.Arrays;

public class ProbeActivity extends Activity {

    private TextView logView;
    private StringBuilder logBuffer = new StringBuilder();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        root.setPadding(20, 20, 20, 20);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER);

        Button btnAc = makeButton("Probe AC");
        btnAc.setOnClickListener(v -> probeAc());
        btnRow.addView(btnAc);

        Button btnDoor = makeButton("Probe DoorLock");
        btnDoor.setOnClickListener(v -> probeDoorLock());
        btnRow.addView(btnDoor);

        Button btnPano = makeButton("Probe Panorama");
        btnPano.setOnClickListener(v -> probePanorama());
        btnRow.addView(btnPano);

        Button btnAcSet = makeButton("AC: Read Current");
        btnAcSet.setOnClickListener(v -> probeAcFullState());
        btnRow.addView(btnAcSet);

        Button btnClear = makeButton("Clear");
        btnClear.setOnClickListener(v -> { logBuffer.setLength(0); logView.setText(""); });
        btnRow.addView(btnClear);

        root.addView(btnRow);

        ScrollView scroll = new ScrollView(this);
        logView = new TextView(this);
        logView.setTextColor(Color.GREEN);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        logView.setPadding(10, 10, 10, 10);
        scroll.addView(logView);

        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT, 1f);
        root.addView(scroll, scrollParams);

        setContentView(root);
        log("=== BYD Probe v1.0 ===");
        log("Auto-running all probes...");

        new Thread(() -> {
            probeAc();
            probeAcFullState();
            probeDoorLock();
            probePanorama();
            log("\n=== ALL PROBES COMPLETE ===");
        }).start();
    }

    private Button makeButton(String text) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        p.setMargins(5, 0, 5, 10);
        btn.setLayoutParams(p);
        return btn;
    }

    private void log(String msg) {
        Log.d("BYD_PROBE", msg);
        logBuffer.append(msg).append("\n");
        runOnUiThread(() -> logView.setText(logBuffer.toString()));
    }

    // ===== AC PROBE =====
    private void probeAc() {
        log("\n--- AC DEVICE PROBE ---");
        try {
            Class<?> acClass = Class.forName("android.hardware.bydauto.ac.BYDAutoAcDevice");
            Method getInstance = acClass.getMethod("getInstance", android.content.Context.class);
            Object acDevice = getInstance.invoke(null, new BydPermissionContext(this));
            log("AC Device instance: " + acDevice);

            // Dump all public methods
            log("\n[AC Methods]");
            Method[] methods = acClass.getMethods();
            for (Method m : methods) {
                String name = m.getName();
                if (name.startsWith("get") || name.startsWith("set") || name.startsWith("start") || name.startsWith("stop") || name.startsWith("has") || name.startsWith("reset")) {
                    StringBuilder sig = new StringBuilder(name + "(");
                    for (Class<?> p : m.getParameterTypes()) {
                        sig.append(p.getSimpleName()).append(",");
                    }
                    sig.append(") -> ").append(m.getReturnType().getSimpleName());
                    log("  " + sig);
                }
            }

            // Key constants
            log("\n[AC Permissions]");
            tryStaticField(acClass, "AC_GET_PERM");
            tryStaticField(acClass, "AC_SET_PERM");
            tryStaticField(acClass, "AC_COMMON_PERM");

            // Temperature ranges
            log("\n[AC Temp Ranges]");
            tryStaticField(acClass, "AC_TEMP_IN_CELSIUS_MIN");
            tryStaticField(acClass, "AC_TEMP_IN_CELSIUS_MAX");
            tryStaticField(acClass, "AC_TEMP_IN_CELSIUS_HALF_MIN");
            tryStaticField(acClass, "AC_TEMP_IN_CELSIUS_HALF_MAX");

            // Remote control time constants
            log("\n[Remote Ctrl Times]");
            tryStaticField(acClass, "AC_REMOTE_CTRL_TIME_10");
            tryStaticField(acClass, "AC_REMOTE_CTRL_TIME_15");
            tryStaticField(acClass, "AC_REMOTE_CTRL_TIME_20");
            tryStaticField(acClass, "AC_REMOTE_CTRL_TIME_25");
            tryStaticField(acClass, "AC_REMOTE_CTRL_TIME_30");

            // Feature IDs
            log("\n[Feature IDs]");
            tryStaticField(acClass, "FEATURE_AC_REMOTE_CTL");
            tryStaticField(acClass, "FEATURE_AC_AUTO_MODE");
            tryStaticField(acClass, "FEATURE_AC_DEFROST");

            // Wind levels
            tryStaticField(acClass, "AC_WINDLEVEL_0");
            tryStaticField(acClass, "AC_WINDLEVEL_7");

            // Control source
            tryStaticField(acClass, "AC_CTRL_SOURCE_UI_KEY");
            tryStaticField(acClass, "AC_CTRL_SOURCE_VOICE");

        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            log("ERROR: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            log("  CAUSE: " + cause.getClass().getSimpleName() + ": " + cause.getMessage());
            for (StackTraceElement st : cause.getStackTrace()) {
                log("    at " + st);
            }
        }
    }

    // ===== AC FULL STATE =====
    private void probeAcFullState() {
        log("\n--- AC FULL STATE ---");
        try {
            Class<?> acClass = Class.forName("android.hardware.bydauto.ac.BYDAutoAcDevice");
            Method getInstance = acClass.getMethod("getInstance", android.content.Context.class);
            Object acDevice = getInstance.invoke(null, new BydPermissionContext(this));

            // Read all status values
            callAndLog(acDevice, acClass, "getAcStartState");
            callAndLog(acDevice, acClass, "getAcOnlineState");
            callAndLog(acDevice, acClass, "getAcControlMode");
            callAndLog(acDevice, acClass, "getAcType");
            callAndLog(acDevice, acClass, "getAcWindLevel");
            callAndLog(acDevice, acClass, "getAcWindMode");
            callAndLog(acDevice, acClass, "getAcCycleMode");
            callAndLog(acDevice, acClass, "getAcMaxCoolingState");
            callAndLog(acDevice, acClass, "getAcCompressorMode");
            callAndLog(acDevice, acClass, "getAcVentilationState");
            callAndLog(acDevice, acClass, "getAcWarmState");
            callAndLog(acDevice, acClass, "getAcRemoteCtrlTime");
            callAndLog(acDevice, acClass, "getTemperatureUnit");
            callAndLog(acDevice, acClass, "getAcTemperatureControlMode");
            callAndLog(acDevice, acClass, "getAcRearPanelLockState");
            callAndLog(acDevice, acClass, "getAcKeyActionState");
            callAndLog(acDevice, acClass, "getAcFaultNumShownState");
            callAndLog(acDevice, acClass, "getAutoCleanAirState");
            callAndLog(acDevice, acClass, "getQuickCleanAirState");
            callAndLog(acDevice, acClass, "getHighTempAntivirusState");
            callAndLog(acDevice, acClass, "getHighTempAntivirusCountDown");

            // Temperatures
            log("\n[Temperatures]");
            try {
                Method getTemp = acClass.getMethod("getTemprature", int.class);
                // Zone 1 = set temp main, 2 = deputy, 3 = rear, 4 = outside
                for (int zone = 0; zone <= 5; zone++) {
                    Object result = getTemp.invoke(acDevice, zone);
                    log("  getTemprature(" + zone + ") = " + result);
                }
            } catch (Exception e) {
                log("  getTemprature error: " + e.getMessage());
            }

            // Feature checks
            log("\n[Feature Checks]");
            try {
                Method hasFeature = acClass.getMethod("hasFeature", String.class);
                String[] features = {"FEATURE_AC_REMOTE_CTL", "FEATURE_AC_AUTO_MODE", "FEATURE_AC_DEFROST",
                    "FEATURE_AC_REAR_PANEL_3F1", "FEATURE_AC_REAR_PANEL_408", "FEATURE_AC_WARM_SETTING", "FEATURE_AC_449_ONLINE"};
                for (String fname : features) {
                    try {
                        Field f = acClass.getField(fname);
                        String featureStr = (String) f.get(null);
                        Object result = hasFeature.invoke(acDevice, featureStr);
                        log("  hasFeature(" + fname + "=" + featureStr + ") = " + result);
                    } catch (Exception e) {
                        log("  hasFeature(" + fname + ") error: " + e.getMessage());
                    }
                }
            } catch (Exception e) {
                log("  hasFeature error: " + e.getMessage());
            }

        } catch (Exception e) {
            log("ERROR: " + e.getMessage());
        }
    }

    // ===== DOOR LOCK PROBE =====
    private void probeDoorLock() {
        log("\n--- DOOR LOCK DEVICE PROBE ---");
        try {
            Class<?> dlClass = Class.forName("android.hardware.bydauto.doorlock.BYDAutoDoorLockDevice");
            Method getInstance = dlClass.getMethod("getInstance", android.content.Context.class);
            Object dlDevice = getInstance.invoke(null, new BydPermissionContext(this));
            log("DoorLock Device instance: " + dlDevice);

            // Dump constants
            log("\n[DoorLock Constants]");
            tryStaticField(dlClass, "DOOR_LOCK_AREA_LEFT_FRONT");
            tryStaticField(dlClass, "DOOR_LOCK_AREA_RIGHT_FRONT");
            tryStaticField(dlClass, "DOOR_LOCK_AREA_LEFT_REAR");
            tryStaticField(dlClass, "DOOR_LOCK_AREA_RIGHT_REAR");
            tryStaticField(dlClass, "DOOR_LOCK_AREA_BACK");
            tryStaticField(dlClass, "DOOR_LOCK_AREA_CHILDLOCK_LEFT");
            tryStaticField(dlClass, "DOOR_LOCK_AREA_CHILDLOCK_RIGHT");
            tryStaticField(dlClass, "DOOR_LOCK_STATE_LOCK");
            tryStaticField(dlClass, "DOOR_LOCK_STATE_UNLOCK");
            tryStaticField(dlClass, "DOOR_LOCK_STATE_INVALID");
            tryStaticField(dlClass, "DOOR_LOCK_GET_PERM");
            tryStaticField(dlClass, "DOOR_LOCK_SET_PERM");
            tryStaticField(dlClass, "DOOR_LOCK_COMMON_PERM");

            // Dump methods
            log("\n[DoorLock Methods]");
            Method[] methods = dlClass.getMethods();
            for (Method m : methods) {
                String name = m.getName();
                if (!name.equals("getClass") && !name.equals("hashCode") && !name.equals("toString") &&
                    !name.equals("notify") && !name.equals("notifyAll") && !name.equals("wait") && !name.equals("equals")) {
                    StringBuilder sig = new StringBuilder(name + "(");
                    for (Class<?> p : m.getParameterTypes()) {
                        sig.append(p.getSimpleName()).append(",");
                    }
                    sig.append(") -> ").append(m.getReturnType().getSimpleName());
                    log("  " + sig);
                }
            }

            // Read door lock status per area
            log("\n[DoorLock Status]");
            Method getDoorLockStatus = dlClass.getMethod("getDoorLockStatus", int.class);
            String[] areaNames = {"LEFT_FRONT", "RIGHT_FRONT", "LEFT_REAR", "RIGHT_REAR", "BACK", "CHILDLOCK_LEFT", "CHILDLOCK_RIGHT"};
            String[] areaFields = {"DOOR_LOCK_AREA_LEFT_FRONT", "DOOR_LOCK_AREA_RIGHT_FRONT", "DOOR_LOCK_AREA_LEFT_REAR",
                "DOOR_LOCK_AREA_RIGHT_REAR", "DOOR_LOCK_AREA_BACK", "DOOR_LOCK_AREA_CHILDLOCK_LEFT", "DOOR_LOCK_AREA_CHILDLOCK_RIGHT"};

            for (int i = 0; i < areaNames.length; i++) {
                try {
                    Field areaField = dlClass.getField(areaFields[i]);
                    int areaValue = areaField.getInt(null);
                    Object status = getDoorLockStatus.invoke(dlDevice, areaValue);
                    log("  " + areaNames[i] + " (area=" + areaValue + "): status=" + status);
                } catch (Exception e) {
                    log("  " + areaNames[i] + ": error=" + e.getMessage());
                }
            }

            // Also try raw area values 0-10
            log("\n[DoorLock Raw Areas 0-10]");
            for (int area = 0; area <= 10; area++) {
                try {
                    Object status = getDoorLockStatus.invoke(dlDevice, area);
                    log("  getDoorLockStatus(" + area + ") = " + status);
                } catch (Exception e) {
                    log("  getDoorLockStatus(" + area + ") error: " + e.getMessage());
                }
            }

            // Check if there's a SET method we missed (try base class)
            log("\n[DoorLock Base Class Methods]");
            Class<?> baseClass = dlClass.getSuperclass();
            if (baseClass != null) {
                log("  Superclass: " + baseClass.getName());
                for (Method m : baseClass.getMethods()) {
                    String name = m.getName();
                    if (name.startsWith("set") || name.equals("postEvent")) {
                        StringBuilder sig = new StringBuilder(name + "(");
                        for (Class<?> p : m.getParameterTypes()) {
                            sig.append(p.getSimpleName()).append(",");
                        }
                        sig.append(") -> ").append(m.getReturnType().getSimpleName());
                        log("  " + sig);
                    }
                }
            }

        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            log("ERROR: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            log("  CAUSE: " + cause.getClass().getSimpleName() + ": " + cause.getMessage());
            for (StackTraceElement st : cause.getStackTrace()) {
                log("    at " + st);
            }
        }
    }

    // ===== PANORAMA PROBE =====
    private void probePanorama() {
        log("\n--- PANORAMA DEVICE PROBE ---");
        try {
            Class<?> panoClass = Class.forName("android.hardware.bydauto.panorama.BYDAutoPanoramaDevice");
            Method getInstance = panoClass.getMethod("getInstance", android.content.Context.class);
            Object panoDevice = getInstance.invoke(null, new BydPermissionContext(this));
            log("Panorama Device instance: " + panoDevice);

            // Dump methods
            log("\n[Panorama Methods]");
            Method[] methods = panoClass.getMethods();
            for (Method m : methods) {
                String name = m.getName();
                if (name.startsWith("get") || name.startsWith("set") || name.startsWith("has")) {
                    StringBuilder sig = new StringBuilder(name + "(");
                    for (Class<?> p : m.getParameterTypes()) {
                        sig.append(p.getSimpleName()).append(",");
                    }
                    sig.append(") -> ").append(m.getReturnType().getSimpleName());
                    log("  " + sig);
                }
            }

            // Read panorama status
            log("\n[Panorama Status]");
            callAndLog(panoDevice, panoClass, "getPanoramaOnlineState");
            callAndLog(panoDevice, panoClass, "getPanoWorkState");
            callAndLog(panoDevice, panoClass, "getPanoOutputState");
            callAndLog(panoDevice, panoClass, "getPanoOutputSignal");
            callAndLog(panoDevice, panoClass, "getDisplayMode");
            callAndLog(panoDevice, panoClass, "getPanoRotation");
            callAndLog(panoDevice, panoClass, "getPanoTransparence");
            callAndLog(panoDevice, panoClass, "getLVDSState");
            callAndLog(panoDevice, panoClass, "getACUState");
            callAndLog(panoDevice, panoClass, "getCarInfo");
            callAndLog(panoDevice, panoClass, "getBackLineConfig");
            callAndLog(panoDevice, panoClass, "getPanoAPAState");
            callAndLog(panoDevice, panoClass, "getPanoRemoteImageCallSupport");
            callAndLog(panoDevice, panoClass, "getEmergencyButtonState");
            callAndLog(panoDevice, panoClass, "getRFCameraSwitchState");
            callAndLog(panoDevice, panoClass, "getRightCameraSwitchState");

            // Check features
            log("\n[Panorama Features]");
            try {
                Method hasFeature = panoClass.getMethod("hasFeature", String.class);
                Field[] fields = panoClass.getFields();
                for (Field f : fields) {
                    if (f.getName().startsWith("FEATURE_")) {
                        String featureStr = (String) f.get(null);
                        Object result = hasFeature.invoke(panoDevice, featureStr);
                        log("  " + f.getName() + " (" + featureStr + ") = " + result);
                    }
                }
            } catch (Exception e) {
                log("  features error: " + e.getMessage());
            }

        } catch (Exception e) {
            log("ERROR: " + e.getMessage());
        }
    }

    // Helper: call no-arg method and log result
    private void callAndLog(Object device, Class<?> cls, String methodName) {
        try {
            Method m = cls.getMethod(methodName);
            Object result = m.invoke(device);
            log("  " + methodName + "() = " + result);
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            log("  " + methodName + "() ITE: " + cause.getClass().getSimpleName() + ": " + cause.getMessage());
        } catch (Exception e) {
            log("  " + methodName + "() ERROR: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    // Helper: read and log static field value
    private void tryStaticField(Class<?> cls, String fieldName) {
        try {
            Field f = cls.getField(fieldName);
            Object val = f.get(null);
            log("  " + fieldName + " = " + val);
        } catch (Exception e) {
            log("  " + fieldName + " = NOT_FOUND");
        }
    }
}
