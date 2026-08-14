package org.slobben.cellserviceload.adapter.websocket;

import java.lang.reflect.Type;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.messaging.WebSocketStompClient;

@Slf4j
public class WebSocketClientConnection {

  private static final StompFrameHandler DISCARDING_HANDLER =
      new StompFrameHandler() {
        @Override
        public @NonNull Type getPayloadType(@NonNull StompHeaders headers) {
          return byte[].class;
        }

        @Override
        public void handleFrame(@NonNull StompHeaders headers, @Nullable Object payload) {
          // load test: drop payloads without deserializing to avoid GC pressure
        }
      };

  private final UUID clientId;
  private final StompSession session;

  private WebSocketClientConnection(UUID clientId, StompSession session) {
    this.clientId = clientId;
    this.session = session;
  }

  public static CompletableFuture<WebSocketClientConnection> connect(
      WebSocketStompClient stompClient, UUID clientId, String url) {
    return stompClient
        .connectAsync("wss://" + url + "/ws", new StompSessionHandlerAdapter() {})
        .thenApply(
            session -> {
              session.subscribe("/topic/" + clientId, DISCARDING_HANDLER);
              session.subscribe("/topic/full/" + clientId, DISCARDING_HANDLER);
              return new WebSocketClientConnection(clientId, session);
            });
  }

  public void sendClientUpdateRequest(
      @Nullable String[] blockToRemove, @Nullable String[] blockToAdd) {
    session.send("/client-update", new ClientUpdateRequest(clientId, blockToRemove, blockToAdd));
  }

  public void disconnect() {
    if (session.isConnected()) {
      session.disconnect();
    }
  }

  public boolean isConnected() {
    return session.isConnected();
  }
}
