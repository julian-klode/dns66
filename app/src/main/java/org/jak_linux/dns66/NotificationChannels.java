/* Copyright (C) 2017 Julian Andres Klode <jak@jak-linux.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.jak_linux.dns66;

import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

/**
 * Static class containing IDs of notification channels and code to create them.
 */
public class NotificationChannels {
    public static final String GROUP_SERVICE = "org.jak_linux.dns66.notifications.service";
    public static final String SERVICE_RUNNING = "org.jak_linux.dns66.notifications.service.running";
    public static final String SERVICE_PAUSED = "org.jak_linux.dns66.notifications.service.paused";
    public static final String GROUP_UPDATE = "org.jak_linux.dns66.notifications.update";
    public static final String UPDATE_STATUS = "org.jak_linux.dns66.notifications.update.status";

    public static void onCreate(Context context) {
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            return;

        notificationManager.createNotificationChannelGroup(new NotificationChannelGroup(GROUP_SERVICE, context.getString(R.string.notifications_group_service)));
        notificationManager.createNotificationChannelGroup(new NotificationChannelGroup(GROUP_UPDATE, context.getString(R.string.notifications_group_updates)));

        NotificationChannel runningChannel = new NotificationChannel(SERVICE_RUNNING, context.getString(R.string.notifications_running), NotificationManager.IMPORTANCE_MIN);
        runningChannel.setDescription(context.getString(R.string.notifications_running_desc));
        runningChannel.setGroup(GROUP_SERVICE);
        runningChannel.setShowBadge(false);
        notificationManager.createNotificationChannel(runningChannel);

        NotificationChannel pausedChannel = new NotificationChannel(SERVICE_PAUSED, context.getString(R.string.notifications_paused), NotificationManager.IMPORTANCE_LOW);
        pausedChannel.setDescription(context.getString(R.string.notifications_paused_desc));
        pausedChannel.setGroup(GROUP_SERVICE);
        pausedChannel.setShowBadge(false);
        notificationManager.createNotificationChannel(pausedChannel);

        NotificationChannel updateChannel = new NotificationChannel(UPDATE_STATUS, context.getString(R.string.notifications_update), NotificationManager.IMPORTANCE_LOW);
        updateChannel.setDescription(context.getString(R.string.notifications_update_desc));
        updateChannel.setGroup(GROUP_UPDATE);
        updateChannel.setShowBadge(false);
        notificationManager.createNotificationChannel(updateChannel);
    }
}
