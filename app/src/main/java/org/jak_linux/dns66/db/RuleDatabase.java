/* Copyright (C) 2016 - 2017 Julian Andres Klode <jak@jak-linux.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.jak_linux.dns66.db;

import android.content.Context;
import androidx.annotation.Nullable;
import android.util.Log;

import org.jak_linux.dns66.Configuration;
import org.jak_linux.dns66.FileHelper;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Represents hosts that are blocked or mapped.
 * <p>
 * This is a very basic map of hosts. But it supports lock-free
 * readers with writers active at the same time, only the writers
 * having to take a lock.
 */
public class RuleDatabase {

    private static final String TAG = "RuleDatabase";
    private static final RuleDatabase instance = new RuleDatabase();
    final AtomicReference<HashMap<String, Rule>> rules = new AtomicReference<>(new HashMap<String, Rule>());
    HashMap<String, Rule> nextRules = null;

    public static class Rule {
        private final boolean blocked;
        private final InetAddress address;
        public static Rule createBlockRule() {
            return new Rule(true, null);
        }
        static Rule createMapRule(InetAddress address) {
            return new Rule(false, address);
        }
        private Rule(boolean blocked, InetAddress address) {
            this.blocked = blocked;
            this.address = address;
        }
        public boolean isBlocked() {
            if (this.blocked != (this.address == null)) {
                throw new RuntimeException("Bad rule " + this);
            }
            return this.blocked;
        }
        public InetAddress getAddress() {
            if (this.blocked != (this.address == null)) {
                throw new RuntimeException("Bad rule " + this);
            }
            return this.address;
        }
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Rule that = (Rule)o;
            if (this.blocked != that.blocked) return false;
            if (this.address == null) return that.address == null;
            return this.address.equals(that.address);
        }
        @Override
        public int hashCode() {
            return (this.blocked ? 1231 : 1237) ^
                (this.address == null ? 1307 : this.address.hashCode());
        }
        @Override
        public String toString() {
            return "blocked: " + this.blocked + " address: " + String.valueOf(this.address);
        }
    }

    /**
     * Package-private constructor for instance and unit tests.
     */
    RuleDatabase() {

    }


    /**
     * Returns the instance of the rule database.
     */
    public static RuleDatabase getInstance() {
        return instance;
    }

    /**
     * Parse a single line in a hosts file
     *
     * @param line A line to parse of the form <IP address> <hostname>
     * @return A pair: IP Address (null if IP address is 0.0.0.0), hostname
     */
    @Nullable
    static SimpleImmutableEntry<InetAddress, String> parseLine(Configuration.Item item, String line) {
        int endOfLine = line.indexOf('#');

        if (endOfLine == -1)
            endOfLine = line.length();

        // Trim spaces
        while (endOfLine > 0 && Character.isWhitespace(line.charAt(endOfLine - 1)))
            endOfLine--;

        // The line is empty.
        if (endOfLine <= 0)
            return null;

        final InetAddress address;
        final String host;

        if (item.state == Configuration.Item.STATE_MAP) {
            line = line.substring(0, endOfLine);
            Pattern LINE_PATTERN = Pattern.compile("^(\\S+)\\s+(\\S+)$");
            Matcher matcher = LINE_PATTERN.matcher(line);
            if (!matcher.matches()) {
                Log.e(TAG, "Ignoring bad map entry " + line);
                return null;
            }
            String addressString = matcher.group(1);
            host = matcher.group(2);
            try {
                address = InetAddress.getByName(addressString);
            } catch (UnknownHostException e) {
                Log.e(TAG, "Unable to parse address " + addressString + " in line '" + line + "'", e);
                return null;
            }
        } else {
            address = null;
            // Find beginning of host field
            int startOfHost = 0;

            if (line.regionMatches(0, "127.0.0.1", 0, 9) && (endOfLine <= 9 || Character.isWhitespace(line.charAt(9))))
                startOfHost += 10;
            else if (line.regionMatches(0, "::1", 0, 3) && (endOfLine <= 3 || Character.isWhitespace(line.charAt(3))))
                startOfHost += 4;
            else if (line.regionMatches(0, "0.0.0.0", 0, 7) && (endOfLine <= 7 || Character.isWhitespace(line.charAt(7))))
                startOfHost += 8;

            // Trim off space at the beginning of the host.
            while (startOfHost < endOfLine && Character.isWhitespace(line.charAt(startOfHost)))
                startOfHost++;

            // Reject lines containing a space
            for (int i = startOfHost; i < endOfLine; i++) {
                if (Character.isWhitespace(line.charAt(i)))
                    return null;
            }

            if (startOfHost >= endOfLine)
                return null;

            host = line.substring(startOfHost, endOfLine);
        }

        SimpleImmutableEntry pair = new SimpleImmutableEntry(address, host.toLowerCase(Locale.ENGLISH));
        return pair;
    }

    /**
     * Returns the Rule for the hostname
     *
     * @param host A hostname
     * @return Rule for the host if it is blocked or mapped, otherwise null.
     */
    public Rule lookup(String host) {
        return rules.get().get(host);
    }

    boolean isEmpty() {
        return rules.get().isEmpty();
    }
    /**
     * Load the hosts according to the configuration
     *
     * @param context A context used for opening files.
     * @throws InterruptedException Thrown if the thread was interrupted, so we don't waste time
     *                              reading more host files than needed.
     */
    public synchronized void initialize(Context context) throws InterruptedException {
        Configuration config = FileHelper.loadCurrentSettings(context);

        nextRules = new HashMap<>(rules.get().size());

        Log.i(TAG, "Loading block list");

        if (!config.hosts.enabled) {
            Log.d(TAG, "loadRules: Not loading, disabled.");
        } else {
            for (Configuration.Item item : config.hosts.items) {
                if (Thread.interrupted())
                    throw new InterruptedException("Interrupted");
                loadItem(context, item);
            }
        }

        rules.set(nextRules);
        Runtime.getRuntime().gc();
    }

    /**
     * Loads an item. An item can be backed by a file or contain a value in the location field.
     *
     * @param context Context to open files
     * @param item    The item to load.
     * @throws InterruptedException If the thread was interrupted.
     */
    private void loadItem(Context context, Configuration.Item item) throws InterruptedException {
        if (item.state == Configuration.Item.STATE_IGNORE)
            return;

        InputStreamReader reader;
        try {
            reader = FileHelper.openItemFile(context, item);
        } catch (FileNotFoundException e) {
            Log.d(TAG, "loadItem: File not found: " + item.location);
            return;
        }

        if (reader == null) {
            addLine(item, item.location);
            return;
        } else {
            loadReader(item, reader);
        }
    }

    /**
     * Add a single line for an item.
     *
     * @param item The item the line belongs to
     * @param host The line (<host> or <ip host>)
     */
    private void addLine(Configuration.Item item, String line) {
        if (item.state == Configuration.Item.STATE_ALLOW) {
            nextRules.remove(line);
        } else if (item.state == Configuration.Item.STATE_DENY) {
            nextRules.put(line, Rule.createBlockRule());
        } else if (item.state == Configuration.Item.STATE_MAP) {
            SimpleImmutableEntry<InetAddress, String> addressHost = parseLine(item, line);
            String host = addressHost.getValue();
            InetAddress address = addressHost.getKey();
            nextRules.put(host, Rule.createMapRule(address));
        }
    }

    private void addHost(Configuration.Item item, InetAddress address, String host) {
        // Single host to block or map
        if (item.state == Configuration.Item.STATE_ALLOW) {
            nextRules.remove(host);
        } else if (item.state == Configuration.Item.STATE_DENY) {
            nextRules.put(host, Rule.createBlockRule());
        } else if (item.state == Configuration.Item.STATE_MAP) {
            nextRules.put(host, Rule.createMapRule(address));
        }
    }

    /**
     * Load a single file
     *
     * @param item   The configuration item referencing the file
     * @param reader A reader to read lines from
     * @throws InterruptedException If thread was interrupted
     */
    boolean loadReader(Configuration.Item item, Reader reader) throws InterruptedException {
        int count = 0;
        try {
            Log.d(TAG, "loadRules: Reading: " + item.location);
            try (BufferedReader br = new BufferedReader(reader)) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (Thread.interrupted())
                        throw new InterruptedException("Interrupted");
                    SimpleImmutableEntry<InetAddress, String> addressHost = parseLine(item, line);
                    if (addressHost != null) {
                        count += 1;
                        addHost(item, addressHost.getKey(), addressHost.getValue());
                    }
                }
            }
            Log.d(TAG, "loadRules: Loaded " + count + " hosts from " + item.location);
            return true;
        } catch (IOException e) {
            Log.e(TAG, "loadRules: Error while reading " + item.location + " after " + count + " items", e);
            return false;
        } finally {
            FileHelper.closeOrWarn(reader, TAG, "loadRules: Error closing " + item.location);
        }
    }
}
