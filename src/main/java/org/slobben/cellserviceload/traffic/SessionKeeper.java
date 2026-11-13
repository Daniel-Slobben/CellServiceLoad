package org.slobben.cellserviceload.traffic;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slobben.cellserviceload.adapter.websocket.WebSocketClientConnection;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.WebSocketStompClient;

/**
 * Keeps clients connected the way the frontend does: a new socket when the old one closes or the
 * server has been silent for load.reconnect-threshold-ms (the frontend's heartbeat check). Each
 * connection is a new client on the server, so it starts by sending the whole view.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SessionKeeper {
  private static final long CONNECT_TIMEOUT_MS = 10_000;
  // Like the frontend's RxStomp: a failed connect waits 1 s, then twice as long each time, up to 8 s.
  private static final long FIRST_RETRY_DELAY_MS = 1_000;
  private static final long MAX_RETRY_DELAY_MS = 8_000;

  private final ClientRegistry clientRegistry;
  private final WebSocketStompClient webSocketStompClient;
  private final Executor virtualThreadExecutor;
  private final TrafficStats trafficStats;

  @Value("${cell-service.ws-url}")
  private String wsUrl;

  // The frontend's heartbeat check; change it to try out another one.
  @Value("${load.reconnect-threshold-ms}")
  private long reconnectThresholdMs;

  public CompletableFuture<WebSocketClientConnection> connect(Client client) {
    return WebSocketClientConnection.connect(
        webSocketStompClient, wsUrl, client.newConnectionListener());
  }

  /** Called once a client has a connection, at start and after every reconnect. */
  public void start(Client client, WebSocketClientConnection connection) {
    client.setConnection(connection);
    client.markServerContact();
    connection.sendClientUpdateRequest(new String[0], client.getBlocksInView().toArray(new String[0]));
  }

  @Scheduled(fixedDelay = 250)
  public void watchdog() {
    long now = System.currentTimeMillis();
    for (Client client : clientRegistry.all()) {
      boolean silent = client.getLastServerContactMs() < now - reconnectThresholdMs;
      if (client.isReconnecting()
          || client.getNextReconnectAtMs() > now
          || !(silent || !client.getConnection().isConnected())) {
        continue;
      }
      client.setReconnecting(true);
      virtualThreadExecutor.execute(() -> reconnect(client));
    }
  }

  private void reconnect(Client client) {
    client.getLock().lock();
    try {
      client.getConnection().disconnect();
      WebSocketClientConnection connection =
          trafficStats
              .getReconnectLatency()
              .time(() -> connect(client).orTimeout(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS).join());
      start(client, connection);
      client.setRetryDelayMs(0);
      trafficStats.count(TrafficStats.Counter.RECONNECTS);
    } catch (Exception e) {
      long delay =
          client.getRetryDelayMs() == 0
              ? FIRST_RETRY_DELAY_MS
              : Math.min(client.getRetryDelayMs() * 2, MAX_RETRY_DELAY_MS);
      client.setRetryDelayMs(delay);
      client.setNextReconnectAtMs(System.currentTimeMillis() + delay);
      trafficStats.count(TrafficStats.Counter.RECONNECT_FAILURES);
      log.debug("reconnect failed: {}", e.toString());
    } finally {
      client.setReconnecting(false);
      client.getLock().unlock();
    }
  }
}
