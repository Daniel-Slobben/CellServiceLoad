# CellServiceLoad

Simulates many browsers on the Cells backend. Each client behaves like the Angular frontend:
`GET /gen-api/settings`, then its own STOMP websocket on `/ws`, subscribed to `/user/queue/blocks`,
with its visible blocks sent to `/client-update`. On the server one socket is one client. A client
opens a new socket when its socket closes or the server is silent for
the run's watchdog threshold, retrying after 1, 2, 4 and 8 s. Every new socket is a new client to the
server, so it sends the whole view again and gets it back as FULL blocks. Clients pan, zoom (out to the
frontend's limit, as many cells as a 4K screen at 0.083 px per cell) and jump to `POST /gen-api/viewport`
with phone or desktop screen sizes, and also jump when the server answers `NO_BLOCKS`. Like the
frontend, a client asks for SECTORs instead of cells once a sector is 1 px or smaller on screen, and goes
back to cells above 1.1 px: phones zoomed far out do, desktops never.

## Running

```bash
./mvnw spring-boot:run                                                   # against localhost:8080
./mvnw spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=pro"
```

Then open http://localhost:8085. The page starts and stops runs, and changes a run while it goes:
more or fewer clients, a faster build-up, another watchdog. It charts each run per 10 s window,
keeps the last 20 runs until the load test restarts, compares a run with an earlier one, and
downloads a run as JSON. The target is set at startup and shown at the top; the page asks before
starting a run against anything other than localhost.

Or as a jar, for running on another machine. Open the page on that machine's port 8085, or start
a run right away without it:

```bash
./mvnw package -DskipTests
java -Xmx4g -jar target/CellServiceLoad-0.0.1-SNAPSHOT.jar --spring.profiles.active=pro --load.autostart=true --load.max-clients=2000
```

The settings are the page's defaults:

| Setting | Default | |
|---|---|---|
| `load.autostart` | false | start a run with these settings at startup |
| `load.max-clients` | 2000 | |
| `load.ramp-up-per-second` | 2 | new clients a second: 2000 clients in 17 minutes |
| `load.duration-minutes` | 0 | stop the run after this long; 0 runs until stopped |
| `load.phone-share` | 0.7 | the rest are desktops |
| `load.zoomed-out-share` | 0.15 | clients that zoom out to between the limit and 0.4 px per cell, up to ~600 blocks; the farthest get sectors |
| `load.reconnect-threshold-ms` | 4000 | the frontend's watchdog; change it to try another one |

## Bandwidth

All clients share the network of the machine running the test. A home connection of ~20 MB/s fills
up at several hundred clients, mostly with FULL blocks sent on connects and zooms.
Frames then arrive late, the watchdog reconnects, and the reconnects ask for everything in FULL
again. That measures the home connection, not the server. Watch `download` in the report: if it sits
near what the machine can download, run the test from a server with a faster connection, or use
fewer clients. A phone on the same Wi-Fi suffers too, so check the site on mobile data.

## Report

Logged every 10 seconds of a run, with counts for those 10 seconds (made-up numbers). The page charts
the same numbers:

```
===== 2m30s ===== clients=590 socketsOpen=588 reconnecting=2 starting=1 stuck=3 | zoomedOut=70 sectors=12 blocksWatched avg=14 max=580
download 11.24MB/s (19KB/s per client) | full 9.80MB/s: 260/s avg 38KB | border 1.44MB/s: 6100/s | sector 0.30MB/s: 1700/s | frames 1180/s largest 2100KB
frame gaps ms p50=500 p95=520 p99=610 max=1830 (n=11800), over 4000ms=0
sockets: started=40 startFailed=0 | reconnects closed=1 silent=1 retry=0, reconnect ms p50=40 p95=60 p99=60 max=52 (n=2) | noBlocks=0
rest ms: settings p50=20 p95=40 p99=50 max=44 (n=40), viewport p50=20 p95=30 p99=30 max=27 (n=61)
```

- **clients**: running clients and how many have an open socket. `reconnecting` are opening a new
  one. `starting` are waiting for `/settings` or their first socket; at 16 ramp-up waits on them.
  `stuck` have had no block frame for longer than the watchdog threshold: what a user sees as a
  frozen screen.
- **download**: block frame bytes, split into FULL, BORDER and SECTOR, with the average FULL block size.
  `sectors` above counts the clients that ask for SECTORs.
- **frame gaps**: time between block frames per client, the server tick as a user sees it; around
  500 ms when healthy. `over Xms` counts gaps the frontend would have reconnected on.
- **sockets**: new clients and clients that failed to start, then reconnects by cause: the socket
  `closed`, the server went `silent`, or a `retry` after a failed reconnect. `reconnect ms` is the
  time to a connected STOMP session. `noBlocks` counts new sockets whose blocks no longer exist.
- **rest**: latency of `/settings` and `/viewport`, timeouts included.

Timings show `FAILED=n` when calls failed, and `none` when there were none. When clients run but no
block frames arrive, a warning says so: the server is down, or this load test is older than its
protocol.
