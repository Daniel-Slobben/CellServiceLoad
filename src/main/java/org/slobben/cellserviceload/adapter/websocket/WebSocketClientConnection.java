package org.slobben.cellserviceload.adapter.websocket;

import java.lang.reflect.Type;
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

  private final StompSession session;

  private WebSocketClientConnection(StompSession session) {
    this.session = session;
  }

  // Each connection is a new client on the server.
  public static CompletableFuture<WebSocketClientConnection> connect(
      WebSocketStompClient stompClient, String wsUrl, TopicListener listener) {
    return stompClient
        .connectAsync(wsUrl, new StompSessionHandlerAdapter() {})
        .thenApply(
            session -> {
              // Block frames and status responses share this queue; the content type tells them apart.
              session.subscribe("/user/queue/blocks", topicHandler(listener));
              return new WebSocketClientConnection(session);
            });
  }

  private static StompFrameHandler topicHandler(TopicListener listener) {
    return new StompFrameHandler() {
      @Override
      public @NonNull Type getPayloadType(@NonNull StompHeaders headers) {
        return isBinary(headers) ? byte[].class : StatusResponse.class;
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
        } else if (payload instanceof StatusResponse response
            && response.type() == StatusResponse.Type.NO_BLOCKS) {
          listener.onNoBlocks();
        } else {
          log.warn("unexpected message on the block queue: {}", payload);
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
    session.send("/client-update", new ClientUpdateRequest(blocksToRemove, blocksToAdd));
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
