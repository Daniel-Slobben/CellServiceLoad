package org.slobben.cellserviceload.adapter.websocket;

public record StatusResponse(Type type) {

  public enum Type {
    NO_BLOCKS
  }
}
