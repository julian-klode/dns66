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

import java.util.ArrayList;
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

    @Before
    public void setUp() {
        mockStatic(Log.class);
        service = mock(AdVpnService.class);
        thread = new AdVpnThread(service, null);
        builder = mock(VpnService.Builder.class);

        config = new Configuration();
        config.whitelist = new Configuration.Whitelist() {
            @Override
            public void resolve(PackageManager pm, Set<String> onVpn, Set<String> notOnVpn) {
                onVpn.add("onVpn");
                notOnVpn.add("notOnVpn");
            }
        };
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
}
