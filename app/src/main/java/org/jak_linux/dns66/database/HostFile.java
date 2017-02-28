/* Copyright (C) 2017 Julian Andres Klode <jak@jak-linux.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.jak_linux.dns66.database;

import android.content.Context;
import android.util.Log;

import org.jak_linux.dns66.Configuration;
import org.jak_linux.dns66.FileHelper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * A representation of a host file.
 * <p>
 * This class maintains information about the data contained in a Configuration.Item.
 */
class HostFile {
    private static final String TAG = "HostFile";
    /**
     * A configuration item associated with this host list
     */
    Configuration.Item item;
    /**
     * The last last-modified time we read
     */
    long lastModified;
    /**
     * The set of hosts contained in the file.
     */
    Set<String> hosts;

    /**
     * Update the host file.
     * <p>
     * This checks if the host file has been changed, and if so, replaces the set of hosts
     * with a new set of hosts.
     *
     * @param context
     * @throws InterruptedException
     */
    public void update(Context context) throws InterruptedException {
        File file = FileHelper.getItemFile(context, item);

        if (file == null) {
            hosts = new HashSet<>();

            /* Not file backed, just read in the value. */
            if (!item.location.contains("/"))
                hosts.add(item.location);
            return;
        }

        long newLastModified = file.lastModified();
        // We already have a cached set of hosts that is up to date: Do nothing
        if (hosts != null && newLastModified <= lastModified) {
            Log.d(TAG, "update: Cache still current, skipping " + item.location);
            return;
        }

        // Start with the update
        hosts = new HashSet<>();
        lastModified = newLastModified;

        FileReader reader;

        try {
            reader = new FileReader(file);
        } catch (FileNotFoundException e) {
            Log.e(TAG, "update: File not found: " + item.location, e);
            return;
        }

        Log.d(TAG, "update: Reading: " + file.getAbsolutePath());

        try (BufferedReader br = new BufferedReader(reader)) {
            String line;
            while ((line = br.readLine()) != null) {
                processLine(line);
            }
        } catch (IOException e) {
            Log.e(TAG, "update: Error while reading files", e);
        } finally {
            try {
                reader.close();
            } catch (IOException e) {
                Log.e(TAG, "update: Error closing reader", e);
            }
        }
        Log.d(TAG, "update: Loaded " + hosts.size() + " hosts" + " from " + item.location);
    }

    /**
     * Read a single line.
     * <p>
     * A line consists of one or two fields, separated by one or more space or tab characters.
     * If a line has two fields, the first field must be either 127.0.0.1 or 0.0.0.0.
     *
     * @param line A line to process
     * @return The number of items in the line
     * @throws InterruptedException
     */
    private void processLine(String line) throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException("Interrupted");
        }
        String s = line.trim();

        if (s.length() != 0) {
            String[] ss = s.split("#");
            s = ss.length > 0 ? ss[0].trim() : "";
        }
        if (s.length() != 0) {
            String[] split = s.split("[ \t]+");
            String host = null;
            if (split.length == 2 && (split[0].equals("127.0.0.1") || split[0].equals("0.0.0.0"))) {
                host = split[1].toLowerCase(Locale.ENGLISH);
            } else if (split.length == 1) {
                host = split[0].toLowerCase(Locale.ENGLISH);
            }
            if (host != null) {
                hosts.add(host);
            }
        }
    }
}
