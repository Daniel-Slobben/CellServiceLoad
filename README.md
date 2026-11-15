# CellServiceLoad

Simulates many browsers on the Cells backend. Each client behaves like the Angular frontend:
`GET /gen-api/settings`, then its own STOMP websocket on `/ws`, subscribed to `/user/queue/blocks`,
with its visible blocks sent to `/client-update`. On the server one socket is one client. A client
opens a new socket when its socket closes or the server is silent for
`load.reconnect-threshold-ms`, retrying after 1, 2, 4 and 8 s. Every new socket is a new client to the
server, so it sends the whole view again and gets it back as FULL blocks. Clients pan, zoom (down to
0.1 px per cell) and jump to `POST /gen-api/viewport` with phone or desktop screen sizes, and also jump
when the server answers `NO_BLOCKS`.

## Running

```bash
./mvnw spring-boot:run                                              # against localhost:8080
./mvnw spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=pro --load.max-clients=500"
```

Or as a jar, for running on another machine:

```bash
./mvnw package -DskipTests
java -Xmx4g -jar target/CellServiceLoad-0.0.1-SNAPSHOT.jar --spring.profiles.active=pro --load.max-clients=2000
```

| Setting | Default | |
|---|---|---|
| `load.max-clients` | 2000 | |
| `load.ramp-up-delay-ms` | 500 | one new client per this many ms: 500 is 2 clients a second, 2000 clients in 17 minutes |
| `load.phone-share` | 0.7 | the rest are desktops |
| `load.zoomed-out-share` | 0.15 | clients that zoom out to 0.1-0.4 px per cell and watch up to ~100 blocks |
| `load.reconnect-threshold-ms` | 4000 | the frontend's watchdog; change it to try another one |

## Bandwidth

All clients share the network of the machine running the test. A home connection of ~20 MB/s fills
up at several hundred clients, mostly with FULL blocks sent on connects and zooms.
Frames then arrive late, the watchdog reconnects, and the reconnects ask for everything in FULL
again. That measures the home connection, not the server. Watch `download` in the report: if it sits
near what the machine can download, run the test from a server with a faster connection, or use
fewer clients. A phone on the same Wi-Fi suffers too, so check the site on mobile data.

## Report

Every 10 seconds, with counts for those 10 seconds (made-up numbers):

```
===== 2m30s ===== clients=590 socketsOpen=588 reconnecting=2 starting=1 stuck=3 | zoomedOut=70 blocksWatched avg=9 max=96
download 11.24MB/s (19KB/s per client) | full 9.80MB/s: 260/s avg 38KB | border 1.44MB/s: 6100/s | frames 1180/s largest 2100KB
frame gaps ms p50=500 p95=520 p99=610 max=1830 (n=11800), over 4000ms=0
sockets: started=40 startFailed=0 | reconnects closed=1 silent=1 retry=0, reconnect ms p50=40 p95=60 p99=60 max=52 (n=2) | noBlocks=0
rest ms: settings p50=20 p95=40 p99=50 max=44 (n=40), viewport p50=20 p95=30 p99=30 max=27 (n=61)
```

- **clients**: running clients and how many have an open socket. `reconnecting` are opening a new
  one. `starting` are waiting for `/settings` or their first socket; at 16 ramp-up waits on them.
  `stuck` have had no block frame for longer than the watchdog threshold: what a user sees as a
  frozen screen.
- **download**: block frame bytes, split into FULL and BORDER, with the average FULL block size.
- **frame gaps**: time between block frames per client, the server tick as a user sees it; around
  500 ms when healthy. `over Xms` counts gaps the frontend would have reconnected on.
- **sockets**: new clients and clients that failed to start, then reconnects by cause: the socket
  `closed`, the server went `silent`, or a `retry` after a failed reconnect. `reconnect ms` is the
  time to a connected STOMP session. `noBlocks` counts new sockets whose blocks no longer exist.
- **rest**: latency of `/settings` and `/viewport`, timeouts included.

Timings show `FAILED=n` when calls failed, and `none` when there were none. When clients run but no
block frames arrive, a warning says so: the server is down, or this load test is older than its
protocol.
