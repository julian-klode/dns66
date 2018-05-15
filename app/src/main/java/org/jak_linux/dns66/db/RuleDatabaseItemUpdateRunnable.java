/* Copyright (C) 2017 Julian Andres Klode <jak@jak-linux.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.jak_linux.dns66.db;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.util.Log;

import org.jak_linux.dns66.Configuration;
import org.jak_linux.dns66.FileHelper;
import org.jak_linux.dns66.R;
import org.jak_linux.dns66.SingleWriterMultipleReaderFile;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.Date;

/**
 * Updates a single item.
 */
class RuleDatabaseItemUpdateRunnable implements Runnable {
    private static final int CONNECT_TIMEOUT_MILLIS = 30000;
    private static final int READ_TIMEOUT_MILLIS = 10000;
    private static final String TAG = "RuleDbItemUpdate";

    RuleDatabaseUpdateTask parentTask;
    Configuration.Item item;
    Context context;
    private URL url;
    private File file;


    RuleDatabaseItemUpdateRunnable(@NonNull RuleDatabaseUpdateTask parentTask, @NonNull Context context, @NonNull Configuration.Item item) {
        this.parentTask = parentTask;
        this.context = context;
        this.item = item;
    }

    boolean shouldDownload() {
        if (item.state == Configuration.Item.STATE_IGNORE) {
            return false;
        }

        // Not sure if that is slow or not.
        if (item.location.startsWith("content:/")) {
            return true;
        }

        file = FileHelper.getItemFile(context, item);
        if (file == null || !item.isDownloadable()) {
            return false;
        }

        try {
            url = new URL(item.location);
        } catch (MalformedURLException e) {
            parentTask.addError(item, context.getString(R.string.invalid_url_s, item.location));
            return false;
        }

        return true;
    }

    /**
     * Runs the item download, and marks it as done when finished.getLocalizedMessage
     */
    @Override
    public void run() {
        try {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
        } catch (UnsatisfiedLinkError e) {
        } catch (RuntimeException e) {
            if (!e.toString().contains("not mocked"))
                throw e;
        }

        if (item.location.startsWith("content:/")) {
            try {
                Uri uri = parseUri(item.location);
                context.getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);

                context.getContentResolver().openInputStream(uri).close();
                Log.d(TAG, "run: Permission requested for " + item.location);
            } catch (SecurityException e) {
                Log.d(TAG, "doInBackground: Error taking permission: ", e);
                parentTask.addError(item, context.getString(R.string.permission_denied));
            } catch (FileNotFoundException e) {
                parentTask.addError(item, context.getString(R.string.file_not_found));
            } catch (IOException e) {
                parentTask.addError(item, context.getString(R.string.unknown_error_s, e.getLocalizedMessage()));
            }
            return;
        }

        SingleWriterMultipleReaderFile singleWriterMultipleReaderFile = new SingleWriterMultipleReaderFile(file);
        HttpURLConnection connection = null;
        parentTask.addBegin(item);
        try {
            connection = getHttpURLConnection(file, singleWriterMultipleReaderFile, url);

            if (!validateResponse(connection))
                return;
            downloadFile(file, singleWriterMultipleReaderFile, connection);
        } catch (SocketTimeoutException e) {
            parentTask.addError(item, context.getString(R.string.requested_timed_out));
        }catch (IOException e) {
            parentTask.addError(item, context.getString(R.string.unknown_error_s, e.toString()));
        } finally {
            parentTask.addDone(item);
            if (connection != null)
                connection.disconnect();
        }
    }

    /**
     * Internal helper for testing
     */
    Uri parseUri(String uri) {
        return Uri.parse(uri);
    }

    /**
     * Opens a new HTTP connection.
     *
     * @param file                           Target file
     * @param singleWriterMultipleReaderFile Target file
     * @param url                            URL to download from
     * @return An initialized HTTP connection.
     * @throws IOException
     */
    @NonNull
    HttpURLConnection getHttpURLConnection(File file, SingleWriterMultipleReaderFile singleWriterMultipleReaderFile, URL url) throws IOException {
        HttpURLConnection connection = internalOpenHttpConnection(url);
        connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
        connection.setReadTimeout(READ_TIMEOUT_MILLIS);
        try {
            singleWriterMultipleReaderFile.openRead().close();
            connection.setIfModifiedSince(file.lastModified());
        } catch (IOException e) {
            // Ignore addError here
        }

        connection.connect();
        return connection;
    }

    // Internal helper for testing.
    HttpURLConnection internalOpenHttpConnection(URL url) throws IOException {
        return (HttpURLConnection) url.openConnection();
    }

    /**
     * Checks if we should read from the URL.
     *
     * @param connection The connection that was established.
     * @return true if there was no problem.
     * @throws IOException If an I/O Exception occured.
     */
    boolean validateResponse(HttpURLConnection connection) throws IOException {
        Log.d(TAG, "validateResponse: " + item.title + ": local = " + new Date(connection.getIfModifiedSince()) + " remote = " + new Date(connection.getLastModified()));
        if (connection.getResponseCode() != 200) {
            Log.d(TAG, "validateResponse: " + item.title + ": Skipping: Server responded with " + connection.getResponseCode() + " for " + item.location);

            if (connection.getResponseCode() == 404) {
                parentTask.addError(item, context.getString(R.string.file_not_found));
            } else if (connection.getResponseCode() != 304) {
                context.getResources().getString(R.string.host_update_error_item);
                parentTask.addError(item, context.getResources().getString(R.string.host_update_error_item, connection.getResponseCode(), connection.getResponseMessage()));
            }
            return false;
        }
        return true;
    }

    /**
     * Downloads a file from a connection to an singleWriterMultipleReaderFile.
     *
     * @param file                           The file to write to
     * @param singleWriterMultipleReaderFile The atomic file for the destination file
     * @param connection                     The connection to read from
     * @throws IOException I/O exceptions.
     */
    void downloadFile(File file, SingleWriterMultipleReaderFile singleWriterMultipleReaderFile, HttpURLConnection connection) throws IOException {
        InputStream inStream = connection.getInputStream();
        FileOutputStream outStream = singleWriterMultipleReaderFile.startWrite();

        try {
            copyStream(inStream, outStream);

            singleWriterMultipleReaderFile.finishWrite(outStream);
            outStream = null;
            // Write has started, set modification time.
            if (connection.getLastModified() == 0 || !file.setLastModified(connection.getLastModified())) {
                Log.d(TAG, "downloadFile: Could not set last modified");
            }
        } finally {
            if (outStream != null)
                singleWriterMultipleReaderFile.failWrite(outStream);
        }
    }

    /**
     * Copies one stream to another.
     *
     * @param inStream  Input stream
     * @param outStream Output stream
     * @throws IOException If an exception occured.
     */
    void copyStream(InputStream inStream, OutputStream outStream) throws IOException {
        byte[] buffer = new byte[4096];
        int read;
        while ((read = inStream.read(buffer)) != -1) {
            outStream.write(buffer, 0, read);
        }
    }
}
