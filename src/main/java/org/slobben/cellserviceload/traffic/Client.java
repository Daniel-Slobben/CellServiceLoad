package org.slobben.cellserviceload.traffic;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import lombok.Getter;
import lombok.Setter;
import org.slobben.cellserviceload.adapter.websocket.BlockFrame;
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

  // Renewed on reconnect; each connection is a new client on the server.
  @Setter private volatile WebSocketClientConnection connection;
  private volatile int connectionNumber;
  private volatile long lastServerContactMs = System.currentTimeMillis();
  @Setter private volatile boolean reconnecting;
  // After failed reconnects: how long the last wait was, and when the next try is due.
  @Setter private volatile long retryDelayMs;
  @Setter private volatile long nextReconnectAtMs;
  // The server found none of the blocks the connection started with; the frontend then jumps elsewhere.
  @Setter private volatile boolean noBlocks;
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

  public Client(Camera camera, boolean zoomsOut, TrafficStats stats) {
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
  }

  /** Listens on a new connection; messages from an older one are ignored, like the frontend does. */
  public TopicListener newConnectionListener() {
    int number = ++connectionNumber;
    return new TopicListener() {
      @Override
      public void onBlocks(BlockFrame frame) {
        if (number != connectionNumber) return;
        long now = System.currentTimeMillis();
        if (lastFrameAtMs > 0) {
          stats.recordFrameGap(now - lastFrameAtMs);
        }
        lastFrameAtMs = now;
        lastServerContactMs = now;
        stats.recordFrame(frame);
      }

      @Override
      public void onNoBlocks() {
        if (number != connectionNumber) return;
        lastServerContactMs = System.currentTimeMillis();
        noBlocks = true;
        stats.count(TrafficStats.Counter.NO_BLOCKS);
      }
    };
  }
}
