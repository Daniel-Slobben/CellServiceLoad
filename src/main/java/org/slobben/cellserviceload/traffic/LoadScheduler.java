package org.slobben.cellserviceload.traffic;

import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slobben.cellserviceload.adapter.rest.ServiceApiWrapper;
import org.slobben.cellserviceload.adapter.rest.Settings;
import org.slobben.cellserviceload.adapter.websocket.TopicListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class LoadScheduler {
  private static final int MAX_IN_FLIGHT_CONNECTS = 16;
  private static final int MAX_STARTS_PER_TICK = 4;
  private static final int INITIAL_VIEW_SIDE = 3;
  private static final int RECORDING_CLIENTS = 100;
  private static final int RECORDING_SAMPLE_EVERY = 20;

  private final ClientRegistry clientRegistry;
  private final ServiceApiWrapper serviceApiWrapper;
  private final SessionKeeper sessionKeeper;
  private final Executor virtualThreadExecutor;
  private final TrafficStats trafficStats;

  @Value("${load.max-clients}")
  private int maxClients;

  private final Semaphore inFlight = new Semaphore(MAX_IN_FLIGHT_CONNECTS);

  @Scheduled(fixedDelay = 200)
  public void rampUp() {
    for (int i = 0; i < MAX_STARTS_PER_TICK; i++) {
      int connecting = MAX_IN_FLIGHT_CONNECTS - inFlight.availablePermits();
      if (clientRegistry.size() + connecting >= maxClients || !inFlight.tryAcquire()) {
        return;
      }
      virtualThreadExecutor.execute(this::createClient);
    }
  }

  private void createClient() {
    try {
      Settings settings = serviceApiWrapper.getSettings();
      boolean record =
          clientRegistry.size() % RECORDING_SAMPLE_EVERY == 0
              && trafficStats.tryAcquireRecordingSlot(RECORDING_CLIENTS);
      Client client =
          new Client(
              settings.clientId(),
              settings.blockSize(),
              Math.floorDiv(settings.viewport().x(), settings.blockSize()),
              Math.floorDiv(settings.viewport().y(), settings.blockSize()),
              INITIAL_VIEW_SIDE,
              record ? trafficStats : TopicListener.NONE);

      sessionKeeper
          .connect(client)
          .whenComplete(
              (connection, error) -> {
                try {
                  if (error != null) {
                    log.warn("connect failed for {}: {}", client.getId(), error.toString());
                    return;
                  }
                  sessionKeeper.start(client, connection);
                  client.setNextMoveAtMs(System.currentTimeMillis() + ThreadLocalRandom.current().nextInt(3_000));
                  clientRegistry.add(client);
                } finally {
                  inFlight.release();
                }
              });
    } catch (RuntimeException e) {
      inFlight.release();
      log.warn("client creation failed: {}", e.toString());
    }
  }
}
