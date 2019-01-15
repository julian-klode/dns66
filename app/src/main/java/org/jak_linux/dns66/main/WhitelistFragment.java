/* Copyright (C) 2016-2019 Julian Andres Klode <jak@jak-linux.org>
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
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.Switch;
import android.widget.TextView;

import org.jak_linux.dns66.BuildConfig;
import org.jak_linux.dns66.Configuration;
import org.jak_linux.dns66.FileHelper;
import org.jak_linux.dns66.MainActivity;
import org.jak_linux.dns66.R;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Activity showing a list of apps that are whitelisted by the VPN.
 *
 * @author Braden Farmer
 */
public class WhitelistFragment extends Fragment {

    private static final String TAG = "Whitelist";
    private AppListGenerator appListGenerator;
    private RecyclerView appList;
    private SwipeRefreshLayout swipeRefresh;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final View rootView = inflater.inflate(R.layout.activity_whitelist, container, false);

        appList = (RecyclerView) rootView.findViewById(R.id.list);
        appList.setHasFixedSize(true);

        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(getContext());
        appList.setLayoutManager(layoutManager);

        DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(appList.getContext(),
                DividerItemDecoration.VERTICAL);
        appList.addItemDecoration(dividerItemDecoration);


        swipeRefresh = (SwipeRefreshLayout) rootView.findViewById(R.id.swiperefresh);
        swipeRefresh.setOnRefreshListener(
                new SwipeRefreshLayout.OnRefreshListener() {
                    @Override
                    public void onRefresh() {
                        appListGenerator = new AppListGenerator(getContext());
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
                appListGenerator = new AppListGenerator(getContext());
                appListGenerator.execute();
            }
        });

        final TextView whitelistDefaultText = (TextView) rootView.findViewById(R.id.whitelist_default_text);
        whitelistDefaultText.setText(getResources().getStringArray(R.array.whitelist_defaults)[MainActivity.config.whitelist.defaultMode]);
        View.OnClickListener onDefaultChangeClicked = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PopupMenu menu = new PopupMenu(getContext(), rootView.findViewById(R.id.change_default));
                menu.inflate(R.menu.whitelist_popup);
                menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        Log.d(TAG, "onMenuItemClick: Setting" + item);
                        switch (item.getItemId()) {
                            case R.id.whitelist_default_on_vpn:
                                Log.d(TAG, "onMenuItemClick: OnVpn");
                                MainActivity.config.whitelist.defaultMode = Configuration.Whitelist.DEFAULT_MODE_ON_VPN;
                                break;
                            case R.id.whitelist_default_not_on_vpn:
                                Log.d(TAG, "onMenuItemClick: NotOnVpn");
                                MainActivity.config.whitelist.defaultMode = Configuration.Whitelist.DEFAULT_MODE_NOT_ON_VPN;
                                break;
                            case R.id.whitelist_default_intelligent:
                                Log.d(TAG, "onMenuItemClick: Intelligent");
                                MainActivity.config.whitelist.defaultMode = Configuration.Whitelist.DEFAULT_MODE_INTELLIGENT;
                                break;
                        }

                        whitelistDefaultText.setText(getResources().getStringArray(R.array.whitelist_defaults)[MainActivity.config.whitelist.defaultMode]);
                        appListGenerator = new AppListGenerator(getContext());
                        appListGenerator.execute();
                        FileHelper.writeSettings(getContext(), MainActivity.config);
                        return true;
                    }
                });

                menu.show();
            }
        };

        rootView.findViewById(R.id.change_default).setOnClickListener(onDefaultChangeClicked);
        whitelistDefaultText.setOnClickListener(onDefaultChangeClicked);

        appListGenerator = new AppListGenerator(getContext());
        appListGenerator.execute();

        ExtraBar.setup(rootView.findViewById(R.id.extra_bar), "whitelist");


        return rootView;
    }

    private class AppListAdapter extends RecyclerView.Adapter<AppListAdapter.ViewHolder> {

        public ArrayList<ListEntry> list;

        Set<String> onVpn = new HashSet<>();
        Set<String> notOnVpn = new HashSet<>();

        public AppListAdapter(PackageManager pm, ArrayList<ListEntry> list) {
            this.list = list;
            MainActivity.config.whitelist.resolve(pm, onVpn, notOnVpn);
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(getContext()).inflate(R.layout.whitelist_row, parent, false));
        }

        @Override
        public void onBindViewHolder(final ViewHolder holder, int position) {
            final ListEntry entry = list.get(position);

            if (holder.task != null)
                holder.task.cancel(true);

            holder.task = null;
            final Drawable icon = entry.getIcon();
            if (icon != null) {
                holder.icon.setImageDrawable(icon);
                holder.icon.setVisibility(View.VISIBLE);
            } else {
                holder.icon.setVisibility(View.INVISIBLE);

                holder.task = new AsyncTask<ListEntry, Void, Drawable>() {
                    @Override
                    protected Drawable doInBackground(ListEntry... entries) {
                        return entries[0].loadIcon(getContext().getPackageManager());
                    }

                    @Override
                    protected void onPostExecute(Drawable drawable) {
                        if (!isCancelled()) {
                            holder.icon.setImageDrawable(drawable);
                            holder.icon.setVisibility(View.VISIBLE);
                        }
                        super.onPostExecute(drawable);
                    }
                };

                holder.task.execute(entry);
            }

            holder.name.setText(entry.getLabel());
            holder.details.setText(entry.getPackageName());
            holder.whitelistSwitch.setOnCheckedChangeListener(null);
            holder.whitelistSwitch.setChecked(notOnVpn.contains(entry.getPackageName()));
            holder.whitelistSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                    /* No change, do nothing */
                    if (checked && MainActivity.config.whitelist.items.contains(entry.getPackageName()))
                        return;
                    if (!checked && MainActivity.config.whitelist.itemsOnVpn.contains(entry.getPackageName()))
                        return;
                    if (checked) {
                        MainActivity.config.whitelist.items.add(entry.getPackageName());
                        MainActivity.config.whitelist.itemsOnVpn.remove(entry.getPackageName());
                        notOnVpn.add(entry.getPackageName());
                    } else {
                        MainActivity.config.whitelist.items.remove(entry.getPackageName());
                        MainActivity.config.whitelist.itemsOnVpn.add(entry.getPackageName());
                        notOnVpn.remove(entry.getPackageName());
                    }
                    FileHelper.writeSettings(getActivity(), MainActivity.config);
                }
            });


            holder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    holder.whitelistSwitch.setChecked(!holder.whitelistSwitch.isChecked());
                }
            });

        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        public class ViewHolder extends RecyclerView.ViewHolder {
            ImageView icon;
            TextView name;
            TextView details;
            Switch whitelistSwitch;
            AsyncTask<ListEntry, Void, Drawable> task;

            public ViewHolder(View itemView) {
                super(itemView);
                icon = (ImageView) itemView.findViewById(R.id.app_icon);
                name = (TextView) itemView.findViewById(R.id.name);
                details = (TextView) itemView.findViewById(R.id.details);
                whitelistSwitch = (Switch) itemView.findViewById(R.id.checkbox);
            }
        }
    }

    private final class AppListGenerator extends AsyncTask<Void, Void, AppListAdapter> {
        private PackageManager pm;

        private AppListGenerator(Context context) {
            pm = context.getPackageManager();
        }

        @Override
        protected AppListAdapter doInBackground(Void... params) {
            List<ApplicationInfo> info = pm.getInstalledApplications(0);

            Collections.sort(info, new ApplicationInfo.DisplayNameComparator(pm));

            final ArrayList<ListEntry> entries = new ArrayList<>();
            for (ApplicationInfo appInfo : info) {
                if (!appInfo.packageName.equals(BuildConfig.APPLICATION_ID) && (MainActivity.config.whitelist.showSystemApps || (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0))
                    entries.add(new ListEntry(
                            appInfo,
                            appInfo.packageName,
                            appInfo.loadLabel(pm).toString()));
            }

            return new AppListAdapter(pm, entries);
        }

        @Override
        protected void onPostExecute(AppListAdapter adapter) {
            appList.setAdapter(adapter);
            swipeRefresh.setRefreshing(false);
        }
    }

    private static class ListEntry {
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