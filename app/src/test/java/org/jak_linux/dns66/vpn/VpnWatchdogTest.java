package org.jak_linux.dns66.vpn;

import android.util.Log;

import org.jak_linux.dns66.FileHelper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.powermock.api.mockito.PowerMockito.*;

/**
 * Tests for the Vpn watchdog
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest(Log.class)
public class VpnWatchdogTest {

    private VpnWatchdog watchdog;
    private DatagramSocket mockSocket;

    @Before
    public void setUp() throws Exception {
        mockStatic(Log.class);
        watchdog = spy(new VpnWatchdog());
        watchdog.initialize(true);

        mockSocket = mock(DatagramSocket.class);
        when(watchdog.newDatagramSocket()).thenReturn(mockSocket);
        watchdog.setTarget(InetAddress.getByAddress(new byte[]{127, 0, 0, 1}));
    }

    @Test
    public void testGetTimeout() throws Exception {
        assertEquals(1000, watchdog.getPollTimeout());
        watchdog.lastPacketReceived = 1;
        watchdog.lastPacketSent = 2;
        assertEquals(7000, watchdog.getPollTimeout());
    }

    @Test
    public void testInitialize() throws Exception {
        watchdog.initialize(true);
        assertEquals(1000, watchdog.getPollTimeout());
        assertEquals(0, watchdog.lastPacketSent);
        watchdog.initPenalty = 100;
        watchdog.initialize(true);
    }

    @Test
    public void test_disabled() throws Exception {
        watchdog.initialize(false);
        assertEquals(-1, watchdog.getPollTimeout());
        // Successful case increments
        watchdog.handleTimeout();
        assertEquals(-1, watchdog.getPollTimeout());

        watchdog.lastPacketReceived = 1;
        watchdog.lastPacketSent = 2;
        watchdog.handleTimeout();
        assertEquals(0, watchdog.initPenalty);

        watchdog.handlePacket(new byte[0]);
        assertEquals(1, watchdog.lastPacketReceived);

        watchdog.sendPacket();
        assertEquals(2, watchdog.lastPacketSent);
    }

    @Test
    public void testHandleTimeout() throws Exception {
        doNothing().when(watchdog, "sendPacket");
        assertEquals(1000, watchdog.getPollTimeout());
        // Successful case increments
        watchdog.handleTimeout();
        assertEquals(4 * 1000, watchdog.getPollTimeout());
        watchdog.handleTimeout();
        assertEquals(4 * 4 * 1000, watchdog.getPollTimeout());
        watchdog.handleTimeout();

        watchdog.pollTimeout = 4 * 4 * 4 * 4 * 4 * 4 * 1000;
        watchdog.handleTimeout();
        assertEquals(4 * 4 * 4 * 4 * 4 * 4 * 1000, watchdog.getPollTimeout());
    }

    @Test(expected = AdVpnThread.VpnNetworkException.class)
    public void testHandleTimeout_error() throws Exception {
        watchdog.lastPacketReceived = 1;
        watchdog.lastPacketSent = 2;

        watchdog.handleTimeout();
    }

    @Test
    public void testHandleTimeout_errorInitPenalty() throws Exception {
        watchdog.lastPacketReceived = 1;
        watchdog.lastPacketSent = 2;


        assertEquals(0, watchdog.initPenalty);
        try {
            watchdog.handleTimeout();
        } catch (AdVpnThread.VpnNetworkException e) {
            // OK
        }
        assertEquals(200, watchdog.initPenalty);

        for (int i = 1; i < 25; i++) {
            assertEquals(200 * i, watchdog.initPenalty);
            try {
                watchdog.handleTimeout();
            } catch (AdVpnThread.VpnNetworkException e) {
                // OK
            }
        }
        assertEquals(5000, watchdog.initPenalty);
        try {
            watchdog.handleTimeout();
        } catch (AdVpnThread.VpnNetworkException e) {
            // OK
        }
        assertEquals(5000, watchdog.initPenalty);
    }

    @Test
    public void handlePacket() throws Exception {
        watchdog.lastPacketReceived = 0;
        watchdog.handlePacket(new byte[0]);
        assertNotEquals(0, watchdog.lastPacketReceived);
    }

    @Test
    public void testSendPacket() throws Exception {
        watchdog.lastPacketSent = 0;
        watchdog.sendPacket();
        assertNotEquals(0, watchdog.lastPacketSent);
    }

    @Test(expected = AdVpnThread.VpnNetworkException.class)
    public void testSendPacket_error() throws Exception {
        doThrow(new IOException("Cannot send")).when(mockSocket).send(any(DatagramPacket.class));
        watchdog.lastPacketSent = 0;
        watchdog.sendPacket();
    }

}