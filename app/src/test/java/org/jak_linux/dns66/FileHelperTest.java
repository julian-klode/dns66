package org.jak_linux.dns66;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.Environment;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructPollfd;
import android.util.Log;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.Closeable;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.UnsupportedEncodingException;

import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.powermock.api.mockito.PowerMockito.*;

/**
 * Created by jak on 07/04/17.
 */
@RunWith(PowerMockRunner.class)
public class FileHelperTest {
    Context mockContext;
    AssetManager mockAssets;
    int testResult;

    @Before
    public void setUp() {
        mockContext = mock(Context.class);
        mockAssets = mock(AssetManager.class);
        testResult = 0;

        when(mockContext.getAssets()).thenReturn(mockAssets);
    }

    @Test
    @PrepareForTest({Environment.class})
    public void testGetItemFile() throws Exception {
        File file = new File("/dir/");
        when(mockContext.getExternalFilesDir(null)).thenReturn(file);

        Configuration.Item item = new Configuration.Item();
        item.location = "http://example.com/";
        assertEquals(new File("/dir/http%3A%2F%2Fexample.com%2F"), FileHelper.getItemFile(mockContext, item));

        item.location = "https://example.com/";
        assertEquals(new File("/dir/https%3A%2F%2Fexample.com%2F"), FileHelper.getItemFile(mockContext, item));

        item.location = "file:/myfile";
        assertNull(FileHelper.getItemFile(mockContext, item));

        mockStatic(Environment.class);
        when(Environment.getExternalStorageDirectory()).thenReturn(new File("/sdcard/"));

        item.location = "file:myfile";
        assertNull(null, FileHelper.getItemFile(mockContext, item));


        item.location = "ahost.com";
        assertNull(FileHelper.getItemFile(mockContext, item));
    }

    @Test
    @Ignore("The exception throwing does not work")
    public void testGetItemFile_encodingError() throws Exception {
        File file = new File("/dir/");
        when(mockContext.getExternalFilesDir(null)).thenReturn(file);

        Configuration.Item item = new Configuration.Item();
        // Test encoding fails
        item.location = "https://example.com/";
        assertEquals(new File("/dir/https%3A%2F%2Fexample.com%2F"), FileHelper.getItemFile(mockContext, item));

        // TODO: The following PowerMockito code prints the exception, but does not fail
        mockStatic(java.net.URLEncoder.class);
        when(java.net.URLEncoder.encode(anyString(), anyString())).thenThrow(new UnsupportedEncodingException("foo"));
        assertNull(FileHelper.getItemFile(mockContext, item));
    }

    @Test
    public void testOpenItemFile() throws Exception {
        Configuration.Item item = new Configuration.Item();
        // Test encoding fails
        item.location = "hexample.com";
        assertNull(FileHelper.openItemFile(mockContext, item));
    }

    @Test
    public void testOpenRead_existingFile() throws Exception {
        FileInputStream stream = mock(FileInputStream.class);
        when(mockContext.openFileInput(anyString())).thenReturn(stream);
        when(mockAssets.open(anyString())).thenThrow(new IOException());

        assertSame(stream, FileHelper.openRead(mockContext, "file"));
    }

    @Test
    public void testOpenRead_fallbackToAsset() throws Exception {
        FileInputStream stream = mock(FileInputStream.class);
        when(mockContext.openFileInput(anyString())).thenThrow(new FileNotFoundException("Test"));
        when(mockAssets.open(anyString())).thenReturn(stream);

        assertSame(stream, FileHelper.openRead(mockContext, "file"));
    }

    @Test
    public void testOpenWrite() throws Exception {
        File file = mock(File.class);
        File file2 = mock(File.class);
        FileOutputStream fos = mock(FileOutputStream.class);
        when(mockContext.getFileStreamPath(eq("filename"))).thenReturn(file);
        when(mockContext.getFileStreamPath(eq("filename.bak"))).thenReturn(file2);
        when(mockContext.openFileOutput(eq("filename"), anyInt())).thenReturn(fos);

        assertSame(fos, FileHelper.openWrite(mockContext, "filename"));

        Mockito.verify(file).renameTo(file2);
        Mockito.verify(mockContext).openFileOutput(eq("filename"), anyInt());
    }

    @Test
    @PrepareForTest({Configuration.class})
    public void testLoadDefaultSettings() throws Exception {
        InputStream mockInStream = mock(InputStream.class);
        Configuration mockConfig = mock(Configuration.class);
        when(mockAssets.open(anyString())).thenReturn(mockInStream);
        when(mockContext.getAssets()).thenReturn(mockAssets);

        mockStatic(Configuration.class);
        doReturn(mockConfig).when(Configuration.class, "read", any(Reader.class));

        assertSame(mockConfig, FileHelper.loadDefaultSettings(mockContext));

        Mockito.verify(mockAssets.open(anyString()));
    }

    @Test
    @PrepareForTest({Log.class, Os.class})
    public void testPoll_retryInterrupt() throws Exception {
        mockStatic(Log.class);
        mockStatic(Os.class);
        when(Os.poll(any(StructPollfd[].class), anyInt())).then(new Answer<Integer>() {
            @Override
            public Integer answer(InvocationOnMock invocation) throws Throwable {
                // First try fails with EINTR, seconds returns success.
                if (testResult++ == 0) {
                    // Android actually sets all OsConstants to 0 when running the
                    // unit tests, so this works, but another constant would have
                    // exactly the same result.
                    throw new ErrnoException("poll", OsConstants.EINTR);
                }
                return 0;
            }
        });

        // poll() will be interrupted first time, so called a second time.
        assertEquals(0, FileHelper.poll(null, 0));
        assertEquals(2, testResult);
    }

    @Test
    public void testPoll_interrupted() throws Exception {
        Thread.currentThread().interrupt();
        try {
            FileHelper.poll(null, 0);
            fail("Did not interrupt");
        } catch (InterruptedException e) {

        }
    }

    @Test
    @PrepareForTest({Log.class, Os.class})
    public void testPoll_fault() throws Exception {
        mockStatic(Log.class);
        mockStatic(Os.class);

        // Eww, Android is playing dirty and setting all errno values to 0.
        // Hack around it so we can test that aborting the loop works.
        final ErrnoException e = new ErrnoException("foo", 42);
        e.getClass().getDeclaredField("errno").setInt(e, 42);
        when(Os.poll(any(StructPollfd[].class), anyInt())).then(new Answer<Integer>() {
            @Override
            public Integer answer(InvocationOnMock invocation) throws Throwable {
                testResult++;
                throw e;
            }
        });

        try {
            FileHelper.poll(null, 0);
            fail("Did not throw");
        } catch (ErrnoException e1) {
            assertEquals(42, e1.errno);
            assertSame(e, e1);
        }
        assertEquals(1, testResult);
    }

    @Test
    @PrepareForTest({Log.class, Os.class})
    public void testPoll_success() throws Exception {
        mockStatic(Log.class);
        mockStatic(Os.class);
        when(Os.poll(any(StructPollfd[].class), anyInt())).then(new CountingAnswer(42));
        assertEquals(42, FileHelper.poll(null, 0));
        assertEquals(1, testResult);
    }


    @Test
    @PrepareForTest({Log.class, Os.class})
    public void testCloseOrWarn_fileDescriptor() throws Exception {
        FileDescriptor fd = mock(FileDescriptor.class);
        mockStatic(Log.class);
        mockStatic(Os.class);
        when(Log.e(anyString(), anyString(), any(Throwable.class))).then(new CountingAnswer(null));

        // Closing null should work just fine
        testResult = 0;
        assertNull(FileHelper.closeOrWarn((FileDescriptor) null, "tag", "msg"));
        assertEquals(0, testResult);

        // Successfully closing the file should not log.
        testResult = 0;
        assertNull(FileHelper.closeOrWarn(fd, "tag", "msg"));
        assertEquals(0, testResult);

        // If closing fails, it should log.
        testResult = 0;
        doThrow(new ErrnoException("close", 0)).when(Os.class, "close", any(FileDescriptor.class));
        assertNull(FileHelper.closeOrWarn(fd, "tag", "msg"));
        assertEquals(1, testResult);
    }

    @Test
    @PrepareForTest(Log.class)
    public void testCloseOrWarn_closeable() throws Exception {
        Closeable closeable = mock(Closeable.class);
        mockStatic(Log.class);
        when(Log.e(anyString(), anyString(), any(Throwable.class))).then(new CountingAnswer(null));

        // Closing null should work just fine
        testResult = 0;
        assertNull(FileHelper.closeOrWarn((Closeable) null, "tag", "msg"));
        assertEquals(0, testResult);

        // Successfully closing the file should not log.
        testResult = 0;
        assertNull(FileHelper.closeOrWarn(closeable, "tag", "msg"));
        assertEquals(0, testResult);

        // If closing fails, it should log.
        when(closeable).thenThrow(new IOException("Foobar"));

        testResult = 0;
        assertNull(FileHelper.closeOrWarn(closeable, "tag", "msg"));
        assertEquals(1, testResult);
    }

    private class CountingAnswer implements Answer<Object> {

        private final Object result;

        public CountingAnswer(Object result) {
            this.result = result;
        }

        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
            testResult++;
            return result;
        }
    }
}