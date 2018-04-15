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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.HashSet;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Represents hosts that are blocked.
 * <p>
 * This is a very basic set of hosts. But it supports lock-free
 * readers with writers active at the same time, only the writers
 * having to take a lock.
 */
public class RuleDatabase {

    private static final String TAG = "RuleDatabase";
    private static final RuleDatabase instance = new RuleDatabase();
    final AtomicReference<HashSet<String>> blockedHosts = new AtomicReference<>(new HashSet<String>());
    HashSet<String> nextBlockedHosts = null;
    final AtomicReference<HashSet<String>> allowedHosts = new AtomicReference<>(new HashSet<String>());
    HashSet<String> nextAllowedHosts = null;
    Configuration config = null;

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
     * @param line A line to parse
     * @return A host
     */
    @Nullable
    static String parseLine(String line) {
        return parseLine(line, false);
    }

    /**
     * Parse a single line in a hosts file
     *
     * @param line A line to parse
     * @return A host
     */
    @Nullable
    static String parseLine(String line, boolean extendedFiltering) {

        // Reject AdBlock Plus filters like these
        // www.google.com#@##videoads
        // youtube.com###companion
        // because otherwise, the filter exception (#@##videoads) would be treated as a comment
        // and then www.google.com would be treated as a domain (presumably to block).
        // This is as fast as using indexOf - https://stackoverflow.com/a/10714409.
        if (line.contains("#@#"))
            return null;
        if (line.contains("###"))
            return null;

        int endOfLine = line.indexOf('#');

        if (endOfLine == -1)
            endOfLine = line.length();

        // Trim spaces
        while (endOfLine > 0 && Character.isWhitespace(line.charAt(endOfLine - 1)))
            endOfLine--;

        // The line is empty.
        if (endOfLine <= 0)
            return null;

        // Find beginning of host field
        int startOfHost = 0;

        if (line.regionMatches(0, "127.0.0.1", 0, 9) && (endOfLine <= 9 || Character.isWhitespace(line.charAt(9))))
            startOfHost += 10;
        else if (line.regionMatches(0, "::1", 0, 3) && (endOfLine <= 3 || Character.isWhitespace(line.charAt(3))))
            startOfHost += 4;
        else if (line.regionMatches(0, "0.0.0.0", 0, 7) && (endOfLine <= 7 || Character.isWhitespace(line.charAt(7))))
            startOfHost += 8;

        // Trim of space at the beginning of the host.
        while (startOfHost < endOfLine && Character.isWhitespace(line.charAt(startOfHost)))
            startOfHost++;

        // If the host is ||domain^, strip the || and ^ - AdBlock Plus syntax for whole domains
        if ((line.charAt(endOfLine - 1) == '^') && (
                (line.charAt(startOfHost) == '|') && (line.charAt(startOfHost + 1) == '|')
        )) {
            startOfHost += 2;
            endOfLine--;
        }

        if (startOfHost >= endOfLine)
            return null;

        // Given that there is already a loop that loops through the string, we can also count the
        // dots without performance hit.
        int numOfDots = 0;

        // Reject strings containing a space or one of the symbols - that wouldn't be a single
        // domain but some more complicated AdBlock plus filter and we want to ignore them
        for (int i = startOfHost; i < endOfLine; i++) {
            char testedChar = line.charAt(i);
            if (Character.isWhitespace(testedChar))
                return null;
            if (testedChar == '#')
                return null;
            if (testedChar == '/')
                return null;
            if (testedChar == '?')
                return null;
            if (testedChar == ',')
                return null;
            if (testedChar == ';')
                return null;
            if (testedChar == ':')
                return null;
            if (testedChar == '!')
                return null;
            if (testedChar == '|')
                return null;
            if (testedChar == '[')
                return null;
            if (testedChar == '&')
                return null;
            if (testedChar == '$')
                return null;
            if (testedChar == '@')
                return null;
            if (testedChar == '=')
                return null;
            if (testedChar == '^')
                return null;
            if (testedChar == '+')
                return null;

            // count dots
            if (testedChar == '.')
                numOfDots++;
        }

        // Reject strings beginning with either of these chars:
        // .  , ; ?  !  : - | / [ & $ @ _ = ^ + #
        // (at this point, domains of format ||domain^ were already detected)
        // these are control chars of the AdBlock Plus format and we want to ignore such lines
        if (line.charAt(startOfHost) == '.') return null;
        if (line.charAt(startOfHost) == '-') return null;
        if (line.charAt(startOfHost) == '_') return null;

        // already detected - if (line.charAt(startOfHost) == ',') return null;
        // already detected - if (line.charAt(startOfHost) == ';') return null;
        // already detected - if (line.charAt(startOfHost) == '?') return null;
        // already detected - if (line.charAt(startOfHost) == '!') return null;
        // already detected - if (line.charAt(startOfHost) == ':') return null;
        // already detected - if (line.charAt(startOfHost) == '|') return null;
        // already detected - if (line.charAt(startOfHost) == '/') return null;
        // already detected - if (line.charAt(startOfHost) == '[') return null;
        // already detected - if (line.charAt(startOfHost) == '&') return null;
        // already detected - if (line.charAt(startOfHost) == '$') return null;
        // already detected - if (line.charAt(startOfHost) == '@') return null;
        // already detected - if (line.charAt(startOfHost) == '=') return null;
        // already detected - if (line.charAt(startOfHost) == '^') return null;
        // already detected - if (line.charAt(startOfHost) == '+') return null;
        // already detected - if (line.charAt(startOfHost) == '#') return null;

        // Also reject strings ending with those chars
        if (line.charAt(endOfLine - 1) == '.') return null;
        if (line.charAt(endOfLine - 1) == '-') return null;
        if (line.charAt(endOfLine - 1) == '_') return null;

        if (extendedFiltering) {
            if (numOfDots > 2) {  // optimization - with less than 3 parts, it doesn't matter
                // If the host address has more than 3 parts (e.g. en.analytics.example.com), it also adds
                // the last 3 parts as another host (e.g. analytics.example.com), so that related subdomains
                // are handled as well (e.g. de.analytics.example.com). This cannot be done for two parts
                // because e.g. analytics.example.com would cause example.com and docs.example.com to be
                // blocked as well and we don't want that. 3 parts is the best balance.
                // If the public suffix is something else than one part (e.g. co.uk instead of com),
                // it is adjusted accordingly.

                int partsPublicSuffix = howManyPartsIsPublicSuffix(line.substring(startOfHost, endOfLine).toLowerCase(Locale.ENGLISH));

                // how much to merge - 3 for sth.example.com, 4 for sth.example.co.uk
                int resultPartsNum = 2 + partsPublicSuffix;

                //    .        sth       .      example        .         com
                //  dot 3    part 3    dot 2     part 2     dot 1       part 1


                int currentDotCount = 0;
                for (int i = endOfLine - 1; i >= startOfHost; i--) {
                    if (line.charAt(i) == '.')
                        currentDotCount++;
                    if (currentDotCount == resultPartsNum) {
                        // delete this dot and everything before it
                        startOfHost = i + 1;
                        break;
                    }
                }

            }

            // If the host address begins with "www." (e.g. www.badsite.com), it also adds the domain
            // name without the leading "www." (e.g. badsite.com).
            if (line.regionMatches(startOfHost, "www.", 0, 4))
                startOfHost += 4;
        }

        // sanity checks again
        if (startOfHost >= endOfLine)
            return null;

        // reject strings shorter than 1 character
        if (startOfHost + 1 > (endOfLine - 1) )
            return null;


        return line.substring(startOfHost, endOfLine).toLowerCase(Locale.ENGLISH);
    }

    /**
     * Checks if a host is blocked.
     *
     * @param host A hostname
     * @return true if the host is blocked, false otherwise.
     */
    public boolean isBlocked(String host) {

        // example: host == server3389.de.beacon.tracking.badserver.com
        if (allowedHosts.get().contains(host)) {
            return false;
        }

        // example: host == server3389.de.beacon.tracking.badserver.com
        if (blockedHosts.get().contains(host)) {
            return true;
        }

        if ((null != config) && (config.extendedFiltering.enabled)) {
            // example of chopping off:
            // i == 0, host == de.beacon.tracking.badserver.com
            // i == 1, host == beacon.tracking.badserver.com
            // i == 2, host == tracking.badserver.com
            // i == 3, host == badserver.com
            // i == 4, host == com
            // (yes, comparing even the top-level domain so that malicious TLDs can be present in the
            //  blocklist and can be blocked)

            // This is effectively like having a wildcard before every domain in the blacklist -
            // *.example.com - but it is more efficient because it is faster to check the existence
            // of a string in a hashset 1-10 times than to build and evaluate a huge regexp
            // containing all the entries of the domain blacklist.

            for (int i = 0; i < 50; i++) {
                // strip up to 10 leading parts (so that there is an upper bound for performance reasons)
                String[] split_host = host.split("\\.", 2);
                if (split_host.length <= 1) {
                    // there's nothing to chop off left
                    break;
                }
                host = split_host[1];
                if (allowedHosts.get().contains(host)) {
                    return false;
                }
                if (blockedHosts.get().contains(host)) {
                    return true;
                }
            }
            // A domain name with 50 components is a pathological case, let's block it
            return true;
        }
        return false;
    }

    /**
     * Check if any hosts are blocked
     *
     * @return true if any hosts are blocked, false otherwise.
     */
    boolean isEmpty() {
        return blockedHosts.get().isEmpty();
    }

    /**
     * Load the hosts according to the configuration
     *
     * @param context A context used for opening files.
     * @throws InterruptedException Thrown if the thread was interrupted, so we don't waste time
     *                              reading more host files than needed.
     */
    public synchronized void initialize(Context context) throws InterruptedException {
        config = FileHelper.loadCurrentSettings(context);

        nextBlockedHosts = new HashSet<>(blockedHosts.get().size());
        nextAllowedHosts = new HashSet<>(allowedHosts.get().size());

        Log.i(TAG, "Loading block list");

        if (!config.hosts.enabled) {
            Log.d(TAG, "loadBlockedHosts: Not loading, disabled.");
        } else {
            for (Configuration.Item item : config.hosts.items) {
                if (Thread.interrupted())
                    throw new InterruptedException("Interrupted");
                loadItem(context, item);
            }
        }

        blockedHosts.set(nextBlockedHosts);
        allowedHosts.set(nextAllowedHosts);
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
            boolean extendedFiltering = false;
            if ((null != config) && (config.extendedFiltering.enabled)) {
                extendedFiltering = true;
            }
            String host = parseLine(item.location, extendedFiltering);
            if (host != null) {
                addHost(item, item.location);
            }
        } else {
            loadReader(item, reader);
        }
    }

    /**
     * Add a host for an item.
     * If the host address has more than 3 parts (e.g. en.analytics.example.com), it also adds
     * the last 3 parts as another host (e.g. analytics.example.com), so that related subdomains
     * are handled as well (e.g. de.analytics.example.com). This cannot be done for two parts
     * because e.g. analytics.example.com would cause example.com and docs.example.com to be
     * blocked as well and we don't want that. 3 parts is the best balance.
     * If the public suffix is something else than one part (e.g. co.uk instead of com),
     * it is adjusted accordingly.
     * If the host address begins with "www." (e.g. www.badsite.com), it also adds the domain
     * name without the leading "www." (e.g. badsite.com).
     *
     * @param item The item the host belongs to
     * @param host The host
     */
    private void addHost(Configuration.Item item, String host) {
        if (item.state == Configuration.Item.STATE_ALLOW) {
            nextBlockedHosts.remove(host);
            nextAllowedHosts.add(host);
        } else if (item.state == Configuration.Item.STATE_DENY) {
            nextBlockedHosts.add(host);
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
        boolean extendedFiltering = false;
        if ((null != config) && (config.extendedFiltering.enabled)) {
            extendedFiltering = true;
        }
        int count = 0;
        try {
            Log.d(TAG, "loadBlockedHosts: Reading: " + item.location);
            try (BufferedReader br = new BufferedReader(reader)) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (Thread.interrupted())
                        throw new InterruptedException("Interrupted");
                    String host = parseLine(line, extendedFiltering);
                    if (host != null) {
                        count += 1;
                        addHost(item, host);
                    }
                }
            }
            Log.d(TAG, "loadBlockedHosts: Loaded " + count + " hosts from " + item.location);
            return true;
        } catch (IOException e) {
            Log.e(TAG, "loadBlockedHosts: Error while reading " + item.location + " after " + count + " items", e);
            return false;
        } finally {
            FileHelper.closeOrWarn(reader, TAG, "loadBlockedHosts: Error closing " + item.location);
        }
    }

    /**
     * Returns number of parts (those strings in domain names joined by dots) of the public suffix
     * of the given domain name.
     * @param domain
     * @return Number of parts of the public suffix, e.g. 1 for .com and 2 for .co.uk
     */
    protected static int howManyPartsIsPublicSuffix(String domain) {
        // A dumb and simple algorithm for determining public prefixes from the beginning of
        // https://bugzilla.mozilla.org/show_bug.cgi?id=252342
        // Remember that the goal of publicsuffix.org is to allow safe handling of cookies,
        // while in DNS66, it is just about minimizing too coarse blocking of fourth-level
        // domains - if there is a misdetection, it won't have a security impact, just a
        // different granularity of blocking for the particular domain. Therefore, misdetections
        // like co.com generally don't matter - it would only mean that anything under .co.com
        // would coalesce one subdomain deeper, but all explicitly listed domains in a blocklist
        // would still be properly blocked.
        
        // positions
        int lastDot = -1;
        int secondLastDot = -1;

        for (int i = domain.length() - 1; i >= 0; i--) {
            if (domain.charAt(i) == '.') {
                if (-1 == lastDot) {
                    lastDot = i;
                } else {
                    secondLastDot = i;
                    break;
                }
            }
        }

        if (-1 == secondLastDot)
            return 1;

        String secondLevelPart = domain.substring(secondLastDot + 1, lastDot).toLowerCase(Locale.ENGLISH);

        if (secondLevelPart.equals("co")) return 2;
        if (secondLevelPart.equals("com")) return 2;
        if (secondLevelPart.equals("org")) return 2;
        if (secondLevelPart.equals("net")) return 2;
        if (secondLevelPart.equals("ed")) return 2;
        if (secondLevelPart.equals("edu")) return 2;
        if (secondLevelPart.equals("gov")) return 2;
        if (secondLevelPart.equals("ac")) return 2;
        if (secondLevelPart.equals("me")) return 2;
        if (secondLevelPart.equals("police")) return 2;
        if (secondLevelPart.equals("nhs")) return 2;
        if (secondLevelPart.equals("ltd")) return 2;

        if (secondLevelPart.equals("plc")) return 2;
        if (secondLevelPart.equals("sch")) return 2;
        if (secondLevelPart.equals("mod")) return 2;
        if (secondLevelPart.equals("mil")) return 2;
        if (secondLevelPart.equals("int")) return 2;

        return 1;
    }


}
