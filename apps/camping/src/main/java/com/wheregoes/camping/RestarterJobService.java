package com.wheregoes.camping;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

public class RestarterJobService extends JobService {
    static final int JOB_ID = 4001;

    @Override
    public boolean onStartJob(JobParameters params) {
        SharedPreferences prefs = getSharedPreferences(CampingService.PREF_NAME, MODE_PRIVATE);
        if (prefs.getBoolean(CampingService.KEY_ENABLED, false) && !CampingService.isRunning()) {
            Log.i("Restarter", "Restarting Camping service");
            startForegroundService(new Intent(this, CampingService.class));
        }
        jobFinished(params, false);
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }
}
