package org.jak_linux.dns66.db;

import android.content.Context;
import android.util.Log;

import org.jak_linux.dns66.Configuration;
import org.jak_linux.dns66.FileHelper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.CharBuffer;
import java.util.ArrayList;

import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.powermock.api.mockito.PowerMockito.*;

@RunWith(PowerMockRunner.class)
@PrepareForTest(Log.class)
public class RuleDatabaseTest {

    @Before
    public void setUp() {
        PowerMockito.mockStatic(Log.class);
        // use Mockito to set up your expectation
        //Mockito.when(Log.d(param, msg)).thenReturn(0);
        //Mockito.when(Log.d(tag, msg, throwable)).thenReturn(0);
    }

    @Test
    public void testGetInstance() throws Exception {
        RuleDatabase instance = RuleDatabase.getInstance();

        assertNotNull(instance);
        assertTrue(instance.isEmpty());
        assertFalse(instance.isBlocked("example.com"));
    }

    @Test
    public void testParseLine() throws Exception {
        // Standard format lines
        assertEquals("example.com", RuleDatabase.parseLine("0.0.0.0 example.com"));
        assertEquals("example.com", RuleDatabase.parseLine("127.0.0.1 example.com"));
        assertEquals("example.com", RuleDatabase.parseLine("::1 example.com"));
        assertEquals("example.com", RuleDatabase.parseLine("example.com"));
        // Comments
        assertEquals("example.com", RuleDatabase.parseLine("example.com # foo"));
        assertEquals("example.com", RuleDatabase.parseLine("0.0.0.0 example.com # foo"));
        assertEquals("example.com", RuleDatabase.parseLine("::1 example.com # foo"));
        // Check lower casing
        assertEquals("example.com", RuleDatabase.parseLine("example.cOm"));
        assertEquals("example.com", RuleDatabase.parseLine("127.0.0.1 example.cOm"));
        assertEquals("example.com", RuleDatabase.parseLine("::1 example.cOm"));
        // Space trimming
        assertNull(RuleDatabase.parseLine(" 127.0.0.1 example.com"));
        assertEquals("127.0.0.1.example.com", RuleDatabase.parseLine("127.0.0.1.example.com "));
        // assertEquals("::1.example.com", RuleDatabase.parseLine("::1.example.com "));  //TODO: research this, it looks invalid to me
        assertEquals("0.0.0.0.example.com", RuleDatabase.parseLine("0.0.0.0.example.com "));
        assertEquals("example.com", RuleDatabase.parseLine("127.0.0.1 example.com "));
        assertEquals("example.com", RuleDatabase.parseLine("127.0.0.1 example.com\t"));
        assertEquals("example.com", RuleDatabase.parseLine("127.0.0.1   example.com "));
        assertEquals("example.com", RuleDatabase.parseLine("127.0.0.1\t example.com "));
        assertEquals("example.com", RuleDatabase.parseLine("::1\t example.com "));
        // allow whole TLDs
        assertEquals("com", RuleDatabase.parseLine("127.0.0.1   com"));
        assertEquals("xxxbizbadtld", RuleDatabase.parseLine("xxxbizbadtld"));
        assertEquals("xx", RuleDatabase.parseLine("xx"));
        // Space between values
        // Invalid lines
        assertNull(RuleDatabase.parseLine("127.0.0.1 "));
        assertNull(RuleDatabase.parseLine("127.0.0.1"));
        assertNull(RuleDatabase.parseLine("0.0.0.0"));
        assertNull(RuleDatabase.parseLine("0.0.0.0 "));
        assertNull(RuleDatabase.parseLine("::1 "));
        assertNull(RuleDatabase.parseLine("::1"));
        assertNull(RuleDatabase.parseLine("invalid example.com"));
        assertNull(RuleDatabase.parseLine("invalid\texample.com"));
        assertNull(RuleDatabase.parseLine("invalid long line"));
        assertNull(RuleDatabase.parseLine("# comment line"));
        assertNull(RuleDatabase.parseLine(""));
        assertNull(RuleDatabase.parseLine("\t"));
        assertNull(RuleDatabase.parseLine(" "));
        assertNull(RuleDatabase.parseLine("."));
        assertNull(RuleDatabase.parseLine("u"));
        assertNull(RuleDatabase.parseLine("www.u", true));
        assertNull(RuleDatabase.parseLine("www."));
        assertNull(RuleDatabase.parseLine("com."));
        assertNull(RuleDatabase.parseLine(".com"));
        assertNull(RuleDatabase.parseLine("exam/ple.com"));
        assertNull(RuleDatabase.parseLine("example.com?asdf=fdsa"));
        assertNull(RuleDatabase.parseLine("example.com#@##videoads"));  // inapplicable adblock filter

        // Extended matching
        assertEquals("example.com", RuleDatabase.parseLine("www.example.com ", true));
        assertEquals("zzz.example.com", RuleDatabase.parseLine("xxx.yyy.zzz.example.com ", true));
        assertEquals("zzz.example.co.uk", RuleDatabase.parseLine("xxx.yyy.zzz.example.co.uk ", true));
        assertEquals("example.spam.uk", RuleDatabase.parseLine("xxx.yyy.zzz.example.spam.uk ", true));
        assertEquals("example.spam.uk", RuleDatabase.parseLine("||xxx.yyy.zzz.example.spam.uk^", true));
        assertEquals("example.com", RuleDatabase.parseLine("||www.example.com^", true));
        assertEquals("example.com", RuleDatabase.parseLine("www.example.com  # somecomment", true));
        assertEquals("example.com", RuleDatabase.parseLine("0.0.0.0  www.example.com  # somecomment", true));

        // Disable extended matching
        assertEquals("www.example.com", RuleDatabase.parseLine("www.example.com ", false));
        assertEquals("xxx.yyy.zzz.example.com", RuleDatabase.parseLine("xxx.yyy.zzz.example.com ", false));
        assertEquals("xxx.yyy.zzz.example.co.uk", RuleDatabase.parseLine("xxx.yyy.zzz.example.co.uk ", false));
        assertEquals("xxx.yyy.zzz.example.spam.uk", RuleDatabase.parseLine("xxx.yyy.zzz.example.spam.uk ", false));
        assertEquals("xxx.yyy.zzz.example.spam.uk", RuleDatabase.parseLine("||xxx.yyy.zzz.example.spam.uk^", false));
        assertEquals("www.example.com", RuleDatabase.parseLine("||www.example.com^", false));
        assertEquals("www.example.com", RuleDatabase.parseLine("www.example.com  # somecomment", false));
        assertEquals("www.example.com", RuleDatabase.parseLine("0.0.0.0  www.example.com  # somecomment", false));
    }

    @Test
    public void testLoadReader() throws Exception {
        RuleDatabase db = new RuleDatabase();
        db.nextBlockedHosts = db.blockedHosts.get();
        db.nextAllowedHosts = db.allowedHosts.get();

        Configuration.Item item = new Configuration.Item();

        item.location = "<some random file>";
        item.state = Configuration.Item.STATE_IGNORE;

        // Ignore. Does nothing
        assertTrue(db.loadReader(item, new StringReader("example.com")));
        assertTrue(db.isEmpty());
        assertFalse(db.isBlocked("example.com"));

        // Deny, the host should be blocked now.
        item.state = Configuration.Item.STATE_DENY;
        assertTrue(db.loadReader(item, new StringReader("example.com")));
        assertFalse(db.isEmpty());
        assertTrue(db.isBlocked("example.com"));

        // Reallow again, the entry should disappear.
        item.state = Configuration.Item.STATE_ALLOW;
        assertTrue(db.loadReader(item, new StringReader("example.com")));
        assertTrue(db.isEmpty());
        assertFalse(db.isBlocked("example.com"));

        assertTrue(db.loadReader(item, new StringReader("whitelisted.www.foo.com")));
        assertTrue(db.loadReader(item, new StringReader("whitelisted.foo.com")));
        assertTrue(db.loadReader(item, new StringReader("whitelisted.bad.foo.com")));


        // Check multiple lines
        item.state = Configuration.Item.STATE_DENY;
        assertFalse(db.isBlocked("example.com"));
        assertFalse(db.isBlocked("foo.com"));
        assertTrue(db.loadReader(item, new StringReader("example.com\n127.0.0.1 foo.com\nbad.foo.com")));
        assertFalse(db.isEmpty());
        assertFalse(db.isBlocked("example.com"));  // it has been explicitly whitelisted above
        assertTrue(db.isBlocked("foo.com"));
        assertFalse(db.isBlocked("www.foo.com"));

        db.config = new Configuration();
        db.config.extendedFiltering.enabled = true;

        assertTrue(db.isBlocked("www.foo.com"));
        assertTrue(db.isBlocked("bar.foo.com"));
        assertTrue(db.isBlocked("bad.foo.com"));
        assertFalse(db.isBlocked("whitelisted.www.foo.com"));
        assertFalse(db.isBlocked("whitelisted.foo.com"));
        assertFalse(db.isBlocked("whitelisted.bad.foo.com"));
        assertFalse(db.isBlocked("foobar.whitelisted.bad.foo.com"));
        assertTrue(db.isBlocked("foobar.baz.foo.com"));
        assertTrue(db.isBlocked("baz.bad.foo.com"));

        // Interrupted test
        Thread.currentThread().interrupt();
        try {
            db.loadReader(item, new StringReader("example.com"));
            fail("Interrupted thread did not cause reader to be interrupted");
        } catch (InterruptedException e) {

        }

        // Test with an invalid line before a valid one.
        item.state = Configuration.Item.STATE_DENY;
        assertTrue(db.loadReader(item, new StringReader("invalid line\notherhost.com")));
        assertTrue(db.isBlocked("otherhost.com"));

        // Allow again
        item.state = Configuration.Item.STATE_ALLOW;
        assertTrue(db.loadReader(item, new StringReader("invalid line\notherhost.com")));
        assertFalse(db.isBlocked("otherhost.com"));

        // Reader can't read, we are aborting.
        Reader reader = Mockito.mock(Reader.class);
        doThrow(new IOException()).when(reader).read((char[]) any());
        doThrow(new IOException()).when(reader).read((char[]) any(), anyInt(), anyInt());
        doThrow(new IOException()).when(reader).read(any(CharBuffer.class));

        assertFalse(db.loadReader(item, reader));
    }

    @Test
    @PrepareForTest({Log.class, FileHelper.class})
    public void testInitialize_host() throws Exception {
        RuleDatabase ruleDatabase = spy(new RuleDatabase());

        Configuration.Item item = new Configuration.Item();

        item.location = "ahost.com";
        item.state = Configuration.Item.STATE_DENY;

        Configuration configuration = new Configuration();
        configuration.hosts = new Configuration.Hosts();
        configuration.hosts.enabled = true;
        configuration.hosts.items = new ArrayList<>();
        configuration.hosts.items.add(item);

        Context context = mock(Context.class);
        mockStatic(FileHelper.class);
        when(FileHelper.loadCurrentSettings(context)).thenReturn(configuration);
        when(FileHelper.openItemFile(context, item)).thenReturn(null);
        ruleDatabase.initialize(context);

        assertTrue(ruleDatabase.isBlocked("ahost.com"));

        configuration.hosts.enabled = false;

        ruleDatabase.initialize(context);

        assertFalse(ruleDatabase.isBlocked("ahost.com"));
        assertTrue(ruleDatabase.isEmpty());
    }

    @PrepareForTest({Log.class, FileHelper.class})
    public void testInitialize_disabled() throws Exception {
        RuleDatabase ruleDatabase = spy(new RuleDatabase());

        Configuration.Item item = new Configuration.Item();

        item.location = "ahost.com";
        item.state = Configuration.Item.STATE_DENY;

        Configuration configuration = new Configuration();
        configuration.hosts = new Configuration.Hosts();
        configuration.hosts.enabled = false;
        configuration.hosts.items = new ArrayList<>();
        configuration.hosts.items.add(item);

        Context context = mock(Context.class);
        mockStatic(FileHelper.class);
        when(FileHelper.loadCurrentSettings(context)).thenReturn(configuration);
        when(FileHelper.openItemFile(context, item)).thenReturn(null);
        ruleDatabase.initialize(context);

        assertFalse(ruleDatabase.isBlocked("ahost.com"));
        assertTrue(ruleDatabase.isEmpty());
    }

    @Test
    @PrepareForTest({Log.class, FileHelper.class})
    public void testInitialize_file() throws Exception {
        RuleDatabase ruleDatabase = spy(new RuleDatabase());

        Configuration.Item item = new Configuration.Item();

        item.location = "protocol://some-weird-file-uri";
        item.state = Configuration.Item.STATE_DENY;

        Configuration configuration = new Configuration();
        configuration.hosts = new Configuration.Hosts();
        configuration.hosts.enabled = true;
        configuration.hosts.items = new ArrayList<>();
        configuration.hosts.items.add(item);

        Context context = mock(Context.class);
        mockStatic(FileHelper.class);
        when(FileHelper.loadCurrentSettings(context)).thenReturn(configuration);
        when(FileHelper.openItemFile(context, item)).thenReturn(new InputStreamReader(new ByteArrayInputStream("example.com".getBytes("utf-8"))));
        ruleDatabase.initialize(context);

        assertTrue(ruleDatabase.isBlocked("example.com"));

        item.state = Configuration.Item.STATE_IGNORE;

        ruleDatabase.initialize(context);

        assertTrue(ruleDatabase.isEmpty());

    }

    @Test
    @PrepareForTest({Log.class, FileHelper.class})
    public void testInitialize_fileNotFound() throws Exception {
        RuleDatabase ruleDatabase = spy(new RuleDatabase());

        Configuration.Item item = new Configuration.Item();

        item.location = "protocol://some-weird-file-uri";
        item.state = Configuration.Item.STATE_DENY;

        Configuration configuration = new Configuration();
        configuration.hosts = new Configuration.Hosts();
        configuration.hosts.enabled = true;
        configuration.hosts.items = new ArrayList<>();
        configuration.hosts.items.add(item);

        Context context = mock(Context.class);
        mockStatic(FileHelper.class);
        when(FileHelper.loadCurrentSettings(context)).thenReturn(configuration);
        when(FileHelper.openItemFile(context, item)).thenThrow(new FileNotFoundException("foobar"));
        ruleDatabase.initialize(context);
        assertTrue(ruleDatabase.isEmpty());
    }

    public static class FooException extends RuntimeException {
    }
}