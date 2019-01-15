/* Copyright (C) 2016-2019 Julian Andres Klode <jak@jak-linux.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.jak_linux.dns66;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.AppCompatDelegate;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import org.jak_linux.dns66.db.RuleDatabaseUpdateJobService;
import org.jak_linux.dns66.db.RuleDatabaseUpdateTask;
import org.jak_linux.dns66.main.FloatingActionButtonFragment;
import org.jak_linux.dns66.main.MainFragmentPagerAdapter;
import org.jak_linux.dns66.main.StartFragment;
import org.jak_linux.dns66.vpn.AdVpnService;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_FILE_OPEN = 1;
    private static final int REQUEST_FILE_STORE = 2;
    private static final int REQUEST_ITEM_EDIT = 3;
    public static Configuration config;
    private ViewPager viewPager;
    private final BroadcastReceiver vpnServiceBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int str_id = intent.getIntExtra(AdVpnService.VPN_UPDATE_STATUS_EXTRA, R.string.notification_stopped);
            updateStatus(str_id);
        }
    };

    private ItemChangedListener itemChangedListener = null;
    private MainFragmentPagerAdapter fragmentPagerAdapter;
    private FloatingActionButton floatingActionButton;
    private ViewPager.SimpleOnPageChangeListener pageChangeListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        NotificationChannels.onCreate(this);

        if (savedInstanceState == null || config == null) {
            config = FileHelper.loadCurrentSettings(this);
        }

        if (config.nightMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }

        setContentView(R.layout.activity_main);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        viewPager = (ViewPager) findViewById(R.id.view_pager);

        fragmentPagerAdapter = new MainFragmentPagerAdapter(this, getSupportFragmentManager());
        viewPager.setAdapter(fragmentPagerAdapter);

        TabLayout tabLayout = (TabLayout) findViewById(R.id.tab_layout);
        tabLayout.setupWithViewPager(viewPager);

        // Add a page change listener that sets the floating action button per tab.
        floatingActionButton = (FloatingActionButton) findViewById(R.id.floating_action_button);
        pageChangeListener = new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                Fragment fragment = getSupportFragmentManager().findFragmentByTag("android:switcher:" + viewPager.getId() + ":" + fragmentPagerAdapter.getItemId(position));
                if (fragment instanceof FloatingActionButtonFragment) {
                    ((FloatingActionButtonFragment) fragment).setupFloatingActionButton(floatingActionButton);
                    floatingActionButton.show();
                } else {
                    floatingActionButton.hide();
                }
            }
        };
        viewPager.addOnPageChangeListener(pageChangeListener);

        RuleDatabaseUpdateJobService.scheduleOrCancel(this, config);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        menu.findItem(R.id.setting_show_notification).setChecked(config.showNotification);
        menu.findItem(R.id.setting_night_mode).setChecked(config.nightMode);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        switch (item.getItemId()) {
            case R.id.action_refresh:
                refresh();
                break;
            case R.id.action_load_defaults:
                config = FileHelper.loadDefaultSettings(this);
                FileHelper.writeSettings(this, MainActivity.config);
                recreate();
                break;
            case R.id.action_import:
                Intent intent = new Intent()
                        .setType("*/*")
                        .setAction(Intent.ACTION_OPEN_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE);

                startActivityForResult(intent, REQUEST_FILE_OPEN);
                break;
            case R.id.action_export:
                Intent exportIntent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .setType("*/*")
                        .putExtra(Intent.EXTRA_TITLE, "dns66.json");

                startActivityForResult(exportIntent, REQUEST_FILE_STORE);
                break;
            case R.id.setting_night_mode:
                item.setChecked(!item.isChecked());
                MainActivity.config.nightMode = item.isChecked();
                FileHelper.writeSettings(MainActivity.this, MainActivity.config);
                recreate();
                break;
            case R.id.setting_show_notification:
                // If we are enabling notifications, we do not need to show a dialog.
                if (!item.isChecked()) {
                    item.setChecked(!item.isChecked());
                    MainActivity.config.showNotification = item.isChecked();
                    FileHelper.writeSettings(MainActivity.this, MainActivity.config);
                    break;
                }
                new AlertDialog.Builder(this)
                        .setIcon(R.drawable.ic_warning)
                        .setTitle(R.string.disable_notification_title)
                        .setMessage(R.string.disable_notification_message)
                        .setPositiveButton(R.string.disable_notification_ack, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                item.setChecked(!item.isChecked());
                                MainActivity.config.showNotification = item.isChecked();
                                FileHelper.writeSettings(MainActivity.this, MainActivity.config);
                            }
                        })
                        .setNegativeButton(R.string.disable_notification_nak, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {

                            }
                        }).show();
                break;
            case R.id.action_about:
                Intent infoIntent = new Intent(this, InfoActivity.class);
                startActivity(infoIntent);
                break;
            case R.id.action_logcat:
                sendLogcat();
                break;
        }

        return super.onOptionsItemSelected(item);
    }

    private void sendLogcat() {
        Process proc = null;
        try {
            proc = Runtime.getRuntime().exec("logcat -d");
            InputStream is = proc.getInputStream();
            BufferedReader bis = new BufferedReader(new InputStreamReader(is));
            StringBuilder logcat = new StringBuilder();
            String line;
            while ((line = bis.readLine()) != null) {
                logcat.append(line);
                logcat.append('\n');
            }

            Intent eMailIntent = new Intent(Intent.ACTION_SEND);
            eMailIntent.setType("text/plain");
            eMailIntent.putExtra(Intent.EXTRA_EMAIL, new String[] {"jak@jak-linux.org"});
            eMailIntent.putExtra(Intent.EXTRA_SUBJECT, "DNS66 Logcat");
            eMailIntent.putExtra(Intent.EXTRA_TEXT, logcat.toString());
            eMailIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(eMailIntent);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Not supported: " + e, Toast.LENGTH_LONG).show();
        } finally {
            if (proc != null)
                proc.destroy();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        Log.d("MainActivity", "onNewIntent: Wee");

        if (intent.getBooleanExtra("UPDATE", false)) {
            refresh();
        }

        List<String> errors = RuleDatabaseUpdateTask.lastErrors.getAndSet(null);
        if (errors != null && !errors.isEmpty()) {
            Log.d("MainActivity", "onNewIntent: It's an error");
            errors.add(0, getString(R.string.update_incomplete_description));
            new AlertDialog.Builder(this)
                    .setAdapter(newAdapter(errors), null)
                    .setTitle(R.string.update_incomplete)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        }
        super.onNewIntent(intent);

    }

    @NonNull
    private ArrayAdapter<String> newAdapter(final List<String> errors) {
        return new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, errors) {
            @NonNull
            @Override
            @SuppressWarnings("deprecation")
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView text1 = (TextView) view.findViewById(android.R.id.text1);
                //noinspection deprecation
                text1.setText(Html.fromHtml(errors.get(position)));
                return view;
            }
        };
    }


    private void refresh() {
        final RuleDatabaseUpdateTask task = new RuleDatabaseUpdateTask(getApplicationContext(), config, true);


        task.execute();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d("MainActivity", "onActivityResult: Received result=" + resultCode + " for request=" + requestCode);
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_FILE_OPEN && resultCode == RESULT_OK) {
            Uri selectedfile = data.getData(); //The uri with the location of the file

            try {
                config = Configuration.read(new InputStreamReader(getContentResolver().openInputStream(selectedfile)));
            } catch (Exception e) {
                Toast.makeText(this, "Cannot read file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
            recreate();
            FileHelper.writeSettings(this, MainActivity.config);
        }
        if (requestCode == REQUEST_FILE_STORE && resultCode == RESULT_OK) {
            Uri selectedfile = data.getData(); //The uri with the location of the file
            Writer writer = null;
            try {
                writer = new OutputStreamWriter(getContentResolver().openOutputStream(selectedfile));
                config.write(writer);
                writer.close();
            } catch (Exception e) {
                Toast.makeText(this, "Cannot write file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            } finally {
                try {
                    writer.close();
                } catch (Exception ignored) {

                }
            }
            recreate();
        }
        if (requestCode == REQUEST_ITEM_EDIT && resultCode == RESULT_OK) {
            Configuration.Item item = new Configuration.Item();
            Log.d("FOOOO", "onActivityResult: item title = " + data.getStringExtra("ITEM_TITLE"));
            if (data.hasExtra("DELETE")) {
                this.itemChangedListener.onItemChanged(null);
                return;
            }
            item.title = data.getStringExtra("ITEM_TITLE");
            item.location = data.getStringExtra("ITEM_LOCATION");
            item.state = data.getIntExtra("ITEM_STATE", 0);
            this.itemChangedListener.onItemChanged(item);
        }
    }

    private void updateStatus(int status) {
        if (viewPager.getChildAt(0) == null)
            return;

        StartFragment.updateStatus(viewPager.getChildAt(0).getRootView(), status);
    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(vpnServiceBroadcastReceiver);
    }

    @Override
    protected void onResume() {
        super.onResume();
        List<String> errors = RuleDatabaseUpdateTask.lastErrors.getAndSet(null);
        if (errors != null && !errors.isEmpty()) {
            Log.d("MainActivity", "onNewIntent: It's an error");
            errors.add(0, getString(R.string.update_incomplete_description));
            new AlertDialog.Builder(this)
                    .setAdapter(newAdapter(errors), null)
                    .setTitle(R.string.update_incomplete)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        }
        pageChangeListener.onPageSelected(viewPager.getCurrentItem());
        updateStatus(AdVpnService.vpnStatus);
        LocalBroadcastManager.getInstance(this)
                .registerReceiver(vpnServiceBroadcastReceiver, new IntentFilter(AdVpnService.VPN_UPDATE_STATUS_INTENT));
    }

    /**
     * Start the item editor activity
     *
     * @param item     an item to edit, may be null
     * @param listener A listener that will be called once the editor returns
     */
    public void editItem(int stateChoices, Configuration.Item item, ItemChangedListener listener) {
        Intent editIntent = new Intent(this, ItemActivity.class);

        this.itemChangedListener = listener;
        if (item != null) {
            editIntent.putExtra("ITEM_TITLE", item.title);
            editIntent.putExtra("ITEM_LOCATION", item.location);
            editIntent.putExtra("ITEM_STATE", item.state);
        }
        editIntent.putExtra("STATE_CHOICES", stateChoices);
        startActivityForResult(editIntent, REQUEST_ITEM_EDIT);
    }
}
