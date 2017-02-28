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

import java.util.Collection;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A database of sets of strings backed by simple files.
 * <p>
 * This class maintains a file-based database of hosts, and provides two operations:
 * <p>
 * A '{@link #contains}' operation that checks whether a given string exists in the database.
 * <p>
 * A {@link #update} operation that takes a new list of files and updates the database to reflect
 * those files.
 */

public class HostDatabase {
    private final static String TAG = "HostDatabase";
    /**
     * The instance of the database.
     */
    private final static HostDatabase INSTANCE = new HostDatabase();

    /**
     * A list of entries, atomically replaced in update()
     */
    private final static AtomicReference<LinkedList<HostFile>> files = new AtomicReference<>(new LinkedList<HostFile>());

    /**
     * Private constructor for singleton pattern.
     */
    private HostDatabase() {

    }

    /**
     * Return the singleton
     *
     * @return the singleton object
     */
    public static HostDatabase getInstance() {
        return INSTANCE;
    }

    /**
     * Check if a host is blacklisted.
     * <p>
     * This operations requires only one atomic reference read and requires as many hash
     * table lookups as there are files, making them relatively efficient. The atomic reference
     * read might be practically free, as there is usually no contention.
     *
     * @param host A host name.
     * @return true if the host is blacklisted
     */
    public boolean contains(String host) {
        int result = Configuration.Item.STATE_ALLOW;
        for (HostFile file : files.get()) {
            if (file.hosts.contains(host))
                result = file.item.state;
        }
        return result == Configuration.Item.STATE_DENY;
    }

    /**
     * Atomically replaces the current database state with the new one.
     * <p>
     * This method copies the current list of host files, maintaining any up-to-date sets
     * contained therein, and reading any newly added or changed files.
     *
     * @param context A context, in case we need to open files
     * @param items   The items to use for the new database
     * @throws InterruptedException
     */
    public synchronized void update(Context context, Collection<Configuration.Item> items) throws InterruptedException {
        LinkedList<HostFile> oldFiles = files.get();
        LinkedList<HostFile> newFiles = new LinkedList<>();

        for (Configuration.Item item : items) {
            if (item.state == Configuration.Item.STATE_IGNORE)
                continue;

            // We must copy the here - we do not want to change items that are currently used.
            HostFile newFile = new HostFile();
            // Copy the item so others can modify further.
            newFile.item = new Configuration.Item();
            newFile.item.location = item.location;
            newFile.item.state = item.state;
            newFile.item.title = item.title;
            for (HostFile file : oldFiles) {
                if (file.item.location.equals(item.location)) {
                    Log.d(TAG, "update: Considering cached file (lastModified=" + file.lastModified + " for " + item.location);
                    newFile.lastModified = file.lastModified;
                    newFile.hosts = file.hosts;
                    break;
                }
            }

            newFile.update(context);
            newFiles.add(newFile);
        }

        files.set(newFiles);
    }
}
