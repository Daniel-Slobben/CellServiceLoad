package org.slobben.cellserviceload.traffic;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slobben.cellserviceload.adapter.rest.ReconnectResponse;
import org.slobben.cellserviceload.adapter.rest.ServiceApiWrapper;
import org.slobben.cellserviceload.adapter.websocket.WebSocketClientConnection;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.WebSocketStompClient;

/**
 * Keeps client sessions alive the way the frontend does: a health check every 8 seconds, and a
 * reconnect on SESSION_DEAD or when the server has been silent for 1.5 seconds.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SessionKeeper {
  static final long HEALTH_CHECK_INTERVAL_MS = 8_000;
  // Ticks come every 500 ms, so a short silence means the session is gone.
  private static final long RECONNECT_THRESHOLD_MS = 1_500;
  private static final long CONNECT_TIMEOUT_MS = 10_000;

  private final ClientRegistry clientRegistry;
  private final ServiceApiWrapper serviceApiWrapper;
  private final WebSocketStompClient webSocketStompClient;
  private final Executor virtualThreadExecutor;
  private final TrafficStats trafficStats;

  @Value("${cell-service.ws-url}")
  private String wsUrl;

  public CompletableFuture<WebSocketClientConnection> connect(Client client) {
    return WebSocketClientConnection.connect(
        webSocketStompClient, client.getId(), wsUrl, client.listenerFor(client.getId()));
  }

  /** Called once a client has a connection, at start and after every reconnect. */
  public void start(Client client, WebSocketClientConnection connection) {
    client.setConnection(connection);
    client.markServerContact();
    client.setNextHealthCheckAtMs(System.currentTimeMillis() + HEALTH_CHECK_INTERVAL_MS);
    connection.sendClientUpdateRequest(new String[0], client.getBlocksInView().toArray(new String[0]));
  }

  @Scheduled(fixedDelay = 500)
  public void healthChecks() {
    long now = System.currentTimeMillis();
    for (Client client : clientRegistry.all()) {
      if (client.isReconnecting() || client.getNextHealthCheckAtMs() > now) {
        continue;
      }
      client.setNextHealthCheckAtMs(now + HEALTH_CHECK_INTERVAL_MS);
      client.getConnection().sendHealthCheck();
    }
  }

  @Scheduled(fixedDelay = 250)
  public void watchdog() {
    long now = System.currentTimeMillis();
    for (Client client : clientRegistry.all()) {
      boolean silent = client.getLastServerContactMs() < now - RECONNECT_THRESHOLD_MS;
      if (client.isReconnecting() || !(silent || client.isSessionDead())) {
        continue;
      }
      client.setReconnecting(true);
      virtualThreadExecutor.execute(() -> reconnect(client));
    }
  }

  private void reconnect(Client client) {
    client.getLock().lock();
    try {
      ReconnectResponse response =
          serviceApiWrapper.reconnect(client.getBlocksInView().toArray(new String[0]));
      client.getConnection().disconnect();
      if (response.viewport() != null) {
        client.centerOn(response.viewport().x(), response.viewport().y());
      }
      // Set before subscribing, so the old topic's last messages are ignored.
      client.setId(response.clientId());
      start(client, connect(client).get(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS));
      trafficStats.recordReconnect();
    } catch (Exception e) {
      // The watchdog tries again on its next tick, like the frontend.
      trafficStats.recordReconnectFailure();
      log.debug("reconnect failed for {}: {}", client.getId(), e.toString());
    } finally {
      client.setReconnecting(false);
      client.getLock().unlock();
    }
  }
}
