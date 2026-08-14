package org.slobben.cellserviceload.traffic;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

import lombok.Getter;
import lombok.Setter;
import org.slobben.cellserviceload.adapter.websocket.WebSocketClientConnection;

@Getter
public class Client {
  private final UUID id;
  private final int anchorX;
  private final int anchorY;
  private final WebSocketClientConnection webSocketClientConnection;
  private final ReentrantLock lock = new ReentrantLock();
  private final Set<String> blocksInView;

  @Setter private int originX;
  @Setter private int originY;
  @Setter private int viewSide;
  @Setter private int dx;
  @Setter private int dy;
  @Setter private int ticksInDirection;
  @Setter private long nextMoveAtMs;
  @Setter private long nextRefreshAtMs;

  public Client(
      UUID id,
      int anchorX,
      int anchorY,
      int viewSide,
      Set<String> initialBlocks,
      WebSocketClientConnection webSocketClientConnection) {
    this.id = id;
    this.anchorX = anchorX;
    this.anchorY = anchorY;
    this.originX = anchorX;
    this.originY = anchorY;
    this.viewSide = viewSide;
    this.blocksInView = new HashSet<>(initialBlocks);
    this.webSocketClientConnection = webSocketClientConnection;
  }
}
