package com.wheregoes.petmode;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

public class RestarterJobService extends JobService {
    private static final String TAG = "PetModeRestarter";
    static final int JOB_ID = 2001;

    @Override
    public boolean onStartJob(JobParameters params) {
        SharedPreferences prefs = getSharedPreferences(PetModeService.PREF_NAME, MODE_PRIVATE);
        if (prefs.getBoolean(PetModeService.KEY_ENABLED, false) && !PetModeService.isRunning()) {
            Log.i(TAG, "Restarting pet mode service");
            startForegroundService(new Intent(this, PetModeService.class));
        }
        // Periodic job; it re-arms itself. The old reschedule() re-armed a 60s
        // override deadline on every run, producing a perpetual wake cycle.
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }
}
