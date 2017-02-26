/* Copyright (C) 2016 Julian Andres Klode <jak@jak-linux.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.jak_linux.dns66;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Activity showing a list of apps that are whitelisted by the VPN.
 *
 * @author Braden Farmer
 */
public class WhitelistActivity extends AppCompatActivity {

    private AppListGenerator appListGenerator;
    private ProgressBar progressBar;
    private ListView appList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_whitelist);
        setFinishOnTouchOutside(false);
        setTitle(getString(R.string.action_whitelist));

        progressBar = (ProgressBar) findViewById(R.id.progress_bar);
        appList = (ListView) findViewById(R.id.list);

        appListGenerator = new AppListGenerator();
        appListGenerator.execute();
    }

    @Override
    public void finish() {
        if (appListGenerator != null && appListGenerator.getStatus() == AsyncTask.Status.RUNNING)
            appListGenerator.cancel(true);

        FileHelper.writeSettings(this, MainActivity.config);

        super.finish();
    }

    private class AppListAdapter extends ArrayAdapter<ListEntry> {
        AppListAdapter(Context context, int layout, List<ListEntry> list) {
            super(context, layout, list);
        }

        @Override
        public View getView(int position, View convertView, final ViewGroup parent) {
            // Check if an existing view is being reused, otherwise inflate the view
            if (convertView == null)
                convertView = LayoutInflater.from(getContext()).inflate(R.layout.whitelist_row, parent, false);

            final ListEntry entry = getItem(position);

            ImageView iconView = (ImageView) convertView.findViewById(R.id.app_icon);
            iconView.setImageDrawable(entry.getIcon());

            TextView textView = (TextView) convertView.findViewById(R.id.name);
            textView.setText(entry.getLabel());


            TextView details = (TextView) convertView.findViewById(R.id.details);
            details.setText(entry.getPackageName());

            final Switch checkBox = (Switch) convertView.findViewById(R.id.checkbox);
            checkBox.setChecked(MainActivity.config.whitelist.items.contains(entry.getPackageName()));

            View layout = convertView.findViewById(R.id.entry);
            layout.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (MainActivity.config.whitelist.items.contains(entry.getPackageName())) {
                        MainActivity.config.whitelist.items.remove(entry.getPackageName());
                        checkBox.setChecked(false);
                    } else {
                        MainActivity.config.whitelist.items.add(entry.getPackageName());
                        checkBox.setChecked(true);
                    }
                }
            });

            return convertView;
        }
    }

    private final class AppListGenerator extends AsyncTask<Void, Void, AppListAdapter> {
        @Override
        protected AppListAdapter doInBackground(Void... params) {
            final PackageManager pm = getPackageManager();

            List<ApplicationInfo> info = pm.getInstalledApplications(0);

            Collections.sort(info, new ApplicationInfo.DisplayNameComparator(pm));

            final List<ListEntry> entries = new ArrayList<>();
            for (ApplicationInfo appInfo : info) {
                if (!appInfo.packageName.equals(BuildConfig.APPLICATION_ID))
                    entries.add(new ListEntry(
                            appInfo.loadIcon(pm),
                            appInfo.packageName,
                            appInfo.loadLabel(pm).toString()));
            }

            return new AppListAdapter(WhitelistActivity.this, R.layout.whitelist_row, entries);
        }

        @Override
        protected void onPostExecute(AppListAdapter adapter) {
            progressBar.setVisibility(View.GONE);
            appList.setAdapter(adapter);
            setFinishOnTouchOutside(true);
        }
    }

    private class ListEntry {
        private Drawable icon;
        private String packageName;
        private String label;

        private ListEntry(Drawable icon, String packageName, String label) {
            this.icon = icon;
            this.packageName = packageName;
            this.label = label;
        }

        private String getPackageName() {
            return packageName;
        }

        private String getLabel() {
            return label;
        }

        private Drawable getIcon() {
            return icon;
        }
    }
}