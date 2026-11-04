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
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.socket.messaging.WebSocketStompClient;

@Slf4j
public class WebSocketClientConnection {

  private final UUID clientId;
  private final StompSession session;

  private WebSocketClientConnection(UUID clientId, StompSession session) {
    this.clientId = clientId;
    this.session = session;
  }

  public static CompletableFuture<WebSocketClientConnection> connect(
      WebSocketStompClient stompClient, UUID clientId, String wsUrl, TopicListener listener) {
    return stompClient
        .connectAsync(wsUrl, new StompSessionHandlerAdapter() {})
        .thenApply(
            session -> {
              // Block frames and health check responses share this topic; the content type tells them apart.
              session.subscribe("/topic/" + clientId, topicHandler(listener));
              return new WebSocketClientConnection(clientId, session);
            });
  }

  private static StompFrameHandler topicHandler(TopicListener listener) {
    return new StompFrameHandler() {
      @Override
      public @NonNull Type getPayloadType(@NonNull StompHeaders headers) {
        return isBinary(headers) ? byte[].class : HealthCheckResponse.class;
      }

      @Override
      public void handleFrame(@NonNull StompHeaders headers, @Nullable Object payload) {
        if (isBinary(headers)) {
          // A tick without block data arrives as an empty frame, with a null payload.
          byte[] bytes = payload instanceof byte[] b ? b : new byte[0];
          try {
            listener.onBlocks(BlockFrame.read(bytes));
          } catch (IllegalArgumentException e) {
            log.warn("dropping malformed block frame: {}", e.getMessage());
          }
        } else if (payload instanceof HealthCheckResponse response) {
          listener.onHealthCheck(response.type());
        } else {
          log.warn("unexpected message on the block topic: {}", payload);
        }
      }
    };
  }

  private static boolean isBinary(StompHeaders headers) {
    return headers.getContentType() != null
        && MimeTypeUtils.APPLICATION_OCTET_STREAM.isCompatibleWith(headers.getContentType());
  }

  // Sends on a closed session are dropped; a reconnect resends the whole view anyway.
  public void sendClientUpdateRequest(String[] blocksToRemove, String[] blocksToAdd) {
    if (!session.isConnected()) return;
    session.send("/client-update", new ClientUpdateRequest(clientId, blocksToRemove, blocksToAdd));
  }

  public void sendHealthCheck() {
    if (!session.isConnected()) return;
    session.send("/health-check", clientId);
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
