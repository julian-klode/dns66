/* Copyright (C) 2016 - 2017 Julian Andres Klode <jak@jak-linux.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.jak_linux.dns66.db;

import android.content.Context;
import android.support.annotation.Nullable;
import android.util.Log;

import org.jak_linux.dns66.Configuration;
import org.jak_linux.dns66.FileHelper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Represents hosts that are blocked.
 * <p>
 * This is a very basic set of hosts.
 */
public class RuleDatabase {

    private static final String TAG = "RuleDatabase";
    public final Set<String> blockedHosts = new HashSet<>();

    /**
     * Parse a single line in a hosts file
     *
     * @param line A line to parse
     * @return A host
     */
    @Nullable
    public static String parseLine(String line) {
        String s = line.trim();

        if (s.length() != 0) {
            String[] ss = s.split("#");
            s = ss.length > 0 ? ss[0].trim() : "";
        }
        String host = null;
        if (s.length() != 0) {
            String[] split = s.split("[ \t]+");

            if (split.length == 2 && (split[0].equals("127.0.0.1") || split[0].equals("0.0.0.0"))) {
                host = split[1].toLowerCase(Locale.ENGLISH);
            } else if (split.length == 1) {
                host = split[0].toLowerCase(Locale.ENGLISH);
            }
        }
        return host;
    }

    /**
     * Checks if a host is blocked.
     *
     * @param host A hostname
     * @return true if the host is blocked, false otherwise.
     */
    public boolean isBlocked(String host) {
        return blockedHosts.contains(host);
    }

    /**
     * Check if any hosts are blocked
     *
     * @return true if any hosts are blocked, false otherwise.
     */
    public boolean isEmpty() {
        return blockedHosts.isEmpty();
    }

    /**
     * Load the hosts according to the configuration
     *
     * @param context A context used for opening files.
     * @throws InterruptedException Thrown if the thread was interrupted, so we don't waste time
     *                              reading more host files than needed.
     */
    public void initialize(Context context) throws InterruptedException {
        Configuration config = FileHelper.loadCurrentSettings(context);

        blockedHosts.clear();
        Runtime.getRuntime().gc();

        Log.i(TAG, "Loading block list");

        if (!config.hosts.enabled) {
            Log.d(TAG, "loadBlockedHosts: Not loading, disabled.");
        }

        for (Configuration.Item item : config.hosts.items) {
            if (Thread.interrupted())
                throw new InterruptedException("Interrupted");
            loadItem(context, item);
        }
    }

    /**
     * Loads an item. An item can be backed by a file or contain a value in the location field.
     *
     * @param context Context to open files
     * @param item    The item to load.
     * @throws InterruptedException If the thread was interrupted.
     */
    public void loadItem(Context context, Configuration.Item item) throws InterruptedException {
        File file = FileHelper.getItemFile(context, item);

        if (item.state == Configuration.Item.STATE_IGNORE)
            return;

        if (file == null && !item.location.contains("/")) {
            addHost(item, item.location);

            return;
        }

        if (file != null) {
            FileReader reader;
            try {
                reader = new FileReader(file);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                return;
            }
            loadReader(item, reader);
        }
    }

    /**
     * Add a single host for an item.
     *
     * @param item The item the host belongs to
     * @param host The host
     */
    public void addHost(Configuration.Item item, String host) {
        // Single address to block
        if (item.state == Configuration.Item.STATE_ALLOW) {
            blockedHosts.remove(host);
        } else if (item.state == Configuration.Item.STATE_DENY) {
            blockedHosts.add(host);
        }
    }

    /**
     * Load a single file
     *
     * @param item   The configuration item referencing the file
     * @param reader A reader to read lines from
     * @throws InterruptedException
     */
    public void loadReader(Configuration.Item item, Reader reader) throws InterruptedException {
        int count = 0;
        try {
            Log.d(TAG, "loadBlockedHosts: Reading: " + item.location);
            try (BufferedReader br = new BufferedReader(reader)) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (Thread.interrupted())
                        throw new InterruptedException("Interrupted");
                    String host = parseLine(line);
                    if (host != null) {
                        count += 1;
                        addHost(item, host);
                    }
                }
            }

        } catch (IOException e) {
            Log.e(TAG, "loadBlockedHosts: Error while reading files", e);
        } finally {
            try {
                reader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        Log.d(TAG, "loadBlockedHosts: Loaded " + count + " hosts from " + item.location);
    }
}
