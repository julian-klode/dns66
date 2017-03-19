package org.jak_linux.dns66;

import android.util.Log;

import org.jak_linux.dns66.db.RuleDatabase;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.CharBuffer;

import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.powermock.api.mockito.PowerMockito.*;

@RunWith(PowerMockRunner.class)
@PrepareForTest(Log.class)
public class RuleDatabaseUnitTest {

    @Before
    public void setUp() {
        PowerMockito.mockStatic(Log.class);
        // use Mockito to set up your expectation
        //Mockito.when(Log.d(param, msg)).thenReturn(0);
        //Mockito.when(Log.d(tag, msg, throwable)).thenReturn(0);
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

        Configuration.Item item = new Configuration.Item();

        item.location = "<some random file>";
        item.state = Configuration.Item.STATE_IGNORE;

        // Ignore. Does nothing
        db.loadReader(item, new StringReader("example.com"));
        assertTrue(db.isEmpty());
        assertFalse(db.isBlocked("example.com"));

        // Deny, the host should be blocked now.
        item.state = Configuration.Item.STATE_DENY;
        db.loadReader(item, new StringReader("example.com"));
        assertFalse(db.isEmpty());
        assertTrue(db.isBlocked("example.com"));

        // Reallow again, the entry should disappear.
        item.state = Configuration.Item.STATE_ALLOW;
        db.loadReader(item, new StringReader("example.com"));
        assertTrue(db.isEmpty());
        assertFalse(db.isBlocked("example.com"));

        // Check multiple lines
        item.state = Configuration.Item.STATE_DENY;
        assertFalse(db.isBlocked("example.com"));
        assertFalse(db.isBlocked("foo.com"));
        db.loadReader(item, new StringReader("example.com\n127.0.0.1 foo.com"));
        assertFalse(db.isEmpty());
        assertTrue(db.isBlocked("example.com"));
        assertTrue(db.isBlocked("foo.com"));

        // Interrupted test
        Thread.currentThread().interrupt();
        try {
            db.loadReader(item, new StringReader("example.com"));
            fail("Interrupted thread did not cause reader to be interrupted");
        } catch (InterruptedException e) {

        }

        // Test with an invalid line before a valid one.
        item.state = Configuration.Item.STATE_DENY;
        db.loadReader(item, new StringReader("invalid line\notherhost.com"));
        assertTrue(db.isBlocked("otherhost.com"));

        // Allow again
        item.state = Configuration.Item.STATE_ALLOW;
        db.loadReader(item, new StringReader("invalid line\notherhost.com"));
        assertFalse(db.isBlocked("otherhost.com"));


        Reader reader = Mockito.mock(Reader.class);
        doThrow(new IOException()).when(reader).read((char[]) any());
        doThrow(new IOException()).when(reader).read((char[]) any(), anyInt(), anyInt());
        doThrow(new IOException()).when(reader).read(any(CharBuffer.class));
        when(Log.e(anyString(), anyString(), any(Throwable.class))).thenThrow(new FooException());

        try {
            db.loadReader(item, reader);
            fail("No error logged when the file could not be read");
        } catch (FooException e) {
        }
    }


    public static class FooException extends RuntimeException {
    }
}