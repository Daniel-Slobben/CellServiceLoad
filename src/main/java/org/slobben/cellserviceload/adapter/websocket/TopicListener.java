package org.slobben.cellserviceload.adapter.websocket;

/** Receives what the server sends on a client's topic. */
public interface TopicListener {
  TopicListener NONE = new TopicListener() {};

  default void onBlocks(BlockFrame frame) {}

  default void onHealthCheck(HealthCheckResponse.Type type) {}
}
