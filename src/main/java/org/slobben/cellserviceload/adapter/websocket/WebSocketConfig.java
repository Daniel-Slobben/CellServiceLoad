package org.slobben.cellserviceload.adapter.websocket;

import java.util.List;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.converter.ByteArrayMessageConverter;
import org.springframework.messaging.converter.CompositeMessageConverter;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.messaging.WebSocketStompClient;

@Configuration
public class WebSocketConfig {

  @Bean(destroyMethod = "shutdown")
  public ThreadPoolTaskScheduler stompHeartbeatScheduler() {
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(4);
    scheduler.setThreadNamePrefix("stomp-hb-");
    scheduler.setDaemon(true);
    scheduler.initialize();
    return scheduler;
  }

  @Bean(destroyMethod = "stop")
  public QueuedThreadPool jettyThreadPool() {
    QueuedThreadPool pool = new QueuedThreadPool(128, 16);
    pool.setName("jetty-ws");
    pool.setDaemon(true);
    return pool;
  }

  // Every client opens its own websocket, like separate browsers would. Jetty's defaults (64
  // handshakes at a time, 1024 queued) would throttle those on our side during a reconnect storm.
  private static final int MAX_CONNECTIONS = 10_000;

  @Bean(initMethod = "start", destroyMethod = "stop")
  public HttpClient jettyHttpClient(QueuedThreadPool jettyThreadPool) {
    HttpClient httpClient = new HttpClient();
    httpClient.setExecutor(jettyThreadPool);
    httpClient.setMaxConnectionsPerDestination(MAX_CONNECTIONS);
    httpClient.setMaxRequestsQueuedPerDestination(MAX_CONNECTIONS);
    return httpClient;
  }

  // Adding many blocks returns them all as FULL blocks in one frame, which is large when zoomed out.
  private static final int MAX_MESSAGE_BYTES = 64 * 1024 * 1024;

  @Bean
  public JettyNativeWebSocketClient jettyNativeWebSocketClient(HttpClient jettyHttpClient) {
    WebSocketClient jettyWs = new WebSocketClient(jettyHttpClient);
    jettyWs.setMaxTextMessageSize(MAX_MESSAGE_BYTES);
    jettyWs.setMaxBinaryMessageSize(MAX_MESSAGE_BYTES);
    return new JettyNativeWebSocketClient(jettyWs);
  }

  @Bean
  public WebSocketStompClient webSocketStompClient(
      JettyNativeWebSocketClient jettyNativeWebSocketClient,
      ThreadPoolTaskScheduler stompHeartbeatScheduler) {
    WebSocketStompClient stompClient = new WebSocketStompClient(jettyNativeWebSocketClient);
    // block frames arrive as octet-stream, health check responses and our requests are JSON
    stompClient.setMessageConverter(
        new CompositeMessageConverter(
            List.of(new ByteArrayMessageConverter(), new JacksonJsonMessageConverter())));
    stompClient.setTaskScheduler(stompHeartbeatScheduler);
    stompClient.setDefaultHeartbeat(new long[] {0, 0});
    stompClient.setInboundMessageSizeLimit(MAX_MESSAGE_BYTES);
    return stompClient;
  }
}
