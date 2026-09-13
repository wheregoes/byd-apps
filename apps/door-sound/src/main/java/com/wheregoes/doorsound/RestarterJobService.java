package com.wheregoes.doorsound;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

public class RestarterJobService extends JobService {
    private static final String TAG = "DoorSoundRestarter";
    /** Single source of truth; DoorSoundService schedules against this id. */
    static final int JOB_ID = 1001;

    @Override
    public boolean onStartJob(JobParameters params) {
        SharedPreferences prefs = getSharedPreferences(
                DoorSoundService.PREF_NAME, MODE_PRIVATE);

        if (prefs.getBoolean(DoorSoundService.KEY_ENABLED, false)
                && !DoorSoundService.isRunning()) {
            Log.i(TAG, "Restarting door sound service");
            startForegroundService(new Intent(this, DoorSoundService.class));
        }
        // The job is periodic now, so it re-arms itself. The old code rescheduled
        // a 60s override deadline on every run, producing a perpetual wake cycle.
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }
}
