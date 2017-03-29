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
            priority++;
            publishProgress(hostfile.location);

            if (hostfile.state == Configuration.Item.STATE_IGNORE)
                continue;

            Cursor lastUpdatedCursor = database.database.rawQuery("select hostfile_id, last_updated_millis from hostfiles where location = ?", new String[]{hostfile.location});
            if (!lastUpdatedCursor.moveToNext())
                throw new RuntimeException("Could not find host file in database");

            /*
            if (currentTime - lastUpdatedCursor.getLong(1) <= TimeUnit.HOURS.toMillis(1))
                continue; */

            lastUpdatedCursor.close();

            database.database.beginTransactionNonExclusive();

            try {
                long hostFileId;
                long lastModifiedMillis;
                ContentValues valuesToUpdate = new ContentValues();

                valuesToUpdate.put("action", hostfile.state);
                valuesToUpdate.put("title", hostfile.title);
                valuesToUpdate.put("location", hostfile.location);
                valuesToUpdate.put("last_seen_millis", currentTime);
                valuesToUpdate.put("priority", priority);


                {
                    Cursor rowCursor = rowCursor = database.database.rawQuery("select hostfile_id, last_modified_millis from hostfiles where location = ?", new String[]{hostfile.location});
                    if (rowCursor.moveToNext()) {
                        hostFileId = rowCursor.getLong(0);
                        lastModifiedMillis = rowCursor.getLong(1);
                    } else {
                        hostFileId = database.database.insertOrThrow("hostfiles", null, valuesToUpdate);
                        lastModifiedMillis = 0;
                    }
                    rowCursor.close();
                }

                if (!hostfile.location.contains("/")) {
                    database.addHost(hostfile, hostfile.location);
                    database.database.setTransactionSuccessful();
                } else {

                    URL url = new URL(hostfile.location);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    database.database.delete("hostfiles", "location = ?", new String[]{hostfile.location});
                    Log.d(TAG, "doInBackground: Trying update for " + hostfile.location);
                    connection.setIfModifiedSince(lastModifiedMillis);
                    connection.connect();
                    if (connection.getResponseCode() == 200) {
                        valuesToUpdate.put("last_modified_millis", connection.getLastModified());
                        database.database.delete("hosts", "hostfile_id = ?", new String[]{Long.toString(hostFileId)});
                        if (database.loadReader(hostfile, new InputStreamReader(connection.getInputStream())))
                            database.database.setTransactionSuccessful();
                    } else if (connection.getResponseCode() == 304) {
                        Log.d(TAG, "doInBackground: Nothing to update for " + hostfile.location);
                        connection.getInputStream().close();
                    }
                    database.database.update("hostfiles", valuesToUpdate, "hostfile_id = ?", new String[]{Long.toString(hostFileId)});
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

        database.database.close();
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        if (context.getApplicationContext() != context)
            progressDialog = ProgressDialog.show(context, "Updating host file", "Doing stuff. Please wait...", true);
    }
}
