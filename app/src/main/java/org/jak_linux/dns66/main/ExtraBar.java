/* Copyright (C) 2016-2019 Julian Andres Klode <jak@jak-linux.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.jak_linux.dns66.main;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.ImageView;

import org.jak_linux.dns66.R;

/**
 * Helper to set-up the on/off extra bar toggle.
 */
class ExtraBar {

    public static void setup(final View view, final String name) {
        if (view == null)
            return;

        final SharedPreferences preferences = view.getContext().getSharedPreferences("state", Context.MODE_PRIVATE);

        final ImageView expand = (ImageView) view.findViewById(R.id.extra_bar_toggle);
        final View extra = view.findViewById(R.id.extra_bar_extra);
        View.OnClickListener l = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (extra.getVisibility() == View.GONE) {
                    if (preferences.getBoolean("extraBarClosed:" + name, false)) {
                        SharedPreferences.Editor editor = preferences.edit();
                        editor.putBoolean("extraBarClosed:" + name, false);
                        editor.apply();
                    }
                    view.announceForAccessibility(view.getContext().getString(R.string.expand_bar_expanded));
                    expand.setImageDrawable(view.getContext().getDrawable(R.drawable.ic_expand_less_black_24dp));
                    expand.setContentDescription(view.getContext().getString(R.string.expand_bar_toggle_close));
                    extra.setVisibility(View.VISIBLE);
                } else {
                    if (!preferences.getBoolean("extraBarClosed:" + name, false)) {
                        SharedPreferences.Editor editor = preferences.edit();
                        editor.putBoolean("extraBarClosed:" + name, true);
                        editor.apply();
                    }
                    view.announceForAccessibility(view.getContext().getString(R.string.expand_bar_closed));
                    expand.setImageDrawable(view.getContext().getDrawable(R.drawable.ic_expand_more_black_24dp));
                    expand.setContentDescription(view.getContext().getString(R.string.expand_bar_toggle_expand));
                    extra.setVisibility(View.GONE);
                }
            }
        };
        expand.setOnClickListener(l);
        view.setOnClickListener(l);

        if (!preferences.getBoolean("extraBarClosed:" + name, false)) {
            expand.callOnClick();
        }
    }

}
