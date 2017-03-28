/* Copyright (C) 2016 - 2017 Julian Andres Klode <jak@jak-linux.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.jak_linux.dns66.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDoneException;
import android.database.sqlite.SQLiteStatement;
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
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Represents hosts that are blocked.
 * <p>
 * This is a very basic set of hosts.
 */
public class RuleDatabase {

    private static final String TAG = "RuleDatabase";
    SQLiteDatabase database = null;
    private RuleDatabaseHelper helper = null;

    private ContentValues hostsetValues = new ContentValues();
    private ContentValues hostValues = new ContentValues();
    private SQLiteStatement lookupStatement = null;

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
        if (lookupStatement == null) {
            lookupStatement = database.compileStatement("select action from host_action where host = ?");
        }

        lookupStatement.bindString(1, host);

        try {
            return lookupStatement.simpleQueryForLong() == Configuration.Item.STATE_DENY;
        } catch (SQLiteDoneException e) {
            return false;
        }
    }

    /**
     * Check if any hosts are blocked
     *
     * @return true if any hosts are blocked, false otherwise.
     */
    public boolean isEmpty() {
        Cursor c = database.query("host_action", new String[]{"action"}, null, null, null, null, null, "1");

        boolean result = c.moveToNext();

        c.close();
        return !result;
    }



    /**
     * Marks all hostfiles in the configuration as reachable, by setting their last_seen_millis
     * column to the value specified in currentTime.
     *
     * @param configuration The configuration we want to match eventually
     * @param currentTime   The time of this update.
     */
    void markReachable(Configuration configuration, long currentTime) {
        database.beginTransactionNonExclusive();
        try {
            for (Configuration.Item hostfile : configuration.hosts.items) {
                ContentValues values = new ContentValues();
                values.put("last_seen_millis", currentTime);

                database.update("hostfiles", values, "location = ?", new String[]{hostfile.location});
            }
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
    }

    /**
     * Deletes all files no longer in the configuration older than some days. This compares
     * the last_seen_millis against the currentTime, and deletes all host files older than 7
     * days and marks all other files older than currentTime as ignored.
     * <p>
     * This delayed deletion allows you to temporarily delete an entry, without all hosts for
     * that entry being deleted at once.
     *
     * @param configuration The configuration we want to match eventually
     * @param currentTime   The time of this update.
     */
    void sweepUnreachable(Configuration configuration, long currentTime) {
        assert configuration != null;
        database.beginTransactionNonExclusive();
        try {
            // Collect really old host files
            database.delete("hostfiles", "last_seen_millis < ?",
                    new String[]{Long.toString(currentTime - TimeUnit.DAYS.toMillis(7))});

            // Mark others from previous generations as ignored.
            ContentValues values = new ContentValues();
            values.put("action", Configuration.Item.STATE_IGNORE);
            database.update("hostfiles", values, "last_seen_millis < ?",
                    new String[]{Long.toString(currentTime)});
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
    }

    /**
     * TODO: Implement
     * @param hostfile
     * @param currentTime
     */
    void updateHostFile(Configuration.Item hostfile, long currentTime, boolean forceUpdate) {
        database.beginTransactionNonExclusive();
        try {
            ContentValues values = new ContentValues();
            Cursor c = database.query("hostfiles", new String[]{"hostfile_id", "last_updated_millis", "last_seen_millis"}, "location = ?", new String[]{hostfile.location}, null, null, null);
            if (!c.moveToNext()) {
                Log.d(TAG, "updateHostFile: Could not update " + hostfile.location + ": No entry found");
                return;
            }
            long id = c.getLong(0);
            long lastUpdatedMillis = c.getLong(1);
            long lastSeenMillis = c.getLong(2);

            values.put("last_seen_millis", currentTime);
            if (forceUpdate || currentTime - lastUpdatedMillis >= TimeUnit.DAYS.toMillis(1)) {
                // Delete all hosts, we are refreshing
                database.delete("hosts", "hostfile_id = ?", new String[]{Long.toString(id)});

                values.put("last_updated_millis", currentTime);
            }


            // Mark the hostfile as updated.
            database.update("hostfiles", values, "hostfile_id = ?", new String[]{Long.toString(id)});
        } finally {
            database.endTransaction();
        }
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

        if (helper == null) {
            helper = new RuleDatabaseHelper(context);
            database = helper.getReadableDatabase();
        }


        Runtime.getRuntime().gc();

        Log.i(TAG, "Loading block list");

        if (!config.hosts.enabled) {
            Log.d(TAG, "loadBlockedHosts: Not loading, disabled.");
        }
        if (!isEmpty()) {
            Log.d(TAG, "initialize: Database is already initialized, not re-doing it");
            return;
        }

        database = helper.getWritableDatabase();

        int priority = 0;
        for (Configuration.Item item : config.hosts.items) {
            if (Thread.interrupted())
                throw new InterruptedException("Interrupted");

            database.beginTransaction();
            if (createOrUpdateItem(item, priority++) && loadItem(context, item))
                database.setTransactionSuccessful();
            database.endTransaction();
        }
    }

    /**
     * Set the database (used for testing)
     *
     * @param database Database to use.
     */
    void setDatabaseForTesting(SQLiteDatabase database) {
        this.database = database;
    }

    /**
     * Create or update an item.
     *
     * @param item     The item to update
     * @param priority The priority of the item (index in list of items)
     */
    public boolean createOrUpdateItem(Configuration.Item item, long priority) {
        hostsetValues.put("title", item.title != null ? item.title : "<no title>");
        hostsetValues.put("location", item.location);
        hostsetValues.put("action", item.state);
        hostsetValues.put("priority", priority);
        // TODO: Timeout handling
        hostsetValues.put("last_seen_millis", 0);
        hostsetValues.put("last_updated_millis", 0);
        hostsetValues.put("last_modified_millis", 0);

        boolean result = database.update("hostfiles", hostsetValues, "location = ?", new String[]{item.location}) > 0;
        if (!result)
            result = database.insertOrThrow("hostfiles", null, hostsetValues) >= 0;
        return result;
    }

    /**
     * Loads an item. An item can be backed by a file or contain a value in the location field.
     *
     * @param context Context to open files
     * @param item    The item to load.
     * @throws InterruptedException If the thread was interrupted.
     */
    public boolean loadItem(Context context, Configuration.Item item) throws InterruptedException {
        File file = FileHelper.getItemFile(context, item);

        if (item.state == Configuration.Item.STATE_IGNORE)
            return true;

        if (file == null && !item.location.contains("/")) {
            addHost(item, item.location);
            return true;
        }

        if (file != null) {
            FileReader reader;
            try {
                reader = new FileReader(file);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                return false;
            }

            return loadReader(item, reader);
        }

        return true;
    }

    /**
     * Add a single host for an item.
     *
     * @param item The item the host belongs to
     * @param host The host
     */
    public void addHost(Configuration.Item item, String host) {
        Cursor c = database.rawQuery("select hostfile_id from hostfiles WHERE location = ?", new String[]{item.location});
        if (!c.moveToNext()) {
            Log.w(TAG, "addHost: Could not find host file " + item.location);
            c.close();
            return;
        }

        long id = c.getLong(0);
        c.close();
        hostValues.put("host", host);
        hostValues.put("hostfile_id", id);
        database.insert("hosts", null, hostValues);
    }

    /**
     * Load a single file
     *
     * @param item   The configuration item referencing the file
     * @param reader A reader to read lines from
     * @throws InterruptedException
     */
    public boolean loadReader(Configuration.Item item, Reader reader) throws InterruptedException {
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
            Log.d(TAG, "loadBlockedHosts: Loaded " + count + " hosts from " + item.location);
            return true;
        } catch (IOException e) {
            Log.e(TAG, "loadBlockedHosts: Error while reading files", e);
            return false;
        } finally {
            try {
                reader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }
}
