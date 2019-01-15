/* Copyright (C) 2016-2019 Julian Andres Klode <jak@jak-linux.org>
 *
 * Derived from AdBuster:
 * Copyright (C) 2016 Daniel Brodie <dbrodie@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * Contributions shall also be provided under any later versions of the
 * GPL.
 */
package org.jak_linux.dns66.vpn;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootComplete extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        AdVpnService.checkStartVpnOnBoot(context);
    }
}
