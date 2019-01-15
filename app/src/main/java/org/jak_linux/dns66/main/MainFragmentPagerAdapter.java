/* Copyright (C) 2016-2019 Julian Andres Klode <jak@jak-linux.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.jak_linux.dns66.main;

import android.content.Context;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;

import org.jak_linux.dns66.R;

import java.util.ArrayList;

/**
 * Adapter for the pager that holds the fragments of the main activity.
 */

public class MainFragmentPagerAdapter extends FragmentPagerAdapter {

    private final Context context;

    public MainFragmentPagerAdapter(Context context, FragmentManager fm) {
        super(fm);
        this.context = context;
    }

    @Override
    public Fragment getItem(int position) {
        switch (position) {
            case 0:
                return new StartFragment();
            case 1:
                return new HostsFragment();
            case 2:
                return new WhitelistFragment();
            case 3:
                return new DNSFragment();
        }
        return null;
    }

    @Override
    public CharSequence getPageTitle(int position) {
        switch (position) {
            case 0:
                return context.getString(R.string.start_tab);
            case 1:
                return context.getString(R.string.hosts_tab);
            case 2:
                return context.getString(R.string.whitelist_tab);
            case 3:
                return context.getString(R.string.dns_tab);
        }
        return null;
    }

    @Override
    public int getCount() {
        return 4;
    }

}
