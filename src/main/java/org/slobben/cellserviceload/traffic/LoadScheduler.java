package org.slobben.cellserviceload.traffic;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slobben.cellserviceload.adapter.rest.ServiceApiWrapper;
import org.slobben.cellserviceload.adapter.rest.Settings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

/** Runs one load test at a time: ramps clients up to the target, removes them when it drops, stops. */
@Component
@RequiredArgsConstructor
@Slf4j
public class LoadScheduler {
  private static final int MAX_IN_FLIGHT_CONNECTS = 16;
  private static final int TICK_MS = 50;
  private static final int KEPT_RUNS = 20;

  private final ClientRegistry clientRegistry;
  private final ServiceApiWrapper serviceApiWrapper;
  private final SessionKeeper sessionKeeper;
  private final Executor virtualThreadExecutor;
  private final TrafficStats trafficStats;

  @Value("${load.autostart}")
  private boolean autostart;

  @Value("${load.max-clients}")
  private int maxClients;

  @Value("${load.ramp-up-per-second}")
  private double rampUpPerSecond;

  @Value("${load.duration-minutes}")
  private int durationMinutes;

  @Value("${load.phone-share}")
  private double phoneShare;

  @Value("${load.zoomed-out-share}")
  private double zoomedOutShare;

  @Value("${load.reconnect-threshold-ms}")
  private long reconnectThresholdMs;

  private final Semaphore inFlight = new Semaphore(MAX_IN_FLIGHT_CONNECTS);
  // newest first
  private final Deque<Run> runs = new ConcurrentLinkedDeque<>();
  private volatile Run current;
  private double startsDue;
  private long lastTickMs;

  /** Clients waiting for their settings or first connect; ramp-up stalls when this sits at the max. */
  public int starting() {
    return MAX_IN_FLIGHT_CONNECTS - inFlight.availablePermits();
  }

  public RunConfig defaults() {
    return new RunConfig(
        maxClients, rampUpPerSecond, durationMinutes, phoneShare, zoomedOutShare, reconnectThresholdMs);
  }

  public Run current() {
    return current;
  }

  public List<Run> runs() {
    return new ArrayList<>(runs);
  }

  @EventListener(ApplicationReadyEvent.class)
  public void autostart() {
    if (autostart) {
      start(defaults());
    } else {
      log.info("Idle. Start a run on the page at :8085, or pass --load.autostart=true");
    }
  }

  public synchronized Run start(RunConfig config) {
    if (current != null) {
      throw new IllegalStateException("run " + current.getId() + " is still going");
    }
    Run run = new Run(runs.isEmpty() ? 1 : runs.peekFirst().getId() + 1, config);
    trafficStats.setReconnectThresholdMs(config.reconnectThresholdMs());
    trafficStats.reset();
    startsDue = 0;
    lastTickMs = run.getStartedAtMs();
    runs.addFirst(run);
    while (runs.size() > KEPT_RUNS) {
      runs.removeLast();
    }
    current = run;
    log.info("Run {} started: {}", run.getId(), config);
    return run;
  }

  public synchronized Run update(RunConfig config) {
    Run run = current;
    if (run == null) {
      throw new IllegalStateException("no run is going");
    }
    run.setConfig(config);
    trafficStats.setReconnectThresholdMs(config.reconnectThresholdMs());
    log.info("Run {} changed: {}", run.getId(), config);
    return run;
  }

  public synchronized void stop(String reason) {
    Run run = current;
    if (run == null) {
      return;
    }
    current = null;
    run.end(reason);
    clientRegistry.all().forEach(this::remove);
    log.info("Run {} {}", run.getId(), reason);
  }

  // Starts clients at the ramp-up rate, a few a tick when it's fast.
  @Scheduled(fixedDelay = TICK_MS)
  public synchronized void rampUp() {
    Run run = current;
    if (run == null) {
      return;
    }
    RunConfig config = run.getConfig();
    long now = System.currentTimeMillis();
    if (config.durationMinutes() > 0 && now >= run.getStartedAtMs() + config.durationMinutes() * 60_000L) {
      stop("reached its duration");
      return;
    }
    int excess = clientRegistry.size() - config.clients();
    if (excess > 0) {
      clientRegistry.all().stream().limit(excess).toList().forEach(this::remove);
    }

    double perTick = config.rampUpPerSecond() * (now - lastTickMs) / 1000.0;
    lastTickMs = now;
    // A stall doesn't save up starts for a burst afterwards.
    startsDue = Math.min(startsDue + perTick, Math.max(1, perTick));
    while (startsDue >= 1
        && clientRegistry.size() + starting() < config.clients()
        && inFlight.tryAcquire()) {
      startsDue--;
      virtualThreadExecutor.execute(() -> createClient(run));
    }
  }

  private void remove(Client client) {
    clientRegistry.remove(client);
    virtualThreadExecutor.execute(client::close);
  }

  private void createClient(Run run) {
    try {
      RunConfig config = run.getConfig();
      Settings settings = trafficStats.getSettingsLatency().time(serviceApiWrapper::getSettings);
      ThreadLocalRandom rng = ThreadLocalRandom.current();
      Device device = rng.nextDouble() < config.phoneShare() ? Device.PHONE : Device.DESKTOP;
      Client client =
          new Client(
              new Camera(device, settings.blockSize()),
              rng.nextDouble() < config.zoomedOutShare(),
              trafficStats);
      client.centerOn(settings.viewport().x(), settings.viewport().y());

      sessionKeeper
          .connect(client)
          .whenComplete(
              (connection, error) -> {
                try {
                  if (error != null) {
                    trafficStats.count(TrafficStats.Counter.START_FAILURES);
                    log.debug("connect failed: {}", error.toString());
                    return;
                  }
                  sessionKeeper.start(client, connection);
                  long now = System.currentTimeMillis();
                  client.setLastMoveAtMs(now);
                  client.setActionEndsAtMs(now + ThreadLocalRandom.current().nextInt(3_000));
                  if (!addTo(run, client)) {
                    connection.disconnect();
                    return;
                  }
                  trafficStats.count(TrafficStats.Counter.CLIENTS_STARTED);
                } finally {
                  inFlight.release();
                }
              });
    } catch (RestClientException e) {
      inFlight.release();
      trafficStats.count(TrafficStats.Counter.START_FAILURES);
      log.debug("settings failed: {}", e.toString());
    } catch (RuntimeException e) {
      inFlight.release();
      trafficStats.count(TrafficStats.Counter.START_FAILURES);
      log.warn("client creation failed: {}", e.toString());
    }
  }

  // Synchronized with stop, so a client that finishes connecting during a stop isn't left behind.
  private synchronized boolean addTo(Run run, Client client) {
    if (run != current) {
      return false;
    }
    clientRegistry.add(client);
    return true;
  }
}
