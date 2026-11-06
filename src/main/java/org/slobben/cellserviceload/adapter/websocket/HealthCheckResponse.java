package org.slobben.cellserviceload.adapter.websocket;

public record HealthCheckResponse(Type type) {

  public enum Type {
    HEALTH_ACK,
    SESSION_DEAD
  }
}
