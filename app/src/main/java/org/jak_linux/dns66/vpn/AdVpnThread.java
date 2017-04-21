/* Copyright (C) 2016 Julian Andres Klode <jak@jak-linux.org>
 *
 * Derived from AdBuster:
 * Copyright (C) 2016 Daniel Brodie <dbrodie@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * Contributions shall also be provided under any later versions of the
 * GPL.
 */
package org.jak_linux.dns66.vpn;


import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructPollfd;
import android.util.Log;

import org.jak_linux.dns66.Configuration;
import org.jak_linux.dns66.FileHelper;
import org.jak_linux.dns66.MainActivity;
import org.pcap4j.packet.IpPacket;
import org.pcap4j.packet.factory.PacketFactoryPropertiesLoader;
import org.pcap4j.util.PropertiesLoader;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;


class AdVpnThread implements Runnable, DnsPacketProxy.EventLoop {
    private static final String TAG = "AdVpnThread";
    private static final int MIN_RETRY_TIME = 5;
    private static final int MAX_RETRY_TIME = 2 * 60;
    /* If we had a successful connection for that long, reset retry timeout */
    private static final long RETRY_RESET_SEC = 60;
    /* Maximum number of responses we want to wait for */
    private static final int DNS_MAXIMUM_WAITING = 1024;
    private static final long DNS_TIMEOUT_SEC = 10;
    /* Upstream DNS servers, indexed by our IP */
    final ArrayList<InetAddress> upstreamDnsServers = new ArrayList<>();
    private final VpnService vpnService;
    private final Notify notify;
    /* Data to be written to the device */
    private final Queue<byte[]> deviceWrites = new LinkedList<>();
    // HashMap that keeps an upper limit of packets
    private final WospList dnsIn = new WospList();
    // The object where we actually handle packets.
    private final DnsPacketProxy dnsPacketProxy = new DnsPacketProxy(this);
    // Watch dog that checks our connection is alive.
    private final VpnWatchdog vpnWatchDog = new VpnWatchdog();
    /**
     * After how many iterations we should clear pcap4js packetfactory property cache
     */
    private final int PCAP4J_FACTORY_CLEAR_NASTY_CACHE_EVERY = 1024;
    private Thread thread = null;
    private FileDescriptor mBlockFd = null;
    private FileDescriptor mInterruptFd = null;
    /**
     * Number of iterations since we last cleared the pcap4j cache
     */
    private int pcap4jFactoryClearCacheCounter = 0;

    public AdVpnThread(VpnService vpnService, Notify notify) {
        this.vpnService = vpnService;
        this.notify = notify;
    }

    private static Set<InetAddress> getDnsServers(Context context) throws VpnNetworkException {
        Set<InetAddress> out = new HashSet<>();
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(VpnService.CONNECTIVITY_SERVICE);
        // Seriously, Android? Seriously?
        NetworkInfo activeInfo = cm.getActiveNetworkInfo();
        if (activeInfo == null)
            throw new VpnNetworkException("No DNS Server");

        for (Network nw : cm.getAllNetworks()) {
            NetworkInfo ni = cm.getNetworkInfo(nw);
            if (ni == null || !ni.isConnected() || ni.getType() != activeInfo.getType()
                    || ni.getSubtype() != activeInfo.getSubtype())
                continue;
            for (InetAddress address : cm.getLinkProperties(nw).getDnsServers())
                out.add(address);
        }
        return out;
    }

    public void startThread() {
        Log.i(TAG, "Starting Vpn Thread");
        thread = new Thread(this, "AdVpnThread");
        thread.start();
        Log.i(TAG, "Vpn Thread started");
    }

    public void stopThread() {
        Log.i(TAG, "Stopping Vpn Thread");
        if (thread != null) thread.interrupt();

        mInterruptFd = FileHelper.closeOrWarn(mInterruptFd, TAG, "stopThread: Could not close interruptFd");
        try {
            if (thread != null) thread.join(2000);
        } catch (InterruptedException e) {
            Log.w(TAG, "stopThread: Interrupted while joining thread", e);
        }
        if (thread != null && thread.isAlive()) {
            Log.w(TAG, "stopThread: Could not kill VPN thread, it is still alive");
        } else {
            thread = null;
            Log.i(TAG, "Vpn Thread stopped");
        }
    }

    @Override
    public synchronized void run() {
        Log.i(TAG, "Starting");

        // Load the block list
        try {
            dnsPacketProxy.initialize(vpnService, upstreamDnsServers);
            vpnWatchDog.initialize(FileHelper.loadCurrentSettings(vpnService).watchDog);
        } catch (InterruptedException e) {
            return;
        }

        if (notify != null) {
            notify.run(AdVpnService.VPN_STATUS_STARTING);
        }

        int retryTimeout = MIN_RETRY_TIME;
        // Try connecting the vpn continuously
        while (true) {
            long connectTimeMillis = 0;
            try {
                connectTimeMillis = System.currentTimeMillis();
                // If the function returns, that means it was interrupted
                runVpn();

                Log.i(TAG, "Told to stop");
                break;
            } catch (InterruptedException e) {
                break;
            } catch (VpnNetworkException e) {
                // We want to filter out VpnNetworkException from out crash analytics as these
                // are exceptions that we expect to happen from network errors
                Log.w(TAG, "Network exception in vpn thread, ignoring and reconnecting", e);
                // If an exception was thrown, show to the user and try again
                if (notify != null)
                    notify.run(AdVpnService.VPN_STATUS_RECONNECTING_NETWORK_ERROR);
            } catch (Exception e) {
                Log.e(TAG, "Network exception in vpn thread, reconnecting", e);
                //ExceptionHandler.saveException(e, Thread.currentThread(), null);
                if (notify != null)
                    notify.run(AdVpnService.VPN_STATUS_RECONNECTING_NETWORK_ERROR);
            }

            if (System.currentTimeMillis() - connectTimeMillis >= RETRY_RESET_SEC * 1000) {
                Log.i(TAG, "Resetting timeout");
                retryTimeout = MIN_RETRY_TIME;
            }

            // ...wait and try again
            Log.i(TAG, "Retrying to connect in " + retryTimeout + "seconds...");
            try {
                Thread.sleep((long) retryTimeout * 1000);
            } catch (InterruptedException e) {
                break;
            }

            if (retryTimeout < MAX_RETRY_TIME)
                retryTimeout *= 2;
        }

        if (notify != null)
            notify.run(AdVpnService.VPN_STATUS_STOPPING);
        Log.i(TAG, "Exiting");
    }

    private void runVpn() throws InterruptedException, ErrnoException, IOException, VpnNetworkException {
        // Allocate the buffer for a single packet.
        byte[] packet = new byte[32767];

        // A pipe we can interrupt the poll() call with by closing the interruptFd end
        FileDescriptor[] pipes = Os.pipe();
        mInterruptFd = pipes[0];
        mBlockFd = pipes[1];

        // Authenticate and configure the virtual network interface.
        try (ParcelFileDescriptor pfd = configure()) {
            // Read and write views of the tun device
            FileInputStream inputStream = new FileInputStream(pfd.getFileDescriptor());
            FileOutputStream outFd = new FileOutputStream(pfd.getFileDescriptor());

            // Now we are connected. Set the flag and show the message.
            if (notify != null)
                notify.run(AdVpnService.VPN_STATUS_RUNNING);

            // We keep forwarding packets till something goes wrong.
            while (doOne(inputStream, outFd, packet))
                ;
        } finally {
            mBlockFd = FileHelper.closeOrWarn(mBlockFd, TAG, "runVpn: Could not close blockFd");
        }
    }

    private boolean doOne(FileInputStream inputStream, FileOutputStream outFd, byte[] packet) throws IOException, ErrnoException, InterruptedException, VpnNetworkException {
        StructPollfd deviceFd = new StructPollfd();
        deviceFd.fd = inputStream.getFD();
        deviceFd.events = (short) OsConstants.POLLIN;
        StructPollfd blockFd = new StructPollfd();
        blockFd.fd = mBlockFd;
        blockFd.events = (short) (OsConstants.POLLHUP | OsConstants.POLLERR);

        if (!deviceWrites.isEmpty())
            deviceFd.events |= (short) OsConstants.POLLOUT;

        StructPollfd[] polls = new StructPollfd[2 + dnsIn.size()];
        polls[0] = deviceFd;
        polls[1] = blockFd;
        {
            int i = -1;
            for (WaitingOnSocketPacket wosp : dnsIn) {
                i++;
                StructPollfd pollFd = polls[2 + i] = new StructPollfd();
                pollFd.fd = ParcelFileDescriptor.fromDatagramSocket(wosp.socket).getFileDescriptor();
                pollFd.events = (short) OsConstants.POLLIN;
            }
        }

        Log.d(TAG, "doOne: Polling " + polls.length + " file descriptors");
        int result = FileHelper.poll(polls, vpnWatchDog.getPollTimeout());
        if (result == 0) {
            vpnWatchDog.handleTimeout();
            return true;
        }
        if (blockFd.revents != 0) {
            Log.i(TAG, "Told to stop VPN");
            return false;
        }
        // Need to do this before reading from the device, otherwise a new insertion there could
        // invalidate one of the sockets we want to read from either due to size or time out
        // constraints
        {
            int i = -1;
            Iterator<WaitingOnSocketPacket> iter = dnsIn.iterator();
            while (iter.hasNext()) {
                i++;
                WaitingOnSocketPacket wosp = iter.next();
                if ((polls[i + 2].revents & OsConstants.POLLIN) != 0) {
                    Log.d(TAG, "Read from DNS socket" + wosp.socket);
                    iter.remove();
                    handleRawDnsResponse(wosp.packet, wosp.socket);
                    wosp.socket.close();
                }
            }
        }
        if ((deviceFd.revents & OsConstants.POLLOUT) != 0) {
            Log.d(TAG, "Write to device");
            writeToDevice(outFd);
        }
        if ((deviceFd.revents & OsConstants.POLLIN) != 0) {
            Log.d(TAG, "Read from device");
            readPacketFromDevice(inputStream, packet);
        }

        // pcap4j has some sort of properties cache in the packet factory. This cache leaks, so
        // we need to clean it up.
        if (++pcap4jFactoryClearCacheCounter % PCAP4J_FACTORY_CLEAR_NASTY_CACHE_EVERY == 0) {
            try {
                PacketFactoryPropertiesLoader l = PacketFactoryPropertiesLoader.getInstance();
                Field field = l.getClass().getDeclaredField("loader");
                field.setAccessible(true);
                PropertiesLoader loader = (PropertiesLoader) field.get(l);
                Log.d(TAG, "Cleaning cache");
                loader.clearCache();
            } catch (NoSuchFieldException e) {
                Log.e(TAG, "Cannot find declared loader field", e);
            } catch (IllegalAccessException e) {
                Log.e(TAG, "Cannot get declared loader field", e);
            }
        }

        return true;
    }

    private void writeToDevice(FileOutputStream outFd) throws VpnNetworkException {
        try {
            outFd.write(deviceWrites.poll());
        } catch (IOException e) {
            // TODO: Make this more specific, only for: "File descriptor closed"
            throw new VpnNetworkException("Outgoing VPN output stream closed");
        }
    }

    private void readPacketFromDevice(FileInputStream inputStream, byte[] packet) throws VpnNetworkException, SocketException {
        // Read the outgoing packet from the input stream.
        int length;

        try {
            length = inputStream.read(packet);
        } catch (IOException e) {
            throw new VpnNetworkException("Cannot read from device", e);
        }


        if (length == 0) {
            // TODO: Possibly change to exception
            Log.w(TAG, "Got empty packet!");
            return;
        }

        final byte[] readPacket = Arrays.copyOfRange(packet, 0, length);

        vpnWatchDog.handlePacket(readPacket);
        dnsPacketProxy.handleDnsRequest(readPacket);
    }

    public void forwardPacket(DatagramPacket outPacket, IpPacket parsedPacket) throws VpnNetworkException {
        DatagramSocket dnsSocket = null;
        try {
            // Packets to be sent to the real DNS server will need to be protected from the VPN
            dnsSocket = new DatagramSocket();

            vpnService.protect(dnsSocket);

            dnsSocket.send(outPacket);

            if (parsedPacket != null)
                dnsIn.add(new WaitingOnSocketPacket(dnsSocket, parsedPacket));
            else
                FileHelper.closeOrWarn(dnsSocket, TAG, "handleDnsRequest: Cannot close socket in error");
        } catch (IOException e) {
            FileHelper.closeOrWarn(dnsSocket, TAG, "handleDnsRequest: Cannot close socket in error");
            if (e.getCause() instanceof ErrnoException) {
                ErrnoException errnoExc = (ErrnoException) e.getCause();
                if ((errnoExc.errno == OsConstants.ENETUNREACH) || (errnoExc.errno == OsConstants.EPERM)) {
                    throw new VpnNetworkException("Cannot send message:", e);
                }
            }
            Log.w(TAG, "handleDnsRequest: Could not send packet to upstream", e);
            return;
        }
    }

    private void handleRawDnsResponse(IpPacket parsedPacket, DatagramSocket dnsSocket) throws IOException {
        byte[] datagramData = new byte[1024];
        DatagramPacket replyPacket = new DatagramPacket(datagramData, datagramData.length);
        dnsSocket.receive(replyPacket);
        dnsPacketProxy.handleDnsResponse(parsedPacket, datagramData);
    }

    public void queueDeviceWrite(IpPacket ipOutPacket) {
        deviceWrites.add(ipOutPacket.getRawData());
    }

    void newDNSServer(VpnService.Builder builder, String format, byte[] ipv6Template, InetAddress addr) throws UnknownHostException {
        // Optimally we'd allow either one, but the forwarder checks if upstream size is empty, so
        // we really need to acquire both an ipv6 and an ipv4 subnet.
        if (addr instanceof Inet6Address && ipv6Template == null) {
            Log.i(TAG, "newDNSServer: Ignoring DNS server " + addr);
        } else if (addr instanceof Inet4Address && format == null) {
            Log.i(TAG, "newDNSServer: Ignoring DNS server " + addr);
        } else if (addr instanceof Inet4Address) {
            upstreamDnsServers.add(addr);
            String alias = String.format(format, upstreamDnsServers.size() + 1);
            Log.i(TAG, "configure: Adding DNS Server " + addr + " as " + alias);
            builder.addDnsServer(alias);
            builder.addRoute(alias, 32);
            vpnWatchDog.setTarget(InetAddress.getByName(alias));
        } else if (addr instanceof Inet6Address) {
            upstreamDnsServers.add(addr);
            ipv6Template[ipv6Template.length - 1] = (byte) (upstreamDnsServers.size() + 1);
            InetAddress i6addr = Inet6Address.getByAddress(ipv6Template);
            Log.i(TAG, "configure: Adding DNS Server " + addr + " as " + i6addr);
            builder.addDnsServer(i6addr);
            vpnWatchDog.setTarget(i6addr);
        }
    }

    void configurePackages(VpnService.Builder builder, Configuration config) {
        Set<String> allowOnVpn = new HashSet<>();
        Set<String> doNotAllowOnVpn = new HashSet<>();

        config.whitelist.resolve(vpnService.getPackageManager(), allowOnVpn, doNotAllowOnVpn);

        if (config.whitelist.defaultMode == Configuration.Whitelist.DEFAULT_MODE_NOT_ON_VPN) {
            for (String app : allowOnVpn) {
                try {
                    Log.d(TAG, "configure: Allowing " + app + " to use the DNS VPN");
                    builder.addAllowedApplication(app);
                } catch (Exception e) {
                    Log.w(TAG, "configure: Cannot disallow", e);
                }
            }
        } else {
            for (String app : doNotAllowOnVpn) {
                try {
                    Log.d(TAG, "configure: Disallowing " + app + " from using the DNS VPN");
                    builder.addDisallowedApplication(app);
                } catch (Exception e) {
                    Log.w(TAG, "configure: Cannot disallow", e);
                }
            }
        }
    }

    private ParcelFileDescriptor configure() throws VpnNetworkException {
        Log.i(TAG, "Configuring" + this);

        Configuration config = FileHelper.loadCurrentSettings(vpnService);

        // Get the current DNS servers before starting the VPN
        Set<InetAddress> dnsServers = getDnsServers(vpnService);
        Log.i(TAG, "Got DNS servers = " + dnsServers);

        // Configure a builder while parsing the parameters.
        VpnService.Builder builder = vpnService.new Builder();

        String format = null;

        // Determine a prefix we can use. These are all reserved prefixes for example
        // use, so it's possible they might be blocked.
        for (String prefix : new String[]{"192.0.2", "198.51.100", "203.0.113"}) {
            try {
                builder.addAddress(prefix + ".1", 24);
            } catch (IllegalArgumentException e) {
                continue;
            }

            format = prefix + ".%d";
            break;
        }

        // For fancy reasons, this is the 2001:db8::/120 subnet of the /32 subnet reserved for
        // documentation purposes. We should do this differently. Anyone have a free /120 subnet
        // for us to use?
        byte[] ipv6Template = new byte[]{32, 1, 13, (byte) (184 & 0xFF), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};

        if (hasIpV6Servers(config, dnsServers)) {
            try {
                InetAddress addr = Inet6Address.getByAddress(ipv6Template);
                Log.d(TAG, "configure: Adding IPv6 address" + addr);
                builder.addAddress(addr, 120);
            } catch (Exception e) {
                e.printStackTrace();

                ipv6Template = null;
            }
        } else {
            ipv6Template = null;
        }

        if (format == null) {
            Log.w(TAG, "configure: Could not find a prefix to use, directly using DNS servers");
            builder.addAddress("192.168.50.1", 24);
        }

        // Add configured DNS servers
        upstreamDnsServers.clear();
        if (config.dnsServers.enabled) {
            for (Configuration.Item item : config.dnsServers.items) {
                if (item.state == item.STATE_ALLOW) {
                    try {
                        newDNSServer(builder, format, ipv6Template, InetAddress.getByName(item.location));
                    } catch (Exception e) {
                        Log.e(TAG, "configure: Cannot add custom DNS server", e);
                    }
                }
            }
        }
        // Add all knows DNS servers
        for (InetAddress addr : dnsServers) {
            try {
                newDNSServer(builder, format, ipv6Template, addr);
            } catch (Exception e) {
                Log.e(TAG, "configure: Cannot add server:", e);
            }
        }

        builder.setBlocking(true);

        configurePackages(builder, config);

        // Create a new interface using the builder and save the parameters.
        ParcelFileDescriptor pfd = builder
                .setSession("DNS66")
                .setConfigureIntent(
                        PendingIntent.getActivity(vpnService, 1, new Intent(vpnService, MainActivity.class),
                                PendingIntent.FLAG_CANCEL_CURRENT)).establish();
        Log.i(TAG, "Configured");
        return pfd;
    }

    boolean hasIpV6Servers(Configuration config, Set<InetAddress> dnsServers) {
        if (!config.ipV6Support)
            return false;

        if (config.dnsServers.enabled) {
            for (Configuration.Item item : config.dnsServers.items) {
                if (item.state == Configuration.Item.STATE_ALLOW && item.location.contains(":"))
                    return true;
            }
        }
        for (InetAddress inetAddress : dnsServers) {
            if (inetAddress instanceof Inet6Address)
                return true;
        }

        return false;
    }

    public interface Notify {
        void run(int value);
    }

    static class VpnNetworkException extends Exception {
        VpnNetworkException(String s) {
            super(s);
        }

        VpnNetworkException(String s, Throwable t) {
            super(s, t);
        }

    }

    /**
     * Helper class holding a socket, the packet we are waiting the answer for, and a time
     */
    private static class WaitingOnSocketPacket {
        final DatagramSocket socket;
        final IpPacket packet;
        private final long time;

        WaitingOnSocketPacket(DatagramSocket socket, IpPacket packet) {
            this.socket = socket;
            this.packet = packet;
            this.time = System.currentTimeMillis();
        }

        long ageSeconds() {
            return (System.currentTimeMillis() - time) / 1000;
        }
    }

    /**
     * Queue of WaitingOnSocketPacket, bound on time and space.
     */
    private static class WospList implements Iterable<WaitingOnSocketPacket> {
        private final LinkedList<WaitingOnSocketPacket> list = new LinkedList<WaitingOnSocketPacket>();

        void add(WaitingOnSocketPacket wosp) {
            if (list.size() > DNS_MAXIMUM_WAITING) {
                Log.d(TAG, "Dropping socket due to space constraints: " + list.element().socket);
                list.element().socket.close();
                list.remove();
            }
            while (!list.isEmpty() && list.element().ageSeconds() > DNS_TIMEOUT_SEC) {
                Log.d(TAG, "Timeout on socket " + list.element().socket);
                list.element().socket.close();
                list.remove();
            }
            list.add(wosp);
        }

        public Iterator<WaitingOnSocketPacket> iterator() {
            return list.iterator();
        }

        int size() {
            return list.size();
        }

    }

}
