package org.jak_linux.dns66.vpn;

import org.pcap4j.packet.IpPacket;
import org.pcap4j.packet.IpV4Packet;
import org.pcap4j.packet.IpV6Packet;
import org.pcap4j.packet.UdpPacket;
import org.pcap4j.packet.UnknownPacket;

import java.net.Inet4Address;
import java.net.Inet6Address;

/**
 * Created by jak on 19/03/17.
 */

public class PacketProxy {
    void handleDnsResponse(IpPacket parsedPacket, byte[] response, AdVpnThread adVpnThread) {
        UdpPacket udpOutPacket = (UdpPacket) parsedPacket.getPayload();
        UdpPacket.Builder payLoadBuilder = new UdpPacket.Builder(udpOutPacket)
                .srcPort(udpOutPacket.getHeader().getDstPort())
                .dstPort(udpOutPacket.getHeader().getSrcPort())
                .srcAddr(parsedPacket.getHeader().getDstAddr())
                .dstAddr(parsedPacket.getHeader().getSrcAddr())
                .correctChecksumAtBuild(true)
                .correctLengthAtBuild(true)
                .payloadBuilder(
                        new UnknownPacket.Builder()
                                .rawData(response)
                );


        IpPacket ipOutPacket;
        if (parsedPacket instanceof IpV4Packet) {
            ipOutPacket = new IpV4Packet.Builder((IpV4Packet) parsedPacket)
                    .srcAddr((Inet4Address) parsedPacket.getHeader().getDstAddr())
                    .dstAddr((Inet4Address) parsedPacket.getHeader().getSrcAddr())
                    .correctChecksumAtBuild(true)
                    .correctLengthAtBuild(true)
                    .payloadBuilder(payLoadBuilder)
                    .build();

        } else {
            ipOutPacket = new IpV6Packet.Builder((IpV6Packet) parsedPacket)
                    .srcAddr((Inet6Address) parsedPacket.getHeader().getDstAddr())
                    .dstAddr((Inet6Address) parsedPacket.getHeader().getSrcAddr())
                    .correctLengthAtBuild(true)
                    .payloadBuilder(payLoadBuilder)
                    .build();
        }

        adVpnThread.queueDeviceWrite(ipOutPacket);
    }
}
