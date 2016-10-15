DNS-Based Ad Blocking for Android
=================================
This is a DNS based ad blocker for Android.

How it works
------------
The app establishes a VPN service, with a route for a single host diverted to
it. The VPN service then intercepts the packages for that host and forwards
any DNS queries that are not blacklisted.

Custom upstream DNS can be configured. If the feature is turned off, the
current connection's DNS servers are used. The app ships are pre-defined
list of well known (mostly German) non-logging servers courtesy of the
Chaos Computer Club.

License
-------
This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

Parts of the program are licensed under the terms of the Apache license,
see the file copyright for more information.

Authors
-------
Julian Andres Klode <jak@jak-linux.org>
