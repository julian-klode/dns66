package org.jak_linux.dns66.db;

import android.util.Log;

import org.jak_linux.dns66.BuildConfig;
import org.jak_linux.dns66.Configuration;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.rule.PowerMockRule;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.CharBuffer;

import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.powermock.api.mockito.PowerMockito.*;

@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class, sdk = 25, manifest = "/app/src/main/AndroidManifest.xml")
@PowerMockIgnore({"org.mockito.*", "org.robolectric.*", "android.*"})
@PrepareForTest(Log.class)
public class RuleDatabaseUnitTest {
    @Rule
    public PowerMockRule rule = new PowerMockRule();

    @Before
    public void setUp() {
        // use Mockito to set up your expectation
        //Mockito.when(Log.d(param, msg)).thenReturn(0);
        //Mockito.when(Log.d(tag, msg, throwable)).thenReturn(0);
        mockStatic(Log.class);
    }

    @Test
    public void testParseLine() throws Exception {
        // Standard format lines
        assertEquals("example.com", RuleDatabase.parseLine("0.0.0.0 example.com"));
        assertEquals("example.com", RuleDatabase.parseLine("127.0.0.1 example.com"));
        assertEquals("example.com", RuleDatabase.parseLine("example.com"));
        // Comments
        assertEquals("example.com", RuleDatabase.parseLine("example.com # foo"));
        assertEquals("example.com", RuleDatabase.parseLine("0.0.0.0 example.com # foo"));
        // Check lower casing
        assertEquals("example.com", RuleDatabase.parseLine("example.cOm"));
        assertEquals("example.com", RuleDatabase.parseLine("127.0.0.1 example.cOm"));
        // Invalid lines
        assertNull(RuleDatabase.parseLine("invalid example.com"));
        assertNull(RuleDatabase.parseLine("invalid long line"));
        assertNull(RuleDatabase.parseLine("# comment line"));
        assertNull(RuleDatabase.parseLine(""));
    }

    @Test
    public void testLoadReader() throws Exception {
        RuleDatabase db = new RuleDatabase();

        db.setDatabaseForTesting(new RuleDatabaseHelper(RuntimeEnvironment.application).getWritableDatabase());

        Configuration.Item item = new Configuration.Item();

        item.location = "<some random file>";
        item.state = Configuration.Item.STATE_IGNORE;

        // Ignore. Does nothing
        db.loadReader(item, new StringReader("example.com"));
        assertTrue(db.isEmpty());
        assertFalse(db.isBlocked("example.com"));

        // Deny, the host should be blocked now.
        item.state = Configuration.Item.STATE_DENY;
        db.createOrUpdateItem(item, 0);
        db.loadReader(item, new StringReader("example.com"));
        assertFalse(db.isEmpty());
        assertTrue(db.isBlocked("example.com"));

        // Reallow again, the entry should disappear.
        item.state = Configuration.Item.STATE_ALLOW;
        db.createOrUpdateItem(item, 0);
        db.loadReader(item, new StringReader("example.com"));
        assertFalse(db.isBlocked("example.com"));

        // Check multiple lines
        item.state = Configuration.Item.STATE_DENY;
        assertFalse(db.isBlocked("example.com"));
        assertFalse(db.isBlocked("foo.com"));
        db.createOrUpdateItem(item, 0);
        db.loadReader(item, new StringReader("example.com\n127.0.0.1 foo.com"));
        assertTrue(db.isBlocked("foo.com"));

        // Interrupted test
        Thread.currentThread().interrupt();
        try {
            db.createOrUpdateItem(item, 0);
            db.loadReader(item, new StringReader("example.com"));
            fail("Interrupted thread did not cause reader to be interrupted");
        } catch (InterruptedException e) {

        }

        // Test with an invalid line before a valid one.
        item.state = Configuration.Item.STATE_DENY;
        db.createOrUpdateItem(item, 0);
        db.loadReader(item, new StringReader("invalid line\notherhost.com"));
        assertTrue(db.isBlocked("otherhost.com"));

        // Allow again
        item.state = Configuration.Item.STATE_ALLOW;
        db.createOrUpdateItem(item, 0);
        db.loadReader(item, new StringReader("invalid line\notherhost.com"));
        assertFalse(db.isBlocked("otherhost.com"));


        Reader reader = Mockito.mock(Reader.class);
        doThrow(new IOException()).when(reader).read((char[]) any());
        doThrow(new IOException()).when(reader).read((char[]) any(), anyInt(), anyInt());
        doThrow(new IOException()).when(reader).read(any(CharBuffer.class));
        when(Log.e(anyString(), anyString(), any(Throwable.class))).thenThrow(new FooException());

        try {
            db.createOrUpdateItem(item, 0);
            db.loadReader(item, reader);
            fail("No error logged when the file could not be read");
        } catch (FooException e) {
        }
    }


    public static class FooException extends RuntimeException {
    }
}