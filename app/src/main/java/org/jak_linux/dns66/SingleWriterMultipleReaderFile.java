/* Copyright (C) 2017 Julian Andres Klode <jak@jak-linux.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.jak_linux.dns66;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * A file that multiple readers can safely read from and a single
 * writer thread can safely write too, without any synchronisation.
 * <p>
 * Implements the same API as AtomicFile, but avoids modifications
 * in openRead(), so it is safe to open files for reading while
 * writing, without losing the writes.
 * <p>
 * It uses two files: The specified one, and a work file with a suffix. On
 * failure, the work file is deleted; on success, it rename()ed to the specified
 * one, causing it to replace that atomically.
 */
public class SingleWriterMultipleReaderFile {

    File activeFile;
    File workFile;

    public SingleWriterMultipleReaderFile(File file) {
        activeFile = file.getAbsoluteFile();
        workFile = new File(activeFile.getAbsolutePath() + ".dns66-new");
    }

    /**
     * Opens the known-good file for reading.
     * @return A {@link FileInputStream} to read from
     * @throws FileNotFoundException See {@link FileInputStream}
     */
    public InputStream openRead() throws FileNotFoundException {
        return new FileInputStream(activeFile);
    }

    /**
     * Starts a write.
     * @return A writable stream.
     * @throws IOException If the work file cannot be replaced or opened for writing.
     */
    public FileOutputStream startWrite() throws IOException {
        if (workFile.exists() && !workFile.delete())
            throw new IOException("Cannot delete working file");

        return new FileOutputStream(workFile);
    }

    /**
     * Atomically replaces the active file with the work file, and closes the stream.
     * @param stream
     * @throws IOException
     */
    public void finishWrite(FileOutputStream stream) throws IOException {
        try {
            stream.close();
        } catch (IOException e) {
            failWrite(stream);
            throw e;
        }

        if (!workFile.renameTo(activeFile)) {
            failWrite(stream);
            throw new IOException("Cannot commit transaction");
        }
    }

    /**
     * Atomically replaces the active file with the work file, and closes the stream.
     * @param stream
     * @throws IOException
     */
    public void failWrite(FileOutputStream stream) throws IOException {
        FileHelper.closeOrWarn(stream, "SingleWriterMultipleReaderFile", "Cannot close working file");
        if (!workFile.delete())
            throw new IOException("Cannot delete working file");
    }
}
