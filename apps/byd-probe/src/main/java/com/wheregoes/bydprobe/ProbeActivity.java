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
import java.lang.reflect.Modifier;
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

        LinearLayout btnRow2 = new LinearLayout(this);
        btnRow2.setOrientation(LinearLayout.HORIZONTAL);
        btnRow2.setGravity(Gravity.CENTER);

        Button btnAcOn = makeButton("AC ON");
        btnAcOn.setOnClickListener(v -> new Thread(() -> testAcStart()).start());
        btnRow2.addView(btnAcOn);

        Button btnAcOff = makeButton("AC OFF");
        btnAcOff.setOnClickListener(v -> new Thread(() -> testAcStop()).start());
        btnRow2.addView(btnAcOff);

        Button btnTemp22 = makeButton("Temp 22°C");
        btnTemp22.setOnClickListener(v -> new Thread(() -> testAcSetTemp(22)).start());
        btnRow2.addView(btnTemp22);

        Button btnTemp25 = makeButton("Temp 25°C");
        btnTemp25.setOnClickListener(v -> new Thread(() -> testAcSetTemp(25)).start());
        btnRow2.addView(btnTemp25);

        Button btnFan3 = makeButton("Fan 3");
        btnFan3.setOnClickListener(v -> new Thread(() -> testAcSetFan(3)).start());
        btnRow2.addView(btnFan3);

        Button btnBodywork = makeButton("Probe Body");
        btnBodywork.setOnClickListener(v -> new Thread(() -> probeBodywork()).start());
        btnRow2.addView(btnBodywork);

        root.addView(btnRow2);

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
        writeToFile(msg);
    }

    private void writeToFile(String msg) {
        try {
            java.io.FileWriter fw = new java.io.FileWriter(
                getExternalFilesDir(null) + "/probe-log.txt", true);
            fw.write(msg + "\n");
            fw.close();
        } catch (Exception e) {}
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
            log("DoorLock class: " + dlDevice.getClass().getName());

            // Dump ALL static fields (including inherited) to find feature IDs
            log("\n[DoorLock ALL Static Fields]");
            for (Class<?> c = dlClass; c != null && !c.equals(Object.class); c = c.getSuperclass()) {
                Field[] fields = c.getDeclaredFields();
                for (Field f : fields) {
                    f.setAccessible(true);
                    if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                        try {
                            Object val = f.get(null);
                            String valStr = (val instanceof int[]) ? Arrays.toString((int[])val) : String.valueOf(val);
                            log("  " + c.getSimpleName() + "." + f.getName() + " = " + valStr + " (" + f.getType().getSimpleName() + ")");
                        } catch (Exception e) {
                            log("  " + c.getSimpleName() + "." + f.getName() + " = ERR:" + e.getMessage());
                        }
                    }
                }
            }

            // Dump ALL methods (including declared private and inherited)
            log("\n[DoorLock ALL Methods]");
            for (Class<?> c = dlClass; c != null && !c.equals(Object.class); c = c.getSuperclass()) {
                Method[] methods = c.getDeclaredMethods();
                for (Method m : methods) {
                    StringBuilder sig = new StringBuilder(c.getSimpleName() + "." + m.getName() + "(");
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
            for (int area = 0; area <= 10; area++) {
                try {
                    Object status = getDoorLockStatus.invoke(dlDevice, area);
                    log("  getDoorLockStatus(" + area + ") = " + status);
                } catch (java.lang.reflect.InvocationTargetException e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    log("  getDoorLockStatus(" + area + ") ITE: " + cause.getMessage());
                } catch (Exception e) {
                    log("  getDoorLockStatus(" + area + ") ERR: " + e.getMessage());
                }
            }

            // Device info
            log("\n[DoorLock Device Info]");
            try {
                Method getDevType = dlClass.getMethod("getDevicetype");
                log("  getDevicetype() = " + getDevType.invoke(dlDevice));
            } catch (Exception e) {}

            // Feature list
            log("\n[DoorLock Feature List]");
            try {
                Method getFeatureList = dlClass.getMethod("getFeatureList");
                int[] features = (int[]) getFeatureList.invoke(dlDevice);
                log("  featureList = " + (features != null ? Arrays.toString(features) : "null"));
                log("  count = " + (features != null ? features.length : 0));
                if (features != null) {
                    for (int fid : features) {
                        log("  0x" + Integer.toHexString(fid) + " (" + fid + ")");
                    }
                }
            } catch (java.lang.reflect.InvocationTargetException e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                log("  getFeatureList ITE: " + cause.getClass().getSimpleName() + ": " + cause.getMessage());
            } catch (Exception e) {
                log("  getFeatureList ERR: " + e.getMessage());
            }

            // Manager scan with device type 1041
            log("\n[DoorLock Manager Scan (devType=1041)]");
            try {
                Field mgrField = dlClass.getSuperclass().getDeclaredField("mDeviceManager");
                mgrField.setAccessible(true);
                Object manager = mgrField.get(dlDevice);
                if (manager != null) {
                    Method getInt = manager.getClass().getMethod("getInt", int.class, int.class);
                    int devType = 1041;
                    // Fine scan of 0x41A0xxxx range
                    for (int fid = 0x41A00000; fid <= 0x41A000FF; fid += 1) {
                        try {
                            int val = (int) getInt.invoke(manager, devType, fid);
                            if (val != -10011) {
                                log("  getInt(1041, 0x" + Integer.toHexString(fid) + ") = " + val);
                            }
                        } catch (Exception e) {}
                    }
                    // Also scan 0x41200000 range
                    for (int fid = 0x41200000; fid <= 0x412000FF; fid += 1) {
                        try {
                            int val = (int) getInt.invoke(manager, devType, fid);
                            if (val != -10011) {
                                log("  getInt(1041, 0x" + Integer.toHexString(fid) + ") = " + val);
                            }
                        } catch (Exception e) {}
                    }
                }
            } catch (Exception e) {
                log("  Manager scan error: " + e.getMessage());
            }

        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            log("ERROR: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            log("  CAUSE: " + cause.getClass().getSimpleName() + ": " + cause.getMessage());
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

    // ===== AC SET TESTS =====
    private void testAcStart() {
        log("\n--- AC START TEST ---");
        try {
            Class<?> acClass = Class.forName("android.hardware.bydauto.ac.BYDAutoAcDevice");
            Method getInstance = acClass.getMethod("getInstance", android.content.Context.class);
            Object acDevice = getInstance.invoke(null, new BydPermissionContext(this));

            Method getState = acClass.getMethod("getAcStartState");
            log("  Before: getAcStartState() = " + getState.invoke(acDevice));

            Method start = acClass.getMethod("start", int.class);
            Object result = start.invoke(acDevice, 0);
            log("  start(0) returned: " + result);

            Thread.sleep(500);
            log("  After: getAcStartState() = " + getState.invoke(acDevice));
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            log("  ITE: " + cause.getClass().getSimpleName() + ": " + cause.getMessage());
        } catch (Exception e) {
            log("  ERROR: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private void testAcStop() {
        log("\n--- AC STOP TEST ---");
        try {
            Class<?> acClass = Class.forName("android.hardware.bydauto.ac.BYDAutoAcDevice");
            Method getInstance = acClass.getMethod("getInstance", android.content.Context.class);
            Object acDevice = getInstance.invoke(null, new BydPermissionContext(this));

            Method getState = acClass.getMethod("getAcStartState");
            log("  Before: getAcStartState() = " + getState.invoke(acDevice));

            Method stop = acClass.getMethod("stop", int.class);
            Object result = stop.invoke(acDevice, 0);
            log("  stop(0) returned: " + result);

            Thread.sleep(500);
            log("  After: getAcStartState() = " + getState.invoke(acDevice));
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            log("  ITE: " + cause.getClass().getSimpleName() + ": " + cause.getMessage());
        } catch (Exception e) {
            log("  ERROR: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private void testAcSetTemp(int tempCelsius) {
        log("\n--- AC SET TEMP " + tempCelsius + "°C ---");
        try {
            Class<?> acClass = Class.forName("android.hardware.bydauto.ac.BYDAutoAcDevice");
            Method getInstance = acClass.getMethod("getInstance", android.content.Context.class);
            Object acDevice = getInstance.invoke(null, new BydPermissionContext(this));

            Method getTemp = acClass.getMethod("getTemprature", int.class);
            Method getMode = acClass.getMethod("getAcControlMode");
            Method getState = acClass.getMethod("getAcStartState");
            log("  AC state: " + getState.invoke(acDevice) + ", mode: " + getMode.invoke(acDevice));
            log("  Before: getTemprature(1) = " + getTemp.invoke(acDevice, 1));

            // Correct call: setAcTemperature(zone, tempCelsius, source=1, param4=1)
            Method setTemp = acClass.getMethod("setAcTemperature", int.class, int.class, int.class, int.class);
            Object result = setTemp.invoke(acDevice, 1, tempCelsius, 1, 1);
            log("  setAcTemperature(1, " + tempCelsius + ", 1, 1) = " + result);
            Thread.sleep(1000);
            log("  After: getTemprature(1) = " + getTemp.invoke(acDevice, 1));

        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            log("  ITE: " + cause.getClass().getSimpleName() + ": " + cause.getMessage());
        } catch (Exception e) {
            log("  ERROR: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private void testAcSetFan(int level) {
        log("\n--- AC SET FAN " + level + " ---");
        try {
            Class<?> acClass = Class.forName("android.hardware.bydauto.ac.BYDAutoAcDevice");
            Method getInstance = acClass.getMethod("getInstance", android.content.Context.class);
            Object acDevice = getInstance.invoke(null, new BydPermissionContext(this));

            Method getWind = acClass.getMethod("getAcWindLevel");
            Method getMode = acClass.getMethod("getAcControlMode");
            log("  mode: " + getMode.invoke(acDevice) + ", wind: " + getWind.invoke(acDevice));

            // Use base class set(deviceType=1000, featureId=0x1DE00030, value)
            // Named setAcWindLevel() is broken — returns INVALID_VALUE for all source values
            Method baseSet = acClass.getSuperclass().getDeclaredMethod("set", int.class, int.class, int.class);
            baseSet.setAccessible(true);
            int r = (int) baseSet.invoke(acDevice, 1000, 0x1DE00030, level);
            log("  set(1000, 0x1DE00030, " + level + ") = " + r);
            Thread.sleep(500);
            log("  After: wind=" + getWind.invoke(acDevice) + ", mode=" + getMode.invoke(acDevice));

        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            log("  ITE: " + cause.getClass().getSimpleName() + ": " + cause.getMessage());
        } catch (Exception e) {
            log("  ERROR: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    // ===== BODYWORK PROBE =====
    private void probeBodywork() {
        log("\n--- BODYWORK DEVICE PROBE ---");
        try {
            Class<?> bwClass = Class.forName("android.hardware.bydauto.bodywork.BYDAutoBodyworkDevice");
            Method getInstance = bwClass.getMethod("getInstance", android.content.Context.class);
            Object bwDevice = getInstance.invoke(null, new BydPermissionContext(this));
            log("Bodywork Device instance: " + bwDevice);

            log("\n[Bodywork Methods]");
            Method[] methods = bwClass.getMethods();
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

            log("\n[Bodywork Status]");
            callAndLog(bwDevice, bwClass, "getBatteryCapacity");
            callAndLog(bwDevice, bwClass, "getAutoVIN");
            callAndLog(bwDevice, bwClass, "getAutoSystemState");
            callAndLog(bwDevice, bwClass, "getSunroofState");

            String[] doorAreas = {"DOOR_AREA_LEFT_FRONT", "DOOR_AREA_RIGHT_FRONT", "DOOR_AREA_LEFT_REAR", "DOOR_AREA_RIGHT_REAR", "DOOR_AREA_BACK"};
            log("\n[Door States]");
            Method getDoorState = null;
            try { getDoorState = bwClass.getMethod("getDoorState", int.class); } catch (Exception e) {}
            if (getDoorState != null) {
                for (String areaName : doorAreas) {
                    try {
                        Field f = bwClass.getField(areaName);
                        int area = f.getInt(null);
                        Object result = getDoorState.invoke(bwDevice, area);
                        log("  getDoorState(" + areaName + "=" + area + ") = " + result);
                    } catch (Exception e) {
                        log("  " + areaName + " = NOT_FOUND");
                    }
                }
                for (int i = 0; i <= 10; i++) {
                    try {
                        Object result = getDoorState.invoke(bwDevice, i);
                        log("  getDoorState(" + i + ") = " + result);
                    } catch (Exception e) {}
                }
            }

            String[] windowAreas = {"WINDOW_AREA_LEFT_FRONT", "WINDOW_AREA_RIGHT_FRONT", "WINDOW_AREA_LEFT_REAR", "WINDOW_AREA_RIGHT_REAR"};
            log("\n[Window States]");
            Method getWindowState = null;
            try { getWindowState = bwClass.getMethod("getWindowState", int.class); } catch (Exception e) {}
            if (getWindowState != null) {
                for (String areaName : windowAreas) {
                    try {
                        Field f = bwClass.getField(areaName);
                        int area = f.getInt(null);
                        Object result = getWindowState.invoke(bwDevice, area);
                        log("  getWindowState(" + areaName + "=" + area + ") = " + result);
                    } catch (Exception e) {
                        log("  " + areaName + " = NOT_FOUND");
                    }
                }
            }

            // Check base class for set methods / door lock related
            log("\n[Bodywork Base Class]");
            Class<?> baseClass = bwClass.getSuperclass();
            if (baseClass != null) {
                log("  Superclass: " + baseClass.getName());
                for (Method m : baseClass.getMethods()) {
                    String name = m.getName();
                    if (name.startsWith("set") || name.equals("postEvent") || name.equals("get") || name.equals("set")) {
                        StringBuilder sig = new StringBuilder(name + "(");
                        for (Class<?> p : m.getParameterTypes()) {
                            sig.append(p.getSimpleName()).append(",");
                        }
                        sig.append(") -> ").append(m.getReturnType().getSimpleName());
                        log("  " + sig);
                    }
                }
            }

        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            log("ERROR: " + cause.getClass().getSimpleName() + ": " + cause.getMessage());
        } catch (Exception e) {
            log("ERROR: " + e.getClass().getSimpleName() + ": " + e.getMessage());
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
