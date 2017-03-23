package org.jak_linux.dns66.db;

import android.app.job.JobParameters;
import android.app.job.JobService;

import org.jak_linux.dns66.FileHelper;

/**
 * Created by jak on 23/03/17.
 */

public class RuleDatabaseUpdateJobService extends JobService {
    private DatabaseUpdateTask updateTask;

    @Override
    public boolean onStartJob(JobParameters jobParameters) {
        RuleDatabase ruleDatabase = new RuleDatabase();
        try {
            ruleDatabase.initialize(getApplicationContext());
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        updateTask = new DatabaseUpdateTask(getApplicationContext(), ruleDatabase);
        updateTask.execute(FileHelper.loadCurrentSettings(getApplicationContext()));

        return false;
    }

    @Override
    public boolean onStopJob(JobParameters jobParameters) {
        updateTask.cancel(true);
        return false;
    }
}
