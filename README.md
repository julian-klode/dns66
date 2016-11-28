DNS-Based Ad Blocking for Android
=================================
This is a DNS based ad blocker for Android.

[<img src="https://f-droid.org/badge/get-it-on.png"
      alt="Get it on F-Droid"
      height="80">](https://f-droid.org/app/org.jak_linux.dns66)

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
* Host files containing just host names are not yet supported (issue #5)

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

Contributing
------------
Contributions are welcome. Any code contribution will be considered to grant
an implicit license under the GPL, version 3 or later, as stated below, or
(on special exceptions) a different license explicitly included in the
contribution that is compatible to the GPL 3.

Icons are welcome too. Modifications of existing icons shall have the same
license as the modified file. New icons may be contributed under the GPL,
version 3 or later (as below), or the Apache license, version 2.

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
