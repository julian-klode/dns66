package org.jak_linux.dns66;

import android.content.Context;
import android.util.JsonReader;
import android.util.JsonWriter;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

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
        try {
            JsonWriter writer = new JsonWriter(new OutputStreamWriter(FileHelper.openWrite(context, "settings.json")));
            config.write(writer);
            writer.close();
        } catch (IOException e) {
            Toast.makeText(context, context.getString(R.string.cannot_write_config, e.getLocalizedMessage()), Toast.LENGTH_SHORT).show();
        }
    }

}
