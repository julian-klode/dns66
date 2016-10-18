/* Copyright (C) 2016 Julian Andres Klode <jak@jak-linux.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.jak_linux.dns66.main;

import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;

import org.jak_linux.dns66.FileHelper;
import org.jak_linux.dns66.MainActivity;
import org.jak_linux.dns66.R;
import org.jak_linux.dns66.vpn.AdVpnService;

import static android.app.Activity.RESULT_OK;

public class StartFragment extends Fragment {
    private static final String TAG = "StartFragment";

    public StartFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_start, container, false);
        Switch switchOnBoot = (Switch) rootView.findViewById(R.id.switch_onboot);

        ImageView view = (ImageView) rootView.findViewById(R.id.start_button);

        TextView stateText = (TextView) rootView.findViewById(R.id.state_textview);
        stateText.setText(getString(AdVpnService.vpnStatusToTextId(AdVpnService.vpnStatus)));

        view.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (AdVpnService.vpnStatus != AdVpnService.VPN_STATUS_STOPPED) {
                    Log.i(TAG, "Attempting to disconnect");

                    Intent intent = new Intent(getActivity(), AdVpnService.class);
                    intent.putExtra("COMMAND", org.jak_linux.dns66.vpn.Command.STOP.ordinal());
                    getActivity().startService(intent);
                } else {
                    Log.i(TAG, "Attempting to connect");
                    Intent intent = VpnService.prepare(getContext());
                    if (intent != null) {
                        startActivityForResult(intent, MainActivity.REQUEST_START_VPN);
                    } else {
                        ((MainActivity) getActivity()).onActivityResult(MainActivity.REQUEST_START_VPN, RESULT_OK, null);
                    }
                }
                return true;
            }
        });

        switchOnBoot.setChecked(MainActivity.config.autoStart);
        switchOnBoot.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                MainActivity.config.autoStart = isChecked;
                FileHelper.writeSettings(getContext(), MainActivity.config);
            }
        });

        return rootView;
    }

}
