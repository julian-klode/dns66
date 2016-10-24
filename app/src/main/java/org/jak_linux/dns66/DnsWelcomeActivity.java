/* Copyright (C) 2016 Julian Andres Klode <jak@jak-linux.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.jak_linux.dns66;

import android.os.Bundle;

import com.stephentuso.welcome.BasicPage;
import com.stephentuso.welcome.TitlePage;
import com.stephentuso.welcome.WelcomeActivity;
import com.stephentuso.welcome.WelcomeConfiguration;

/**
 * Welcome activity.
 * This displays various informations for the first use of the app.
 */
public class DnsWelcomeActivity extends WelcomeActivity {
    @Override
    protected WelcomeConfiguration configuration() {
        return new WelcomeConfiguration.Builder(this)
                .defaultBackgroundColor(R.color.colorPrimaryDark)
                .page(new TitlePage(R.mipmap.app_icon_large,
                        getString(R.string.welcome_title))
                )
                .page(new BasicPage(R.drawable.ic_menu_start_white,
                        getString(R.string.welcome_title_start),
                        getString(R.string.welcome_message_start)).background(R.color.colorPrimaryDarkFriend1)
                )
                .page(new BasicPage(R.drawable.ic_menu_hosts_white,
                        getString(R.string.welcome_title_hosts),
                        getString(R.string.welcome_message_hosts)).background(R.color.colorPrimaryDarkFriend2)
                )
                .page(new BasicPage(R.drawable.ic_menu_dns_white,
                        getString(R.string.welcome_title_dns),
                        getString(R.string.welcome_message_dns)).background(R.color.colorPrimaryDarkFriend3)
                )
                .build();
    }
}
