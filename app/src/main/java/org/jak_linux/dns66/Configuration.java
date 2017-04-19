/* Copyright (C) 2016 Julian Andres Klode <jak@jak-linux.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.jak_linux.dns66;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.util.JsonReader;
import android.util.JsonWriter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Configuration class. This is serialized as JSON using read() and write() methods.
 *
 * @author Julian Andres Klode
 */
public class Configuration {
    private static final int VERSION = 1;
    public boolean autoStart;
    public Hosts hosts;
    public DnsServers dnsServers;
    public Whitelist whitelist;
    public boolean showNotification = true;
    public boolean nightMode;
    public boolean watchDog = true;
    public boolean ipV6Support = true;

    private static Whitelist readWhitelist(JsonReader reader) throws IOException {
        Whitelist whitelist = new Whitelist();
        reader.beginObject();

        while (reader.hasNext()) {
            switch (reader.nextName()) {
                case "showSystemApps":
                    whitelist.showSystemApps = reader.nextBoolean();
                    break;
                case "defaultMode":
                    whitelist.defaultMode = reader.nextInt();
                    break;
                case "items":
                    reader.beginArray();
                    while (reader.hasNext())
                        whitelist.items.add(reader.nextString());

                    reader.endArray();
                    break;
                case "itemsBlacklist":
                    reader.beginArray();
                    while (reader.hasNext())
                        whitelist.items.add(reader.nextString());

                    reader.endArray();
                    break;
                default:
                    reader.skipValue();
                    break;
            }
        }
        reader.endObject();

        return whitelist;
    }

    private static Hosts readHosts(JsonReader reader) throws IOException {
        Hosts hosts = new Hosts();
        reader.beginObject();
        while (reader.hasNext()) {
            switch (reader.nextName()) {
                case "enabled":
                    hosts.enabled = reader.nextBoolean();
                    break;
                case "automaticRefresh":
                    hosts.automaticRefresh = reader.nextBoolean();
                    break;
                case "items":
                    hosts.items = readItemList(reader);
                    break;
                default:
                    reader.skipValue();
                    break;
            }
        }
        reader.endObject();

        return hosts;
    }

    private static DnsServers readDnsServers(JsonReader reader) throws IOException {
        DnsServers servers = new DnsServers();
        reader.beginObject();
        while (reader.hasNext()) {
            switch (reader.nextName()) {
                case "enabled":
                    servers.enabled = reader.nextBoolean();
                    break;
                case "items":
                    servers.items = readItemList(reader);
                    break;
                default:
                    reader.skipValue();
                    break;
            }
        }
        reader.endObject();

        return servers;
    }

    private static List<Item> readItemList(JsonReader reader) throws IOException {
        reader.beginArray();
        List<Item> list = new ArrayList<>();
        while (reader.hasNext())
            list.add(readItem(reader));

        reader.endArray();
        return list;
    }

    private static Item readItem(JsonReader reader) throws IOException {
        Item item = new Item();
        reader.beginObject();
        while (reader.hasNext()) {
            switch (reader.nextName()) {
                case "title":
                    item.title = reader.nextString();
                    break;
                case "location":
                    item.location = reader.nextString();
                    break;
                case "state":
                    item.state = reader.nextInt();
                    break;
                default:
                    reader.skipValue();
                    break;
            }
        }
        reader.endObject();

        return item;
    }

    private static void writeWhitelist(JsonWriter writer, Whitelist w) throws IOException {
        writer.beginObject();
        writer.name("showSystemApps").value(w.showSystemApps);
        writer.name("defaultMode").value(w.defaultMode);
        writer.name("items");
        writer.beginArray();
        for (String string : w.items) {
            writer.value(string);
        }
        writer.endArray();
        writer.name("itemsOnVpn");
        writer.beginArray();
        for (String string : w.itemsOnVpn) {
            writer.value(string);
        }
        writer.endArray();
        writer.endObject();
    }

    private static void writeHosts(JsonWriter writer, Hosts h) throws IOException {
        writer.beginObject();
        writer.name("enabled").value(h.enabled);
        writer.name("automaticRefresh").value(h.automaticRefresh);
        writer.name("items");
        writeItemList(writer, h.items);
        writer.endObject();
    }

    private static void writeDnsServers(JsonWriter writer, DnsServers s) throws IOException {
        writer.beginObject();
        writer.name("enabled").value(s.enabled);
        writer.name("items");
        writeItemList(writer, s.items);
        writer.endObject();
    }

    private static void writeItemList(JsonWriter writer, List<Item> items) throws IOException {
        writer.beginArray();
        for (Item i : items) {
            writeItem(writer, i);
        }
        writer.endArray();
    }

    private static void writeItem(JsonWriter writer, Item i) throws IOException {
        writer.beginObject();
        writer.name("title").value(i.title);
        writer.name("location").value(i.location);
        writer.name("state").value(i.state);
        writer.endObject();
    }

    public void write(JsonWriter writer) throws IOException {
        writer.beginObject();
        writer.name("version").value(VERSION);
        writer.name("autoStart").value(autoStart);
        writer.name("showNotification").value(showNotification);
        writer.name("nightMode").value(nightMode);
        writer.name("watchDog").value(watchDog);
        writer.name("ipV6Support").value(ipV6Support);
        writer.name("hosts");
        writeHosts(writer, hosts);
        writer.name("dnsServers");
        writeDnsServers(writer, dnsServers);
        writer.name("whitelist");
        writeWhitelist(writer, whitelist);
        writer.endObject();
    }

    public void read(JsonReader reader) throws IOException {
        reader.beginObject();
        while (reader.hasNext()) {
            switch (reader.nextName()) {
                case "version":
                    if (reader.nextInt() > VERSION) {
                        throw new RuntimeException("Incompatible format");
                    }
                    break;
                case "autoStart":
                    autoStart = reader.nextBoolean();
                    break;
                case "showNotification":
                    showNotification = reader.nextBoolean();
                    break;
                case "nightMode":
                    nightMode = reader.nextBoolean();
                    break;
                case "watchDog":
                    watchDog = reader.nextBoolean();
                    break;
                case "ipV6Support":
                    ipV6Support = reader.nextBoolean();
                    break;
                case "hosts":
                    hosts = readHosts(reader);
                    break;
                case "dnsServers":
                    dnsServers = readDnsServers(reader);
                    break;
                case "whitelist":
                    whitelist = readWhitelist(reader);
                    break;
                default:
                    reader.skipValue();
                    break;
            }
        }
        reader.endObject();

        if (whitelist == null) {
            whitelist = new Whitelist();
            whitelist.items.add("org.jak_linux.dns66");
            whitelist.items.add("com.android.vending");
        }
    }

    public static class Item {
        public static final int STATE_IGNORE = 2;
        public static final int STATE_DENY = 0;
        public static final int STATE_ALLOW = 1;
        public String title;
        public String location;
        public int state;

        public boolean isDownloadable() {
            return location.startsWith("https://") || location.startsWith("http://");
        }
    }

    public static class Hosts {
        public boolean enabled;
        public boolean automaticRefresh = false;
        public List<Item> items = new ArrayList<>();
    }

    public static class DnsServers {
        public boolean enabled;
        public List<Item> items = new ArrayList<>();
    }

    public static class Whitelist {
        /**
         * All apps use the VPN.
         */
        public static final int DEFAULT_MODE_ON_VPN = 0;
        /**
         * No apps use the VPN.
         */
        public static final int DEFAULT_MODE_NOT_ON_VPN = 1;
        /**
         * System apps (excluding browsers) do not use the VPN.
         */
        public static final int DEFAULT_MODE_INTELLIGENT = 2;

        public boolean showSystemApps;
        /**
         * The default mode to put apps in, that are not listed in the lists.
         */
        public int defaultMode = DEFAULT_MODE_ON_VPN;
        /**
         * Apps that should not be allowed on the VPN
         */
        public List<String> items = new ArrayList<>();
        /**
         * Apps that should be on the VPN
         */
        public List<String> itemsOnVpn = new ArrayList<>();

        /**
         * Categorizes all packages in the system into "on vpn" or
         * "not on vpn".
         *
         * @param pm       A {@link PackageManager}
         * @param onVpn    names of packages to use the VPN
         * @param notOnVpn Names of packages not to use the VPN
         */
        public void resolve(PackageManager pm, Set<String> onVpn, Set<String> notOnVpn) {
            Set<String> webBrowserPackageNames = new HashSet<String>();
            List<ResolveInfo> resolveInfoList = pm.queryIntentActivities(newBrowserIntent(), 0);
            for (ResolveInfo resolveInfo : resolveInfoList) {
                webBrowserPackageNames.add(resolveInfo.activityInfo.packageName);
            }

            for (ApplicationInfo applicationInfo : pm.getInstalledApplications(0)) {
                // We need to always keep ourselves using the VPN, otherwise our
                // watchdog does not work.
                if (applicationInfo.packageName.equals(BuildConfig.APPLICATION_ID)) {
                    onVpn.add(applicationInfo.packageName);
                } else if (itemsOnVpn.contains(applicationInfo.packageName)) {
                    onVpn.add(applicationInfo.packageName);
                } else if (items.contains(applicationInfo.packageName)) {
                    notOnVpn.add(applicationInfo.packageName);
                } else if (defaultMode == DEFAULT_MODE_ON_VPN) {
                    onVpn.add(applicationInfo.packageName);
                } else if (defaultMode == DEFAULT_MODE_NOT_ON_VPN) {
                    notOnVpn.add(applicationInfo.packageName);
                } else if (defaultMode == DEFAULT_MODE_INTELLIGENT) {
                    if (webBrowserPackageNames.contains(applicationInfo.packageName))
                        onVpn.add(applicationInfo.packageName);
                    else if ((applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0)
                        notOnVpn.add(applicationInfo.packageName);
                    else
                        onVpn.add(applicationInfo.packageName);
                }
            }
        }

        /**
         * Returns an intent for opening a website, used for finding
         * web browsers. Extracted method for mocking.
         */
        Intent newBrowserIntent() {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("https://isabrowser.dns66.jak-linux.org/"));
            return intent;
        }
    }
}
