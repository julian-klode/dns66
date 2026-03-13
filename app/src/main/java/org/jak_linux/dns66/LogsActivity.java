package org.jak_linux.dns66;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.jak_linux.dns66.vpn.DnsPacketProxy;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class LogsActivity extends AppCompatActivity {

    private final List<String> logcatOutput = new ArrayList<>(100);
    private boolean showDnsRequestsOnly = true;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_logs);

        ((TextView) findViewById(R.id.logs_output)).setHorizontallyScrolling(true);

        viewLogcat();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.logcat, menu);
        menu.findItem(R.id.option_only_show_dns_requests).setChecked(showDnsRequestsOnly);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_send_logcat:
                sendLogcat();
                break;
            case R.id.action_refresh_logcat:
                viewLogcat();
                break;
            case R.id.option_only_show_dns_requests:
                toggleDnsRequestsFilter(item);
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void toggleDnsRequestsFilter(MenuItem item) {
        showDnsRequestsOnly = !showDnsRequestsOnly;
        item.setChecked(showDnsRequestsOnly);
    }

    private void viewLogcat() {
        try {
            updateLogcatOutput();

            if (logcatOutput.isEmpty()) {
                Toast.makeText(this, R.string.logcat_empty, Toast.LENGTH_SHORT).show();
                return;
            }

            List<String> filteredLogs = logcatOutput;
            if (showDnsRequestsOnly) {
                filteredLogs = new ArrayList<>(20);
                for (String line : logcatOutput) {
                    if (line.contains(DnsPacketProxy.DNS_REQUESTS_FILTER_MESSAGE)) {
                        filteredLogs.add(line);
                    }
                }
            }

            String logsString = TextUtils.join("\n", filteredLogs);
            ((TextView) findViewById(R.id.logs_output)).setText(logsString);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Not supported: " + e, Toast.LENGTH_LONG).show();
        }
    }

    private void updateLogcatOutput() throws IOException {
        logcatOutput.clear();
        Process process = null;
        try {
            process = Runtime.getRuntime().exec("logcat -d");
            InputStream is = process.getInputStream();
            BufferedReader bis = new BufferedReader(new InputStreamReader(is));
            String line;
            while ((line = bis.readLine()) != null) {
                logcatOutput.add(line);
            }
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    private void sendLogcat() {
        if (logcatOutput.isEmpty()) {
            Toast.makeText(this, R.string.logcat_empty, Toast.LENGTH_SHORT).show();
            return;
        }

        String logsString = TextUtils.join("\n", logcatOutput);

        Intent eMailIntent = new Intent(Intent.ACTION_SEND);
        eMailIntent.setType("text/plain");
        eMailIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{"jak@jak-linux.org"});
        eMailIntent.putExtra(Intent.EXTRA_SUBJECT, "DNS66 Logcat");
        eMailIntent.putExtra(Intent.EXTRA_TEXT, logsString);
        eMailIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(eMailIntent);
    }
}
