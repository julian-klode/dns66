DNS-Based Host Blocking for Android
===================================
This is a local DNS-based host blocker for Android. In the default
configuration, several widely-respected host files are used to block
ads, malware, and other weird stuff in browsers and apps (although
app support is partial).

[![codecov](https://codecov.io/gh/julian-klode/dns66/branch/master/graph/badge.svg)](https://codecov.io/gh/julian-klode/dns66)
[![Build Status](https://travis-ci.org/julian-klode/dns66.svg?branch=master)](https://travis-ci.org/julian-klode/dns66)


Recommended installation method
----------
1. Install the app repository F-Droid: https://f-droid.org/
2. Open F-Droid, search for DNS66 and install it:
https://f-droid.org/app/org.jak_linux.dns66

This will allow you to receive updates through F-Droid.

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
      alt="Get it on F-Droid"
      height="80">](https://f-droid.org/app/org.jak_linux.dns66)


Using it
---------
DNS66 intercepts all DNS requests **before they leave the phone**, 
and blocks requests that are part of one of the blocklists. 

DNS66 has the following tabs:
* **The "START" tab** allows you to turn DNS66 on/off and to configure whether it
should automatically start at boot.

* **The "HOSTS" tab** allows you to manually update hosts files (blocklists and 
whitelists), and to set up a daily refresh. You may add a custom list of hosts (or even
a single host) to block or whitelist. A later entry overrides a previous entry.

* **The "APPS" tab** allows you to bypass (disable) DNS66 for specific apps. By
default, DNS66 is disabled for System apps - this is a conservative default
that may let content pass through.

* **The "DNS" tab** allows you to choose which DNS server must be used for 
requests that are let through. By default, the feature is turned off, which means 
that the current connection's DNS servers are used. We pre-loaded a list of 
well-known (mostly German) non-logging servers courtesy of the Chaos Computer Club,
but you can also add your own. If multiple servers are checked, the first server
on the list is always preferred. 

Advanced configuration & known bugs
---------
### Alternative installation methods
You can also:
* Use my personal F-Droid repository at https://jak-linux.org/fdroid/repo which
may get updates earlier.
* Download apk files in GitHub's download section (currently, these are the same
files as in my personal F-Droid repository, but that might change in the future).

### Known bugs
The following limitations apply:
* DNS66 works at the DNS level and can only block domains and subdomains. This 
means that **not all ads can be blocked** so partial ad blocking is usually not 
a bug. 
* DNS66 does not directly provide any blocklist - if you think a domain should 
be blocked but isn't, report it to the blocklist providers, we can't do anything
about it.
* In-App Ad Blocking might or might not work. In-Browser blocking works fine. 

Currently, there are also some minor usability issues:
* If you change a setting, you must manually restart the vpn service (issue #3). 
Refreshing host files does not require a restart.
* There's also no validation of input, so DNS servers that are not valid IPv4
addresses are not rejected, neither are URLs for DNS server entries (we intend
to support URLs in the future, so you can point the app to a remote list of
servers)

Support & Discussions
-----------------------------------
* Discuss DNS66 on 
[the dedicated XDA thread](https://forum.xda-developers.com/android/apps-games/app-dns66-source-host-ad-blocker-root-t3487497)
(I also occasionally post preview builts)
* Report bugs through the 
[Issues tab](https://github.com/julian-klode/dns66/issues) - but please read 
the "Known bugs" section above first.
* For blocking issues, use the "Logcat" menu option and send it to me via 
E-Mail (jak@jak-linux.org). If it worked before, send a logcat for the old 
version as well.

Privacy Guarantee
-----------------
Privacy is the most important aspect of DNS66. Currently, DNS66 is strictly
data reducing: running it can only reduce the amount of data leaving your
device, not increase it (except for fetching hosts files, obviously). As for
each request, we will either allow it to leave your device or not - we will
not send other requests or add other information to the request.

While not yet implemented, future versions of DNS66 might have additional
features that might share more data than your phone normally would. Among
these features are:

1. Automatic updates. Your phone might periodically contact servers to query
   for new upstream versions and new host lists. DNS66 will only include as
   much data as necessary to complete the request.

2. Debugging. We hope to have a better way to debug program failures than
   manually running logcat. Such a feature by definition requires sharing
   debug logs. Debug logs (including logcat) may include personal information,
   and you should review them before sharing them publicly.

If such a feature is added, you will be presented with the choice to enable
it (it will be disabled by default). No such feature will be turned on without
your explicit consent (for example, clicking yes in a dialog asking whether you
want to have automatic updates).

Contributing
------------
See [CONTRIBUTING.md](CONTRIBUTING.md)

License
-------
This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

Parts of the program are licensed under only version 3 of the license, and
some parts might be licensed under the terms of other compatible licenses. See
the file [copyright](app/src/main/assets/copyright) for further (machine-readable) information.

Binaries also bundle external libraries. To the best of our knowledge those
are licensed under the Apache license, version 2.0, except for pcap4j, which
is licensed under the MIT license, and dnsjava, which uses a 3 clause BSD
license. See
the file [copyright.libraries](app/src/main/assets/copyright.libraries) for further (machine-readable) information.

Authors
-------
Julian Andres Klode <jak@jak-linux.org>

Parts are derived from https://github.com/dbrodie/AdBuster by Daniel Brodie.
