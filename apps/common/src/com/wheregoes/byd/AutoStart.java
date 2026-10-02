package com.wheregoes.byd;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;

/**
 * BYD's auto-start manager, {@code com.byd.appstartmanagement}.
 *
 * Two things about that screen are not obvious, and each one costs a diagnosis
 * round trip:
 *
 * <ul>
 *   <li><b>It is a block list and its switches read backwards.</b> A row shown
 *       ON means auto-start is <i>disabled</i> for that app; OFF means it is
 *       allowed. Measured on a DiLink 3 head unit: with the row ON a background
 *       app never received {@code BOOT_COMPLETED} at all -- the receiver was
 *       registered, the package was not in the stopped state and nothing
 *       crashed, the broadcast simply never arrived. Turning the row OFF is what
 *       makes the boot path work.</li>
 *   <li><b>The list is keyed by the APK directory</b>, which changes on every
 *       install and every in-place upgrade, so an app that was allowed goes back
 *       to blocked after an update without telling anyone.</li>
 * </ul>
 *
 * Deliberately string-free: the shared sources compile without an {@code R}
 * class, so each app supplies its own copy.
 */
public final class AutoStart {

    public static final String PACKAGE = "com.byd.appstartmanagement";

    /** Identity of the install the user was last pointed at the screen for. */
    private static final String KEY_REMINDED_FOR = "autostart_reminded_for";

    private AutoStart() {}

    /** @return true when this head unit has BYD's auto-start manager with a launchable UI. */
    public static boolean isAvailable(Context context) {
        return launchIntent(context) != null;
    }

    /**
     * Opens the auto-start screen.
     *
     * @return false when the unit has no such screen, so the caller can say so
     *         instead of leaving a dead button.
     */
    public static boolean open(Context context) {
        Intent intent = launchIntent(context);
        if (intent == null) {
            return false;
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * True when the user has not been pointed at the auto-start screen for the
     * install that is running now. Every upgrade re-arms this, because BYD keys
     * its list by APK directory and a new directory starts out blocked.
     */
    public static boolean shouldRemind(Context context, SharedPreferences prefs) {
        return isAvailable(context)
                && !installTag(context).equals(prefs.getString(KEY_REMINDED_FOR, ""));
    }

    public static void markReminded(Context context, SharedPreferences prefs) {
        prefs.edit().putString(KEY_REMINDED_FOR, installTag(context)).apply();
    }

    /** APK directory plus version code: exactly what BYD's list keys on, plus the upgrade. */
    private static String installTag(Context context) {
        String dir;
        try {
            dir = context.getApplicationInfo().sourceDir;
        } catch (Throwable t) {
            dir = "?";
        }
        return dir + "@" + versionCode(context);
    }

    private static long versionCode(Context context) {
        try {
            PackageInfo info = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            return info.getLongVersionCode();
        } catch (Throwable t) {
            return -1L;
        }
    }

    private static Intent launchIntent(Context context) {
        try {
            Intent intent = context.getPackageManager().getLaunchIntentForPackage(PACKAGE);
            if (intent == null || intent.resolveActivity(context.getPackageManager()) == null) {
                return null;
            }
            return intent;
        } catch (Throwable t) {
            return null;
        }
    }
}
