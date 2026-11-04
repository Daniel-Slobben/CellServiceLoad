package org.slobben.cellserviceload.traffic;

import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slobben.cellserviceload.adapter.rest.ServiceApiWrapper;
import org.slobben.cellserviceload.adapter.rest.Settings;
import org.slobben.cellserviceload.adapter.websocket.WebSocketClientConnection;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.WebSocketStompClient;

@Component
@RequiredArgsConstructor
@Slf4j
public class LoadScheduler {
  private static final int MAX_CLIENTS = 3000;
  private static final int MAX_IN_FLIGHT_CONNECTS = 16;
  private static final int MAX_STARTS_PER_TICK = 4;
  private static final int INITIAL_VIEW_SIDE = 3;
  private static final int RECORDING_CLIENTS = 100;
  private static final int RECORDING_SAMPLE_EVERY = 20;

  private final ClientRegistry clientRegistry;
  private final ServiceApiWrapper serviceApiWrapper;
  private final WebSocketStompClient webSocketStompClient;
  private final Executor virtualThreadExecutor;
  private final TrafficStats trafficStats;

  @Value("${cell-service.url}")
  private String url;

  private final Semaphore inFlight = new Semaphore(MAX_IN_FLIGHT_CONNECTS);

  @Scheduled(fixedDelay = 200)
  public void rampUp() {
    int current = clientRegistry.size();
    log.info("current amount of clients: {}", current);
    if (current >= MAX_CLIENTS) {
      return;
    }
    for (int i = 0; i < MAX_STARTS_PER_TICK; i++) {
      if (!inFlight.tryAcquire()) {
        return;
      }
      virtualThreadExecutor.execute(this::createClient);
    }
  }

  private void createClient() {
    try {
      Settings settings = serviceApiWrapper.getNewClient();
      int anchorX = settings.chaosHit().worldX() / settings.blockSize();
      int anchorY = settings.chaosHit().worldY() / settings.blockSize();
      Set<String> initialBlocks = ViewportMover.computeView(anchorX, anchorY, INITIAL_VIEW_SIDE);
      String[] blocksToAdd = initialBlocks.toArray(new String[0]);

      boolean record =
          clientRegistry.size() % RECORDING_SAMPLE_EVERY == 0
              && trafficStats.tryAcquireRecordingSlot(RECORDING_CLIENTS);
      WebSocketClientConnection.connect(
              webSocketStompClient,
              settings.clientId(),
              url,
              record ? trafficStats::recordPartial : null,
              record ? trafficStats::recordFull : null)
          .whenComplete(
              (connection, error) -> {
                try {
                  if (error != null) {
                    log.warn("connect failed for {}: {}", settings.clientId(), error.toString());
                    return;
                  }
                  connection.sendClientUpdateRequest(null, blocksToAdd);
                  Client client =
                      new Client(
                          settings.clientId(),
                          anchorX,
                          anchorY,
                          INITIAL_VIEW_SIDE,
                          initialBlocks,
                          connection);
                  long now = System.currentTimeMillis();
                  ThreadLocalRandom rng = ThreadLocalRandom.current();
                  client.setNextMoveAtMs(now + rng.nextInt(3_000));
                  client.setNextRefreshAtMs(now + rng.nextInt((int) SettingsRefresher.REFRESH_INTERVAL_MS));
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
