package com.wheregoes.doorsound;

import android.os.Handler;

/**
 * Re-posts itself to the handler it was given. The old version posted to
 * getWindow().getDecorView() instead, a different Handler from the one
 * MainActivity cancelled on, so every resume leaked another chain holding a
 * strong Activity reference.
 */
class StatusRefresher implements Runnable {
    static final long INTERVAL_MS = 2000L;

    private final MainActivity activity;
    private final Handler handler;

    StatusRefresher(MainActivity activity, Handler handler) {
        this.activity = activity;
        this.handler = handler;
    }

    @Override
    public void run() {
        activity.updateStatus();
        handler.postDelayed(this, INTERVAL_MS);
    }
}
