package org.jak_linux.dns66.db;

import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.os.AsyncTask;
import android.util.Log;

import org.jak_linux.dns66.Configuration;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.TimeUnit;

/**
 * Created by jak on 23/03/17.
 */

public class DatabaseUpdateTask extends AsyncTask<Configuration, String, Void> {

    private static final String TAG = "DatabaseUpdateTask";
    RuleDatabase database;
    Context context;
    ProgressDialog progressDialog;

    public DatabaseUpdateTask(Context context, RuleDatabase database) {
        this.database = database;
        this.context = context;
    }

    @Override
    protected Void doInBackground(Configuration... configurations) {
        long priority = -1;
        long currentTime = System.currentTimeMillis();

        database.markReachable(configurations[0], currentTime);

        // Update entries
        for (Configuration.Item hostfile : configurations[0].hosts.items) {
            Cursor rowCursor = null;
            priority++;
            publishProgress(hostfile.location);

            if (hostfile.state == Configuration.Item.STATE_IGNORE)
                continue;
            database.database.beginTransactionNonExclusive();

            try {
                ContentValues valuesToUpdate = new ContentValues();

                valuesToUpdate.put("last_updated_millis", currentTime);
                valuesToUpdate.put("last_seen_millis", currentTime);

                database.database.delete("hostfiles", "location = ?", new String[]{hostfile.location});
                if (!database.createOrUpdateItem(hostfile, priority))
                    throw new IOException("Cannot create hostfile");

                rowCursor = database.database.rawQuery("select hostfile_id, last_seen_millis from hostfiles where location = ?", new String[]{hostfile.location});
                if (!rowCursor.moveToNext())
                    throw new RuntimeException("Could not find host file in database");

                if (!hostfile.location.contains("/")) {
                    database.addHost(hostfile, hostfile.location);
                    database.database.setTransactionSuccessful();
                } else {

                    URL url = new URL(hostfile.location);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();

                    connection.setIfModifiedSince(rowCursor.getLong(1));
                    connection.connect();

                    valuesToUpdate.put("last_modified_millis", connection.getLastModified());
                    if (database.loadReader(hostfile, new InputStreamReader(connection.getInputStream())))
                        database.database.setTransactionSuccessful();
                }
            } catch (MalformedURLException e) {
                Log.w(TAG, "doInBackground: Could not update", e);
            } catch (InterruptedException e) {
                Log.d(TAG, "doInBackground: Interrupted", e);
                break;
            } catch (IOException e) {
                Log.w(TAG, "doInBackground: Could not update", e);
            } finally {
                database.database.endTransaction();
            }
        }

        database.sweepUnreachable(configurations[0], currentTime);

        return null;
    }

    @Override
    protected void onProgressUpdate(String... values) {
        super.onProgressUpdate(values);

        if (progressDialog != null)
            progressDialog.setMessage("Updating " + values[0]);
    }

    @Override
    protected void onPostExecute(Void aVoid) {
        super.onPostExecute(aVoid);
        if (progressDialog != null)
            progressDialog.dismiss();
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        if (context.getApplicationContext() != context)
            progressDialog = ProgressDialog.show(context, "Updating host file", "Doing stuff. Please wait...", true);
    }
}
