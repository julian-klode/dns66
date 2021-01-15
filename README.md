DNS-Based Host Blocking for Android
===================================
This is a DNS-based host blocker for Android. In the default configuration,
several widely-respected host files are used to block ads, malware, and other
weird stuff.

[![codecov](https://codecov.io/gh/julian-klode/dns66/branch/master/graph/badge.svg)](https://codecov.io/gh/julian-klode/dns66)
[![Build Status](https://travis-ci.com/julian-klode/dns66.svg?branch=master)](https://travis-ci.com/julian-klode/dns66)

Installing
----------
[<img src="https://f-droid.org/badge/get-it-on.png"
      alt="Get it on F-Droid"
      height="80">](https://f-droid.org/app/org.jak_linux.dns66)

You can either install it via F-Droid, using the official F-Droid repository, or you can use my personal repository at https://jak-linux.org/fdroid/repo which gets updates ASAP.

You can also download apk files in GitHub's download section. Currently, these are the same files as in my personal F-Droid repository, but that might change in the future.

XDA: Discussions and preview builds
-----------------------------------
There is a thread at XDA, where DNS66 can be discussed and I occasionaly post
preview builts of the git repository:

https://forum.xda-developers.com/android/apps-games/app-dns66-source-host-ad-blocker-root-t3487497

Using it
---------
On the first start, you must manually update the hosts files (using the
refresh button) before the service can work correctly (issue #1); and you
must also update the hosts files yourself regularly for now (issue #2).

Items in the hosts and DNS servers lists can be moved around and removed)
of the list using standard RecyclerView interactions (long press makes the
entry movable, swipe to either side removes it). For hosts, a later entry
overrides a previous entry; for DNS servers, the first server is preferred.

Currently, there are some minor usability issues:

* If you change a setting, you must manually restart the vpn service (issue #3)
* IPv6 servers are not supported (issue #4)

There's also no validation of input, so DNS servers that are not valid IPv4
addresses are not rejected, neither are URLs for DNS server entries (we intend
to support URLs in the future, so you can point the app to a remote list of
servers).

How it works
------------
The app establishes a VPN service, with routes for all DNS servers diverted to
it. The VPN service then intercepts the packages for the servers and forwards
any DNS queries that are not blacklisted.

Custom upstream DNS can be configured. If the feature is turned off, the
current connection's DNS servers are used. The app ships are pre-defined
list of well known (mostly German) non-logging servers courtesy of the
Chaos Computer Club.


Privacy Guarantee
-----------------
Privacy is the most important aspect of DNS66. Currently, DNS66 is strictly
data reducing: Running it can only reduce the amount of data leaving your
device, not increase it (except for fetching hosts files, obviously), as for
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

Code of Conduct
---------------
Please note that this project is released with a Contributor Code of
Conduct. By participating in this project you agree to abide by its terms.

Authors
-------
Julian Andres Klode <jak@jak-linux.org>

Parts are derived from https://github.com/dbrodie/AdBuster by Daniel Brodie.
