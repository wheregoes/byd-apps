package com.wheregoes.doorsound;

/**
 * Every event the app can react to, with its preference keys derived from one
 * prefix. The four original events keep their existing key names so stored
 * configuration is unaffected.
 *
 * Adding an event here is all that is needed: the service, both tabs of the UI
 * and the warning logic are all driven off {@link #VALUES}.
 *
 * This is a class of constants rather than an enum because the pinned d8 cannot
 * dex enums -- see the note in apps/common/build-common.sh.
 */
final class SoundEvent {
    static final SoundEvent DOOR_OPEN =
            new SoundEvent(0, "door_open", R.string.event_door_open);
    static final SoundEvent DOOR_CLOSE =
            new SoundEvent(1, "door_close", R.string.event_door_close);
    static final SoundEvent LOCK =
            new SoundEvent(2, "lock", R.string.event_lock);
    static final SoundEvent UNLOCK =
            new SoundEvent(3, "unlock", R.string.event_unlock);
    static final SoundEvent HOOD =
            new SoundEvent(4, "hood", R.string.event_hood);
    static final SoundEvent TRUNK =
            new SoundEvent(5, "trunk", R.string.event_trunk);
    static final SoundEvent ALARM =
            new SoundEvent(6, "alarm", R.string.event_alarm);
    static final SoundEvent WINDOW =
            new SoundEvent(7, "window", R.string.event_window);

    /** Declaration order is the display order; must stay last. */
    static final SoundEvent[] VALUES = {
            DOOR_OPEN, DOOR_CLOSE, LOCK, UNLOCK, HOOD, TRUNK, ALARM, WINDOW
    };

    final int index;
    final String name;
    final String enabledKey;
    final String pathKey;
    final String volumeKey;
    final String outsideEnabledKey;
    final String outsidePatternKey;
    final int titleRes;

    private SoundEvent(int index, String prefix, int titleRes) {
        this.index = index;
        this.name = prefix;
        this.enabledKey = prefix + "_enabled";
        this.pathKey = prefix + "_path";
        this.volumeKey = prefix + "_volume";
        this.outsideEnabledKey = "outside_" + prefix + "_enabled";
        this.outsidePatternKey = "outside_" + prefix + "_pattern";
        this.titleRes = titleRes;
    }
}
