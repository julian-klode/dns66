/* Copyright (C) 2017 Julian Andres Klode <jak@jak-linux.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.jak_linux.dns66.db;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.UriPermission;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import org.jak_linux.dns66.Configuration;
import org.jak_linux.dns66.MainActivity;
import org.jak_linux.dns66.NotificationChannels;
import org.jak_linux.dns66.R;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Asynchronous task to update the database.
 * <p>
 * This spawns a thread pool fetching updating host files from
 * remote servers.
 */
public class RuleDatabaseUpdateTask extends AsyncTask<Void, Void, Void> {
    public static final AtomicReference<List<String>> lastErrors = new AtomicReference<>(null);
    private static final String TAG = "RuleDatabaseUpdateTask";
    private static final int UPDATE_NOTIFICATION_ID = 42;
    Context context;
    Configuration configuration;
    ArrayList<String> errors = new ArrayList<>();
    List<String> pending = new ArrayList<>();
    List<String> done = new ArrayList<>();
    private NotificationManager notificationManager;
    private NotificationCompat.Builder notificationBuilder;

    public RuleDatabaseUpdateTask(Context context, Configuration configuration, boolean notifications) {
        Log.d(TAG, "RuleDatabaseUpdateTask: Begin");
        this.context = context;
        this.configuration = configuration;

        if (notifications)
            setupNotificationBuilder();

        Log.d(TAG, "RuleDatabaseUpdateTask: Setup");
    }

    private void setupNotificationBuilder() {
        notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationBuilder = new NotificationCompat.Builder(context, NotificationChannels.UPDATE_STATUS);
        notificationBuilder.setContentTitle(context.getString(R.string.updating_hostfiles))
                .setContentText(context.getString(R.string.updating_hostfiles))
                .setSmallIcon(R.drawable.ic_refresh);

        notificationBuilder.setProgress(configuration.hosts.items.size(), 0, false);
    }

    @Override
    protected Void doInBackground(final Void... configurations) {
        Log.d(TAG, "doInBackground: begin");
        long start = System.currentTimeMillis();
        ExecutorService executor = Executors.newCachedThreadPool();

        for (Configuration.Item item : configuration.hosts.items) {
            RuleDatabaseItemUpdateRunnable runnable = getCommand(item);
            if (runnable.shouldDownload())
                executor.execute(runnable);
        }

        releaseGarbagePermissions();

        executor.shutdown();
        while (true) {
            try {
                if (executor.awaitTermination(1, TimeUnit.HOURS))
                    break;

                Log.d(TAG, "doInBackground: Waiting for completion");
            } catch (InterruptedException e) {
            }
        }
        long end = System.currentTimeMillis();
        Log.d(TAG, "doInBackground: end after " + (end - start) + "milliseconds");

        postExecute();

        return null;
    }

    /**
     * Releases all persisted URI permissions that are no longer referenced
     */
    void releaseGarbagePermissions() {
        ContentResolver contentResolver = context.getContentResolver();
        for (UriPermission permission : contentResolver.getPersistedUriPermissions()) {
            if (isGarbage(permission.getUri())) {
                Log.i(TAG, "releaseGarbagePermissions: Releasing permission for " + permission.getUri());
                contentResolver.releasePersistableUriPermission(permission.getUri(), Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } else {
                Log.v(TAG, "releaseGarbagePermissions: Keeping permission for " + permission.getUri());
            }
        }
    }

    /**
     * Returns whether URI is no longer referenced in the configuration
     *
     * @param uri URI to check
     */
    private boolean isGarbage(Uri uri) {
        for (Configuration.Item item : configuration.hosts.items) {
            if (Uri.parse(item.location).equals(uri))
                return false;
        }
        return true;
    }

    /**
     * RuleDatabaseItemUpdateRunnable factory for unit tests
     */
    @NonNull
    RuleDatabaseItemUpdateRunnable getCommand(Configuration.Item item) {
        return new RuleDatabaseItemUpdateRunnable(this, context, item);
    }

    /**
     * Sets progress message.
     */
    private synchronized void updateProgressNotification() {
        StringBuilder builder = new StringBuilder();
        for (String p : pending) {
            if (builder.length() > 0)
                builder.append("\n");
            builder.append(p);
        }

        if (notificationBuilder != null) {
            notificationBuilder.setProgress(pending.size() + done.size(), done.size(), false);
            notificationBuilder.setStyle(new NotificationCompat.BigTextStyle().bigText(builder.toString()));
            notificationBuilder.setContentText(context.getString(R.string.updating_n_host_files, pending.size()));
            notificationManager.notify(UPDATE_NOTIFICATION_ID, notificationBuilder.build());
        }
    }

    /**
     * Clears the notifications or updates it for viewing errors.
     */
    private synchronized void postExecute() {
        Log.d(TAG, "postExecute: Sending notification");
        try {
            RuleDatabase.getInstance().initialize(context);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (notificationBuilder != null) {
            if (errors.isEmpty()) {
                notificationManager.cancel(UPDATE_NOTIFICATION_ID);
            } else {
                notificationBuilder.setProgress(0, 0, false);
                notificationBuilder.setContentText(context.getString(R.string.could_not_update_all_hosts));
                notificationBuilder.setSmallIcon(R.drawable.ic_warning);

                Intent intent = new Intent(context, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

                lastErrors.set(errors);
                PendingIntent pendingIntent = PendingIntent.getActivity(context, 0,
                        intent, PendingIntent.FLAG_ONE_SHOT);


                notificationBuilder.setContentIntent(pendingIntent);

                notificationBuilder.setAutoCancel(true);
                notificationManager.notify(UPDATE_NOTIFICATION_ID, notificationBuilder.build());
            }
        }
    }

    /**
     * Adds an error message related to the item to the log.
     *
     * @param item    The item
     * @param message Message
     */
    synchronized void addError(Configuration.Item item, String message) {
        Log.d(TAG, "error: " + item.title + ":" + message);
        errors.add("<b>" + item.title + "</b><br>" + message);
    }

    /**
     * Marks an item as done.
     *
     * @param item Item that has finished.
     */
    synchronized void addDone(Configuration.Item item) {
        Log.d(TAG, "done: " + item.title);
        pending.remove(item.title);
        done.add(item.title);
        updateProgressNotification();
    }

    /**
     * Adds an item to the notification
     *
     * @param item The item currently being processed.
     */
    synchronized void addBegin(Configuration.Item item) {
        pending.add(item.title);
        updateProgressNotification();
    }

    synchronized long pendingCount() {
        return pending.size();
    }
}
