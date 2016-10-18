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


import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import org.jak_linux.dns66.Configuration;
import org.jak_linux.dns66.FileHelper;
import org.jak_linux.dns66.MainActivity;
import org.pcap4j.packet.IpV4Packet;
import org.pcap4j.packet.UdpPacket;
import org.pcap4j.packet.UnknownPacket;
import org.xbill.DNS.ARecord;
import org.xbill.DNS.Flags;
import org.xbill.DNS.Message;
import org.xbill.DNS.Rcode;
import org.xbill.DNS.Section;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;


public class AdVpnThread implements Runnable {
    private static final String TAG = "AdVpnThread";
    private static final int MIN_RETRY_TIME = 5;
    private static final int MAX_RETRY_TIME = 2 * 60;

    private VpnService vpnService;
    private Notify notify;

    private InetAddress dnsServer = null;
    private ParcelFileDescriptor vpnFileDescriptor = null;
    private Thread thread = null;
    private InterruptibleFileInputStream interruptible = null;
    private Set<String> blockedHosts = new HashSet<>();

    public AdVpnThread(VpnService vpnService, Notify notify) {
        this.vpnService = vpnService;
        this.notify = notify;
    }

    public static InetAddress getDnsServers(Context context) {
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
                return address;
        }
        throw new VpnNetworkException("No DNS Server");
    }

    public void startThread() {
        Log.i(TAG, "Starting Vpn Thread");
        thread = new Thread(this, "AdBusterVpnThread");
        thread.start();
        Log.i(TAG, "Vpn Thread started");
    }

    public void stopThread() {
        Log.i(TAG, "Stopping Vpn Thread");
        if (thread != null) thread.interrupt();
        if (interruptible != null) try {
            interruptible.interrupt();
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            if (thread != null) thread.join(2000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        if (thread != null && thread.isAlive()) {
            Log.w(TAG, "Couldn't kill Vpn Thread");
        }
        thread = null;
        Log.i(TAG, "Vpn Thread stopped");
    }

    @Override
    public synchronized void run() {
        try {
            Log.i(TAG, "Starting");

            // Load the block list
            loadBlockedHosts();

            if (notify != null) {
                notify.run(AdVpnService.VPN_STATUS_STARTING);
                notify.run(AdVpnService.VPN_STATUS_STARTING);
            }

            int retryTimeout = MIN_RETRY_TIME;
            // Try connecting the vpn continuously
            while (true) {
                try {
                    // If the function returns, that means it was interrupted
                    runVpn();

                    Log.i(TAG, "Told to stop");
                    break;
                } catch (InterruptedException e) {
                    throw e;
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

                // ...wait and try again
                Log.i(TAG, "Retrying to connect in " + retryTimeout + "seconds...");
                try {
                    Thread.sleep((long) retryTimeout * 1000);
                } catch (InterruptedException e) {
                }

                if (retryTimeout < MAX_RETRY_TIME)
                    retryTimeout *= 2;
            }

            Log.i(TAG, "Stopped");
        } catch (InterruptedException e) {
            Log.i(TAG, "Vpn Thread interrupted");
        } catch (Exception e) {
            //ExceptionHandler.saveException(e, Thread.currentThread(), null);
            Log.e(TAG, "Exception in run() ", e);
        } finally {
            if (notify != null) notify.run(AdVpnService.VPN_STATUS_STOPPING);
            Log.i(TAG, "Exiting");
        }
    }

    public void runVpn() throws Exception {
        // Authenticate and configure the virtual network interface.
        ParcelFileDescriptor pfd = configure();
        vpnFileDescriptor = pfd;

        // Packets to be sent are queued in this input stream.
        InterruptibleFileInputStream inputStream = new InterruptibleFileInputStream(pfd.getFileDescriptor());
        interruptible = inputStream;

        // Allocate the buffer for a single packet.
        byte[] packet = new byte[32767];

        // Like this `Executors.newCachedThreadPool()`, except with an upper limit
        ThreadPoolExecutor executor = new ThreadPoolExecutor(0, 32, 60L, TimeUnit.SECONDS, new SynchronousQueue<Runnable>());

        try {
            // Now we are connected. Set the flag and show the message.
            if (notify != null) notify.run(AdVpnService.VPN_STATUS_RUNNING);

            // We keep forwarding packets till something goes wrong.
            while (!readPacketFromDevice(pfd, inputStream, packet, executor))
                ;
        } finally {
            executor.shutdownNow();
            pfd.close();
            vpnFileDescriptor = null;
        }
    }

    private boolean readPacketFromDevice(ParcelFileDescriptor pfd, InterruptibleFileInputStream inputStream, byte[] packet, ThreadPoolExecutor executor) throws IOException {
        // Read the outgoing packet from the input stream.
        int length;
        try {
            length = inputStream.read(packet);
        } catch (InterruptibleFileInputStream.InterruptedStreamException e) {
            Log.i(TAG, "Told to stop VPN");
            return true;
        }

        if (length == 0) {
            // TODO: Possibly change to exception
            Log.w(TAG, "Got empty packet!");
        }

        final byte[] readPacket = Arrays.copyOfRange(packet, 0, length);

        // Packets received need to be written to this output stream.
        final FileOutputStream outFd = new FileOutputStream(pfd.getFileDescriptor());

        // Packets to be sent to the real DNS server will need to be protected from the VPN
        final DatagramSocket dnsSocket = new DatagramSocket();
        vpnService.protect(dnsSocket);

        Log.i(TAG, "Starting new thread to handle dns request" +
                " (active = " + executor.getActiveCount() + " backlog = " + executor.getQueue().size());
        // Start a new thread to handle the DNS request
        try {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    handleDnsRequest(readPacket, dnsSocket, outFd);
                }
            });
        } catch (RejectedExecutionException e) {
            throw new VpnNetworkException("High backlog in dns thread pool executor, network probably stalled");
        }
        return false;
    }

    private void handleDnsRequest(byte[] packet, DatagramSocket dnsSocket, FileOutputStream outFd) {
        try {
            IpV4Packet parsedPacket = IpV4Packet.newPacket(packet, 0, packet.length);

            if (!(parsedPacket.getPayload() instanceof UdpPacket)) {
                Log.i(TAG, "Ignoring unknown packet type ${parsedPacket.payload}");
                return;
            }

            byte[] dnsRawData = ((UdpPacket) parsedPacket.getPayload()).getPayload().getRawData();
            Message dnsMsg = new Message(dnsRawData);
            if (dnsMsg.getQuestion() == null) {
                Log.i(TAG, "Ignoring DNS packet with no query " + dnsMsg);
                return;
            }
            String dnsQueryName = dnsMsg.getQuestion().getName().toString(true);

            byte[] response;

            if (!blockedHosts.contains(dnsQueryName)) {
                Log.i(TAG, "DNS Name " + dnsQueryName + " Allowed!");
                DatagramPacket outPacket = new DatagramPacket(dnsRawData, 0, dnsRawData.length, dnsServer, 53);

                dnsSocket.send(outPacket);

                handleRawDnsResponse(outFd, parsedPacket, dnsSocket);
            } else {
                Log.i(TAG, "DNS Name " + dnsQueryName + " Blocked!");
                dnsMsg.getHeader().setFlag(Flags.QR);
                dnsMsg.getHeader().setRcode(Rcode.NXDOMAIN);
                handleDnsResponse(outFd, parsedPacket, dnsMsg.toWire());
            }
        } catch (VpnNetworkException e) {
            Log.w(TAG, "Ignoring exception, stopping thread", e);
        } catch (Exception e) {
            Log.e(TAG, "Got exception", e);
            //ExceptionHandler.saveException(e, Thread.currentThread(), null);
        } finally {
            dnsSocket.close();
            try {
                outFd.close();
            } catch (IOException e) {
                Log.w(TAG, "handleDnsRequest: Ignoring failure to close outFd: " + e.getMessage());
            }
        }

    }

    private void handleRawDnsResponse(FileOutputStream outFd, IpV4Packet parsedPacket, DatagramSocket dnsSocket) throws IOException {
        byte[] response;
        byte[] datagramData = new byte[1024];
        DatagramPacket replyPacket = new DatagramPacket(datagramData, datagramData.length);
        dnsSocket.receive(replyPacket);
        response = datagramData;
        handleDnsResponse(outFd, parsedPacket, response);
    }

    private void handleDnsResponse(FileOutputStream outFd, IpV4Packet parsedPacket, byte[] response) {
        UdpPacket udpOutPacket = (UdpPacket) parsedPacket.getPayload();
        IpV4Packet ipOutPacket = new IpV4Packet.Builder(parsedPacket)
                .srcAddr(parsedPacket.getHeader().getDstAddr())
                .dstAddr(parsedPacket.getHeader().getSrcAddr())
                .correctChecksumAtBuild(true)
                .correctLengthAtBuild(true)
                .payloadBuilder(
                        new UdpPacket.Builder(udpOutPacket)
                                .srcPort(udpOutPacket.getHeader().getDstPort())
                                .dstPort(udpOutPacket.getHeader().getSrcPort())
                                .srcAddr(parsedPacket.getHeader().getDstAddr())
                                .dstAddr(parsedPacket.getHeader().getSrcAddr())
                                .correctChecksumAtBuild(true)
                                .correctLengthAtBuild(true)
                                .payloadBuilder(
                                        new UnknownPacket.Builder()
                                                .rawData(response)
                                )
                ).build();
        try {
            outFd.write(ipOutPacket.getRawData());
        } catch (IOException e) {
            // TODO: Make this more specific, only for: "File descriptor closed"
            throw new VpnNetworkException("Outgoing VPN output stream closed");
        }
    }

    private void loadBlockedHosts() {
        Configuration config = FileHelper.loadCurrentSettings(vpnService);

        blockedHosts = new HashSet<>();

        Log.i(TAG, "Loading block list");

        for (Configuration.Item item : config.hosts.items) {
            File file = FileHelper.getItemFile(vpnService, item);

            FileReader reader = null;
            if (file == null || item.state == 2)
                continue;
            try {
                reader = new FileReader(file);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                continue;
            }

            int count = 0;
            try {
                Log.d(TAG, "loadBlockedHosts: Reading: " + file.getAbsolutePath());
                try (BufferedReader br = new BufferedReader(reader)) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        String s = line.trim();

                        if (s.length() != 0) {
                            String[] ss = s.split("#");
                            s = ss.length > 0 ? ss[0].trim() : "";
                        }
                        if (s.length() != 0) {
                            String[] split = s.split("[ \t]+");
                            if (split.length == 2 && (split[0].equals("127.0.0.1") || split[0].equals("0.0.0.0"))) {
                                count += 1;
                                if (item.state == 0)
                                    blockedHosts.add(split[1].toLowerCase());
                                else if (item.state == 1)
                                    blockedHosts.remove(split[1].toLowerCase());
                            }
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
        }
    }

    private ParcelFileDescriptor configure() throws Exception {
        Log.i(TAG, "Configuring");

        // Get the current DNS servers before starting the VPN
        dnsServer = getDnsServers(vpnService);
        Log.i(TAG, "Got DNS server = " + dnsServer);

        // Configure a builder while parsing the parameters.
        // TODO: Make this dynamic
        VpnService.Builder builder = vpnService.new Builder();
        builder.addAddress("192.168.50.1", 24);
        builder.addDnsServer("192.168.50.5");
        builder.addRoute("192.168.50.0", 24);
        builder.setBlocking(true);

        // Create a new interface using the builder and save the parameters.
        ParcelFileDescriptor pfd = builder
                .setSession("Ad Buster")
                .setConfigureIntent(
                        PendingIntent.getActivity((Context) vpnService, 1, new Intent(vpnService, MainActivity.class),
                                PendingIntent.FLAG_CANCEL_CURRENT)).establish();
        Log.i(TAG, "Configured");
        return pfd;
    }

    public interface Notify {
        void run(int value);
    }

    public static class VpnNetworkException extends RuntimeException {
        public VpnNetworkException(String s) {
            super(s);
        }
    }
}