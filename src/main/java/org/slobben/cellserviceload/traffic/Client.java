package org.slobben.cellserviceload.traffic;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

import lombok.Getter;
import lombok.Setter;
import org.slobben.cellserviceload.adapter.websocket.BlockFrame;
import org.slobben.cellserviceload.adapter.websocket.HealthCheckResponse;
import org.slobben.cellserviceload.adapter.websocket.TopicListener;
import org.slobben.cellserviceload.adapter.websocket.WebSocketClientConnection;

@Getter
public class Client {
  private final int blockSize;
  private final TopicListener recorder;
  private final ReentrantLock lock = new ReentrantLock();
  private final Set<String> blocksInView;

  // Change on reconnect: the server hands out a new id and the socket is renewed.
  @Setter private volatile UUID id;
  @Setter private volatile WebSocketClientConnection connection;
  private volatile long lastServerContactMs = System.currentTimeMillis();
  private volatile boolean sessionDead;
  @Setter private volatile boolean reconnecting;
  @Setter private volatile long nextHealthCheckAtMs;

  private int anchorX;
  private int anchorY;
  @Setter private int originX;
  @Setter private int originY;
  @Setter private int viewSide;
  @Setter private int dx;
  @Setter private int dy;
  @Setter private int ticksInDirection;
  @Setter private long nextMoveAtMs;

  public Client(UUID id, int blockSize, int anchorX, int anchorY, int viewSide, TopicListener recorder) {
    this.id = id;
    this.blockSize = blockSize;
    this.viewSide = viewSide;
    this.recorder = recorder;
    this.blocksInView = new HashSet<>();
    recenter(anchorX, anchorY);
  }

  /** Jumps to a new viewport, as the frontend does after a reconnect that returns one. */
  public void recenter(int anchorX, int anchorY) {
    this.anchorX = anchorX;
    this.anchorY = anchorY;
    this.originX = anchorX;
    this.originY = anchorY;
    blocksInView.clear();
    blocksInView.addAll(ViewportMover.computeView(anchorX, anchorY, viewSide));
  }

  public void markServerContact() {
    lastServerContactMs = System.currentTimeMillis();
    sessionDead = false;
  }

  /** Listens on the topic of one client id; messages for an older id are ignored, like the frontend does. */
  public TopicListener listenerFor(UUID topicId) {
    return new TopicListener() {
      @Override
      public void onBlocks(BlockFrame frame) {
        if (!topicId.equals(id)) return;
        lastServerContactMs = System.currentTimeMillis();
        recorder.onBlocks(frame);
      }

      @Override
      public void onHealthCheck(HealthCheckResponse.Type type) {
        if (!topicId.equals(id)) return;
        lastServerContactMs = System.currentTimeMillis();
        if (type == HealthCheckResponse.Type.SESSION_DEAD) {
          sessionDead = true;
        }
        recorder.onHealthCheck(type);
      }
    };
  }
}
