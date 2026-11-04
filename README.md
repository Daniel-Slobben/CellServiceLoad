# CellServiceLoad

Simulates many browsers on the Cells backend. Each client behaves like the Angular frontend:
`GET /gen-api/settings`, a STOMP websocket on `/ws`, its visible blocks via `/client-update`, a
health check every 8 s, and a reconnect (`POST /gen-api/reconnect`) on `SESSION_DEAD` or when the
server is silent for `load.reconnect-threshold-ms`. Clients pan, zoom (down to 0.1 px per cell) and
jump to `GET /gen-api/viewport` with phone or desktop screen sizes.

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
| `load.max-clients` | 3000 | ramps up at about 20 clients a second |
| `load.phone-share` | 0.7 | the rest are desktops |
| `load.zoomed-out-share` | 0.15 | clients that zoom out to 0.1-0.4 px per cell and watch up to ~100 blocks |
| `load.reconnect-threshold-ms` | 1500 | the frontend's watchdog; change it to try another one |

## Bandwidth

All clients share the network of the machine running the test. A home connection of ~20 MB/s fills
up at a few hundred clients, mostly with FULL blocks (~75 KB each) sent on connects and zooms.
Frames then arrive late, the watchdog reconnects, and the reconnects ask for everything in FULL
again. That measures the home connection, not the server. Watch `MB/s` in the report: if it sits
near what the machine can download, run the test from a server with a faster connection, or use
fewer clients. A phone on the same Wi-Fi suffers too, so check the site on mobile data.

## Report

Every 10 seconds:

- **clients**: registered clients, how many are reconnecting, how many are still starting
  (`starting` at 16 means ramp-up waits on slow `/settings` calls), zoomed out, blocks watched.
- **frames**: complete block frames received, MB/s, largest frame, FULL and BORDER blocks per second.
- **frame gaps**: time between block frames per client, the server tick as a user sees it; around
  500 ms when healthy. `over Xms` counts gaps the frontend would have reconnected on.
- **sessions**: health check round trip, `SESSION_DEAD`s, reconnects and their failures, clients
  that failed to start.
- **rest**: latency of `/settings`, `/viewport` and `/reconnect`, timeouts included.
