package org.jak_linux.dns66;

import android.app.PendingIntent;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Toast;

import org.jak_linux.dns66.vpn.AdVpnService;
import org.jak_linux.dns66.vpn.Command;

public class TaskerVpnActivity extends AppCompatActivity {
    public static final int REQUEST_START_VPN = 1;
    Configuration config;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        config = FileHelper.loadCurrentSettings(this);

        Bundle bundle = getIntent().getExtras();
        if(bundle != null) {
            String status = bundle.getString("STATUS");
            Toast.makeText(this, status, Toast.LENGTH_SHORT).show();
            if (status != null) {
                if (status.equals("enable")) {
                    Intent intent = VpnService.prepare(this);
                    if (intent != null) {
                        startActivityForResult(intent, REQUEST_START_VPN);
                    } else {
                        onActivityResult(REQUEST_START_VPN, RESULT_OK, null);
                    }
                } else if(status.equals("disable")) {
                    if (AdVpnService.vpnStatus != AdVpnService.VPN_STATUS_STOPPED) {

                        Intent intent = new Intent(this, AdVpnService.class);
                        intent.putExtra("COMMAND", Command.STOP.ordinal());
                        startService(intent);
                        finish();
                    }
                }
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_START_VPN && resultCode == RESULT_CANCELED) {
            Toast.makeText(this, R.string.could_not_configure_vpn_service, Toast.LENGTH_LONG).show();
        }
        if (requestCode == REQUEST_START_VPN && resultCode == RESULT_OK) {
            Log.d("MainActivity", "onActivityResult: Starting service");
            Intent intent = new Intent(this, AdVpnService.class);
            intent.putExtra("COMMAND", Command.START.ordinal());
            intent.putExtra("NOTIFICATION_INTENT",
                    PendingIntent.getActivity(this, 0,
                            new Intent(this, MainActivity.class), 0));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && config.showNotification) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        }
        finish();
    }
}
