# kartops

## What is this?

When playing [CTPG Revolution](https://www.chadsoft.co.uk/) (a Mario Kart Wii mod) on the local
network, we had more players than could play at one time. "Wouldn't it be nice," we said, "if we
could get notifications when a set of races finished, so that you could head back to the kart room
and get in on the next round?"

So we hacked around and built a tool that did just that! This is a trimmed down demo of that tool.

## How to use this tool

We're assume you're running some *nix-like operating system, and have [lein](https://leiningen.org/) installed

1. First off you're going to need to do a little networking. The Wiis are all communicating with
   each other, and we want to get in on that communication. How? By using a switch that supports
   [Port Mirroring](https://en.wikipedia.org/wiki/Port_mirroring)! Connect the computer you're
   running this code on to the same switch as the Wii that hosts your games, and configure the
   switch to mirror all the Wii's traffic to the computer.

2. Put the NIC receiving the mirrored traffic into [promiscuous
   mode](https://en.wikipedia.org/wiki/Promiscuous_mode) so that the wii traffic doesn't get dropped
   on the floor. (Honestly, I can't remember if this is actually required, but it won't hurt.)

3. Install libpcap, so that [pcap4j](https://www.pcap4j.org/) will work.

4. Execute `lein run $device $path`.
   Where `$device` is the name of the NIC receiving traffic (e.g `enp3s0`, but it depends on your OS
   and device), and `$path` is file to which you would like to log events (e.g `/dev/null` to
   discard these logs)

5. Play a round of Kart!

6. Observe that you see things like `Started a Grand Prix!` and `Race finished on Maple Treeway.` printed in the console.

7. If all is well, modify `kartops.core/notify-event-started` and
   `kartops.core/notify-event-started` to send the notifications anywhere you want! For example, you
   could [post to Slack](https://api.slack.com/messaging/webhooks).


## Additional info


The file `resources/ordered-track-list.edn` is used to translate [track
indexes](http://wiki.tockdom.com/wiki/MKWii_Network_Protocol/RACEHEADER_1) to track names based on
the order that the tracks appear. If you are seeing notifications for tracks that don't have the
correct name, it's probably because the file needs to be modified to match the track ordering in
your setup. See [custom track distributions](http://wiki.tockdom.com/wiki/Custom_Track_Distribution)
for many track lists. The current track list was built from the [CTGP 1.02 track
list](http://wiki.tockdom.com/wiki/CTGP_Revolution_v1.02#Tracks).

If you want to expand the amount of information pulled from the network, the Mario Kart Wii network
protocol: http://wiki.tockdom.com/wiki/MKWii_Network_Protocol is invaluable.

It can be helpful for testing to have a dump of tcp traffic that can be replayed at will. You can
achieve this by using `tcpdump` to write packets to a file while (part of) a kart game is running
and then send them through the NIC as much as you want with `tcpreplay`.


## Credits

Alphabetically by last name:

- [Xander Herrick](https://github.com/XTech2K) - Network packet parsing, putting things together
- [John Miller](https://github.com/justjohn2001) - Network packet parsing, initial Clojure implementation
- [Laverne Schrock](https://github.com/lverns/) - Putting things together, deployment
- [Cameron Sumpter](https://github.com/csumpter) - Networking, deployment


## License

Copyright © 2020 Hotel JV Services, LLC

Unless otherwise noted, all files in this repository are made available under the 3-Clause BSD
license. See LICENSE for full text.
