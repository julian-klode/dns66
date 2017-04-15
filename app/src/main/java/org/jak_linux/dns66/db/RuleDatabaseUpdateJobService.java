/* Copyright (C) 2017 Julian Andres Klode <jak@jak-linux.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.jak_linux.dns66.db;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.util.Log;

import org.jak_linux.dns66.Configuration;
import org.jak_linux.dns66.FileHelper;

import java.util.concurrent.TimeUnit;

/**
 * Automatic daily host file refreshes.
 */
public class RuleDatabaseUpdateJobService extends JobService {
    private static final int JOB_ID = 1;
    private static final String TAG = "DbUpdateJobService";
    RuleDatabaseUpdateTask task;

    /**
     * Schedules or cancels the job, depending on the configuration
     *
     * @return true if the job could be scheduled.
     */
    public static boolean scheduleOrCancel(Context context, Configuration configuration) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);

        if (!configuration.hosts.automaticRefresh) {
            Log.d(TAG, "scheduleOrCancel: Cancelling Job");

            scheduler.cancel(JOB_ID);
            return true;
        }
        Log.d(TAG, "scheduleOrCancel: Scheduling Job");

        ComponentName serviceName = new ComponentName(context, RuleDatabaseUpdateJobService.class);

        JobInfo jobInfo = new JobInfo.Builder(JOB_ID, serviceName)
                .setRequiresCharging(true)
                .setRequiresDeviceIdle(true)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                .setPersisted(true)
                .setPeriodic(TimeUnit.DAYS.toMillis(1))
                .build();


        int result = scheduler.schedule(jobInfo);
        if (result == JobScheduler.RESULT_SUCCESS)
            Log.d(TAG, "Job scheduled");
        else
            Log.d(TAG, "Job not scheduled");

        return result == JobScheduler.RESULT_SUCCESS;
    }

    @Override
    public boolean onStartJob(final JobParameters params) {
        Log.d(TAG, "onStartJob: Start job");
        task = new RuleDatabaseUpdateTask(this, FileHelper.loadCurrentSettings(this), true) {
            @Override
            protected void onPostExecute(Void aVoid) {
                super.onPostExecute(aVoid);
                jobFinished(params, task.pendingCount() > 0);
            }

            @Override
            protected void onCancelled(Void aVoid) {
                super.onCancelled(aVoid);
                jobFinished(params, task.pendingCount() > 0);
            }
        };
        task.execute();
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        Log.d(TAG, "onStartJob: Stop job");
        task.cancel(true);
        return task.pendingCount() > 0;
    }
}
