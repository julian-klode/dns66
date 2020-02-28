package org.jak_linux.dns66.vpn;

import android.content.pm.PackageManager;
import android.net.VpnService;
import android.util.Log;

import org.jak_linux.dns66.Configuration;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static junit.framework.Assert.*;
import static org.mockito.Matchers.*;
import static org.powermock.api.mockito.PowerMockito.*;

/**
 * Created by jak on 19/04/17.
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest(Log.class)
public class AdVpnThreadTest {

    private AdVpnService service;
    private AdVpnThread thread;
    private Configuration config;
    private VpnService.Builder builder;
    private List<InetAddress> serversAdded;

    @Before
    public void setUp() {
        mockStatic(Log.class);
        service = mock(AdVpnService.class);
        thread = new AdVpnThread(service, null);
        builder = mock(VpnService.Builder.class);

        config = new Configuration();
        config.dnsServers = new Configuration.DnsServers();
        config.whitelist = new Configuration.Whitelist() {
            @Override
            public void resolve(PackageManager pm, Set<String> onVpn, Set<String> notOnVpn) {
                onVpn.add("onVpn");
                notOnVpn.add("notOnVpn");
            }
        };

        serversAdded = new ArrayList<>();

        when(builder.addDnsServer(anyString())).thenAnswer(new Answer<VpnService.Builder>() {
            @Override
            public VpnService.Builder answer(InvocationOnMock invocation) throws Throwable {
                serversAdded.add(InetAddress.getByName(invocation.getArgumentAt(0, String.class)));
                return builder;
            }
        });
        when(builder.addDnsServer(any(InetAddress.class))).thenAnswer(new Answer<VpnService.Builder>() {
            @Override
            public VpnService.Builder answer(InvocationOnMock invocation) throws Throwable {
                serversAdded.add(invocation.getArgumentAt(0, InetAddress.class));
                return builder;
            }
        });
    }

    @Test
    public void testConfigurePackages() throws Exception {
        final List<String> disallowed = new ArrayList<>();
        final List<String> allowed = new ArrayList<>();
        when(builder.addDisallowedApplication(anyString())).thenAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                disallowed.add(invocation.getArgumentAt(0, String.class));
                return null;
            }
        });
        when(builder.addAllowedApplication(anyString())).thenAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                allowed.add(invocation.getArgumentAt(0, String.class));
                return null;
            }
        });

        // We are defaulting to disallow: allow all allowed packages.
        allowed.clear();
        disallowed.clear();
        config.whitelist.defaultMode = Configuration.Whitelist.DEFAULT_MODE_NOT_ON_VPN;
        thread.configurePackages(builder, config);
        assertTrue(allowed.contains("onVpn"));
        assertEquals(new ArrayList<String>(), disallowed);

        // We are defaulting to allow: deny all non-allowed packages.
        allowed.clear();
        disallowed.clear();
        config.whitelist.defaultMode = Configuration.Whitelist.DEFAULT_MODE_ON_VPN;
        thread.configurePackages(builder, config);
        assertTrue(disallowed.contains("notOnVpn"));
        assertEquals(new ArrayList<String>(), allowed);

        // Intelligent is like allow, it only disallows system apps
        allowed.clear();
        disallowed.clear();
        config.whitelist.defaultMode = Configuration.Whitelist.DEFAULT_MODE_INTELLIGENT;
        thread.configurePackages(builder, config);
        assertTrue(disallowed.contains("notOnVpn"));
        assertEquals(new ArrayList<String>(), allowed);

    }

    @Test
    public void testHasIpV6Servers() throws Exception {
        Configuration.Item item0 = new Configuration.Item();
        Configuration.Item item1 = new Configuration.Item();
        config.ipV6Support = true;
        config.dnsServers.enabled = true;
        config.dnsServers.items.add(item0);
        config.dnsServers.items.add(item1);
        item0.location = "::1";
        item0.state = Configuration.Item.STATE_ALLOW;
        item1.location = "127.0.0.1";
        item1.state = Configuration.Item.STATE_ALLOW;
        List<InetAddress> servers = new ArrayList<>();

        assertTrue(thread.hasIpV6Servers(config, servers));
        config.ipV6Support = false;
        assertFalse(thread.hasIpV6Servers(config, servers));
        config.ipV6Support = true;

        item0.state = Configuration.Item.STATE_DENY;
        assertFalse(thread.hasIpV6Servers(config, servers));

        servers.add(Inet6Address.getByName("127.0.0.1"));
        assertFalse(thread.hasIpV6Servers(config, servers));

        servers.add(Inet6Address.getByName("::1"));
        assertTrue(thread.hasIpV6Servers(config, servers));
    }

    @Test
    // Everything works fine, everyone gets through.
    public void testNewDNSServer() throws Exception {
        String format = "192.168.0.%d";
        byte[] ipv6Template = new byte[]{32, 1, 13, (byte) (184 & 0xFF), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};

        InetAddress i6addr = Inet6Address.getByName("::1");
        InetAddress i4addr = Inet4Address.getByName("127.0.0.1");

        thread.newDNSServer(builder, format, ipv6Template, i4addr);
        assertTrue(thread.upstreamDnsServers.contains(i4addr));
        assertTrue(serversAdded.contains(InetAddress.getByName("192.168.0.2")));

        thread.newDNSServer(builder, format, ipv6Template, i6addr);
        assertTrue(thread.upstreamDnsServers.contains(i6addr));
        assertEquals(3, ipv6Template[ipv6Template.length - 1]);
        assertTrue(serversAdded.contains(InetAddress.getByAddress(ipv6Template)));
    }

    @Test
    // IPv6 is disabled: We only get IPv4 servers through
    public void testNewDNSServer_ipv6disabled() throws Exception {
        byte[] ipv6Template = new byte[]{32, 1, 13, (byte) (184 & 0xFF), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        InetAddress i6addr = Inet6Address.getByName("::1");

        thread.newDNSServer(builder, "192.168.0.%d", null, i6addr);
        assertTrue(serversAdded.isEmpty());
        assertTrue(thread.upstreamDnsServers.isEmpty());

        InetAddress i4addr = Inet4Address.getByName("127.0.0.1");
        thread.newDNSServer(builder, "192.168.0.%d", null, i4addr);
        assertTrue(serversAdded.contains(InetAddress.getByName("192.168.0.2")));
        assertTrue(thread.upstreamDnsServers.contains(i4addr));
    }

    @Test
    // IPv4 is disabled: We only get IPv6 servers through
    public void testNewDNSServer_ipv4disabled() throws Exception {
        String format = "192.168.0.%d";
        byte[] ipv6Template = new byte[]{32, 1, 13, (byte) (184 & 0xFF), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};

        InetAddress i6addr = Inet6Address.getByName("::1");
        InetAddress i4addr = Inet4Address.getByName("127.0.0.1");

        thread.newDNSServer(builder, null, ipv6Template, i4addr);
        assertTrue(thread.upstreamDnsServers.isEmpty());
        assertTrue(serversAdded.isEmpty());

        thread.newDNSServer(builder, format, ipv6Template, i6addr);
        assertTrue(thread.upstreamDnsServers.contains(i6addr));
        assertEquals(2, ipv6Template[ipv6Template.length - 1]);
        assertTrue(serversAdded.contains(InetAddress.getByAddress(ipv6Template)));
    }

}
