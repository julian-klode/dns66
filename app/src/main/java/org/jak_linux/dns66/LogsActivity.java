package org.jak_linux.dns66;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class LogsActivity extends AppCompatActivity {

    String currentLogcat = null;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_logs);

        viewLogcat();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.logcat, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_send_logcat) {
            sendLogcat();
        }
        return super.onOptionsItemSelected(item);
    }

    private void viewLogcat() {
        try {
            String logcat = getLogcatOutput();

            if (logcat == null || logcat.trim().isEmpty()) {
                Toast.makeText(this, R.string.logcat_empty, Toast.LENGTH_SHORT).show();
                return;
            }

            currentLogcat = logcat;

            ((TextView) findViewById(R.id.logs_output)).setText(logcat);

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Not supported: " + e, Toast.LENGTH_LONG).show();
        }
    }

    private String getLogcatOutput() throws IOException {
        Process process = null;
        try {
            process = Runtime.getRuntime().exec("logcat -d");
            InputStream is = process.getInputStream();
            BufferedReader bis = new BufferedReader(new InputStreamReader(is));
            StringBuilder logcat = new StringBuilder();
            String line;
            while ((line = bis.readLine()) != null) {
                logcat.append(line);
                logcat.append('\n');
            }
            return logcat.toString();
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    private void sendLogcat() {
        if (currentLogcat == null || currentLogcat.trim().isEmpty()) {
            Toast.makeText(this, R.string.logcat_empty, Toast.LENGTH_SHORT).show();
            return;
        }

        Intent eMailIntent = new Intent(Intent.ACTION_SEND);
        eMailIntent.setType("text/plain");
        eMailIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{"jak@jak-linux.org"});
        eMailIntent.putExtra(Intent.EXTRA_SUBJECT, "DNS66 Logcat");
        eMailIntent.putExtra(Intent.EXTRA_TEXT, currentLogcat);
        eMailIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(eMailIntent);
    }
}
