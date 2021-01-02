package org.jak_linux.dns66;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import androidx.annotation.NonNull;

import org.hamcrest.CoreMatchers;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Created by jak on 07/04/17.
 */
public class ConfigurationTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private Configuration.Item newItemForLocation(String location) {
        Configuration.Item item = new Configuration.Item();
        item.location = location;
        return item;
    }

    @Test
    public void testIsDownloadable() {
        try {
            newItemForLocation(null).isDownloadable();
            fail("Was null");
        } catch (NullPointerException e) {
            // OK
        }

        assertTrue("http:// URI downloadable", newItemForLocation("http://example.com").isDownloadable());
        assertTrue("https:// URI downloadable", newItemForLocation("https://example.com").isDownloadable());
        assertFalse("file:// URI downloadable", newItemForLocation("file://example.com").isDownloadable());
        assertFalse("file:// URI downloadable", newItemForLocation("file:/example.com").isDownloadable());
        assertFalse("https domain not downloadable", newItemForLocation("https.example.com").isDownloadable());
        assertFalse("http domain not downloadable", newItemForLocation("http.example.com").isDownloadable());
    }

    @Test
    public void testResolve() throws Exception {
        Configuration.Whitelist wl = new Configuration.Whitelist() {
            @Override
            Intent newBrowserIntent() {
                return mock(Intent.class);
            }
        };

        List<ResolveInfo> resolveInfoList = new ArrayList<>();
        List<ApplicationInfo> applicationInfoList = new ArrayList<>();

        // Web browsers
        resolveInfoList.add(newResolveInfo("system-browser", 0));
        applicationInfoList.add(newApplicationInfo("system-browser", ApplicationInfo.FLAG_SYSTEM));
        resolveInfoList.add(newResolveInfo("data-browser", 0));
        applicationInfoList.add(newApplicationInfo("data-browser", 0));

        // Not a browser
        applicationInfoList.add(newApplicationInfo("system-app", ApplicationInfo.FLAG_SYSTEM));
        applicationInfoList.add(newApplicationInfo("data-app", 0));

        // This app
        applicationInfoList.add(newApplicationInfo(BuildConfig.APPLICATION_ID, 0));

        PackageManager pm = mock(PackageManager.class);
        //noinspection WrongConstant
        when(pm.queryIntentActivities(any(Intent.class), anyInt())).thenReturn(resolveInfoList);
        //noinspection WrongConstant
        when(pm.getInstalledApplications(anyInt())).thenReturn(applicationInfoList);

        Set<String> onVpn = new HashSet<>();
        Set<String> notOnVpn = new HashSet<>();

        wl.defaultMode = Configuration.Whitelist.DEFAULT_MODE_NOT_ON_VPN;
        wl.resolve(pm, onVpn, notOnVpn);

        assertTrue(onVpn.contains(BuildConfig.APPLICATION_ID));
        assertTrue(notOnVpn.contains("system-app"));
        assertTrue(notOnVpn.contains("data-app"));
        assertTrue(notOnVpn.contains("system-browser"));
        assertTrue(notOnVpn.contains("data-browser"));

        // Default allow on vpn
        onVpn.clear();
        notOnVpn.clear();
        wl.defaultMode = Configuration.Whitelist.DEFAULT_MODE_ON_VPN;
        wl.resolve(pm, onVpn, notOnVpn);

        assertTrue(onVpn.contains(BuildConfig.APPLICATION_ID));
        assertTrue(onVpn.contains("system-app"));
        assertTrue(onVpn.contains("data-app"));
        assertTrue(onVpn.contains("system-browser"));
        assertTrue(onVpn.contains("data-browser"));

        // Default intelligent on vpn
        onVpn.clear();
        notOnVpn.clear();
        wl.defaultMode = Configuration.Whitelist.DEFAULT_MODE_INTELLIGENT;
        wl.resolve(pm, onVpn, notOnVpn);

        assertTrue(onVpn.contains(BuildConfig.APPLICATION_ID));
        assertTrue(notOnVpn.contains("system-app"));
        assertTrue(onVpn.contains("data-app"));
        assertTrue(onVpn.contains("system-browser"));
        assertTrue(onVpn.contains("data-browser"));

        // Default intelligent on vpn
        onVpn.clear();
        notOnVpn.clear();
        wl.items.clear();
        wl.itemsOnVpn.clear();
        wl.items.add(BuildConfig.APPLICATION_ID);
        wl.items.add("system-browser");
        wl.defaultMode = Configuration.Whitelist.DEFAULT_MODE_INTELLIGENT;
        wl.resolve(pm, onVpn, notOnVpn);
        assertTrue(onVpn.contains(BuildConfig.APPLICATION_ID));
        assertTrue(notOnVpn.contains("system-browser"));

        // Check that blacklisting works
        onVpn.clear();
        notOnVpn.clear();
        wl.items.clear();
        wl.itemsOnVpn.clear();
        wl.itemsOnVpn.add("data-app");
        wl.defaultMode = Configuration.Whitelist.DEFAULT_MODE_NOT_ON_VPN;
        wl.resolve(pm, onVpn, notOnVpn);
        assertTrue(onVpn.contains("data-app"));
    }

    @Test
    public void testRead() throws Exception {
        Configuration config = Configuration.read(new StringReader("{}"));

        assertNotNull(config.hosts);
        assertNotNull(config.hosts.items);
        assertNotNull(config.whitelist);
        assertNotNull(config.whitelist.items);
        assertNotNull(config.whitelist.itemsOnVpn);
        assertNotNull(config.dnsServers);
        assertNotNull(config.dnsServers.items);
        assertTrue(config.ipV6Support);
        assertFalse(config.watchDog);
        assertFalse(config.nightMode);
        assertTrue(config.showNotification);
        assertFalse(config.autoStart);
    }

    @Test
    public void testReadNewer() throws Exception {
        thrown.expect(IOException.class);

        thrown.expectMessage(CoreMatchers.containsString("version"));
        Configuration.read(new StringReader("{version: " + (Configuration.VERSION + 1) + "}"));
    }

    @Test
    public void testReadWrite() throws Exception {
        Configuration config = Configuration.read(new StringReader("{}"));
        StringWriter writer = new StringWriter();
        config.write(writer);
        Configuration config2 = Configuration.read(new StringReader(writer.toString()));
        StringWriter writer2 = new StringWriter();
        config2.write(writer2);
        assertEquals(writer.toString(), writer2.toString());
    }

    @NonNull
    private ResolveInfo newResolveInfo(String name, int flags) {
        ResolveInfo resolveInfo = mock(ResolveInfo.class);
        ActivityInfo activityInfo = mock(ActivityInfo.class);
        activityInfo.packageName = name;
        resolveInfo.activityInfo = activityInfo;
        resolveInfo.activityInfo.flags = flags;
        return resolveInfo;
    }

    @NonNull
    private ApplicationInfo newApplicationInfo(String name, int flags) {
        ApplicationInfo applicationInfo = mock(ApplicationInfo.class);
        applicationInfo.packageName = name;
        applicationInfo.flags = flags;
        return applicationInfo;
    }

}