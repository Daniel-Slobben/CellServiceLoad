package org.slobben.cellserviceload.traffic;

import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slobben.cellserviceload.adapter.rest.ServiceApiWrapper;
import org.slobben.cellserviceload.adapter.rest.Settings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class LoadScheduler {
  private static final int MAX_IN_FLIGHT_CONNECTS = 16;

  private final ClientRegistry clientRegistry;
  private final ServiceApiWrapper serviceApiWrapper;
  private final SessionKeeper sessionKeeper;
  private final Executor virtualThreadExecutor;
  private final TrafficStats trafficStats;

  @Value("${load.max-clients}")
  private int maxClients;

  @Value("${load.phone-share}")
  private double phoneShare;

  @Value("${load.zoomed-out-share}")
  private double zoomedOutShare;

  private final Semaphore inFlight = new Semaphore(MAX_IN_FLIGHT_CONNECTS);

  /** Clients waiting for their settings or first connect; ramp-up stalls when this sits at the max. */
  public int starting() {
    return MAX_IN_FLIGHT_CONNECTS - inFlight.availablePermits();
  }

  // Starts one client per tick.
  @Scheduled(fixedDelayString = "${load.ramp-up-delay-ms}")
  public void rampUp() {
    if (clientRegistry.size() + starting() >= maxClients || !inFlight.tryAcquire()) {
      return;
    }
    virtualThreadExecutor.execute(this::createClient);
  }

  private void createClient() {
    try {
      Settings settings = trafficStats.getSettingsLatency().time(serviceApiWrapper::getSettings);
      ThreadLocalRandom rng = ThreadLocalRandom.current();
      Device device = rng.nextDouble() < phoneShare ? Device.PHONE : Device.DESKTOP;
      Client client =
          new Client(
              settings.clientId(),
              new Camera(device, settings.blockSize()),
              rng.nextDouble() < zoomedOutShare,
              trafficStats);
      client.centerOn(settings.viewport().x(), settings.viewport().y());

      sessionKeeper
          .connect(client)
          .whenComplete(
              (connection, error) -> {
                try {
                  if (error != null) {
                    trafficStats.count(TrafficStats.Counter.CONNECT_FAILURES);
                    log.warn("connect failed for {}: {}", client.getId(), error.toString());
                    return;
                  }
                  sessionKeeper.start(client, connection);
                  long now = System.currentTimeMillis();
                  client.setLastMoveAtMs(now);
                  client.setActionEndsAtMs(now + ThreadLocalRandom.current().nextInt(3_000));
                  clientRegistry.add(client);
                } finally {
                  inFlight.release();
                }
              });
    } catch (RuntimeException e) {
      inFlight.release();
      trafficStats.count(TrafficStats.Counter.CONNECT_FAILURES);
      log.warn("client creation failed: {}", e.toString());
    }
  }
}
