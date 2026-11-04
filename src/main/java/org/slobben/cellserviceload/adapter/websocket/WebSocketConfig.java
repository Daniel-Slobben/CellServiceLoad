package org.slobben.cellserviceload.adapter.websocket;

import java.util.List;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

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

  @Bean(initMethod = "start", destroyMethod = "stop")
  public HttpClient jettyHttpClient(QueuedThreadPool jettyThreadPool) {
    HttpClient httpClient = new HttpClient();
    httpClient.setExecutor(jettyThreadPool);
    return httpClient;
  }

  private static final long MAX_MESSAGE_BYTES = 1024L * 1024L;

  @Bean
  public JettyNativeWebSocketClient jettyNativeWebSocketClient(HttpClient jettyHttpClient) {
    WebSocketClient jettyWs = new WebSocketClient(jettyHttpClient);
    jettyWs.setMaxTextMessageSize(MAX_MESSAGE_BYTES);
    jettyWs.setMaxBinaryMessageSize(MAX_MESSAGE_BYTES);
    return new JettyNativeWebSocketClient(jettyWs);
  }

  @Bean
  public SockJsClient sockJsClient(
      JettyNativeWebSocketClient jettyNativeWebSocketClient,
      ThreadPoolTaskScheduler stompHeartbeatScheduler) {
    List<Transport> transports = List.of(new WebSocketTransport(jettyNativeWebSocketClient));
    SockJsClient sockJsClient = new SockJsClient(transports);
    sockJsClient.setConnectTimeoutScheduler(stompHeartbeatScheduler);
    return sockJsClient;
  }

  @Bean
  public WebSocketStompClient webSocketStompClient(
      SockJsClient sockJsClient, ThreadPoolTaskScheduler stompHeartbeatScheduler) {
    WebSocketStompClient stompClient = new WebSocketStompClient(sockJsClient);
    stompClient.setMessageConverter(new JacksonJsonMessageConverter());
    stompClient.setTaskScheduler(stompHeartbeatScheduler);
    stompClient.setDefaultHeartbeat(new long[] {0, 0});
    stompClient.setInboundMessageSizeLimit((int) MAX_MESSAGE_BYTES);
    return stompClient;
  }
}
