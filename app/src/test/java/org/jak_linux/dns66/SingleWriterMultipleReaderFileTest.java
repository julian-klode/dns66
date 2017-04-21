package org.jak_linux.dns66;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.powermock.api.mockito.PowerMockito.*;


/**
 * Created by jak on 08/04/17.
 */
@RunWith(PowerMockRunner.class)
public class SingleWriterMultipleReaderFileTest {
    private SingleWriterMultipleReaderFile reader;
    private File activeFile;
    private File workFile;
    private FileOutputStream fos;

    @Before
    public void init() {
        reader = mock(SingleWriterMultipleReaderFile.class);
        activeFile = mock(File.class);
        workFile = mock(File.class);
        fos = mock(FileOutputStream.class);
        reader.activeFile = activeFile;
        reader.workFile = workFile;
    }

    @Test
    public void testOpenRead() throws Exception {
        when(reader.openRead()).thenCallRealMethod();


        System.err.println("Foo " + reader);
    }

    @Test
    @PrepareForTest({FileOutputStream.class, SingleWriterMultipleReaderFile.class})
    public void testStartWrite_success() throws Exception {
        when(reader.startWrite()).thenCallRealMethod();
        when(workFile.exists()).thenReturn(false);
        when(workFile.getPath()).thenReturn("/nonexisting/path/for/dns66");
        whenNew(FileOutputStream.class).withAnyArguments().thenReturn(fos);
        assertSame(fos, reader.startWrite());
    }

    @Test
    public void testStartWrite_failDelete() throws Exception {
        when(reader.startWrite()).thenCallRealMethod();

        when(workFile.exists()).thenReturn(true);
        when(workFile.delete()).thenReturn(false);

        try {
            reader.startWrite();
            fail("Failure to delete work file not detected");
        } catch (IOException e) {
            // pass
        }
    }

    @Test
    public void testFinishWrite_renameFailure() throws Exception {
        doCallRealMethod().when(reader).finishWrite(any(FileOutputStream.class));
        doNothing().when(reader).failWrite(any(FileOutputStream.class));

        when(workFile.renameTo(activeFile)).thenReturn(false);

        try {
            reader.finishWrite(fos);
            Mockito.verify(fos).close();
            Mockito.verify(reader).failWrite(fos);
            fail("Failing rename should fail finish");
        } catch (IOException e) {
            assertTrue(e.getMessage() + "wrong", e.getMessage().contains("Cannot commit"));
        }
    }

    @Test
    public void testFinishWrite_closeFailure() throws Exception {
        doCallRealMethod().when(reader).finishWrite(any(FileOutputStream.class));
        doNothing().when(reader).failWrite(any(FileOutputStream.class));

        doThrow(new IOException("Not closing")).when(fos).close();
        when(workFile.renameTo(activeFile)).thenReturn(true);

        try {
            reader.finishWrite(fos);
            Mockito.verify(fos).close();
            Mockito.verify(reader).failWrite(fos);
            fail("Failing close should fail finish");
        } catch (IOException e) {
            assertTrue(e.getMessage() + "wrong", e.getMessage().contains("Not closing"));
        }
    }

    @Test
    public void testFinishWrite_success() throws Exception {
        doCallRealMethod().when(reader).finishWrite(any(FileOutputStream.class));

        when(workFile.renameTo(activeFile)).thenReturn(true);

        try {
            reader.finishWrite(fos);
            Mockito.verify(fos).close();
        } catch (IOException e) {
            fail("Successful rename commits transaction");
        }
    }

    @Test
    public void testFailWrite() throws Exception {
        doCallRealMethod().when(reader).finishWrite(any(FileOutputStream.class));
        doCallRealMethod().when(reader).failWrite(any(FileOutputStream.class));

        when(workFile.delete()).thenReturn(true);
        try {
            reader.failWrite(fos);
        } catch (Exception e) {
            fail("Should not throw");
        }

        when(workFile.delete()).thenReturn(false);
        try {
            reader.failWrite(fos);
            fail("Should throw");
        } catch (Exception e) {
            // pass
        }
    }

}