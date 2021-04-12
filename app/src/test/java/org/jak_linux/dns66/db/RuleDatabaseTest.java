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

    // TODO(thromer) More tests

    @Test
    public void testGetInstance() throws Exception {
        RuleDatabase instance = RuleDatabase.getInstance();

        assertNotNull(instance);
        assertTrue(instance.isEmpty());
        assertNull(instance.lookup("example.com"));
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
        assertEquals("::1.example.com", RuleDatabase.parseLine("::1.example.com "));
        assertEquals("0.0.0.0.example.com", RuleDatabase.parseLine("0.0.0.0.example.com "));
        assertEquals("example.com", RuleDatabase.parseLine("127.0.0.1 example.com "));
        assertEquals("example.com", RuleDatabase.parseLine("127.0.0.1 example.com\t"));
        assertEquals("example.com", RuleDatabase.parseLine("127.0.0.1   example.com "));
        assertEquals("example.com", RuleDatabase.parseLine("127.0.0.1\t example.com "));
        assertEquals("example.com", RuleDatabase.parseLine("::1\t example.com "));
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
    }

    @Test
    public void testLoadReader() throws Exception {
        RuleDatabase db = new RuleDatabase();
        db.nextRules = db.rules.get();

        Configuration.Item item = new Configuration.Item();

        item.location = "<some random file>";
        item.state = Configuration.Item.STATE_IGNORE;

        // Ignore. Does nothing
        assertTrue(db.loadReader(item, new StringReader("example.com")));
        assertTrue(db.isEmpty());
        assertNull(db.lookup("example.com"));

        // Deny, the host should be blocked now.
        item.state = Configuration.Item.STATE_DENY;
        assertTrue(db.loadReader(item, new StringReader("example.com")));
        assertFalse(db.isEmpty());
        assertTrue(db.lookup("example.com").isBlocked());

        // Reallow again, the entry should disappear.
        item.state = Configuration.Item.STATE_ALLOW;
        assertTrue(db.loadReader(item, new StringReader("example.com")));
        assertTrue(db.isEmpty());
        assertNull(db.lookup("example.com"));

        // Check multiple lines
        item.state = Configuration.Item.STATE_DENY;
        assertNull(db.lookup("example.com"));
        assertNull(db.lookup("foo.com"));
        assertTrue(db.loadReader(item, new StringReader("example.com\n127.0.0.1 foo.com")));
        assertFalse(db.isEmpty());
        assertTrue(db.lookup("example.com").isBlocked());
        assertTrue(db.lookup("foo.com").isBlocked());

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
        assertTrue(db.lookup("otherhost.com").isBlocked());

        // Allow again
        item.state = Configuration.Item.STATE_ALLOW;
        assertTrue(db.loadReader(item, new StringReader("invalid line\notherhost.com")));
        assertNull(db.lookup("otherhost.com").isBlocked());

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

        assertTrue(ruleDatabase.lookup("ahost.com").isBlocked());

        configuration.hosts.enabled = false;

        ruleDatabase.initialize(context);

        assertNull(ruleDatabase.lookup("ahost.com"));
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

        assertNull(ruleDatabase.lookup("ahost.com"));
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

        assertTrue(ruleDatabase.lookup("example.com").isBlocked());

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