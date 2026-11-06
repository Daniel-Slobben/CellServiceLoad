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
  private final Camera camera;
  // Some users zoom all the way out; they watch many more blocks.
  private final boolean zoomsOut;
  private final TrafficStats stats;
  private final ReentrantLock lock = new ReentrantLock();
  private final Set<String> blocksInView = new HashSet<>();
  @Setter private int[] viewRange;

  // Change on reconnect: the server hands out a new id and the socket is renewed.
  @Setter private volatile UUID id;
  @Setter private volatile WebSocketClientConnection connection;
  private volatile long lastServerContactMs = System.currentTimeMillis();
  private volatile boolean sessionDead;
  @Setter private volatile boolean reconnecting;
  @Setter private volatile long nextHealthCheckAtMs;
  @Setter private volatile long healthCheckSentAtMs;
  // Not reset on reconnect, so a gap includes the time a reconnect took.
  private volatile long lastFrameAtMs;

  @Setter private volatile ViewportMover.Action action = ViewportMover.Action.IDLE;
  @Setter private volatile long actionEndsAtMs;
  @Setter private long lastMoveAtMs;
  @Setter private double panVelocityX;
  @Setter private double panVelocityY;
  @Setter private double zoomTarget;
  @Setter private double zoomPointX;
  @Setter private double zoomPointY;

  public Client(UUID id, Camera camera, boolean zoomsOut, TrafficStats stats) {
    this.id = id;
    this.camera = camera;
    this.zoomsOut = zoomsOut;
    this.stats = stats;
  }

  /** Jumps to a viewport and takes the blocks there as the view, as at start and after a reconnect. */
  public void centerOn(int worldX, int worldY) {
    camera.centerOn(worldX, worldY);
    viewRange = camera.blockRange();
    blocksInView.clear();
    blocksInView.addAll(Camera.blocksIn(viewRange));
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
        long now = System.currentTimeMillis();
        if (lastFrameAtMs > 0) {
          stats.recordFrameGap(now - lastFrameAtMs);
        }
        lastFrameAtMs = now;
        lastServerContactMs = now;
        stats.recordFrame(frame);
      }

      @Override
      public void onHealthCheck(HealthCheckResponse.Type type) {
        if (!topicId.equals(id)) return;
        long now = System.currentTimeMillis();
        lastServerContactMs = now;
        if (type == HealthCheckResponse.Type.SESSION_DEAD) {
          sessionDead = true;
          stats.count(TrafficStats.Counter.SESSIONS_DEAD);
        } else if (healthCheckSentAtMs > 0) {
          stats.getHealthAckRtt().record(now - healthCheckSentAtMs);
          healthCheckSentAtMs = 0;
        }
      }
    };
  }
}
