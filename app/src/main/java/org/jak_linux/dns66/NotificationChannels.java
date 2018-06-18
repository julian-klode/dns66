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

        createChannel(notificationManager, SERVICE_RUNNING, NotificationManager.IMPORTANCE_MIN, GROUP_SERVICE,
                context.getString(R.string.notifications_running), context.getString(R.string.notifications_running_desc));

        createChannel(notificationManager, SERVICE_PAUSED, NotificationManager.IMPORTANCE_LOW, GROUP_SERVICE,
                context.getString(R.string.notifications_paused), context.getString(R.string.notifications_paused_desc));

        createChannel(notificationManager, UPDATE_STATUS, NotificationManager.IMPORTANCE_LOW, GROUP_UPDATE,
                context.getString(R.string.notifications_update), context.getString(R.string.notifications_update_desc));
    }

    private static void createChannel(NotificationManager notificationManager, String channelId, int channelImportance,
                                      String groupId, String channelName, String description) {
        NotificationChannel updateChannel = new NotificationChannel(channelId, channelName, channelImportance);
        updateChannel.setDescription(description);
        updateChannel.setGroup(groupId);
        updateChannel.setShowBadge(false);
        notificationManager.createNotificationChannel(updateChannel);
    }
}
