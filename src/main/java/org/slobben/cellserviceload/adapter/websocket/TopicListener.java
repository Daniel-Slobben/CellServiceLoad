package org.slobben.cellserviceload.adapter.websocket;

/** Receives what the server sends to a client's session. */
public interface TopicListener {
  default void onBlocks(BlockFrame frame) {}

  /** None of the blocks the connection started with exist. */
  default void onNoBlocks() {}
}
