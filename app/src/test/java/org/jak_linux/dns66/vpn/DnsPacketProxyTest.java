package org.jak_linux.dns66.vpn;

import android.util.Log;

import org.jak_linux.dns66.Configuration;
import org.jak_linux.dns66.db.RuleDatabase;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.pcap4j.packet.ArpPacket;
import org.pcap4j.packet.IpPacket;
import org.pcap4j.packet.IpV4Packet;
import org.pcap4j.packet.IpV4Rfc791Tos;
import org.pcap4j.packet.TcpPacket;
import org.pcap4j.packet.UdpPacket;
import org.pcap4j.packet.UnknownPacket;
import org.pcap4j.packet.namednumber.IpNumber;
import org.pcap4j.packet.namednumber.IpVersion;
import org.pcap4j.packet.namednumber.TcpPort;
import org.pcap4j.packet.namednumber.UdpPort;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.net.DatagramPacket;
import java.net.Inet4Address;
import java.net.InetAddress;

import static org.junit.Assert.*;

/**
 * Various tests for the core DNS packet proxying code.
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest(Log.class)
public class DnsPacketProxyTest {
    private MockEventLoop mockEventLoop;
    private DnsPacketProxy dnsPacketProxy;
    private RuleDatabase ruleDatabase;

    @Before
    public void setUp() throws NoSuchFieldException, IllegalAccessException {
        mockEventLoop = new MockEventLoop();
        dnsPacketProxy = new DnsPacketProxy(mockEventLoop);
        ruleDatabase = (RuleDatabase) dnsPacketProxy.getClass().getDeclaredField("ruleDatabase").get(dnsPacketProxy);


        Configuration.Item item = new Configuration.Item();
        item.location = "example.com";
        item.state = Configuration.Item.STATE_DENY;

        ruleDatabase.addHost(item, item.location);

        PowerMockito.mockStatic(Log.class);
    }

    public void tinySetUp() {
        mockEventLoop.lastOutgoing = null;
        mockEventLoop.lastResponse = null;
        dnsPacketProxy.upstreamDnsServers.clear();
    }

    @Test
    public void testHandleDnsRequestNotIpPacket() throws Exception {
        dnsPacketProxy.handleDnsRequest(new byte[] {'f', 'o', 'o'});
        assertNull(mockEventLoop.lastOutgoing);
        assertNull(mockEventLoop.lastResponse);
        // TODO: 19/03/17 Check for correct point of error
    }

    @Test
    public void testHandleDnsRequestNotUdpPacket() throws Exception {
        TcpPacket.Builder payLoadBuilder = new TcpPacket.Builder()
                .srcPort(TcpPort.HTTP)
                .dstPort(TcpPort.HTTP)
                .srcAddr(InetAddress.getByAddress(new byte[]{8, 8, 4, 4}))
                .dstAddr(InetAddress.getByAddress(new byte[]{8, 8, 8, 8}))
                .correctChecksumAtBuild(true)
                .correctLengthAtBuild(true)
                .payloadBuilder(
                        new UnknownPacket.Builder()
                                .rawData(new byte[0])
                );

        IpPacket ipOutPacket = new IpV4Packet.Builder()
                .version(IpVersion.IPV4)
                .tos(IpV4Rfc791Tos.newInstance((byte) 0))
                .protocol(IpNumber.UDP)
                .srcAddr((Inet4Address) Inet4Address.getByAddress(new byte[]{8, 8, 4, 4}))
                .dstAddr((Inet4Address) Inet4Address.getByAddress(new byte[]{8, 8, 8, 8}))
                .correctChecksumAtBuild(true)
                .correctLengthAtBuild(true)
                .payloadBuilder(payLoadBuilder)
                .build();

        dnsPacketProxy.handleDnsRequest(ipOutPacket.getRawData());
        assertNull(mockEventLoop.lastOutgoing);
        assertNull(mockEventLoop.lastResponse);
        // TODO: 19/03/17 Check for correct point of error
    }
    @Test
    public void testHandleDnsRequestNotDnsPacket() throws Exception {
        UdpPacket.Builder payLoadBuilder = new UdpPacket.Builder()
                .srcPort(UdpPort.HTTP)
                .dstPort(UdpPort.HTTP)
                .srcAddr(InetAddress.getByAddress(new byte[]{8, 8, 4, 4}))
                .dstAddr(InetAddress.getByAddress(new byte[]{8, 8, 8, 8}))
                .correctChecksumAtBuild(true)
                .correctLengthAtBuild(true)
                .payloadBuilder(
                        new UnknownPacket.Builder()
                                .rawData(new byte[]{1,2,3,4,5})
                );

        IpPacket ipOutPacket = new IpV4Packet.Builder()
                .version(IpVersion.IPV4)
                .tos(IpV4Rfc791Tos.newInstance((byte) 0))
                .protocol(IpNumber.UDP)
                .srcAddr((Inet4Address) Inet4Address.getByAddress(new byte[]{8, 8, 4, 4}))
                .dstAddr((Inet4Address) Inet4Address.getByAddress(new byte[]{8, 8, 8, 8}))
                .correctChecksumAtBuild(true)
                .correctLengthAtBuild(true)
                .payloadBuilder(payLoadBuilder)
                .build();

        dnsPacketProxy.handleDnsRequest(ipOutPacket.getRawData());
        assertNull(mockEventLoop.lastOutgoing);
        assertNull(mockEventLoop.lastResponse);
        // TODO: 19/03/17 Check for correct point of error
    }

    @Test
    public void testHandleDnsRequestEmptyPacket() throws Exception {
        UdpPacket.Builder payLoadBuilder = new UdpPacket.Builder()
                .srcPort(UdpPort.DOMAIN)
                .dstPort(UdpPort.DOMAIN)
                .srcAddr(InetAddress.getByAddress(new byte[]{8, 8, 4, 4}))
                .dstAddr(InetAddress.getByAddress(new byte[]{8, 8, 8, 8}))
                .correctChecksumAtBuild(true)
                .correctLengthAtBuild(true)
                .payloadBuilder(
                        new UnknownPacket.Builder()
                                .rawData(new byte[0])
                );

        IpPacket ipOutPacket = new IpV4Packet.Builder()
                .version(IpVersion.IPV4)
                .tos(IpV4Rfc791Tos.newInstance((byte) 0))
                .protocol(IpNumber.UDP)
                .srcAddr((Inet4Address) Inet4Address.getByAddress(new byte[]{8, 8, 4, 4}))
                .dstAddr((Inet4Address) Inet4Address.getByAddress(new byte[]{8, 8, 8, 8}))
                .correctChecksumAtBuild(true)
                .correctLengthAtBuild(true)
                .payloadBuilder(payLoadBuilder)
                .build();

        dnsPacketProxy.handleDnsRequest(ipOutPacket.getRawData());

        assertNotNull(mockEventLoop.lastOutgoing);
        assertEquals(0, mockEventLoop.lastOutgoing.getLength());
        assertEquals(Inet4Address.getByAddress(new byte[]{8, 8, 8, 8}), mockEventLoop.lastOutgoing.getAddress());

        assertNull(mockEventLoop.lastResponse);

        // Check the same thing with upstream DNS servers configured.
        tinySetUp();
        for (byte i = 0; i < 9; i++)
            dnsPacketProxy.upstreamDnsServers.add(Inet4Address.getByAddress(new byte[]{1, 1, 1, i}));
        dnsPacketProxy.handleDnsRequest(ipOutPacket.getRawData());

        assertNotNull(mockEventLoop.lastOutgoing);
        assertEquals(0, mockEventLoop.lastOutgoing.getLength());
        // We are using last byte - 2 as the offset into the table.
        assertEquals(Inet4Address.getByAddress(new byte[]{1, 1, 1, 6}), mockEventLoop.lastOutgoing.getAddress());
        assertNull(mockEventLoop.lastResponse);
    }

    private static class MockEventLoop implements DnsPacketProxy.EventLoop {
        DatagramPacket lastOutgoing;
        IpPacket lastResponse;

        @Override
        public void forwardPacket(DatagramPacket packet, IpPacket requestPacket) throws AdVpnThread.VpnNetworkException {
            lastOutgoing = packet;
        }

        @Override
        public void queueDeviceWrite(IpPacket packet) {
            lastResponse = packet;
        }
    }
}