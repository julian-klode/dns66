package org.jak_linux.dns66;

import android.content.Context;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructPollfd;
import android.util.JsonReader;
import android.util.JsonWriter;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Created by jak on 15/10/16.
 */

public final class FileHelper {

    public static InputStream openRead(Context context, String filename) throws IOException {
        try {
            return context.openFileInput(filename);
        } catch (FileNotFoundException e) {
            return context.getAssets().open(filename);
        }
    }

    public static OutputStream openWrite(Context context, String filename) throws IOException {
        File out = context.getFileStreamPath(filename);

        // Create backup
        out.renameTo(context.getFileStreamPath(filename + ".bak"));

        return context.openFileOutput(filename, Context.MODE_PRIVATE);
    }

    private static Configuration readConfigFile(Context context, String name, boolean defaultsOnly) throws IOException {
        InputStream stream;
        if (defaultsOnly)
            stream = context.getAssets().open(name);
        else
            stream = FileHelper.openRead(context, name);
        Configuration config = new Configuration();
        config.read(new JsonReader(new InputStreamReader(stream)));
        return config;
    }

    public static Configuration loadCurrentSettings(Context context) {
        try {
            return readConfigFile(context, "settings.json", false);
        } catch (Exception e) {
            Toast.makeText(context, context.getString(R.string.cannot_read_config, e.getLocalizedMessage()), Toast.LENGTH_LONG).show();
            return loadPreviousSettings(context);
        }
    }

    public static Configuration loadPreviousSettings(Context context) {
        try {
            return readConfigFile(context, "settings.json.bak", false);
        } catch (Exception e) {
            Toast.makeText(context, context.getString(R.string.cannot_restore_previous_config, e.getLocalizedMessage()), Toast.LENGTH_LONG).show();
            return loadDefaultSettings(context);
        }
    }

    public static Configuration loadDefaultSettings(Context context) {
        try {
            return readConfigFile(context, "settings.json", true);
        } catch (Exception e) {
            Toast.makeText(context, context.getString(R.string.cannot_load_default_config, e.getLocalizedMessage()), Toast.LENGTH_LONG).show();
            return null;
        }
    }

    public static void writeSettings(Context context, Configuration config) {
        Log.d("FileHelper", "writeSettings: Writing the settings file");
        try {
            JsonWriter writer = new JsonWriter(new OutputStreamWriter(FileHelper.openWrite(context, "settings.json")));
            config.write(writer);
            writer.close();
        } catch (IOException e) {
            Toast.makeText(context, context.getString(R.string.cannot_write_config, e.getLocalizedMessage()), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Returns a file where the item should be downloaded to.
     * @param context
     * @param item
     * @return File or null, if that item is not downloadable.
     */
    public static File getItemFile(Context context, Configuration.Item item) {
        if (!item.location.contains("/"))
            return null;

        try {
            return new File(context.getExternalFilesDir(null), java.net.URLEncoder.encode(item.location, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static int poll(StructPollfd[] fds, int timeout) throws ErrnoException {
        while (true) {
            try {
                return Os.poll(fds, timeout);
            } catch (ErrnoException e) {
                if (e.errno == OsConstants.EINTR)
                    continue;
                throw e;
            }
        }
    }

}
