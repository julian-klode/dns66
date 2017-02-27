/* Copyright (C) 2016 Julian Andres Klode <jak@jak-linux.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.jak_linux.dns66.main;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;

import org.jak_linux.dns66.BuildConfig;
import org.jak_linux.dns66.FileHelper;
import org.jak_linux.dns66.MainActivity;
import org.jak_linux.dns66.R;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Activity showing a list of apps that are whitelisted by the VPN.
 *
 * @author Braden Farmer
 */
public class WhitelistFragment extends Fragment {

    private static final String TAG = "Whitelist";
    private AppListGenerator appListGenerator;
    private ListView appList;
    private SwipeRefreshLayout swipeRefresh;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        View rootView = inflater.inflate(R.layout.activity_whitelist, container, false);

        appList = (ListView) rootView.findViewById(R.id.list);

        swipeRefresh = (SwipeRefreshLayout) rootView.findViewById(R.id.swiperefresh);
        swipeRefresh.setOnRefreshListener(
                new SwipeRefreshLayout.OnRefreshListener() {
                    @Override
                    public void onRefresh() {
                        appListGenerator = new AppListGenerator();
                        appListGenerator.execute();
                    }
                }
        );
        swipeRefresh.setRefreshing(true);

        Switch switchShowSystemApps = (Switch) rootView.findViewById(R.id.switch_show_system_apps);

        switchShowSystemApps.setChecked(MainActivity.config.whitelist.showSystemApps);
        switchShowSystemApps.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                MainActivity.config.whitelist.showSystemApps = isChecked;
                FileHelper.writeSettings(getContext(), MainActivity.config);
                appListGenerator = new AppListGenerator();
                appListGenerator.execute();
            }
        });

        appListGenerator = new AppListGenerator();
        appListGenerator.execute();

        return rootView;
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

            final ImageView iconView = (ImageView) convertView.findViewById(R.id.app_icon);

            AsyncTask<ListEntry, Void, Drawable> task = (AsyncTask<ListEntry, Void, Drawable>) convertView.getTag();
            if (task != null)
                task.cancel(true);

            task = null;
            final Drawable icon = entry.getIcon();
            if (icon != null) {
                iconView.setImageDrawable(icon);
                iconView.setVisibility(View.VISIBLE);
                convertView.setTag(null);
            } else {
                iconView.setVisibility(View.INVISIBLE);

                task = new AsyncTask<ListEntry, Void, Drawable>() {
                    @Override
                    protected Drawable doInBackground(ListEntry... entries) {
                        return entries[0].loadIcon(getContext().getPackageManager());
                    }

                    @Override
                    protected void onPostExecute(Drawable drawable) {
                        if (!isCancelled()) {
                            iconView.setImageDrawable(drawable);
                            iconView.setVisibility(View.VISIBLE);
                        }
                        super.onPostExecute(drawable);
                    }
                };
                convertView.setTag(task);

                task.execute(entry);
            }

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
                    FileHelper.writeSettings(getActivity(), MainActivity.config);
                }
            });

            return convertView;
        }
    }

    private final class AppListGenerator extends AsyncTask<Void, Void, AppListAdapter> {
        private PackageManager pm;

        @Override
        protected AppListAdapter doInBackground(Void... params) {
            pm = getContext().getPackageManager();

            List<ApplicationInfo> info = pm.getInstalledApplications(0);

            Collections.sort(info, new ApplicationInfo.DisplayNameComparator(pm));

            final List<ListEntry> entries = new ArrayList<>();
            for (ApplicationInfo appInfo : info) {
                if (!appInfo.packageName.equals(BuildConfig.APPLICATION_ID) && (MainActivity.config.whitelist.showSystemApps || (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0))
                    entries.add(new ListEntry(
                            appInfo,
                            appInfo.packageName,
                            appInfo.loadLabel(pm).toString()));
            }


            return new AppListAdapter(getContext(), R.layout.whitelist_row, entries);
        }

        @Override
        protected void onPostExecute(AppListAdapter adapter) {
            appList.setAdapter(adapter);
            swipeRefresh.setRefreshing(false);
        }
    }

    private class ListEntry {
        private ApplicationInfo appInfo;
        private String packageName;
        private String label;
        private WeakReference<Drawable> weakIcon;

        private ListEntry(ApplicationInfo appInfo, String packageName, String label) {
            this.appInfo = appInfo;
            this.packageName = packageName;
            this.label = label;
        }

        private String getPackageName() {
            return packageName;
        }

        private String getLabel() {
            return label;
        }

        private ApplicationInfo getAppInfo() {
            return appInfo;
        }

        private Drawable getIcon() {
            return weakIcon != null ? weakIcon.get() : null;
        }

        private Drawable loadIcon(PackageManager pm) {
            Drawable icon = weakIcon != null ? weakIcon.get() : null;
            if (icon == null) {
                icon = appInfo.loadIcon(pm);
                weakIcon = new WeakReference<>(icon);
            }
            return icon;
        }
    }
}