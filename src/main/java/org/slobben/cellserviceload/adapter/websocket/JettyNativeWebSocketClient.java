package org.slobben.cellserviceload.adapter.websocket;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.eclipse.jetty.websocket.api.Callback;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.springframework.context.SmartLifecycle;
import org.springframework.http.HttpHeaders;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketExtension;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.AbstractWebSocketClient;

public class JettyNativeWebSocketClient extends AbstractWebSocketClient implements SmartLifecycle {

  private final WebSocketClient jettyClient;
  private volatile boolean running;

  public JettyNativeWebSocketClient(WebSocketClient jettyClient) {
    this.jettyClient = jettyClient;
  }

  @Override
  public void start() {
    try {
      jettyClient.start();
      running = true;
    } catch (Exception e) {
      throw new IllegalStateException("failed to start jetty ws client", e);
    }
  }

  @Override
  public void stop() {
    try {
      jettyClient.stop();
    } catch (Exception ignored) {
    } finally {
      running = false;
    }
  }

  @Override
  public boolean isRunning() {
    return running;
  }

  @Override
  protected CompletableFuture<WebSocketSession> executeInternal(
      WebSocketHandler handler,
      HttpHeaders headers,
      URI uri,
      List<String> subProtocols,
      List<WebSocketExtension> extensions,
      Map<String, Object> attributes) {

    JettyNativeWebSocketSession session = new JettyNativeWebSocketSession(uri, headers);
    if (attributes != null) {
      session.getAttributes().putAll(attributes);
    }

    ClientUpgradeRequest upgrade = new ClientUpgradeRequest();
    if (subProtocols != null && !subProtocols.isEmpty()) {
      upgrade.setSubProtocols(subProtocols);
    }
    if (headers != null) {
      headers.forEach(upgrade::setHeader);
    }

    CompletableFuture<WebSocketSession> result = new CompletableFuture<>();
    Listener listener = new Listener(handler, session, result);

    try {
      jettyClient
          .connect(listener, uri, upgrade)
          .whenComplete(
              (jettySession, error) -> {
                if (error != null) {
                  result.completeExceptionally(error);
                }
                // success path completes result via listener.onWebSocketOpen
              });
    } catch (Exception e) {
      result.completeExceptionally(e);
    }
    return result;
  }

  public static final class Listener implements Session.Listener.AutoDemanding {
    private final WebSocketHandler handler;
    private final JettyNativeWebSocketSession session;
    private final CompletableFuture<WebSocketSession> result;

    Listener(
        WebSocketHandler handler,
        JettyNativeWebSocketSession session,
        CompletableFuture<WebSocketSession> result) {
      this.handler = handler;
      this.session = session;
      this.result = result;
    }

    @Override
    public void onWebSocketOpen(Session jettySession) {
      session.initSession(jettySession);
      try {
        handler.afterConnectionEstablished(session);
      } catch (Exception e) {
        safeError(e);
        return;
      }
      result.complete(session);
    }

    @Override
    public void onWebSocketText(String message) {
      try {
        handler.handleMessage(session, new TextMessage(message));
      } catch (Exception e) {
        safeError(e);
      }
    }

    @Override
    public void onWebSocketBinary(ByteBuffer payload, Callback callback) {
      try {
        handler.handleMessage(session, new BinaryMessage(payload, true));
        callback.succeed();
      } catch (Exception e) {
        callback.fail(e);
        safeError(e);
      }
    }

    @Override
    public void onWebSocketError(Throwable cause) {
      safeError(cause);
      if (!result.isDone()) {
        result.completeExceptionally(cause);
      }
    }

    @Override
    public void onWebSocketClose(int statusCode, String reason) {
      try {
        handler.afterConnectionClosed(session, new CloseStatus(statusCode, reason));
      } catch (Exception ignored) {
      }
    }

    private void safeError(Throwable e) {
      try {
        handler.handleTransportError(session, e);
      } catch (Exception ignored) {
      }
    }
  }
}
