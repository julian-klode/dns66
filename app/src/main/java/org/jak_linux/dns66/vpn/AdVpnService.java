/* Copyright (C) 2016 Julian Andres Klode <jak@jak-linux.org>
 *
 * Derived from AdBuster:
 * Copyright (C) 2016 Daniel Brodie <dbrodie@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 */
package org.jak_linux.dns66.vpn;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.VpnService;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import org.jak_linux.dns66.Configuration;
import org.jak_linux.dns66.FileHelper;
import org.jak_linux.dns66.MainActivity;
import org.jak_linux.dns66.R;

public class AdVpnService extends VpnService implements Handler.Callback {
    public static final int VPN_STATUS_STARTING = 0;
    public static final int VPN_STATUS_RUNNING = 1;
    public static final int VPN_STATUS_STOPPING = 2;
    public static final int VPN_STATUS_WAITING_FOR_NETWORK = 3;
    public static final int VPN_STATUS_RECONNECTING = 4;
    public static final int VPN_STATUS_RECONNECTING_NETWORK_ERROR = 5;
    public static final int VPN_STATUS_STOPPED = 6;
    public static final String VPN_UPDATE_STATUS_INTENT = "org.jak_linux.dns66.VPN_UPDATE_STATUS";
    public static final String VPN_UPDATE_STATUS_EXTRA = "VPN_STATUS";
    private static final int VPN_MSG_STATUS_UPDATE = 0;
    private static final int VPN_MSG_NETWORK_CHANGED = 1;
    private static final String TAG = "VpnService";
    // TODO: Temporary Hack til refactor is done
    public static int vpnStatus = VPN_STATUS_STOPPED;
    private final Handler handler = new Handler(this);
    private final AdVpnThread vpnThread = new AdVpnThread(this, new AdVpnThread.Notify() {
        @Override
        public void run(int value) {
            handler.sendMessage(handler.obtainMessage(VPN_MSG_STATUS_UPDATE, value, 0));
        }
    });
    private final BroadcastReceiver connectivityChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            handler.sendMessage(handler.obtainMessage(VPN_MSG_NETWORK_CHANGED, intent));
        }
    };
    private final NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this)
            .setSmallIcon(R.drawable.ic_menu_info) // TODO: Notification icon
            .setPriority(Notification.PRIORITY_MIN);

    public static int vpnStatusToTextId(int status) {
        switch (status) {
            case VPN_STATUS_STARTING:
                return R.string.notification_starting;
            case VPN_STATUS_RUNNING:
                return R.string.notification_running;
            case VPN_STATUS_STOPPING:
                return R.string.notification_stopping;
            case VPN_STATUS_WAITING_FOR_NETWORK:
                return R.string.notification_waiting_for_net;
            case VPN_STATUS_RECONNECTING:
                return R.string.notification_reconnecting;
            case VPN_STATUS_RECONNECTING_NETWORK_ERROR:
                return R.string.notification_reconnecting_error;
            case VPN_STATUS_STOPPED:
                return R.string.notification_stopped;
            default:
                throw new IllegalArgumentException("Invalid vpnStatus value (" + status + ")");
        }
    }

    public static void checkStartVpnOnBoot(Context context) {
        Log.i("BOOT", "Checking whether to start ad buster on boot");
        Configuration config = FileHelper.loadCurrentSettings(context);
        if (config == null || !config.autoStart) {
            return;
        }

        if (VpnService.prepare(context) != null) {
            Log.i("BOOT", "VPN preparation not confirmed by user, changing enabled to false");
        }

        Log.i("BOOT", "Starting ad buster from boot");

        Intent intent = new Intent(context, AdVpnService.class);
        intent.putExtra("COMMAND", Command.START.ordinal());
        intent.putExtra("NOTIFICATION_INTENT",
                PendingIntent.getActivity(context, 0,
                        new Intent(context, MainActivity.class), 0));
        context.startService(intent);
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        Log.i(TAG, "onStartCommand");
        switch (intent == null ? Command.START : Command.values()[intent.getIntExtra("COMMAND", Command.START.ordinal())]) {
            case START:
                startVpn(intent == null ? null : (PendingIntent) intent.getParcelableExtra("NOTIFICATION_INTENT"));
                break;
            case STOP:
                stopVpn();
                break;
        }

        return Service.START_STICKY;
    }

    private void updateVpnStatus(int status) {
        vpnStatus = status;
        int notificationTextId = vpnStatusToTextId(status);
        notificationBuilder.setContentText(getString(notificationTextId));

        startForeground(10, notificationBuilder.build());

        Intent intent = new Intent(VPN_UPDATE_STATUS_INTENT);
        intent.putExtra(VPN_UPDATE_STATUS_EXTRA, status);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }


    private void startVpn(PendingIntent notificationIntent) {
        notificationBuilder.setContentTitle(getString(R.string.notification_title));
        if (notificationIntent != null)
            notificationBuilder.setContentIntent(notificationIntent);
        updateVpnStatus(VPN_STATUS_STARTING);

        registerReceiver(connectivityChangedReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));

        restartVpnThread();
    }

    private void restartVpnThread() {
        vpnThread.stopThread();
        vpnThread.startThread();
    }


    private void stopVpnThread() {
        vpnThread.stopThread();
    }

    private void waitForNetVpn() {
        stopVpnThread();
        updateVpnStatus(VPN_STATUS_WAITING_FOR_NETWORK);
    }

    private void reconnect() {
        updateVpnStatus(VPN_STATUS_RECONNECTING);
        restartVpnThread();
    }

    private void stopVpn() {
        Log.i(TAG, "Stopping Service");
        stopVpnThread();
        try {
            unregisterReceiver(connectivityChangedReceiver);
        } catch (IllegalArgumentException e) {
            Log.i(TAG, "Ignoring exception on unregistering receiver");
        }
        updateVpnStatus(VPN_STATUS_STOPPED);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "Destroyed, shutting down");
        stopVpn();
    }

    @Override
    public boolean handleMessage(Message message) {
        if (message == null) {
            return true;
        }

        switch (message.what) {
            case VPN_MSG_STATUS_UPDATE:
                updateVpnStatus(message.arg1);
                break;
            case VPN_MSG_NETWORK_CHANGED:
                connectivityChanged((Intent) message.obj);
                break;
            default:
                throw new IllegalArgumentException("Invalid message with what = " + message.what);
        }
        return true;
    }

    private void connectivityChanged(Intent intent) {
        if (intent.getIntExtra(ConnectivityManager.EXTRA_NETWORK_TYPE, 0) == ConnectivityManager.TYPE_VPN) {
            Log.i(TAG, "Ignoring connectivity changed for our own network");
            return;
        }

        if (!ConnectivityManager.CONNECTIVITY_ACTION.equals(intent.getAction())) {
            Log.e(TAG, "Got bad intent on connectivity changed " + intent.getAction());
        }
        if (intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, false)) {
            Log.i(TAG, "Connectivity changed to no connectivity, wait for a network");
            waitForNetVpn();
        } else {
            Log.i(TAG, "Network changed, try to reconnect");
            reconnect();
        }
    }
}