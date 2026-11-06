package org.slobben.cellserviceload.adapter.websocket;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.jetty.websocket.api.Callback;
import org.eclipse.jetty.websocket.api.Session;
import org.springframework.http.HttpHeaders;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.PongMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketExtension;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

class JettyNativeWebSocketSession implements WebSocketSession {

  private final String id = UUID.randomUUID().toString();
  private final URI uri;
  private final HttpHeaders handshakeHeaders;
  private final Map<String, Object> attributes = new ConcurrentHashMap<>();
  private volatile Session session;
  private int textLimit = 256 * 1024;
  private int binaryLimit = 64 * 1024;

  JettyNativeWebSocketSession(URI uri, HttpHeaders handshakeHeaders) {
    this.uri = uri;
    this.handshakeHeaders = handshakeHeaders;
  }

  void initSession(Session session) {
    this.session = session;
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  public URI getUri() {
    return uri;
  }

  @Override
  public HttpHeaders getHandshakeHeaders() {
    return handshakeHeaders;
  }

  @Override
  public Map<String, Object> getAttributes() {
    return attributes;
  }

  @Override
  public Principal getPrincipal() {
    return null;
  }

  @Override
  public InetSocketAddress getLocalAddress() {
    return asInet(session != null ? session.getLocalSocketAddress() : null);
  }

  @Override
  public InetSocketAddress getRemoteAddress() {
    return asInet(session != null ? session.getRemoteSocketAddress() : null);
  }

  private static InetSocketAddress asInet(SocketAddress a) {
    return (a instanceof InetSocketAddress i) ? i : null;
  }

  @Override
  public String getAcceptedProtocol() {
    return null;
  }

  @Override
  public void setTextMessageSizeLimit(int limit) {
    this.textLimit = limit;
    if (session != null) session.setMaxTextMessageSize(limit);
  }

  @Override
  public int getTextMessageSizeLimit() {
    return textLimit;
  }

  @Override
  public void setBinaryMessageSizeLimit(int limit) {
    this.binaryLimit = limit;
    if (session != null) session.setMaxBinaryMessageSize(limit);
  }

  @Override
  public int getBinaryMessageSizeLimit() {
    return binaryLimit;
  }

  @Override
  public List<WebSocketExtension> getExtensions() {
    return List.of();
  }

  @Override
  public void sendMessage(WebSocketMessage<?> message) throws IOException {
    if (session == null || !session.isOpen()) {
      throw new IOException("session not open");
    }
    if (message instanceof TextMessage t) {
      session.sendText(t.getPayload(), Callback.NOOP);
    } else if (message instanceof BinaryMessage b) {
      session.sendBinary(b.getPayload(), Callback.NOOP);
    } else if (message instanceof PingMessage p) {
      session.sendPing(p.getPayload(), Callback.NOOP);
    } else if (message instanceof PongMessage p) {
      session.sendPong(p.getPayload(), Callback.NOOP);
    } else {
      throw new IllegalArgumentException("unsupported message type: " + message.getClass());
    }
  }

  @Override
  public boolean isOpen() {
    return session != null && session.isOpen();
  }

  @Override
  public void close() throws IOException {
    close(CloseStatus.NORMAL);
  }

  @Override
  public void close(CloseStatus status) {
    if (session != null && session.isOpen()) {
      session.close(status.getCode(), status.getReason(), Callback.NOOP);
    }
  }

}
