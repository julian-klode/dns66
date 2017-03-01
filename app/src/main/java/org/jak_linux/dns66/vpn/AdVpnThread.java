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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
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


class AdVpnThread extends Thread implements DnsPacketProxy.EventLoop {
    // Commands we can send on the interrupt fd
    public static final byte COMMAND_PAUSE = 0;
    public static final byte COMMAND_RESUME = 1;
    public static final byte COMMAND_DESTROY = 2;
    private static final String TAG = "AdVpnThread";
    private static final int MIN_RETRY_TIME = 5;
    private static final int MAX_RETRY_TIME = 2 * 60;
    /* If we had a successful connection for that long, reset retry timeout */
    private static final long RETRY_RESET_SEC = 60;
    /* Maximum number of responses we want to wait for */
    private static final int DNS_MAXIMUM_WAITING = 1024;
    private static final long DNS_TIMEOUT_SEC = 10;
    private final VpnService vpnService;
    private final Notify notify;
    /* Data to be written to the device */
    private final Queue<byte[]> deviceWrites = new LinkedList<>();
    // HashMap that keeps an upper limit of packets
    private final WospList dnsIn = new WospList();
    // The object where we actually handle packets.
    private final DnsPacketProxy dnsPacketProxy = new DnsPacketProxy(this);
    /**
     * After how many iterations we should clear pcap4js packetfactory property cache
     */
    private final int PCAP4J_FACTORY_CLEAR_NASTY_CACHE_EVERY = 1024;
    /* Upstream DNS servers, indexed by our IP */
    private final ArrayList<InetAddress> upstreamDnsServers = new ArrayList<>();
    private Thread thread = null;
    private FileDescriptor mCommandOutFd = null;
    private FileDescriptor mCommandInFd = null;
    /**
     * Number of iterations since we last cleared the pcap4j cache
     */
    private int pcap4jFactoryClearCacheCounter = 0;
    /* Device stream */
    private FileInputStream devInStream;
    private FileOutputStream devOutStream;
    /* Buffer for a single packet */
    private byte[] packetBuffer = new byte[32767];
    private boolean shallDestroy = false;
    private ParcelFileDescriptor pfd;

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

    @Override
    public void start() {
        try {
            FileDescriptor[] pipe = Os.pipe();
            mCommandInFd = pipe[0];
            mCommandOutFd = pipe[1];
        } catch (ErrnoException e) {
            Log.e(TAG, "run: Could not setup command pipe", e);
        }
        super.start();
    }


    @Override
    public void run() {
        Log.i(TAG, "Starting");

        // Load the block list
        try {
            dnsPacketProxy.initialize(vpnService, upstreamDnsServers);
        } catch (InterruptedException e) {
            return;
        }

        if (notify != null) {
            notify.run(AdVpnService.VPN_STATUS_STARTING);
        }

        int retryTimeout = MIN_RETRY_TIME;
        // Try connecting the vpn continuously
        while (!shallDestroy) {
            try {
                Log.d(TAG, "run: Doing one");
                if (!doOne()) {
                    Log.i(TAG, "Told to stop");
                    break;
                }
                Log.d(TAG, "run: Did one");
            } catch (InterruptedException e) {
                break;
            } catch (ErrnoException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (VpnNetworkException e) {
                e.printStackTrace();
            }
        }

        if (notify != null)
            notify.run(AdVpnService.VPN_STATUS_STOPPED);
        Log.i(TAG, "Exiting");
    }


    public void sendCommand(byte command) {
        try {
            Log.d(TAG, "sendCommand: Sending command" + command);
            Os.write(mCommandOutFd, new byte[]{command}, 0, 1);
            Log.d(TAG, "sendCommand: Sent command" + command);
        } catch (Exception e) {
            Log.e(TAG, "sendCommand: Could not send command " + command, e);
        }
    }


    private void onCommand() throws ErrnoException, IOException, VpnNetworkException, InterruptedException {
        byte[] command = new byte[1];
        Os.read(mCommandInFd, command, 0, 1);
        Log.d(TAG, "onCommand: Received command " + command[0]);

        if (command[0] == COMMAND_RESUME) {
            onPause();
            onResume();
        } else if (command[0] == COMMAND_DESTROY) {
            onPause();
            onDestroy();
        } else if (command[0] == COMMAND_PAUSE) {
            onPause();
        }
    }

    /**
     * Resume (or start) a VPN connection
     *
     * @throws VpnNetworkException
     */
    private void onResume() throws VpnNetworkException {
        Log.d(TAG, "onResume: Resuming network");
        // Load the block list
        try {
            Configuration config = FileHelper.loadCurrentSettings(vpnService);

            dnsPacketProxy.ruleDatabase.initialize(vpnService);
        } catch (InterruptedException e) {
            return;
        }

        pfd = configure();

        if (pfd != null) {
            Log.d(TAG, "onResume: Configured new tun device");
            // Read and write views of the tun device
            devInStream = new FileInputStream(pfd.getFileDescriptor());
            devOutStream = new FileOutputStream(pfd.getFileDescriptor());

            if (notify != null)
                notify.run(AdVpnService.VPN_STATUS_RUNNING);
        } else {
            if (notify != null)
                notify.run(AdVpnService.VPN_STATUS_RECONNECTING_NETWORK_ERROR);
        }
    }

    /**
     * Pause the VPN (close the VPN device, but keep thread running)
     *
     * @throws IOException
     */
    private void onPause() throws IOException {
        Log.d(TAG, "onPause: Pausing VPN");
        if (devInStream != null)
            devInStream.close();
        if (devOutStream != null)
            devOutStream.close();
        if (pfd != null)
            pfd.close();
        devInStream = null;
        devOutStream = null;
        pfd = null;
        notify.run(AdVpnService.VPN_STATUS_RECONNECTING);
    }

    /**
     * Destroy the thread.
     * <p>
     * This is used in the service's onDestroy() method.
     *
     * @throws InterruptedException
     */
    private void onDestroy() throws InterruptedException {
        shallDestroy = true;
    }

    private boolean doOne() throws IOException, ErrnoException, InterruptedException, VpnNetworkException {
        StructPollfd blockFd = new StructPollfd();
        blockFd.fd = mCommandInFd;
        blockFd.events = (short) (OsConstants.POLLHUP | OsConstants.POLLERR | OsConstants.POLLIN);


        StructPollfd deviceFd = null;
        if (devInStream != null) {
            deviceFd = new StructPollfd();
            deviceFd.fd = devInStream.getFD();
            deviceFd.events = (short) OsConstants.POLLIN;
            if (!deviceWrites.isEmpty())
                deviceFd.events |= (short) OsConstants.POLLOUT;
        }

        StructPollfd[] polls = new StructPollfd[dnsIn.size() + (deviceFd != null ? 2 : 1)];
        if (deviceFd != null)
            polls[polls.length - 2] = deviceFd;
        polls[polls.length - 1] = blockFd;
        {
            int i = -1;
            for (WaitingOnSocketPacket wosp : dnsIn) {
                i++;
                StructPollfd pollFd = polls[i] = new StructPollfd();
                pollFd.fd = ParcelFileDescriptor.fromDatagramSocket(wosp.socket).getFileDescriptor();
                pollFd.events = (short) OsConstants.POLLIN;
            }
        }

        Log.d(TAG, "doOne: Polling " + polls.length + " file descriptors");
        int result = FileHelper.poll(polls, -1);
        if (blockFd.revents != 0) {
            if ((blockFd.revents & OsConstants.POLLIN) != 0)
                onCommand();
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
                if ((polls[i].revents & OsConstants.POLLIN) != 0) {
                    Log.d(TAG, "Read from DNS socket" + wosp.socket);
                    iter.remove();
                    handleRawDnsResponse(wosp.packet, wosp.socket);
                    wosp.socket.close();
                }
            }
        }
        if (deviceFd != null && (deviceFd.revents & OsConstants.POLLOUT) != 0) {
            Log.d(TAG, "Write to device");
            writeToDevice(devOutStream);
        }
        if (deviceFd != null && (deviceFd.revents & OsConstants.POLLIN) != 0) {
            Log.d(TAG, "Read from device");
            readPacketFromDevice(devInStream);
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

    private void writeToDevice(FileOutputStream devOutStream) throws VpnNetworkException {
        try {
            devOutStream.write(deviceWrites.poll());
        } catch (IOException e) {
            // TODO: Make this more specific, only for: "File descriptor closed"
            throw new VpnNetworkException("Outgoing VPN output stream closed");
        }
    }

    private void readPacketFromDevice(FileInputStream devInStream) throws VpnNetworkException, SocketException {
        // Read the outgoing packet from the input stream.
        int length;

        try {
            length = devInStream.read(packetBuffer);
        } catch (IOException e) {
            throw new VpnNetworkException("Cannot read from device", e);
        }


        if (length == 0) {
            // TODO: Possibly change to exception
            Log.w(TAG, "Got empty packet!");
            return;
        }

        final byte[] readPacket = Arrays.copyOfRange(packetBuffer, 0, length);

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

    private void newDNSServer(VpnService.Builder builder, String format, byte[] ipv6Template, InetAddress addr) throws UnknownHostException {
        // Optimally we'd allow either one, but the forwarder checks if upstream size is empty, so
        // we really need to acquire both an ipv6 and an ipv4 subnet.
        if (ipv6Template == null || format == null) {
            Log.i(TAG, "configure: Adding DNS Server " + addr);
            builder.addDnsServer(addr);
            builder.addRoute(addr, addr.getAddress().length * 8);
        } else if (addr instanceof Inet4Address) {
            upstreamDnsServers.add(addr);
            String alias = String.format(format, upstreamDnsServers.size() + 1);
            Log.i(TAG, "configure: Adding DNS Server " + addr + " as " + alias);
            builder.addDnsServer(alias);
            builder.addRoute(alias, 32);
        } else if (addr instanceof Inet6Address) {
            upstreamDnsServers.add(addr);
            ipv6Template[ipv6Template.length - 1] = (byte) (upstreamDnsServers.size() + 1);
            InetAddress i6addr = Inet6Address.getByAddress(ipv6Template);
            Log.i(TAG, "configure: Adding DNS Server " + addr + " as " + i6addr);
            builder.addDnsServer(i6addr);
        }
    }

    private ParcelFileDescriptor configure() throws VpnNetworkException {
        Log.i(TAG, "Configuring" + this);

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
        try {
            builder.addAddress(Inet6Address.getByAddress(ipv6Template), 120);
        } catch (Exception e) {
            e.printStackTrace();
            ipv6Template = null;
        }

        if (format == null) {
            Log.w(TAG, "configure: Could not find a prefix to use, directly using DNS servers");
            builder.addAddress("192.168.50.1", 24);
        }

        upstreamDnsServers.clear();

        // Add configured DNS servers
        Configuration config = FileHelper.loadCurrentSettings(vpnService);
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

        // Work around DownloadManager bug on Nougat - It cannot resolve DNS
        // names while a VPN service is active.
        for (String app : config.whitelist.items) {
            try {
                Log.d(TAG, "configure: Disallowing " + app + " from using the DNS VPN");
                builder.addDisallowedApplication(app);
            } catch (Exception e) {
                Log.w(TAG, "configure: Cannot disallow", e);
            }
        }

        // Create a new interface using the builder and save the parameters.
        ParcelFileDescriptor pfd = builder
                .setSession("DNS66")
                .setConfigureIntent(
                        PendingIntent.getActivity(vpnService, 1, new Intent(vpnService, MainActivity.class),
                                PendingIntent.FLAG_CANCEL_CURRENT)).establish();
        Log.i(TAG, "Configured");
        return pfd;
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
