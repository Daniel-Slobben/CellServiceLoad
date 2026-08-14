package org.slobben.cellserviceload.adapter.websocket;

import java.lang.reflect.Type;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import jakarta.websocket.ContainerProvider;
import jakarta.websocket.WebSocketContainer;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

@Slf4j
public class WebSocketClientConnection {

  private final UUID clientId;
  private final StompSession session;

  public WebSocketClientConnection(UUID clientId, String url)
      throws ExecutionException, InterruptedException, TimeoutException {
    this.clientId = clientId;

    WebSocketContainer container = ContainerProvider.getWebSocketContainer();
    container.setDefaultMaxTextMessageBufferSize(2 * 1024 * 1024);
    container.setDefaultMaxBinaryMessageBufferSize(2 * 1024 * 1024);

    StandardWebSocketClient standardWebSocketClient = new StandardWebSocketClient(container);

    List<Transport> transports = List.of(new WebSocketTransport(standardWebSocketClient));
    WebSocketStompClient webSocketStompClient =
        new WebSocketStompClient(new SockJsClient(transports));
    webSocketStompClient.setMessageConverter(new JacksonJsonMessageConverter());

    final StompSession session =
        webSocketStompClient
            .connectAsync("wss://" + url + "/ws", new StompSessionHandlerAdapter() {})
            .get(5, TimeUnit.SECONDS);

    this.session = session;

    session.subscribe(
        "/topic/" + clientId,
        new StompFrameHandler() {
          @Override
          public @NonNull Type getPayloadType(@NonNull StompHeaders headers) {
            return EncodedBlock[].class;
          }

          @Override
          public void handleFrame(@NonNull StompHeaders headers, @Nullable Object payload) {
          }
        });
  }

  public void sendClientUpdateRequest(
      @Nullable String[] blockToRemove, @Nullable String[] blockToAdd) {
    log.info("Sending request to delete blocks: {}, and add blocks: {}", blockToRemove, blockToAdd);
    session.send("/client-update", new ClientUpdateRequest(clientId, blockToRemove, blockToAdd));
  }
}
